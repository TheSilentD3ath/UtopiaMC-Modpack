package dev.utopia.core.client;

import dev.utopia.core.UtopiaCore;
import dev.utopia.core.unlock.NodeShape;
import dev.utopia.core.unlock.UnlockTree;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Karte des Freischaltbaums: frei schwenk- und zoombar, Knoten als Form mit Item-Icon.
 *
 * <p><b>Warum keine Beschriftung auf der Karte:</b> 68 Knoten mit Textkasten brauchen rund
 * das Doppelte der verfuegbaren Flaeche. Solange jeder Knoten ein Label traegt, endet
 * "alles einpassen" zwangslaeufig am Mindestzoom und niemand erkennt mehr etwas. Das Item
 * <em>ist</em> die Beschriftung; der Name kommt beim Darueberfahren und im Inspektor.
 *
 * <p><b>Warum Form und Abzeichen statt nur Farbe:</b> Auf Holzmaserung und bei 16 px
 * Knotengroesse ist Farbe der schwaechste Kanal. Die vorherigen Zustandsfarben lagen bei
 * 1,18:1 zueinander — "schon gekauft" und "jetzt kaufbar" waren praktisch nicht
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

    private static final Identifier CANVAS_WOOD = UtopiaCore.id("textures/gui/unlock/canvas_wood.png");
    /** Kantenlaenge der Holztextur. */
    private static final int WOOD_TEXTURE = 512;
    /** Kantenlaenge der Form- und Rahmentexturen. */
    private static final int SHAPE_TEXTURE = 64;
    private static final int BADGE_TEXTURE = 32;
    private static final int CANVAS_TILE_SIZE = 220;

    private static final double UNIT = 64.0;
    private static final double MIN_ZOOM = 0.08;
    private static final double MAX_ZOOM = 2.0;
    /** Zoomschritt je Mausrad-Raste. Deutlich groesser als frueher, sonst kurbelt man ewig. */
    private static final double ZOOM_STEP = 1.25;
    /** Knotengroesse bei 100 %. */
    private static final int NODE_BASE = 54;
    private static final double DRAG_THRESHOLD = 4.0;
    private static final double DASH_LENGTH = 6.0;
    private static final double DASH_PERIOD = 14.0;
    /** Anteil, um den sich die Ansicht pro Bild dem Ziel naehert. */
    private static final float EASING = 0.4F;

    // Palette. Alle Werte gegen die gemessene Holzhelligkeit auf Kontrast geprueft;
    // die Zustandsrahmen erreichen mindestens 3,0:1 gegen den Hintergrund.
    //
    // BLOCKED ist bewusst ein heller, entsaettigter Ton und kein dunkler: Ein dunkler
    // Rahmen auf dunklem Holz bestand zwar die Kontrastschwelle, verschluckte aber das
    // Item-Icon im Knoten — gesperrte Knoten waren schwarze Kleckse, bei denen man nicht
    // mehr erkannte, worum es ueberhaupt geht. Der Zustand tritt jetzt ueber die Saettigung
    // zurueck, nicht ueber die Helligkeit.
    private static final int COLOR_OWNED_OUTLINE = 0xFFB8F0A0;
    private static final int COLOR_BUYABLE_OUTLINE = 0xFFFFE9A8;
    private static final int COLOR_EXPENSIVE_OUTLINE = 0xFFFCD5A6;
    private static final int COLOR_BLOCKED_OUTLINE = 0xFFE7D8C3;
    private static final int COLOR_OWNED_FILL = 0xFF24401C;
    private static final int COLOR_BUYABLE_FILL = 0xFF453213;
    private static final int COLOR_EXPENSIVE_FILL = 0xFF3A2C1E;
    private static final int COLOR_BLOCKED_FILL = 0xFF2E2823;
    private static final int COLOR_EDGE_OWNED = 0xFFA8E88C;
    private static final int COLOR_EDGE_OPEN = 0xFFFCD596;
    private static final int COLOR_EDGE_SECONDARY = 0xFFB4E0EC;
    private static final int COLOR_EDGE_SHADOW = 0xB8120A06;
    private static final int COLOR_SELECTED = 0xFFFFF3C4;
    private static final int COLOR_HOVERED = 0xFFE8CE92;
    private static final int COLOR_SEARCH_MATCH = 0xFFFFD98A;

    /** Nur fuer die Ersatzbeschriftung, wenn ein Knoten kein Item-Icon hat. */
    private net.minecraft.client.font.TextRenderer textRenderer;
    private final List<Edge> edgeBuffer = new ArrayList<>();
    private final Set<String> highlightChain = new HashSet<>();

    private int left;
    private int top;
    private int right;
    private int bottom;

    /** Ziel der Kamera: Modellkoordinate, die in der Canvas-Mitte liegen soll. */
    private double centerX;
    private double centerY;
    private double zoom = 1.0;
    /** Dargestellte Kamera; naehert sich dem Ziel weich an. */
    private double viewCenterX;
    private double viewCenterY;
    private double viewZoom = 1.0;

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
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    int zoomPercent() {
        return (int) Math.round(zoom * 100.0);
    }

    // --- Zeichnen ---------------------------------------------------------

    void render(DrawContext context, UnlockTree tree, StateProvider states, boolean editing,
            int mouseX, int mouseY) {
        if (textRenderer == null) {
            textRenderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        }
        ease();
        context.enableScissor(left, top, right, bottom);
        drawBackground(context);
        if (editing) {
            drawEditorGrid(context);
        }

        hovered = contains(mouseX, mouseY) ? nodeAt(tree, mouseX, mouseY) : null;
        buildHighlightChain(tree, hovered != null ? hovered : selected);

        drawEdges(context, tree, states);

        int size = nodeSize();
        for (Map.Entry<String, UnlockTree.Node> entry : tree.nodes().entrySet()) {
            UnlockTree.Node node = entry.getValue();
            if (!nodeVisible(node, size)) {
                continue;
            }
            String key = entry.getKey();
            drawNode(context, key, node, states.state(key, node), size,
                    key.equals(hovered), key.equals(selected), dimmed(key));
        }
        context.disableScissor();
    }

    private void ease() {
        viewCenterX += (centerX - viewCenterX) * EASING;
        viewCenterY += (centerY - viewCenterY) * EASING;
        viewZoom += (zoom - viewZoom) * EASING;
        // Restweg abschneiden, damit die Ansicht nicht ewig um Bruchteile zittert.
        if (Math.abs(centerX - viewCenterX) < 0.0005) {
            viewCenterX = centerX;
        }
        if (Math.abs(centerY - viewCenterY) < 0.0005) {
            viewCenterY = centerY;
        }
        if (Math.abs(zoom - viewZoom) < 0.0005) {
            viewZoom = zoom;
        }
    }

    /** Die Holzplatte liegt im Baum-Koordinatenraum und bewegt sich beim Schwenken mit. */
    private void drawBackground(DrawContext context) {
        int tile = MathHelper.clamp((int) Math.round(CANVAS_TILE_SIZE * viewZoom), 96, 384);
        int originX = screenX(0.0);
        int originY = screenY(0.0);
        int startX = left + Math.floorMod(originX - left, tile) - tile;
        int startY = top + Math.floorMod(originY - top, tile) - tile;
        for (int x = startX; x < right; x += tile) {
            for (int y = startY; y < bottom; y += tile) {
                context.drawTexture(CANVAS_WOOD, x, y, tile, tile,
                        0.0F, 0.0F, WOOD_TEXTURE, WOOD_TEXTURE, WOOD_TEXTURE, WOOD_TEXTURE);
            }
        }
        context.fill(left, top, right, bottom, 0x14180E08);
        drawVignette(context);
    }

    private void drawVignette(DrawContext context) {
        int depth = Math.min(18, Math.min((right - left) / 5, (bottom - top) / 5));
        for (int inset = 0; inset < depth; inset += 2) {
            int alpha = Math.max(0, 60 - inset * 3);
            int color = alpha << 24 | 0x00170B06;
            context.fill(left + inset, top + inset, right - inset, top + inset + 2, color);
            context.fill(left + inset, bottom - inset - 2, right - inset, bottom - inset, color);
            context.fill(left + inset, top + inset + 2, left + inset + 2, bottom - inset - 2, color);
            context.fill(right - inset - 2, top + inset + 2, right - inset, bottom - inset - 2, color);
        }
    }

    private void drawEditorGrid(DrawContext context) {
        int spacing = Math.max(10, (int) Math.round(UNIT * 0.5 * viewZoom));
        int originX = screenX(0.0);
        int originY = screenY(0.0);
        for (int x = left + Math.floorMod(originX - left, spacing); x < right; x += spacing) {
            context.fill(x, top, x + 1, bottom, 0x263C281A);
        }
        for (int y = top + Math.floorMod(originY - top, spacing); y < bottom; y += spacing) {
            context.fill(left, y, right, y + 1, 0x263C281A);
        }
    }

    /**
     * Alle Voraussetzungen des betrachteten Knotens, transitiv.
     *
     * Frueher zeigte die Karte nur die erste Elternkante je Knoten. Bei 45 von 68 Knoten
     * mit mehreren Eltern war die gezeichnete Form damit nicht die tatsaechliche Struktur —
     * man konnte nicht planen, weil man nicht sah, was noch fehlt. Jetzt sind alle Kanten
     * da, und die Kette des betrachteten Knotens wird zusaetzlich hervorgehoben.
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
        int radius = nodeSize() / 2 + 2;
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
        // Strichstaerke haengt an der Knotengroesse. Mit fester Staerke waren die Kanten
        // beim Herauszoomen breiter als die Knoten, die sie verbinden — die Verbindungen
        // schrien dann lauter als das, worum es geht.
        int weight = Math.max(1, nodeSize() / 12);
        // Schatten zuerst, dann die Linien, hervorgehobene zuletzt: so liegt die Kette
        // des betrachteten Knotens immer obenauf.
        for (Edge edge : edgeBuffer) {
            if (edge.primary()) {
                drawConnection(context, edge, weight + 1, COLOR_EDGE_SHADOW, false);
            }
        }
        for (Edge edge : edgeBuffer) {
            if (!edge.lit()) {
                drawConnection(context, edge, edge.primary() ? weight : Math.max(1, weight - 1),
                        edge.color(), !edge.primary());
            }
        }
        for (Edge edge : edgeBuffer) {
            if (edge.lit()) {
                drawConnection(context, edge, edge.primary() ? weight + 1 : weight,
                        edge.color(), !edge.primary());
            }
        }
    }

    /** Kante zwischen zwei Knotenraendern statt zwischen den Mittelpunkten. */
    private Edge edge(UnlockTree.Node parent, UnlockTree.Node child, int radius, boolean primary,
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
        return new Edge((int) Math.round(x1 + ux * radius), (int) Math.round(y1 + uy * radius),
                (int) Math.round(x2 - ux * radius), (int) Math.round(y2 - uy * radius), primary, lit, color);
    }

    private static void drawConnection(DrawContext context, Edge edge, int thickness, int color,
            boolean dashed) {
        if (!dashed) {
            drawSegment(context, edge.x1(), edge.y1(), edge.x2(), edge.y2(), thickness, color);
            return;
        }
        double deltaX = edge.x2() - edge.x1();
        double deltaY = edge.y2() - edge.y1();
        double length = Math.hypot(deltaX, deltaY);
        if (length < 0.001) {
            return;
        }
        double unitX = deltaX / length;
        double unitY = deltaY / length;
        for (double offset = 0.0; offset < length; offset += DASH_PERIOD) {
            double end = Math.min(length, offset + DASH_LENGTH);
            drawSegment(context, edge.x1() + unitX * offset, edge.y1() + unitY * offset,
                    edge.x1() + unitX * end, edge.y1() + unitY * end, thickness, color);
        }
    }

    private static void drawSegment(DrawContext context, double x1, double y1, double x2, double y2,
            int thickness, int color) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        if (length < 0.001) {
            return;
        }
        int segmentTop = -thickness / 2;
        context.getMatrices().push();
        context.getMatrices().translate(x1, y1, 0.0F);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(y2 - y1, x2 - x1)));
        context.fill(0, segmentTop, (int) Math.ceil(length), segmentTop + thickness, color);
        context.getMatrices().pop();
    }

    private void drawNode(DrawContext context, String key, UnlockTree.Node node, State state, int size,
            boolean isHovered, boolean isSelected, boolean dim) {
        int centerPixelX = screenX(node.x());
        int centerPixelY = screenY(node.y());
        int x = centerPixelX - size / 2;
        int y = centerPixelY - size / 2;
        NodeShape shape = node.shape();
        int alpha = dim ? 0x30 : 0xFF;

        // Auswahl- und Zeigerring: derselbe Umriss, etwas groesser, hinter dem Knoten.
        if (isSelected || isHovered) {
            int ringSize = size + 6;
            tinted(context, outlineTexture(shape), centerPixelX - ringSize / 2, centerPixelY - ringSize / 2,
                    ringSize, SHAPE_TEXTURE, withAlpha(isSelected ? COLOR_SELECTED : COLOR_HOVERED, alpha));
        }
        if (searchMatches != null && searchMatches.contains(key)) {
            int ringSize = size + 10;
            tinted(context, outlineTexture(shape), centerPixelX - ringSize / 2, centerPixelY - ringSize / 2,
                    ringSize, SHAPE_TEXTURE, COLOR_SEARCH_MATCH);
        }

        tinted(context, fillTexture(shape), x, y, size, SHAPE_TEXTURE, withAlpha(fillColor(state), alpha));

        Item item = node.icon().map(Registries.ITEM::get).orElse(null);
        boolean hasIcon = item != null && item != net.minecraft.item.Items.AIR;
        if (hasIcon) {
            drawIcon(context, item, centerPixelX, centerPixelY, size);
        }
        if (state == State.BLOCKED) {
            // Gesperrte Knoten werden zusaetzlich abgedunkelt; das Schloss allein
            // koennte man bei kleinem Zoom uebersehen.
            tinted(context, fillTexture(shape), x, y, size, SHAPE_TEXTURE, withAlpha(0xFF120C08, dim ? 0x22 : 0x44));
        }

        tinted(context, outlineTexture(shape), x, y, size, SHAPE_TEXTURE, withAlpha(outlineColor(state), alpha));
        drawBadge(context, state, centerPixelX, centerPixelY, size, alpha);
        if (!hasIcon && size >= 18) {
            // Kein aufloesbares Icon — die Mod fehlt, oder beim Knoten ist keines gesetzt.
            // Ohne Ersatz waere der Knoten eine leere Flaeche. Ganz zum Schluss gezeichnet,
            // damit weder die Abdunklung gesperrter Knoten noch der Rahmen darueber liegen.
            drawInitials(context, key, node, centerPixelX, centerPixelY, outlineColor(state), alpha);
        }
    }

    /** Ersatzdarstellung, wenn kein Item-Icon vorliegt: die ersten Buchstaben des Namens. */
    private void drawInitials(DrawContext context, String key, UnlockTree.Node node, int centerPixelX,
            int centerPixelY, int color, int alpha) {
        if (textRenderer == null) {
            return;
        }
        // Den angezeigten Namen nehmen, nicht den Uebersetzungsschluessel: aus
        // "tree.utopia.create.basics" wuerde sonst "B" statt "KB" fuer "Kinetic Basics".
        String source = UnlockScreen.displayName(node.name(), key).getString();
        int cut = source.lastIndexOf('.');
        if (cut >= 0 && cut + 1 < source.length()) {
            source = source.substring(cut + 1);
        }
        source = source.replace('_', ' ').trim();
        if (source.isEmpty()) {
            return;
        }
        StringBuilder initials = new StringBuilder();
        for (String word : source.split(" ")) {
            if (!word.isEmpty() && initials.length() < 2) {
                initials.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        String text = initials.toString();
        context.drawText(textRenderer, text, centerPixelX - textRenderer.getWidth(text) / 2,
                centerPixelY - 4, withAlpha(color, alpha), false);
    }

    /**
     * Item auf ganzzahliger Skalierung zeichnen.
     *
     * Item-Modelle sind 16 px. Wird mit krummen Faktoren skaliert, sieht jedes Icon
     * matschig aus — genau das war beim frueheren stufenlosen Zoom immer der Fall.
     * Hier rastet die Skalierung auf saubere Stufen ein, unabhaengig vom Zoom.
     */
    private static void drawIcon(DrawContext context, Item item, int centerPixelX, int centerPixelY, int size) {
        float available = size * 0.62F;
        float scale;
        if (available >= 30.0F) {
            scale = 2.0F;
        } else if (available >= 22.0F) {
            scale = 1.5F;
        } else if (available >= 15.0F) {
            scale = 1.0F;
        } else if (available >= 11.0F) {
            scale = 0.75F;
        } else {
            scale = 0.5F;
        }
        int offset = Math.round(8.0F * scale);
        context.getMatrices().push();
        context.getMatrices().translate(centerPixelX - offset, centerPixelY - offset, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.drawItem(new ItemStack(item), 0, 0);
        context.getMatrices().pop();
    }

    private static void drawBadge(DrawContext context, State state, int centerPixelX, int centerPixelY,
            int size, int alpha) {
        int badge = Math.max(8, (size * 7 / 16) & ~1);
        int x = centerPixelX + size / 2 - badge + 1;
        int y = centerPixelY + size / 2 - badge + 1;
        // Dunkle Unterlegung, damit das Abzeichen auf jedem Item-Icon steht.
        tinted(context, badgeTexture(state), x - 1, y - 1, badge + 2, BADGE_TEXTURE, withAlpha(0xFF120C08, alpha));
        tinted(context, badgeTexture(state), x, y, badge, BADGE_TEXTURE, withAlpha(badgeColor(state), alpha));
    }

    private static void tinted(DrawContext context, Identifier texture, int x, int y, int size,
            int textureSize, int color) {
        float a = (color >>> 24) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        context.setShaderColor(r, g, b, a);
        context.drawTexture(texture, x, y, size, size, 0.0F, 0.0F, textureSize, textureSize,
                textureSize, textureSize);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha & 255) << 24 | (color & 0x00FFFFFF);
    }

    private boolean dimmed(String key) {
        return searchMatches != null && !searchMatches.contains(key);
    }

    private static Identifier fillTexture(NodeShape shape) {
        return UtopiaCore.id("textures/gui/unlock/" + shape.id() + ".png");
    }

    private static Identifier outlineTexture(NodeShape shape) {
        return UtopiaCore.id("textures/gui/unlock/" + shape.id() + "_outline.png");
    }

    static Identifier badgeTexture(State state) {
        return switch (state) {
            case OWNED -> UtopiaCore.id("textures/gui/unlock/badge_check.png");
            case BUYABLE -> UtopiaCore.id("textures/gui/unlock/badge_plus.png");
            case TOO_EXPENSIVE -> UtopiaCore.id("textures/gui/unlock/badge_coin.png");
            case BLOCKED -> UtopiaCore.id("textures/gui/unlock/badge_lock.png");
        };
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
        return switch (state) {
            case OWNED -> COLOR_OWNED_OUTLINE;
            case BUYABLE -> COLOR_BUYABLE_OUTLINE;
            case TOO_EXPENSIVE -> COLOR_EXPENSIVE_OUTLINE;
            case BLOCKED -> COLOR_BLOCKED_OUTLINE;
        };
    }

    // --- Treffer und Eingabe ---------------------------------------------

    /** Naechstliegender Knoten unter dem Zeiger, damit sich ueberlappende Formen sauber treffen. */
    String nodeAt(UnlockTree tree, double mouseX, double mouseY) {
        int size = nodeSize();
        double reach = size / 2.0 + 1.0;
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
        double modelX = centerX + (anchorX - midX()) / (UNIT * zoom);
        double modelY = centerY + (anchorY - midY()) / (UNIT * zoom);
        zoom = next;
        centerX = modelX - (anchorX - midX()) / (UNIT * zoom);
        centerY = modelY - (anchorY - midY()) / (UNIT * zoom);
    }

    // --- Kamera -----------------------------------------------------------

    /** Ganzen Baum einpassen — die Uebersicht, nicht die Standardansicht. */
    void fit(UnlockTree tree) {
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

    private int screenX(double modelX) {
        return (int) Math.round(midX() + (modelX - viewCenterX) * UNIT * viewZoom);
    }

    private int screenY(double modelY) {
        return (int) Math.round(midY() + (modelY - viewCenterY) * UNIT * viewZoom);
    }

    /** Vielfaches von vier: die Formtexturen landen damit auf sauberen Rastergroessen. */
    private int nodeSize() {
        int raw = (int) Math.round(NODE_BASE * viewZoom);
        return MathHelper.clamp((raw + 2) & ~3, 8, 88);
    }

    private boolean nodeVisible(UnlockTree.Node node, int size) {
        int margin = size + 16;
        int x = screenX(node.x());
        int y = screenY(node.y());
        return x >= left - margin && x <= right + margin && y >= top - margin && y <= bottom + margin;
    }

    private boolean edgeVisible(Edge edge) {
        int margin = 48;
        return Math.max(edge.x1(), edge.x2()) >= left - margin
                && Math.min(edge.x1(), edge.x2()) <= right + margin
                && Math.max(edge.y1(), edge.y2()) >= top - margin
                && Math.min(edge.y1(), edge.y2()) <= bottom + margin;
    }

    private record Edge(int x1, int y1, int x2, int y2, boolean primary, boolean lit, int color) {
    }

    record Position(double x, double y) {
    }
}
