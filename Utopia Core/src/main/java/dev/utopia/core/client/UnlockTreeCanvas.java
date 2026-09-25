package dev.utopia.core.client;

import dev.utopia.core.unlock.NodeShape;
import dev.utopia.core.unlock.UnlockTree;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Karte des Freischaltbaums: frei schwenk- und zoombar, Knoten als Form mit Item-Icon.
 *
 * <p><b>Warum alles als Vektorform:</b> Formen, Rahmen, Linien und Abzeichen waren
 * Texturen von 32 bis 64 px, die beim Hineinzoomen auf fast das Dreifache vergroessert
 * wurden — mit sichtbaren Treppenkanten. Jetzt rechnet {@link SmoothPainter} sie in jeder
 * Groesse neu und glaettet die Kanten auf einen Bildschirmpixel genau.
 *
 * <p><b>Warum es jetzt fluessig laeuft:</b> Positionen wurden auf ganze GUI-Pixel
 * gerundet. Bei GUI-Massstab 3 sind das drei Bildschirmpixel, die ganze Karte ruckte beim
 * Schwenken also in Dreiersprüngen hinter dem Mauszeiger her. Jetzt wird auf echte
 * Bildschirmpixel gerundet. Die Knotengroesse rastete auf Vielfache von vier ein und
 * sprang beim Zoomen sichtbar; jetzt waechst sie stetig. Und die Annaeherung an den
 * Zielzoom lief pro Bild statt pro Zeit — bei 144 Bildern je Sekunde viermal so schnell
 * wie bei 30, bei schwankender Bildrate ruckelnd.
 *
 * <p><b>Beschriftung:</b> Namen stehen unter den Knoten, aber nur, wo Platz ist. 68
 * Knoten mit Textkasten brauchen rund das Doppelte der Flaeche; deshalb blenden die Namen
 * unterhalb von 24 GUI-Einheiten Knotengroesse aus, und ein Name, der einen anderen
 * Knoten oder Namen verdecken wuerde, entfaellt. Vorrang haben Auswahl, Zeiger,
 * Suchtreffer und dann das, was man als Naechstes kaufen kann.
 *
 * <p><b>Warum Form und Abzeichen statt nur Farbe:</b> Die frueheren Zustandsfarben
 * lagen bei 1,18:1 zueinander — "schon gekauft" und "jetzt kaufbar" waren praktisch nicht
 * unterscheidbar, fuer Rot-Gruen-Schwaeche gar nicht. Zustand laeuft deshalb ueber das
 * Abzeichen (Haken, Plus, Muenze, Schloss), die Form ueber die Art des Knotens.
 *
 * <p>Die Kamera ist mittenbasiert ({@link #centerX}/{@link #centerY} ist die Modell-
 * koordinate in der Canvas-Mitte). Dadurch skaliert ein Zoom um die Mitte statt um die
 * linke obere Ecke, und die weiche Annaeherung an den Zielzoom bleibt anschaulich.
 */
final class UnlockTreeCanvas {

    enum State { OWNED, BUYABLE, TOO_EXPENSIVE, BLOCKED }

    interface StateProvider {
        State state(String key, UnlockTree.Node node);
    }

    interface MoveListener {
        void move(String key, double x, double y);
    }

    private static final double UNIT = 64.0;
    private static final double MIN_ZOOM = 0.08;
    private static final double MAX_ZOOM = 2.0;
    /** Zoomschritt je Mausrad-Raste. */
    private static final double ZOOM_STEP = 1.2;
    /** Knotengroesse bei 100 %. */
    private static final double NODE_BASE = 72.0;
    private static final double DRAG_THRESHOLD = 4.0;
    private static final float DASH_LENGTH = 6.0F;
    private static final float DASH_PERIOD = 14.0F;
    /** Anteil, um den sich die Ansicht je Sechzigstelsekunde dem Ziel naehert. */
    private static final double EASING = 0.4;
    /**
     * Geschwindigkeit des Flusses entlang der hervorgehobenen Kette, GUI-Einheiten je
     * Sekunde. Das Muster stammt aus FTB Quests: Es zeigt die Richtung vom Vorgaenger zum
     * Nachfolger. Anders als dort laeuft es nur an der betrachteten Kette — in Ruhe steht
     * die Karte still. 0 schaltet es ab.
     */
    private static final double FLOW_SPEED = 18.0;

    // Formmasse als Anteil der Knotengroesse, von den frueheren 64-px-Texturen abgenommen,
    // damit sich nur die Kantenschaerfe aendert und nicht die Gestalt.
    private static final double SHAPE_RADIUS = 0.469;
    private static final double SQUARE_HALF = 0.422;
    private static final double RSQUARE_HALF = 0.4375;
    private static final double RSQUARE_CORNER = 0.27;
    private static final double GEAR_ROOT = 0.352;
    private static final double GEAR_TOOTH_HALF = 0.078;
    private static final int GEAR_TEETH = 8;
    private static final double OUTLINE = 0.075;
    /** Icongroesse im Verhaeltnis zum Knoten. */
    private static final double ICON = 0.78;
    /** Abzeichen: Durchmesser und Mittelpunkt (rechts unten) im Verhaeltnis zum Knoten. */
    private static final double BADGE = 0.42;
    private static final double BADGE_OFFSET = 0.30;

    /** Unterhalb dieser Knotengroesse (GUI-Einheiten) keine Namen; darueber blenden sie ein. */
    private static final double LABEL_FADE_START = 18.0;
    private static final double LABEL_FADE_END = 24.0;

    /** Tiefe fuer alles, was ueber den Item-Icons liegen muss (die liegen bei etwa 150). */
    private static final float Z_OVER_ICON = 200.0F;

    /**
     * Flache Kartenflaeche.
     *
     * Derselbe Farbton wie das abgedunkelte Holz vorher (gemessen #42321D), aber ohne
     * Maserung und etwas dunkler: relative Leuchtdichte 0,022 statt 0,035. Das ist naeher
     * am Mockup und bleibt heller als der Holzrahmen (0,014), damit die Karte eine eigene
     * Flaeche bleibt. Alle Rahmenkontraste steigen dadurch: gesperrt 3,7:1 (vorher 3,2),
     * zu teuer 6,5:1, freigeschaltet 10,1:1, kaufbar 10,6:1. Die Reihenfolge der
     * Helligkeitsleiter bleibt erhalten, und die Fuellung gesperrter Knoten (0,036) liegt
     * weiter ueber dem Grund — sie liest sich als Platte, nicht als Loch.
     */
    private static final int COLOR_CANVAS = 0xFF342716;

    // Palette. Die Rahmen bilden bewusst eine Helligkeitsleiter statt nur verschiedener
    // Farbtoene, damit der Zustand auch ohne Farbsehen ankommt:
    //   gesperrt  <  zu teuer  <  kaufbar  ~  freigeschaltet
    // Die beiden oberen Stufen sind absichtlich gleich hell — sie sind beide "gut" und
    // werden durch Farbton (gruen/gold) und Abzeichen (Haken/Plus) auseinandergehalten.
    //
    // Gesperrte Knoten treten ueber einen dunkleren Rahmen zurueck, ihr Item-Icon bleibt
    // aber sichtbar: der Abdunkler darueber liegt bei 0x44, nicht bei 0x99. Mit 0x99 waren
    // sie schwarze Kleckse, bei denen man nicht mehr erkannte, worum es ueberhaupt geht.
    private static final int COLOR_OWNED_OUTLINE = 0xFFA6E88A;
    private static final int COLOR_BUYABLE_OUTLINE = 0xFFFFD866;
    private static final int COLOR_EXPENSIVE_OUTLINE = 0xFFC8A87A;
    private static final int COLOR_BLOCKED_OUTLINE = 0xFF8C7E6C;
    private static final int COLOR_OWNED_FILL = 0xFF324A26;
    private static final int COLOR_BUYABLE_FILL = 0xFF54401C;
    private static final int COLOR_EXPENSIVE_FILL = 0xFF4A3B24;
    private static final int COLOR_BLOCKED_FILL = 0xFF3B342C;
    private static final int COLOR_EDGE_OWNED = 0xFFA8E88C;
    private static final int COLOR_EDGE_OPEN = 0xFFFCD596;
    private static final int COLOR_EDGE_SECONDARY = 0xFFB4E0EC;
    private static final int COLOR_FLOW = 0xFFFFF3C4;
    private static final int COLOR_SELECTED = 0xFFFFF3C4;
    private static final int COLOR_HOVERED = 0xFFE8CE92;
    private static final int COLOR_SEARCH_MATCH = 0xFFFFD98A;
    private static final int COLOR_SHADE = 0xFF120C08;
    private static final int COLOR_BADGE_GROUND = 0xFF140E08;
    private static final int COLOR_GRID = 0x22C8A87A;
    /** Namen: 10:1 gegen den Grund; gesperrte gedaempft, aber noch 4,9:1. */
    private static final int COLOR_LABEL = 0xFFE9D6B0;
    private static final int COLOR_LABEL_BLOCKED = 0xFFA39480;

    private TextRenderer textRenderer;
    private final List<Edge> edgeBuffer = new ArrayList<>();
    private final Set<String> highlightChain = new HashSet<>();
    private final List<double[]> placedLabels = new ArrayList<>();
    private final Map<String, String[]> wrapCache = new HashMap<>();

    private int left;
    private int top;
    private int right;
    private int bottom;
    /** Links davon liegt gerade die aufgeklappte Baumliste ueber der Karte. */
    private int occludeLeft = Integer.MIN_VALUE;

    /** Ziel der Kamera: Modellkoordinate, die in der Canvas-Mitte liegen soll. */
    private double centerX;
    private double centerY;
    private double zoom = 1.0;
    /** Dargestellte Kamera; naehert sich dem Ziel weich an. */
    private double viewCenterX;
    private double viewCenterY;
    private double viewZoom = 1.0;
    private long lastFrameNanos;

    /**
     * Zoom mit festgehaltenem Punkt: Waehrend der Annaeherung bleibt der Modellpunkt, auf
     * den das Mausrad zielte, genau unter dem Zeiger. Vorher naeherten sich Mitte und
     * Zoom getrennt an, und der Punkt unter dem Zeiger wanderte waehrend der Animation.
     */
    private boolean anchored;
    private double anchorScreenX;
    private double anchorScreenY;
    private double anchorModelX;
    private double anchorModelY;

    private boolean panning;

    private String draggedNode;
    private String pressedNode;
    private boolean gestureActive;
    private int pressedButton = -1;
    private double pressMouseX;
    private double pressMouseY;
    private double dragOffsetX;
    private double dragOffsetY;
    private String selected;
    private String hovered;
    private Set<String> searchMatches;

    void bounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = Math.max(left + 1, right);
        this.bottom = Math.max(top + 1, bottom);
    }

    /**
     * Bis wohin von links die aufgeklappte Baumliste die Karte gerade verdeckt, oder
     * {@link Integer#MIN_VALUE}. Darunter wird nicht gezeichnet und nichts getroffen —
     * sonst schienen Item-Icons, die tiefer liegen als die Liste, durch sie hindurch.
     */
    void occludeLeft(int x) {
        this.occludeLeft = x;
    }

    String selected() {
        return selected;
    }

    String hovered() {
        return hovered;
    }

    void select(String key) {
        selected = key;
    }

    /**
     * null = keine Suche aktiv. Eine leere, aber vorhandene Menge bedeutet "gesucht und
     * nichts gefunden" und nimmt alle Knoten zurueck — sonst waere eine Suche ohne Treffer
     * von gar keiner Suche nicht zu unterscheiden.
     */
    void searchMatches(Set<String> matches) {
        this.searchMatches = matches;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= Math.max(left, occludeLeft) && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    int zoomPercent() {
        return (int) Math.round(zoom * 100.0);
    }

    /** Ob die Karte gerade geschwenkt wird. */
    boolean panning() {
        return panning;
    }

    // --- Zeichnen ---------------------------------------------------------

    void render(DrawContext context, UnlockTree tree, StateProvider states, boolean editing,
            int mouseX, int mouseY) {
        if (textRenderer == null) {
            textRenderer = MinecraftClient.getInstance().textRenderer;
        }
        ease();
        int clipLeft = Math.max(left, occludeLeft);
        if (clipLeft >= right) {
            return;
        }
        context.enableScissor(clipLeft, top, right, bottom);
        context.fill(clipLeft, top, right, bottom, COLOR_CANVAS);
        if (editing) {
            drawEditorGrid(context);
        }

        hovered = contains(mouseX, mouseY) ? nodeAt(tree, mouseX, mouseY) : null;
        buildHighlightChain(tree, hovered != null ? hovered : selected);

        drawEdges(context, tree, states);

        double size = nodeSize();
        List<Map.Entry<String, UnlockTree.Node>> visible = new ArrayList<>();
        for (Map.Entry<String, UnlockTree.Node> entry : tree.nodes().entrySet()) {
            if (nodeVisible(entry.getValue(), size)) {
                visible.add(entry);
            }
        }
        drawNodes(context, visible, states, size);
        drawLabels(context, visible, states, size);
        context.disableScissor();
    }

    private void ease() {
        long now = Util.getMeasuringTimeNano();
        double seconds = lastFrameNanos == 0L ? 0.0 : Math.min(0.1, (now - lastFrameNanos) / 1.0E9);
        lastFrameNanos = now;
        // Dieselbe Annaeherung wie frueher bei 60 Bildern je Sekunde, aber an der Zeit statt
        // an der Bildzahl gemessen: gleich schnell bei 30 und bei 144 Bildern.
        double t = 1.0 - Math.pow(1.0 - EASING, seconds * 60.0);

        // Zoom auf logarithmischer Skala: jede Raste fuehlt sich gleich an, egal wie nah.
        double logView = Math.log(viewZoom);
        viewZoom = Math.exp(logView + (Math.log(zoom) - logView) * t);
        boolean zoomDone = Math.abs(zoom - viewZoom) < 0.0005;
        if (zoomDone) {
            viewZoom = zoom;
        }

        if (anchored) {
            viewCenterX = anchorModelX - (anchorScreenX - midX()) / (UNIT * viewZoom);
            viewCenterY = anchorModelY - (anchorScreenY - midY()) / (UNIT * viewZoom);
            if (zoomDone) {
                anchored = false;
                viewCenterX = centerX;
                viewCenterY = centerY;
            }
            return;
        }
        viewCenterX += (centerX - viewCenterX) * t;
        viewCenterY += (centerY - viewCenterY) * t;
        // Restweg abschneiden, damit die Ansicht nicht ewig um Bruchteile zittert.
        if (Math.abs(centerX - viewCenterX) < 0.0005) {
            viewCenterX = centerX;
        }
        if (Math.abs(centerY - viewCenterY) < 0.0005) {
            viewCenterY = centerY;
        }
    }

    private void drawEditorGrid(DrawContext context) {
        double spacing = Math.max(10.0, UNIT * 0.5 * viewZoom);
        double pixel = SmoothPainter.pixel();
        double originX = screenX(0.0);
        double originY = screenY(0.0);
        // Haarlinien, auf Pixelmitte gesetzt: auf der Pixelgrenze waeren es zwei halbe
        // Pixel und damit ein verwaschener Streifen.
        SmoothPainter painter = SmoothPainter.begin(context, 0.0F);
        double startX = left + (((originX - left) % spacing) + spacing) % spacing;
        for (double x = startX; x < right; x += spacing) {
            double px = SmoothPainter.snap(x) + pixel * 0.5;
            painter.line(px, top, px, bottom, (float) pixel, COLOR_GRID);
        }
        double startY = top + (((originY - top) % spacing) + spacing) % spacing;
        for (double y = startY; y < bottom; y += spacing) {
            double py = SmoothPainter.snap(y) + pixel * 0.5;
            painter.line(left, py, right, py, (float) pixel, COLOR_GRID);
        }
        painter.end();
    }

    /**
     * Alle Voraussetzungen des betrachteten Knotens, transitiv.
     *
     * Dauerhaft gezeichnet wird nur die erste Voraussetzung jedes Knotens. Die Kette hier
     * bestimmt, welche Kanten zusaetzlich erscheinen und hervorgehoben werden, damit man
     * beim Planen sieht, was insgesamt noch fehlt.
     */
    private void buildHighlightChain(UnlockTree tree, String focus) {
        highlightChain.clear();
        if (focus == null) {
            return;
        }
        List<String> queue = new ArrayList<>();
        queue.add(focus);
        highlightChain.add(focus);
        for (int i = 0; i < queue.size() && i < 512; i++) {
            UnlockTree.Node node = tree.nodes().get(queue.get(i));
            if (node == null) {
                continue;
            }
            for (String parent : node.parents()) {
                if (tree.nodes().containsKey(parent) && highlightChain.add(parent)) {
                    queue.add(parent);
                }
            }
        }
    }

    private void drawEdges(DrawContext context, UnlockTree tree, StateProvider states) {
        edgeBuffer.clear();
        double radius = nodeSize() * 0.5 + 1.5;
        for (Map.Entry<String, UnlockTree.Node> entry : tree.nodes().entrySet()) {
            String childKey = entry.getKey();
            UnlockTree.Node child = entry.getValue();
            List<String> parents = child.parents();
            for (int i = 0; i < parents.size(); i++) {
                String parentKey = parents.get(i);
                UnlockTree.Node parent = tree.nodes().get(parentKey);
                if (parent == null) {
                    continue;
                }
                boolean primary = i == 0;
                boolean lit = highlightChain.contains(childKey) && highlightChain.contains(parentKey);
                // Zusaetzliche Voraussetzungen erscheinen NUR am betrachteten Knoten.
                //
                // Dauerhaft gezeichnet waren sie ein Netz gestrichelter Linien quer ueber die
                // ganze Karte, dem man nicht ansah, wozu es gehoert — es verdeckte die Baumform,
                // statt sie zu ergaenzen. Die Angabe geht dadurch nicht verloren: beim
                // Darueberfahren leuchtet die vollstaendige Kette auf, und der Inspektor
                // listet alle Vorgaenger mit Namen auf.
                if (!primary && !lit) {
                    continue;
                }
                int color = primary
                        ? (states.state(parentKey, parent) == State.OWNED ? COLOR_EDGE_OWNED : COLOR_EDGE_OPEN)
                        : COLOR_EDGE_SECONDARY;
                if (!lit) {
                    color = withAlpha(color, 0xA0);
                }
                Edge edge = edge(parent, child, radius, primary, lit, color);
                if (edge != null && edgeVisible(edge)) {
                    edgeBuffer.add(edge);
                }
            }
        }
        if (edgeBuffer.isEmpty()) {
            return;
        }
        // Strichstaerke haengt an der Knotengroesse, damit Kanten beim Herauszoomen nicht
        // breiter wirken als die Knoten, die sie verbinden. Mindestens zwei Bildschirmpixel,
        // sonst verschwinden sie im Grund.
        float weight = (float) Math.max(SmoothPainter.pixel() * 2.0, nodeSize() / 20.0);
        float phase = (float) (-(Util.getMeasuringTimeMs() / 1000.0) * FLOW_SPEED);
        SmoothPainter painter = SmoothPainter.begin(context, 0.0F);
        // Erst die ruhenden Kanten, die hervorgehobenen zuletzt: so liegt die Kette des
        // betrachteten Knotens immer obenauf.
        for (Edge edge : edgeBuffer) {
            if (!edge.lit()) {
                drawConnection(painter, edge, edge.primary() ? weight : weight * 0.8F, 0.0F);
            }
        }
        for (Edge edge : edgeBuffer) {
            if (edge.lit()) {
                drawConnection(painter, edge, edge.primary() ? weight * 1.4F : weight, phase);
            }
        }
        painter.end();
    }

    private static void drawConnection(SmoothPainter painter, Edge edge, float width, float phase) {
        if (!edge.primary()) {
            painter.dashed(edge.x1(), edge.y1(), edge.x2(), edge.y2(), width, DASH_LENGTH, DASH_PERIOD,
                    phase, edge.color());
            return;
        }
        painter.line(edge.x1(), edge.y1(), edge.x2(), edge.y2(), width, edge.color());
        if (edge.lit() && FLOW_SPEED > 0.0) {
            // Heller Fluss auf der Linie, vom Vorgaenger zum Nachfolger.
            painter.dashed(edge.x1(), edge.y1(), edge.x2(), edge.y2(), Math.max(width * 0.5F,
                    SmoothPainter.pixel()), DASH_PERIOD * 0.35F, DASH_PERIOD, phase, withAlpha(COLOR_FLOW, 0x90));
        }
    }

    /** Kante zwischen zwei Knotenraendern statt zwischen den Mittelpunkten. */
    private Edge edge(UnlockTree.Node parent, UnlockTree.Node child, double radius, boolean primary,
            boolean lit, int color) {
        double x1 = screenX(parent.x());
        double y1 = screenY(parent.y());
        double x2 = screenX(child.x());
        double y2 = screenY(child.y());
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        if (length <= radius * 2.0 + 1.0) {
            return null;
        }
        double ux = dx / length;
        double uy = dy / length;
        return new Edge(x1 + ux * radius, y1 + uy * radius, x2 - ux * radius, y2 - uy * radius, primary, lit,
                color);
    }

    /**
     * Knoten in drei Lagen: erst alle Flaechen, dann die Item-Icons, dann alles, was ueber
     * den Icons liegen muss (Abdunklung, Rahmen, Abzeichen). Jede Vektorlage geht in einem
     * einzigen Aufruf an die Grafikkarte.
     */
    private void drawNodes(DrawContext context, List<Map.Entry<String, UnlockTree.Node>> visible,
            StateProvider states, double size) {
        SmoothPainter base = SmoothPainter.begin(context, 0.0F);
        for (Map.Entry<String, UnlockTree.Node> entry : visible) {
            String key = entry.getKey();
            UnlockTree.Node node = entry.getValue();
            State state = states.state(key, node);
            double cx = screenX(node.x());
            double cy = screenY(node.y());
            int alpha = dimmed(key) ? 0x30 : 0xFF;
            boolean isSelected = key.equals(selected);
            if (isSelected || key.equals(hovered)) {
                // Auswahl- und Zeigerring: dieselbe Form, etwas groesser, hinter dem Knoten.
                double ring = size + 6.0;
                float[][] halo = shape(node.shape(), cx, cy, ring);
                base.ring(halo[0], halo[1], (float) (ring * OUTLINE),
                        withAlpha(isSelected ? COLOR_SELECTED : COLOR_HOVERED, alpha));
            }
            if (searchMatches != null && searchMatches.contains(key)) {
                double ring = size + 10.0;
                float[][] halo = shape(node.shape(), cx, cy, ring);
                base.ring(halo[0], halo[1], (float) (ring * OUTLINE), COLOR_SEARCH_MATCH);
            }
            float[][] body = shape(node.shape(), cx, cy, size);
            base.fill(body[0], body[1], (float) cx, (float) cy, withAlpha(fillColor(state), alpha));
        }
        base.end();

        boolean[] hasIcon = new boolean[visible.size()];
        for (int i = 0; i < visible.size(); i++) {
            UnlockTree.Node node = visible.get(i).getValue();
            Item item = node.icon().map(Registries.ITEM::get).orElse(null);
            hasIcon[i] = item != null && item != net.minecraft.item.Items.AIR;
            if (hasIcon[i]) {
                drawIcon(context, item, screenX(node.x()), screenY(node.y()), size);
            }
        }

        SmoothPainter over = SmoothPainter.begin(context, Z_OVER_ICON);
        for (int i = 0; i < visible.size(); i++) {
            String key = visible.get(i).getKey();
            UnlockTree.Node node = visible.get(i).getValue();
            State state = states.state(key, node);
            double cx = screenX(node.x());
            double cy = screenY(node.y());
            boolean dim = dimmed(key);
            int alpha = dim ? 0x30 : 0xFF;
            float[][] body = shape(node.shape(), cx, cy, size);
            // Abdunkeln, in zwei Staerken.
            //
            // Ueber dem Item, nicht per Alpha: Item-Modelle werden mit ihrem eigenen Shader
            // gezeichnet und nehmen die Einfaerbung des Knotens nicht an. Ohne diese Schicht
            // blieben zurueckgestellte Knoten bei der Suche mit voll leuchtendem Icon stehen,
            // waehrend Rahmen und Abzeichen verblassten — genau die falsche Haelfte.
            int shade = 0;
            if (dim) {
                shade = 0xA0;
            } else if (state == State.BLOCKED && hasIcon[i]) {
                // Gesperrt: nur so weit, dass man noch erkennt, worum es geht. Nur mit Icon —
                // ohne eines wuerde die Fuellung getroffen und der Knoten saehe auf der
                // dunklen Karte wie ein Loch im Brett aus statt wie eine Platte.
                shade = 0x44;
            }
            if (shade > 0) {
                over.fill(body[0], body[1], (float) cx, (float) cy, withAlpha(COLOR_SHADE, shade));
            }
            over.ring(body[0], body[1], (float) (size * OUTLINE), withAlpha(outlineColor(state), alpha));
            badge(over, state, cx + size * BADGE_OFFSET, cy + size * BADGE_OFFSET, size * BADGE, alpha);
        }
        over.end();

        for (int i = 0; i < visible.size(); i++) {
            if (!hasIcon[i] && size >= 16.0) {
                // Kein aufloesbares Icon — die Mod fehlt, oder beim Knoten ist keines gesetzt.
                // Ohne Ersatz waere der Knoten eine leere Flaeche.
                String key = visible.get(i).getKey();
                UnlockTree.Node node = visible.get(i).getValue();
                drawInitials(context, key, node, screenX(node.x()), screenY(node.y()),
                        outlineColor(states.state(key, node)), dimmed(key) ? 0x30 : 0xFF);
            }
        }
    }

    /** Umriss der Knotenform um (cx, cy) in der Groesse {@code size}. */
    private static float[][] shape(NodeShape shape, double cx, double cy, double size) {
        return switch (shape) {
            case SQUARE -> SmoothPainter.roundedRect(cx, cy, size * SQUARE_HALF, size * SQUARE_HALF, 0.0);
            case RSQUARE -> SmoothPainter.roundedRect(cx, cy, size * RSQUARE_HALF, size * RSQUARE_HALF,
                    size * RSQUARE_CORNER);
            case DIAMOND -> SmoothPainter.regular(cx, cy, size * SHAPE_RADIUS, 4, 0.0);
            case HEXAGON -> SmoothPainter.regular(cx, cy, size * SHAPE_RADIUS, 6, -Math.PI / 2.0);
            case GEAR -> gear(cx, cy, size);
            case CIRCLE -> SmoothPainter.circle(cx, cy, size * SHAPE_RADIUS);
        };
    }

    /** Zahnrad mit acht Zaehnen und parallelen Flanken, der erste Zahn oben. */
    private static float[][] gear(double cx, double cy, double size) {
        double tip = size * SHAPE_RADIUS;
        double root = size * GEAR_ROOT;
        double half = size * GEAR_TOOTH_HALF;
        double base = Math.sqrt(Math.max(0.0, root * root - half * half));
        int arc = 3;
        float[] xs = new float[GEAR_TEETH * (4 + arc)];
        float[] ys = new float[xs.length];
        int k = 0;
        double step = Math.PI * 2.0 / GEAR_TEETH;
        for (int tooth = 0; tooth < GEAR_TEETH; tooth++) {
            double angle = -Math.PI / 2.0 + tooth * step;
            double ux = Math.cos(angle);
            double uy = Math.sin(angle);
            // Tangente in Richtung steigender Winkel.
            double vx = -uy;
            double vy = ux;
            double[][] corners = {
                    {base, -half}, {tip, -half}, {tip, half}, {base, half}};
            for (double[] c : corners) {
                xs[k] = (float) (cx + ux * c[0] + vx * c[1]);
                ys[k] = (float) (cy + uy * c[0] + vy * c[1]);
                k++;
            }
            // Fusskreis bis zum naechsten Zahn.
            double from = angle + Math.asin(Math.min(1.0, half / root));
            double to = angle + step - Math.asin(Math.min(1.0, half / root));
            for (int s = 1; s <= arc; s++) {
                double a = from + (to - from) * s / (arc + 1);
                xs[k] = (float) (cx + root * Math.cos(a));
                ys[k] = (float) (cy + root * Math.sin(a));
                k++;
            }
        }
        return new float[][] {xs, ys};
    }

    /**
     * Item auf einer Kommazahl-Position zeichnen, Groesse auf ganze Bildschirmpixel gerundet.
     *
     * Die Groesse waechst stetig mit dem Zoom. Frueher rastete sie auf fuenf feste Stufen
     * ein — scharf, aber beim Zoomen sprang jedes Icon sichtbar von Stufe zu Stufe.
     */
    private static void drawIcon(DrawContext context, Item item, double cx, double cy, double size) {
        double icon = SmoothPainter.snap(size * ICON);
        float scale = (float) (icon / 16.0);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(SmoothPainter.snap(cx - icon * 0.5), SmoothPainter.snap(cy - icon * 0.5), 0.0);
        matrices.scale(scale, scale, 1.0F);
        context.drawItem(new ItemStack(item), 0, 0);
        matrices.pop();
    }

    /** Ersatzdarstellung, wenn kein Item-Icon vorliegt: die ersten Buchstaben des Namens. */
    private void drawInitials(DrawContext context, String key, UnlockTree.Node node, double cx, double cy,
            int color, int alpha) {
        if (textRenderer == null) {
            return;
        }
        // Den angezeigten Namen nehmen, nicht den Uebersetzungsschluessel: aus
        // "tree.utopia.create.basics" wuerde sonst "B" statt "KB" fuer "Kinetic Basics".
        String source = displayName(key, node);
        StringBuilder initials = new StringBuilder();
        for (String word : source.split(" ")) {
            if (!word.isEmpty() && initials.length() < 2) {
                initials.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        String text = initials.toString();
        if (text.isEmpty()) {
            return;
        }
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(SmoothPainter.snap(cx - textRenderer.getWidth(text) * 0.5),
                SmoothPainter.snap(cy - 4.0), Z_OVER_ICON + 1.0F);
        context.drawText(textRenderer, text, 0, 0, withAlpha(color, alpha), false);
        matrices.pop();
    }

    /**
     * Namen unter den Knoten.
     *
     * Die Schrift wird so skaliert, dass ein Pixel der Minecraft-Schrift auf eine ganze
     * Zahl Bildschirmpixel faellt (bei GUI-Massstab 3: zwei). Mit krummen Faktoren waeren
     * die Buchstaben ungleich breit. Namen, die einen anderen Knoten oder Namen verdecken
     * wuerden, entfallen — Vorrang hat, was man gerade braucht.
     */
    private void drawLabels(DrawContext context, List<Map.Entry<String, UnlockTree.Node>> visible,
            StateProvider states, double size) {
        double fade = MathHelper.clamp((size - LABEL_FADE_START) / (LABEL_FADE_END - LABEL_FADE_START), 0.0, 1.0);
        if (fade <= 0.0 || textRenderer == null) {
            return;
        }
        double guiScale = SmoothPainter.guiScale();
        double textScale = Math.max(1.0, Math.round(guiScale * 0.67)) / guiScale;
        double maxWidth = MathHelper.clamp(size * 1.7, 44.0, 110.0);
        int wrapWidth = (int) Math.floor(maxWidth / textScale);
        double lineHeight = (textRenderer.fontHeight + 1) * textScale;
        double gap = 1.5 + size * 0.06;
        double nodeReach = size * 0.52;

        List<Map.Entry<String, UnlockTree.Node>> order = new ArrayList<>(visible);
        order.sort((a, b) -> Integer.compare(labelPriority(a.getKey(), states.state(a.getKey(), a.getValue())),
                labelPriority(b.getKey(), states.state(b.getKey(), b.getValue()))));

        placedLabels.clear();
        List<Label> labels = new ArrayList<>();
        int visibleLeft = Math.max(left, occludeLeft);
        for (Map.Entry<String, UnlockTree.Node> entry : order) {
            String key = entry.getKey();
            UnlockTree.Node node = entry.getValue();
            double cx = screenX(node.x());
            double cy = screenY(node.y());
            // Nur Knoten, deren Mitte zu sehen ist. Sonst stuende am Kartenrand ein Name
            // ohne erkennbaren Knoten dazu.
            if (cx < visibleLeft || cx >= right || cy < top || cy >= bottom) {
                continue;
            }
            double labelAlpha = fade * (dimmed(key) ? 0.35 : 1.0);
            int alpha = (int) Math.round(255 * labelAlpha);
            if (alpha < 4) {
                // Die Minecraft-Schrift behandelt Alpha unter 4 als "voll deckend".
                continue;
            }
            String[] lines = wrap(displayName(key, node), wrapWidth);
            if (lines.length == 0) {
                continue;
            }
            double width = 0.0;
            for (String line : lines) {
                width = Math.max(width, textRenderer.getWidth(line) * textScale);
            }
            double height = lines.length * lineHeight;
            double x0 = cx - width * 0.5;
            double y0 = cy + size * 0.5 + gap;
            if (collides(x0, y0, width, height, key, visible, nodeReach)) {
                continue;
            }
            placedLabels.add(new double[] {x0 - 2.0, y0 - 1.0, x0 + width + 2.0, y0 + height + 1.0});
            State state = states.state(key, node);
            labels.add(new Label(cx, x0, y0, width, height, lines,
                    withAlpha(state == State.BLOCKED ? COLOR_LABEL_BLOCKED : COLOR_LABEL, alpha), alpha));
        }
        if (labels.isEmpty()) {
            return;
        }

        // Unterlage in Kartenfarbe: Linien, die unter einem Namen durchlaufen, treten dort
        // zurueck. Text quer ueber einer Linie war kaum zu lesen.
        SmoothPainter plates = SmoothPainter.begin(context, Z_OVER_ICON);
        for (Label label : labels) {
            float[][] plate = SmoothPainter.roundedRect(label.cx(), label.y0() + label.height() * 0.5,
                    label.width() * 0.5 + 2.0, label.height() * 0.5 + 1.0, 2.0);
            plates.fill(plate[0], plate[1], (float) label.cx(), (float) (label.y0() + label.height() * 0.5),
                    withAlpha(COLOR_CANVAS, label.alpha() * 0xD0 / 0xFF));
        }
        plates.end();

        MatrixStack matrices = context.getMatrices();
        for (Label label : labels) {
            for (int i = 0; i < label.lines().length; i++) {
                String line = label.lines()[i];
                double lineWidth = textRenderer.getWidth(line) * textScale;
                matrices.push();
                matrices.translate(SmoothPainter.snap(label.cx() - lineWidth * 0.5),
                        SmoothPainter.snap(label.y0() + i * lineHeight), Z_OVER_ICON + 1.0F);
                matrices.scale((float) textScale, (float) textScale, 1.0F);
                context.drawText(textRenderer, line, 0, 0, label.color(), false);
                matrices.pop();
            }
        }
    }

    private record Label(double cx, double x0, double y0, double width, double height, String[] lines,
            int color, int alpha) {
    }

    private int labelPriority(String key, State state) {
        if (key.equals(selected)) {
            return 0;
        }
        if (key.equals(hovered)) {
            return 1;
        }
        if (searchMatches != null && searchMatches.contains(key)) {
            return 2;
        }
        return switch (state) {
            case BUYABLE -> 3;
            case TOO_EXPENSIVE -> 4;
            case OWNED -> 5;
            case BLOCKED -> 6;
        };
    }

    /** Ob ein Namensfeld einen bereits gesetzten Namen oder einen fremden Knoten beruehrt. */
    private boolean collides(double x, double y, double width, double height, String own,
            List<Map.Entry<String, UnlockTree.Node>> visible, double nodeReach) {
        double x2 = x + width;
        double y2 = y + height;
        for (double[] placed : placedLabels) {
            if (x < placed[2] && x2 > placed[0] && y < placed[3] && y2 > placed[1]) {
                return true;
            }
        }
        for (Map.Entry<String, UnlockTree.Node> entry : visible) {
            if (entry.getKey().equals(own)) {
                continue;
            }
            double nx = screenX(entry.getValue().x());
            double ny = screenY(entry.getValue().y());
            double closestX = MathHelper.clamp(nx, x, x2);
            double closestY = MathHelper.clamp(ny, y, y2);
            if (Math.hypot(nx - closestX, ny - closestY) < nodeReach) {
                return true;
            }
        }
        return false;
    }

    /** Hoechstens zwei Zeilen; was nicht passt, endet mit einer Auslassung. */
    private String[] wrap(String text, int width) {
        String cacheKey = width + "\u0000" + text;
        String[] cached = wrapCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        if (wrapCache.size() > 1024) {
            wrapCache.clear();
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = text.trim().split("\\s+");
        int index = 0;
        while (index < words.length && lines.size() < 2) {
            String word = words[index];
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (textRenderer.getWidth(candidate) <= width || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
                index++;
                continue;
            }
            lines.add(line.toString());
            line.setLength(0);
        }
        if (line.length() > 0 && lines.size() < 2) {
            lines.add(line.toString());
        }
        if (index < words.length || (!lines.isEmpty() && textRenderer.getWidth(lines.get(lines.size() - 1)) > width)) {
            // Rest passt nicht mehr: letzte Zeile kuerzen und kenntlich machen.
            StringBuilder rest = new StringBuilder(lines.isEmpty() ? "" : lines.remove(lines.size() - 1));
            for (int i = index; i < words.length; i++) {
                rest.append(' ').append(words[i]);
            }
            String ellipsis = "…";
            String trimmed = textRenderer.trimToWidth(rest.toString().trim(), width - textRenderer.getWidth(ellipsis));
            lines.add(trimmed.trim() + ellipsis);
        }
        String[] result = lines.toArray(new String[0]);
        wrapCache.put(cacheKey, result);
        return result;
    }

    private static String displayName(String key, UnlockTree.Node node) {
        String source = UnlockScreen.displayName(node.name(), key).getString();
        int cut = source.lastIndexOf('.');
        if (cut >= 0 && cut + 1 < source.length() && source.indexOf(' ') < 0) {
            // Unuebersetzter Schluessel: nur das letzte Glied, lesbar gemacht.
            source = source.substring(cut + 1);
        }
        return source.replace('_', ' ').trim();
    }

    // --- Abzeichen --------------------------------------------------------

    /** Abzeichen einzeln zeichnen, etwa fuer die Legende. */
    static void drawBadge(DrawContext context, State state, double cx, double cy, double diameter) {
        SmoothPainter painter = SmoothPainter.begin(context, 0.0F);
        badge(painter, state, cx, cy, diameter, 0xFF);
        painter.end();
    }

    /**
     * Rundes Abzeichen: dunkle Scheibe, farbiger Rand, Zeichen in Zustandsfarbe. Die
     * Scheibe haelt das Zeichen auf jedem Item-Icon lesbar, ohne den frueheren Schatten.
     */
    private static void badge(SmoothPainter painter, State state, double cx, double cy, double d, int alpha) {
        int color = withAlpha(badgeColor(state), alpha);
        float[][] disc = SmoothPainter.circle(cx, cy, d * 0.5);
        painter.fill(disc[0], disc[1], (float) cx, (float) cy, withAlpha(COLOR_BADGE_GROUND, alpha * 0xF0 / 0xFF));
        painter.ring(disc[0], disc[1], (float) Math.max(SmoothPainter.pixel(), d * 0.075), color);
        float stroke = (float) (d * 0.13);
        switch (state) {
            case OWNED -> painter.polyline(
                    new float[] {(float) (cx - d * 0.22), (float) (cx - d * 0.06), (float) (cx + d * 0.23)},
                    new float[] {(float) (cy + d * 0.01), (float) (cy + d * 0.17), (float) (cy - d * 0.15)},
                    stroke, color);
            case BUYABLE -> {
                painter.line(cx - d * 0.21, cy, cx + d * 0.21, cy, stroke, color);
                painter.line(cx, cy - d * 0.21, cx, cy + d * 0.21, stroke, color);
            }
            case TOO_EXPENSIVE -> {
                float[][] coin = SmoothPainter.circle(cx, cy, d * 0.2);
                painter.ring(coin[0], coin[1], (float) (d * 0.09), color);
            }
            case BLOCKED -> {
                float[][] body = SmoothPainter.roundedRect(cx, cy + d * 0.08, d * 0.19, d * 0.14, d * 0.04);
                painter.fill(body[0], body[1], (float) cx, (float) (cy + d * 0.08), color);
                int segments = 8;
                float[] xs = new float[segments + 1];
                float[] ys = new float[segments + 1];
                for (int i = 0; i <= segments; i++) {
                    double angle = Math.PI + Math.PI * i / segments;
                    xs[i] = (float) (cx + d * 0.12 * Math.cos(angle));
                    ys[i] = (float) (cy - d * 0.04 + d * 0.12 * Math.sin(angle));
                }
                painter.polyline(xs, ys, (float) (d * 0.075), color);
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha & 255) << 24 | (color & 0x00FFFFFF);
    }

    private boolean dimmed(String key) {
        return searchMatches != null && !searchMatches.contains(key);
    }

    private static int fillColor(State state) {
        return switch (state) {
            case OWNED -> COLOR_OWNED_FILL;
            case BUYABLE -> COLOR_BUYABLE_FILL;
            case TOO_EXPENSIVE -> COLOR_EXPENSIVE_FILL;
            case BLOCKED -> COLOR_BLOCKED_FILL;
        };
    }

    static int outlineColor(State state) {
        return switch (state) {
            case OWNED -> COLOR_OWNED_OUTLINE;
            case BUYABLE -> COLOR_BUYABLE_OUTLINE;
            case TOO_EXPENSIVE -> COLOR_EXPENSIVE_OUTLINE;
            case BLOCKED -> COLOR_BLOCKED_OUTLINE;
        };
    }

    private static int badgeColor(State state) {
        return outlineColor(state);
    }

    // --- Treffer und Eingabe ---------------------------------------------

    /** Naechstliegender Knoten unter dem Zeiger, damit sich ueberlappende Formen sauber treffen. */
    String nodeAt(UnlockTree tree, double mouseX, double mouseY) {
        double reach = nodeSize() / 2.0 + 1.0;
        String best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, UnlockTree.Node> entry : tree.nodes().entrySet()) {
            double distance = Math.hypot(mouseX - screenX(entry.getValue().x()),
                    mouseY - screenY(entry.getValue().y()));
            if (distance <= reach && distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        return best;
    }

    boolean mouseClicked(UnlockTree tree, double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (button != 0 && button != 2) {
            return false;
        }
        pressedNode = nodeAt(tree, mouseX, mouseY);
        if (button == 0) {
            // Klick ins Leere hebt die Auswahl auf. Vorher liess sie sich ueberhaupt
            // nicht mehr loesen, wodurch der Inspektor dauerhaft stehen blieb.
            selected = pressedNode;
            if (pressedNode != null) {
                UnlockTree.Node node = tree.nodes().get(pressedNode);
                dragOffsetX = mouseX - screenX(node.x());
                dragOffsetY = mouseY - screenY(node.y());
            }
        }
        gestureActive = true;
        pressedButton = button;
        pressMouseX = mouseX;
        pressMouseY = mouseY;
        return true;
    }

    boolean mouseDragged(double mouseX, double mouseY, double deltaX, double deltaY,
            boolean moveNodes, boolean snap, MoveListener listener) {
        if (!gestureActive) {
            return false;
        }
        if (!panning && draggedNode == null
                && Math.hypot(mouseX - pressMouseX, mouseY - pressMouseY) >= DRAG_THRESHOLD) {
            if (moveNodes && pressedButton == 0 && pressedNode != null) {
                draggedNode = pressedNode;
            } else {
                panning = true;
                // Wer die Karte packt, haelt einen laufenden Zoom an Ort und Stelle an —
                // sonst sprang sie auf das Zoomziel, sobald das Ziehen begann.
                zoom = viewZoom;
                centerX = viewCenterX;
                centerY = viewCenterY;
                anchored = false;
            }
        }
        if (draggedNode != null) {
            Position position = modelAt(mouseX - dragOffsetX, mouseY - dragOffsetY, snap);
            listener.move(draggedNode, position.x(), position.y());
            return true;
        }
        if (panning) {
            double scale = UNIT * viewZoom;
            centerX -= deltaX / scale;
            centerY -= deltaY / scale;
            // Ziehen muss unmittelbar wirken; eine Annaeherung waere hier Traegheit.
            viewCenterX = centerX;
            viewCenterY = centerY;
            return true;
        }
        return false;
    }

    boolean mouseReleased() {
        boolean handled = gestureActive;
        panning = false;
        draggedNode = null;
        pressedNode = null;
        gestureActive = false;
        pressedButton = -1;
        return handled;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        zoomTo(zoom * Math.pow(ZOOM_STEP, amount), mouseX, mouseY);
        return true;
    }

    void zoomBy(double amount) {
        zoomTo(zoom * Math.pow(ZOOM_STEP, amount), (left + right) / 2.0, (top + bottom) / 2.0);
    }

    /** Zoomt so, dass der Modellpunkt unter {@code anchor} dort bleibt, wo er ist. */
    private void zoomTo(double target, double anchorX, double anchorY) {
        double next = MathHelper.clamp(target, MIN_ZOOM, MAX_ZOOM);
        if (Math.abs(next - zoom) < 1.0E-6) {
            return;
        }
        // Der Punkt, den der Benutzer gerade sieht — nicht der, der am Ende einer noch
        // laufenden Animation dort laege.
        double modelX = viewCenterX + (anchorX - midX()) / (UNIT * viewZoom);
        double modelY = viewCenterY + (anchorY - midY()) / (UNIT * viewZoom);
        zoom = next;
        centerX = modelX - (anchorX - midX()) / (UNIT * zoom);
        centerY = modelY - (anchorY - midY()) / (UNIT * zoom);
        anchored = true;
        anchorScreenX = anchorX;
        anchorScreenY = anchorY;
        anchorModelX = modelX;
        anchorModelY = modelY;
    }

    // --- Kamera -----------------------------------------------------------

    /** Ganzen Baum einpassen — die Uebersicht, nicht die Standardansicht. */
    void fit(UnlockTree tree) {
        anchored = false;
        if (tree == null || tree.nodes().isEmpty()) {
            centerX = 0.0;
            centerY = 0.0;
            zoom = 1.0;
            return;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (UnlockTree.Node node : tree.nodes().values()) {
            minX = Math.min(minX, node.x());
            maxX = Math.max(maxX, node.x());
            minY = Math.min(minY, node.y());
            maxY = Math.max(maxY, node.y());
        }
        double modelWidth = Math.max(1.0, (maxX - minX + 1.6) * UNIT);
        double modelHeight = Math.max(1.0, (maxY - minY + 1.6) * UNIT);
        double fitX = Math.max(1.0, right - left - 16.0) / modelWidth;
        double fitY = Math.max(1.0, bottom - top - 16.0) / modelHeight;
        zoom = MathHelper.clamp(Math.min(1.0, Math.min(fitX, fitY)), MIN_ZOOM, MAX_ZOOM);
        centerX = (minX + maxX) * 0.5;
        centerY = (minY + maxY) * 0.5;
    }

    /**
     * Auf einen Knoten fahren, ohne herauszuzoomen.
     *
     * Das ist die Standardansicht beim Oeffnen: lieber lesbar an der richtigen Stelle als
     * vollstaendig und unlesbar.
     */
    void focus(UnlockTree tree, String key, double preferredZoom) {
        anchored = false;
        UnlockTree.Node node = tree == null ? null : tree.nodes().get(key);
        if (node == null) {
            fit(tree);
            return;
        }
        // Den Wunschzoom so weit zuruecknehmen, dass die direkten Nachbarn mit im Bild
        // sind. Ein fester Wert taugt dafuer nicht: Wie weit die Nachbarn entfernt sind,
        // haengt vollstaendig davon ab, wie grosszuegig der Baum gesetzt wurde.
        double radius = neighbourRadius(tree, key, node);
        if (radius > 0.0) {
            double halfShortSide = Math.min(right - left, bottom - top) / 2.0 - NODE_BASE * 0.5;
            if (halfShortSide > 0.0) {
                preferredZoom = Math.min(preferredZoom, halfShortSide / (radius * UNIT));
            }
        }
        zoom = MathHelper.clamp(preferredZoom, MIN_ZOOM, MAX_ZOOM);
        centerX = node.x();
        centerY = node.y();
    }

    /** Abstand zum entferntesten unmittelbaren Nachbarn, in Modelleinheiten. */
    private static double neighbourRadius(UnlockTree tree, String key, UnlockTree.Node node) {
        double radius = 0.0;
        for (String parent : node.parents()) {
            UnlockTree.Node other = tree.nodes().get(parent);
            if (other != null) {
                radius = Math.max(radius, Math.hypot(other.x() - node.x(), other.y() - node.y()));
            }
        }
        for (UnlockTree.Node child : tree.nodes().values()) {
            if (child.parents().contains(key)) {
                radius = Math.max(radius, Math.hypot(child.x() - node.x(), child.y() - node.y()));
            }
        }
        return radius;
    }

    /** Ohne Annaeherung an die Zielansicht springen — beim Oeffnen und Baumwechsel. */
    void snapToTarget() {
        anchored = false;
        viewCenterX = centerX;
        viewCenterY = centerY;
        viewZoom = zoom;
    }

    Position modelAt(double mouseX, double mouseY, boolean snap) {
        // Bewusst die dargestellte Kamera, nicht die Zielkamera: Der Benutzer zielt auf
        // das, was er sieht. Waehrend einer laufenden Zoom-Annaeherung weichen beide ab.
        double modelX = viewCenterX + (mouseX - midX()) / (UNIT * viewZoom);
        double modelY = viewCenterY + (mouseY - midY()) / (UNIT * viewZoom);
        if (snap) {
            modelX = Math.round(modelX * 2.0) / 2.0;
            modelY = Math.round(modelY * 2.0) / 2.0;
        }
        return new Position(modelX, modelY);
    }

    int left() {
        return left;
    }

    int top() {
        return top;
    }

    int right() {
        return right;
    }

    int bottom() {
        return bottom;
    }

    private double midX() {
        return (left + right) / 2.0;
    }

    private double midY() {
        return (top + bottom) / 2.0;
    }

    /** Auf Bildschirmpixel gerundet, nicht auf GUI-Pixel: scharf und trotzdem fluessig. */
    private double screenX(double modelX) {
        return SmoothPainter.snap(midX() + (modelX - viewCenterX) * UNIT * viewZoom);
    }

    private double screenY(double modelY) {
        return SmoothPainter.snap(midY() + (modelY - viewCenterY) * UNIT * viewZoom);
    }

    /** Stetig mit dem Zoom, auf ganze Bildschirmpixel gerundet. */
    private double nodeSize() {
        return SmoothPainter.snap(MathHelper.clamp(NODE_BASE * viewZoom, 8.0, 88.0));
    }

    private boolean nodeVisible(UnlockTree.Node node, double size) {
        double margin = size + 16.0;
        double x = screenX(node.x());
        double y = screenY(node.y());
        return x >= Math.max(left, occludeLeft) - margin && x <= right + margin
                && y >= top - margin && y <= bottom + margin;
    }

    private boolean edgeVisible(Edge edge) {
        double margin = 48.0;
        return Math.max(edge.x1(), edge.x2()) >= left - margin
                && Math.min(edge.x1(), edge.x2()) <= right + margin
                && Math.max(edge.y1(), edge.y2()) >= top - margin
                && Math.min(edge.y1(), edge.y2()) <= bottom + margin;
    }

    private record Edge(double x1, double y1, double x2, double y2, boolean primary, boolean lit, int color) {
    }

    record Position(double x, double y) {
    }
}
