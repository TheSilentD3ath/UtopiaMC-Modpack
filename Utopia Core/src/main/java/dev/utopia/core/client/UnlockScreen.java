package dev.utopia.core.client;

import dev.utopia.core.UtopiaCore;
import dev.utopia.core.character.CharacterAccess;
import dev.utopia.core.character.CharacterData;
import dev.utopia.core.network.UtopiaNetworking;
import dev.utopia.core.unlock.NodeShape;
import dev.utopia.core.unlock.UnlockService;
import dev.utopia.core.unlock.UnlockTree;
import dev.utopia.core.unlock.UnlockTreeValidator;
import dev.utopia.core.unlock.UnlockTrees;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.levelz.access.PlayerStatsManagerAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Rustikaler Freischaltbaum mit server-autoritativem OP-Editor.
 *
 * <p><b>Aufteilung:</b> Kopfzeile, darunter drei Spalten (Seitenleiste, Karte, Inspektor),
 * darunter eine Fusszeile mit Legende und Status. Alle Abmessungen entstehen an einer
 * einzigen Stelle ({@link #layout()}); Spalten, fuer die kein Platz ist, entfallen ganz,
 * statt sich zu ueberlappen oder aus dem Rahmen zu laufen.
 *
 * <p><b>Warum der Inspektor eine eigene Spalte ist:</b> Vorher lag er als schwebende Flaeche
 * ueber der Karte und verdeckte etwa ein Fuenftel davon — einschliesslich der Knoten
 * darunter, die dadurch auch nicht mehr anklickbar waren.
 *
 * <p><b>Warum die Seitenleiste verschwinden kann:</b> Bei einem einzigen Baum enthielt sie
 * nur einen Reiter und ansonsten 150 px Leere. Dieser Platz gehoert der Karte.
 */
public class UnlockScreen extends Screen {

    private enum EditorTool { SELECT, MOVE }

    private static final Identifier DARK_WOOD = UtopiaCore.id("textures/gui/unlock/dark_wood.png");
    private static final int TEXTURE_SIZE = 512;

    private static final int MAX_PANEL_WIDTH = 960;
    private static final int MAX_PANEL_HEIGHT = 700;
    private static final int OUTER = 6;
    private static final int GAP = 5;
    private static final int HEADER_HEIGHT = 30;
    /** Eine gemeinsame Leiste unten: Zoom links, Legende mittig, Aktionen rechts. */
    private static final int BAR_HEIGHT = 24;
    private static final int SIDEBAR_WIDTH = 140;
    private static final int INSPECTOR_WIDTH = 190;
    private static final int MIN_CANVAS_WIDTH = 150;
    private static final int ENTRY_HEIGHT = 20;

    // Die Oberflaeche liegt durchgehend auf dunklem Grund, damit die Karte die hellste
    // grosse Flaeche ist und der Inhalt vor dem Rahmenwerk steht. Alle Werte sind gegen
    // die gemessene Helligkeit von dark_wood geprueft und erreichen mindestens 4,5:1.
    private static final int COLOR_HEADING = 0xFFFFE9B8;
    private static final int COLOR_LIGHT = 0xFFF5DCA5;
    private static final int COLOR_MUTED = 0xFFB9A27C;
    private static final int COLOR_DISABLED = 0xFF96866F;
    private static final int COLOR_WARN = 0xFFFF8A72;
    private static final int COLOR_ACCENT = 0xFFC89B5E;
    /** Haarlinie zum Trennen von Bereichen — ersetzt die frueheren Platten mit Rahmen. */
    private static final int COLOR_RULE = 0xFF4A3828;

    /** Zoom beim Oeffnen: lesbar an der richtigen Stelle statt vollstaendig und winzig. */
    private static final double READABLE_ZOOM = 0.9;

    private Identifier currentTree;
    private UnlockTreeDraft draft;
    private boolean editing;
    private boolean focusAfterInit = true;
    private UnlockTreeCanvas canvas;
    private final List<ButtonWidget> treeTabs = new ArrayList<>();
    private final List<EditorMenuEntry> contextMenu = new ArrayList<>();
    private final Map<String, UnlockTreeCanvas.State> stateCache = new HashMap<>();

    private TextFieldWidget search;
    private String lastQuery = "";
    private Set<String> searchMatches;

    private ButtonWidget purchaseButton;
    private ButtonWidget editNodeButton;
    private ButtonWidget undoButton;
    private ButtonWidget selectedTreeTab;
    private ButtonWidget selectToolButton;
    private ButtonWidget moveToolButton;
    private EditorTool editorTool = EditorTool.SELECT;

    private int contextMenuX;
    private int contextMenuY;
    private int contextMenuWidth;
    private int detailScroll;
    private String status;
    /** Linke Kante der Fusszeilen-Knoepfe; Legende und Status enden davor. */
    private int footerButtonsLeft;
    /** Rechte Kante der Zoom-Bedienung; die Legende beginnt dahinter. */
    private int zoomBarRight;

    // Aufteilung; gesetzt in layout().
    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private int innerLeft;
    private int innerRight;
    private int headerTop;
    private int headerBottom;
    private int bodyTop;
    private int bodyBottom;
    private int sidebarLeft;
    private int sidebarRight;
    private int canvasLeft;
    private int canvasRight;
    private int inspectorLeft;
    private int inspectorRight;
    private int footerTop;
    /** Tatsaechliche Inspektorbreite dieses Aufbaus; INSPECTOR_WIDTH ist nur die Obergrenze. */
    private int inspectorWidth;
    private boolean showSidebar;
    private boolean showInspector;

    public UnlockScreen() {
        super(Text.translatable("screen.utopia.unlocks"));
    }

    private CharacterData data() {
        return ((CharacterAccess) this.client.player).utopia$getCharacter();
    }

    private int overallLevel() {
        return ((PlayerStatsManagerAccess) this.client.player).getPlayerStatsManager().getOverallLevel();
    }

    // --- Aufteilung -------------------------------------------------------

    /**
     * Legt alle Bereiche fest.
     *
     * Reihenfolge des Verzichts bei wenig Platz: zuerst faellt der Inspektor weg (seine
     * Angaben stehen auch im Tooltip), dann die Seitenleiste. Die Karte behaelt immer
     * mindestens {@link #MIN_CANVAS_WIDTH}.
     */
    private void layout() {
        int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(240, this.width - 8));
        int panelHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(180, this.height - 8));
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        panelRight = panelLeft + panelWidth;
        panelBottom = panelTop + panelHeight;

        innerLeft = panelLeft + OUTER + 4;
        innerRight = panelRight - OUTER - 4;
        headerTop = panelTop + OUTER + 3;
        headerBottom = headerTop + HEADER_HEIGHT;
        footerTop = panelBottom - OUTER - 3 - BAR_HEIGHT;
        bodyTop = headerBottom + GAP;
        bodyBottom = footerTop - GAP;

        showSidebar = UnlockTrees.ordered().size() > 1 || editing;
        int available = innerRight - innerLeft;
        if (showSidebar && available < MIN_CANVAS_WIDTH + SIDEBAR_WIDTH + GAP) {
            showSidebar = false;
        }
        sidebarLeft = innerLeft;
        sidebarRight = showSidebar ? sidebarLeft + SIDEBAR_WIDTH : sidebarLeft;

        // Die Karte bekommt die gesamte verbleibende Breite. Der Inspektor liegt als
        // Auflage ueber ihrem rechten Rand und nur dann, wenn wirklich etwas ausgewaehlt
        // ist — als Dauerspalte stand er die meiste Zeit leer und kostete ein Drittel der
        // Flaeche, die dem Baum gehoert.
        canvasLeft = sidebarRight + (showSidebar ? GAP : 0);
        canvasRight = innerRight;
        inspectorWidth = MathHelper.clamp((canvasRight - canvasLeft) * 30 / 100, 140, INSPECTOR_WIDTH);
        showInspector = canvasRight - canvasLeft - inspectorWidth >= MIN_CANVAS_WIDTH;
        inspectorRight = canvasRight;
        inspectorLeft = inspectorRight - inspectorWidth;
    }

    @Override
    protected void init() {
        treeTabs.clear();
        contextMenu.clear();
        editNodeButton = null;
        undoButton = null;
        selectedTreeTab = null;
        selectToolButton = null;
        moveToolButton = null;
        if (canvas == null) {
            canvas = new UnlockTreeCanvas();
        }
        layout();
        canvas.bounds(canvasLeft, bodyTop, canvasRight, bodyBottom);

        List<Identifier> ids = new ArrayList<>(UnlockTrees.ordered());
        if (currentTree != null && !ids.contains(currentTree)) {
            ids.add(currentTree);
        }
        if (currentTree == null && !ids.isEmpty()) {
            currentTree = ids.get(0);
            focusAfterInit = true;
        }

        buildHeader();
        if (showSidebar) {
            buildSidebar(ids);
        }
        buildFooter();
        if (showInspector) {
            // Nur als Kind fuer die Ereignisbehandlung registrieren: gezeichnet wird er
            // innerhalb der Auflage, damit er ueber deren Hintergrund liegt.
            purchaseButton = addSelectableChild(new RusticButton(inspectorLeft + 7, bodyBottom - 24,
                    inspectorWidth - 14, 19, Text.translatable("screen.utopia.unlocks.purchase"),
                    button -> purchaseSelected()));
        } else {
            purchaseButton = null;
        }

        // Suchtreffer nach einem Neuaufbau erneut anwenden.
        lastQuery = "";
        if (focusAfterInit) {
            focusProgress();
            canvas.snapToTarget();
            focusAfterInit = false;
        }
    }

    private void buildHeader() {
        int searchWidth = Math.min(150, Math.max(70, (innerRight - innerLeft) / 3));
        int pointsWidth = this.textRenderer.getWidth(pointsText()) + 22;
        int searchLeft = innerRight - pointsWidth - GAP - searchWidth;
        if (searchLeft < innerLeft + 40) {
            searchWidth = Math.max(60, innerRight - pointsWidth - GAP - innerLeft - 40);
            searchLeft = innerRight - pointsWidth - GAP - searchWidth;
        }
        String previous = search == null ? "" : search.getText();
        search = new TextFieldWidget(this.textRenderer, searchLeft, headerTop + 7, searchWidth, 16,
                Text.translatable("screen.utopia.unlocks.search"));
        search.setMaxLength(64);
        search.setText(previous);
        addDrawableChild(search);
    }

    private void buildSidebar(List<Identifier> ids) {
        int y = bodyTop;
        for (Identifier id : ids) {
            UnlockTree tree = id.equals(currentTree) ? currentTreeData() : UnlockTrees.tree(id);
            Text label = tree == null ? Text.literal(id.getPath()) : displayName(tree.name(), id.getPath());
            RusticButton tab = new RusticButton(sidebarLeft, y, SIDEBAR_WIDTH, 22, label,
                    button -> selectTree(id));
            tab.selected = id.equals(currentTree);
            tab.active = !editing && !id.equals(currentTree);
            addRustic(tab);
            treeTabs.add(tab);
            if (id.equals(currentTree)) {
                selectedTreeTab = tab;
            }
            y += 24;
        }

        if (!editing || !UtopiaCoreClient.canEditTrees) {
            return;
        }
        y += 10;
        int half = (SIDEBAR_WIDTH - 4) / 2;
        selectToolButton = new RusticButton(sidebarLeft, y, half, 20,
                Text.translatable("screen.utopia.unlocks.editor.tool.select"),
                button -> setEditorTool(EditorTool.SELECT));
        ((RusticButton) selectToolButton).selected = editorTool == EditorTool.SELECT;
        addRustic(selectToolButton);
        moveToolButton = new RusticButton(sidebarLeft + half + 4, y, SIDEBAR_WIDTH - half - 4, 20,
                Text.translatable("screen.utopia.unlocks.editor.tool.move"),
                button -> setEditorTool(EditorTool.MOVE));
        ((RusticButton) moveToolButton).selected = editorTool == EditorTool.MOVE;
        addRustic(moveToolButton);
        y += 24;
        addRustic(new RusticButton(sidebarLeft, y, SIDEBAR_WIDTH, 20,
                Text.translatable("screen.utopia.unlocks.editor.add_node"), button -> addNode()));
        y += 22;
        editNodeButton = addRustic(new RusticButton(sidebarLeft, y, SIDEBAR_WIDTH, 20,
                Text.translatable("screen.utopia.unlocks.editor.node"), button -> editSelectedNode()));
        y += 22;
        addRustic(new RusticButton(sidebarLeft, y, SIDEBAR_WIDTH, 20,
                Text.translatable("screen.utopia.unlocks.editor.tree"), button -> editTreeSettings()));
        y += 22;
        addRustic(new RusticButton(sidebarLeft, y, SIDEBAR_WIDTH, 20,
                Text.translatable("screen.utopia.unlocks.editor.auto_layout"), button -> autoLayout()));
        y += 22;
        undoButton = addRustic(new RusticButton(sidebarLeft, y, SIDEBAR_WIDTH, 20,
                Text.translatable("screen.utopia.unlocks.editor.undo"), button -> undoEdit()));
    }

    /** Eine Leiste unten: Zoom links, Legende mittig, Aktionen rechts. */
    private void buildFooter() {
        int y = footerTop + 1;
        int h = BAR_HEIGHT - 2;
        addRustic(new RusticButton(innerLeft, y, 18, h, Text.literal("\u2212"), button -> canvas.zoomBy(-1.0)));
        addRustic(new RusticButton(innerLeft + 56, y, 18, h, Text.literal("+"), button -> canvas.zoomBy(1.0)));
        addRustic(new RusticButton(innerLeft + 78, y, 66, h,
                Text.translatable("screen.utopia.unlocks.overview"), button -> fitTree()));
        zoomBarRight = innerLeft + 148;

        int x = innerRight;
        addRustic(new RusticButton(x - 56, y, 56, h, Text.translatable("gui.done"), button -> close()));
        x -= 60;
        footerButtonsLeft = x;
        if (!UtopiaCoreClient.canEditTrees) {
            return;
        }
        if (editing) {
            addRustic(new RusticButton(x - 62, y, 62, h,
                    Text.translatable("screen.utopia.unlocks.editor.discard"), button -> discardEditor()));
            x -= 66;
            addRustic(new RusticButton(x - 62, y, 62, h,
                    Text.translatable("screen.utopia.unlocks.editor.save"), button -> saveTree()));
            footerButtonsLeft = x - 62;
        } else {
            addRustic(new RusticButton(x - 76, y, 76, h,
                    Text.translatable("screen.utopia.unlocks.editor.open"), button -> enterEditor()));
            x -= 80;
            addRustic(new RusticButton(x - 68, y, 68, h,
                    Text.translatable("screen.utopia.unlocks.editor.new_tree"), button -> createTree()));
            footerButtonsLeft = x - 68;
        }
    }

    private <T extends ButtonWidget> T addRustic(T button) {
        return addDrawableChild(button);
    }

    // --- Zustand ----------------------------------------------------------

    /**
     * Zustand eines Knotens, hoechstens einmal je Bild berechnet.
     *
     * {@code buyability} schlaegt den Knoten und jeden Elternknoten nach und erzeugt dabei
     * Identifier. Ohne diesen Zwischenspeicher lief das pro Bild mehrere hundert Mal.
     */
    private UnlockTreeCanvas.State state(String key, UnlockTree.Node node) {
        UnlockTreeCanvas.State cached = stateCache.get(key);
        if (cached != null) {
            return cached;
        }
        UnlockTreeCanvas.State computed = compute(key, node);
        stateCache.put(key, computed);
        return computed;
    }

    private UnlockTreeCanvas.State compute(String key, UnlockTree.Node node) {
        if (currentTree == null || node == null) {
            return UnlockTreeCanvas.State.BLOCKED;
        }
        String nodeId = currentTree.getPath() + "/" + key;
        return switch (UnlockService.buyability(data(), nodeId, overallLevel())) {
            case BUYABLE -> UnlockTreeCanvas.State.BUYABLE;
            case ALREADY_OWNED -> UnlockTreeCanvas.State.OWNED;
            case NOT_ENOUGH_POINTS -> UnlockTreeCanvas.State.TOO_EXPENSIVE;
            default -> UnlockTreeCanvas.State.BLOCKED;
        };
    }

    private UnlockTree currentTreeData() {
        if (editing && draft != null) {
            return draft.build();
        }
        return currentTree == null ? null : UnlockTrees.tree(currentTree);
    }

    private Text pointsText() {
        return Text.translatable("screen.utopia.unlocks.points", data().unlockPoints());
    }

    // --- Zeichnen ---------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        stateCache.clear();
        renderBackground(context);
        UnlockTree tree = currentTreeData();
        updateSearch(tree);

        // Eine durchgehende dunkle Grundflaeche. Vorher standen hier fuenf einzeln
        // gerahmte Platten in vier verschiedenen Holztoenen nebeneinander; das las sich
        // als Flickenteppich und liess dem Baum kaum Gewicht.
        drawWoodPanel(context, panelLeft + OUTER, panelTop + OUTER, panelRight - OUTER, panelBottom - OUTER);
        // Bereiche werden nur noch durch Haarlinien getrennt, nicht durch eigene Flaechen.
        context.fill(innerLeft, headerBottom - 1, innerRight, headerBottom, COLOR_RULE);
        context.fill(innerLeft, footerTop - 1, innerRight, footerTop, COLOR_RULE);
        if (showSidebar) {
            context.fill(sidebarRight + GAP / 2, bodyTop, sidebarRight + GAP / 2 + 1, bodyBottom, COLOR_RULE);
        }
        drawCanvasFrame(context, canvasLeft, bodyTop, canvasRight, bodyBottom);

        renderHeader(context, tree);

        if (tree == null) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("screen.utopia.unlocks.empty"),
                    (canvasLeft + canvasRight) / 2, (bodyTop + bodyBottom) / 2, COLOR_LIGHT);
        } else {
            canvas.render(context, tree, this::state, editing, mouseX, mouseY);
        }

        renderFooter(context);
        updateButtonState(tree);
        super.render(context, mouseX, mouseY, delta);
        renderSearchPlaceholder(context);

        if (inspectorVisible(tree)) {
            renderInspector(context, tree);
        }
        if (tree != null && contextMenu.isEmpty()) {
            renderNodeTooltip(context, tree, mouseX, mouseY);
        }
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 600.0F);
        renderContextMenu(context, mouseX, mouseY);
        context.getMatrices().pop();
    }

    private void renderHeader(DrawContext context, UnlockTree tree) {
        int centerY = (headerTop + headerBottom) / 2;
        int textY = centerY - this.textRenderer.fontHeight / 2;
        int x = innerLeft + 2;
        if (tree != null) {
            Item icon = tree.icon().map(Registries.ITEM::get).orElse(null);
            if (icon != null && icon != net.minecraft.item.Items.AIR) {
                context.drawItem(new ItemStack(icon), x, centerY - 8);
                x += 21;
            }
        }
        Text heading = tree == null ? Text.translatable("screen.utopia.unlocks")
                : displayName(tree.name(), currentTree == null ? "" : currentTree.getPath());
        int headingLimit = (search == null ? innerRight : search.getX()) - x - GAP;
        if (headingLimit > 24) {
            // Der Titel wird vergroessert gezeichnet. Ohne Groessenunterschied hatte die
            // Ueberschrift dasselbe Gewicht wie der Zoom-Hinweis, und die Kopfzeile las
            // sich als Reihe gleichrangiger Woerter.
            float scale = 1.6F;
            Text scaled = trim(heading, (int) (headingLimit / scale));
            context.getMatrices().push();
            context.getMatrices().translate(x, centerY - this.textRenderer.fontHeight * scale / 2.0F, 0.0F);
            context.getMatrices().scale(scale, scale, 1.0F);
            context.drawText(this.textRenderer, scaled, 0, 0, COLOR_HEADING, false);
            context.getMatrices().pop();
        }

        // Punktestand und Fortschritt rechts, nur durch Farbe abgesetzt.
        Text points = pointsText();
        context.drawText(this.textRenderer, points,
                innerRight - this.textRenderer.getWidth(points), textY, COLOR_ACCENT, false);
        if (tree != null) {
            int owned = 0;
            for (Map.Entry<String, UnlockTree.Node> entry : tree.nodes().entrySet()) {
                if (state(entry.getKey(), entry.getValue()) == UnlockTreeCanvas.State.OWNED) {
                    owned++;
                }
            }
            Text progress = Text.translatable("screen.utopia.unlocks.progress", owned, tree.nodes().size());
            int progressX = innerRight - this.textRenderer.getWidth(points) - GAP
                    - this.textRenderer.getWidth(progress);
            if (progressX > (search == null ? innerLeft : search.getX() + search.getWidth()) + GAP) {
                context.drawText(this.textRenderer, progress, progressX, textY, COLOR_MUTED, false);
            }
        }
    }

    /**
     * Platzhalter im leeren Suchfeld.
     *
     * Muss nach {@code super.render} laufen: Das Textfeld zeichnet seinen eigenen
     * Hintergrund und wuerde den Platzhalter sonst wieder zudecken.
     */
    private void renderSearchPlaceholder(DrawContext context) {
        if (search == null) {
            return;
        }
        if (search.getText().isEmpty() && !search.isFocused()) {
            context.drawText(this.textRenderer, trim(Text.translatable("screen.utopia.unlocks.search"),
                    search.getWidth() - 8), search.getX() + 4, search.getY() + 4, 0xFF8A7355, false);
        }
    }

    /**
     * Die eine Leiste am unteren Rand: Zoomanzeige, Legende, Status.
     *
     * Vorher waren das zwei uebereinanderliegende Balken — eine Werkzeugleiste unter der
     * Karte und darunter noch eine Fusszeile. Zusammen mit der Dauerspalte rechts blieb dem
     * Baum nur gut ein Drittel der Flaeche.
     */
    private void renderFooter(DrawContext context) {
        int y = footerTop + (BAR_HEIGHT - 8) / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(canvas.zoomPercent() + "%"),
                innerLeft + 37, y, COLOR_LIGHT);

        int x = zoomBarRight + GAP;
        int limit = footerContentRight();
        for (UnlockTreeCanvas.State state : UnlockTreeCanvas.State.values()) {
            // Eigene Kurzform: die vollen Zustandstexte passen nicht zu viert nebeneinander.
            Text label = Text.translatable("screen.utopia.unlocks.legend." + state.name().toLowerCase(Locale.ROOT));
            int width = 11 + this.textRenderer.getWidth(label) + 9;
            if (x + width > limit) {
                break;
            }
            int color = UnlockTreeCanvas.outlineColor(state);
            context.setShaderColor((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                    (color & 255) / 255.0F, 1.0F);
            context.drawTexture(UnlockTreeCanvas.badgeTexture(state), x, y - 1, 9, 9,
                    0.0F, 0.0F, 32, 32, 32, 32);
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            context.drawText(this.textRenderer, label, x + 12, y, COLOR_MUTED, false);
            x += width;
        }
        if (status != null && x < limit - 40) {
            context.drawTextWithShadow(this.textRenderer, trim(Text.literal(status), limit - x - 8),
                    x + 8, y, COLOR_ACCENT);
        }
    }

    /**
     * Rechte Grenze fuer Legende und Status.
     *
     * Ergibt sich aus der tatsaechlichen linken Kante der Fusszeilen-Knoepfe. Eine geschaetzte
     * Konstante hatte hier die Legende bis auf den ersten Eintrag abgeschnitten, sobald die
     * Oberflaechenskalierung vom angenommenen Fall abwich.
     */
    private int footerContentRight() {
        return footerButtonsLeft > innerLeft ? footerButtonsLeft - GAP : innerRight;
    }

    /** Sichtbar nur mit Auswahl: sonst stuende hier die meiste Zeit eine leere Flaeche. */
    private boolean inspectorVisible(UnlockTree tree) {
        String selected = canvas == null ? null : canvas.selected();
        return showInspector && tree != null && selected != null && tree.nodes().containsKey(selected);
    }

    /**
     * Angaben zum ausgewaehlten Knoten, als Auflage ueber dem rechten Rand der Karte.
     *
     * Als staendige Spalte kostete der Inspektor rund ein Drittel der Breite, stand aber die
     * meiste Zeit leer. Als Auflage ist das vertretbar, weil die Auswahl jederzeit durch
     * einen Klick ins Leere aufgehoben werden kann — genau das ging vorher nicht.
     */
    private void renderInspector(DrawContext context, UnlockTree tree) {
        String selected = canvas.selected();
        int x = inspectorLeft + 7;
        int width = inspectorWidth - 14;

        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 300.0F);
        context.fill(inspectorLeft, bodyTop, inspectorRight, bodyBottom, 0xF01A130D);
        context.fill(inspectorLeft, bodyTop, inspectorLeft + 1, bodyBottom, COLOR_ACCENT);

        UnlockTree.Node node = tree.nodes().get(selected);
        String nodeId = currentTree.getPath() + "/" + selected;
        int y = bodyTop + 7;
        Text name = displayName(node.name(), selected);
        context.drawTextWrapped(this.textRenderer, name, x, y, width, COLOR_HEADING);
        y += this.textRenderer.wrapLines(name, width).size() * 10 + 3;

        if (node.description().isPresent()) {
            Text description = displayName(node.description(), node.description().get());
            context.drawTextWrapped(this.textRenderer, description, x, y, width, COLOR_MUTED);
            y += this.textRenderer.wrapLines(description, width).size() * 9 + 5;
        }

        int cost = UnlockService.cost(data(), nodeId, node);
        int level = UnlockService.requiredLevel(data(), nodeId, node);
        UnlockTreeCanvas.State state = state(selected, node);
        context.drawText(this.textRenderer,
                Text.translatable("screen.utopia.unlocks.state." + state.name().toLowerCase(Locale.ROOT)),
                x, y, COLOR_LIGHT, false);
        y += 12;
        if (state != UnlockTreeCanvas.State.OWNED) {
            context.drawText(this.textRenderer, Text.translatable("screen.utopia.unlocks.cost", cost), x, y,
                    data().unlockPoints() >= cost ? COLOR_MUTED : COLOR_WARN, false);
            y += 11;
            if (level > 0) {
                context.drawText(this.textRenderer, Text.translatable("screen.utopia.unlocks.level", level), x, y,
                        overallLevel() >= level ? COLOR_MUTED : COLOR_WARN, false);
                y += 11;
            }
        }

        if (!node.parents().isEmpty()) {
            // Namen statt roher Schluessel: "Messing-Zeitalter" sagt mehr als "brass".
            List<Text> names = new ArrayList<>();
            for (String parent : node.parents()) {
                UnlockTree.Node parentNode = tree.nodes().get(parent);
                names.add(parentNode == null ? Text.literal(parent) : displayName(parentNode.name(), parent));
            }
            Text joined = Text.translatable("screen.utopia.unlocks.requires", join(names));
            context.drawTextWrapped(this.textRenderer, joined, x, y + 3, width, COLOR_MUTED);
            y += this.textRenderer.wrapLines(joined, width).size() * 9 + 8;
        }

        context.fill(x, y, x + width, y + 1, COLOR_RULE);
        y += 6;
        context.drawText(this.textRenderer, Text.translatable("screen.utopia.unlocks.contains"), x, y,
                COLOR_HEADING, false);
        y += 12;

        int listBottom = purchaseButton != null && purchaseButton.visible ? purchaseButton.getY() - 5 : bodyBottom - 4;
        if (listBottom > y) {
            context.enableScissor(inspectorLeft + 2, y - 2, inspectorRight - 2, listBottom);
            int entryY = y - detailScroll;
            if (node.unlocks().isEmpty()) {
                context.drawText(this.textRenderer, Text.translatable("screen.utopia.unlocks.no_entries"), x, entryY,
                        COLOR_MUTED, false);
            }
            for (String unlock : node.unlocks()) {
                if (entryY > listBottom) {
                    break;
                }
                if (entryY + ENTRY_HEIGHT >= y) {
                    drawUnlockEntry(context, unlock, x, entryY, width);
                }
                entryY += ENTRY_HEIGHT;
            }
            context.disableScissor();
        }
        if (purchaseButton != null && purchaseButton.visible) {
            purchaseButton.render(context, -1, -1, 0.0F);
        }
        context.getMatrices().pop();
    }

    private void drawUnlockEntry(DrawContext context, String entry, int x, int y, int width) {
        Text label;
        Item item = null;
        if (entry.endsWith(":*")) {
            label = Text.translatable("screen.utopia.unlocks.addon_all", entry.substring(0, entry.length() - 2));
        } else if (entry.startsWith("#")) {
            label = Text.translatable("screen.utopia.unlocks.tag", entry.substring(1));
        } else {
            Identifier id = Identifier.tryParse(entry);
            item = id == null ? null : Registries.ITEM.get(id);
            label = item == null || item == net.minecraft.item.Items.AIR ? Text.literal(entry) : item.getName();
        }
        if (item != null && item != net.minecraft.item.Items.AIR) {
            context.drawItem(new ItemStack(item), x, y - 3);
        }
        context.drawText(this.textRenderer, trim(label, width - 22), x + 20, y, COLOR_LIGHT, false);
    }

    /**
     * Tooltip am Zeiger. Er traegt seit dem Wegfall der Kartenbeschriftung die Hauptlast:
     * Name und Zustand sollen erreichbar sein, ohne dass man erst klicken muss.
     */
    private void renderNodeTooltip(DrawContext context, UnlockTree tree, int mouseX, int mouseY) {
        String key = canvas.hovered();
        if (key == null) {
            return;
        }
        UnlockTree.Node node = tree.nodes().get(key);
        if (node == null) {
            return;
        }
        String nodeId = currentTree.getPath() + "/" + key;
        UnlockTreeCanvas.State state = state(key, node);
        List<Text> lines = new ArrayList<>();
        lines.add(displayName(node.name(), key).copy().formatted(Formatting.WHITE));
        lines.add(Text.translatable("screen.utopia.unlocks.state." + state.name().toLowerCase(Locale.ROOT))
                .formatted(switch (state) {
                    case OWNED -> Formatting.GREEN;
                    case BUYABLE -> Formatting.YELLOW;
                    case TOO_EXPENSIVE -> Formatting.GOLD;
                    case BLOCKED -> Formatting.GRAY;
                }));
        if (state != UnlockTreeCanvas.State.OWNED) {
            int cost = UnlockService.cost(data(), nodeId, node);
            lines.add(Text.translatable("screen.utopia.unlocks.cost", cost)
                    .formatted(data().unlockPoints() >= cost ? Formatting.GRAY : Formatting.RED));
            int level = UnlockService.requiredLevel(data(), nodeId, node);
            if (level > 0) {
                lines.add(Text.translatable("screen.utopia.unlocks.level", level)
                        .formatted(overallLevel() >= level ? Formatting.GRAY : Formatting.RED));
            }
        }
        if (!node.unlocks().isEmpty()) {
            lines.add(Text.translatable("screen.utopia.unlocks.entry_count", node.unlocks().size())
                    .formatted(Formatting.DARK_GRAY));
        }
        context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
    }

    private void updateButtonState(UnlockTree tree) {
        String selected = canvas.selected();
        boolean hasNode = tree != null && selected != null && tree.nodes().containsKey(selected);
        if (editNodeButton != null) {
            editNodeButton.active = hasNode;
        }
        if (undoButton != null) {
            undoButton.active = draft != null && draft.canUndo();
        }
        if (purchaseButton != null) {
            purchaseButton.visible = !editing && hasNode && inspectorVisible(tree);
            purchaseButton.active = hasNode
                    && state(selected, tree.nodes().get(selected)) == UnlockTreeCanvas.State.BUYABLE;
        }
        for (ButtonWidget tab : treeTabs) {
            tab.active = !editing && tab != selectedTreeTab;
        }
    }

    // --- Suche ------------------------------------------------------------

    /**
     * Treffer nur bei Textaenderung berechnen, nicht je Bild: Ein Durchlauf loest die
     * Item-Namen aller Eintraege auf, das sind beim Create-Baum ueber tausend Registry-Zugriffe.
     */
    private void updateSearch(UnlockTree tree) {
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        if (query.equals(lastQuery)) {
            return;
        }
        lastQuery = query;
        if (query.isEmpty() || tree == null) {
            searchMatches = null;
            canvas.searchMatches(null);
            return;
        }
        Set<String> matches = new HashSet<>();
        tree.nodes().forEach((key, node) -> {
            if (matches(key, node, query)) {
                matches.add(key);
            }
        });
        searchMatches = matches;
        canvas.searchMatches(matches);
        status = Text.translatable("screen.utopia.unlocks.search_result", matches.size()).getString();
    }

    private boolean matches(String key, UnlockTree.Node node, String query) {
        if (key.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (displayName(node.name(), key).getString().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (node.description().isPresent()
                && displayName(node.description(), "").getString().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (String entry : node.unlocks()) {
            if (entry.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
            Identifier id = Identifier.tryParse(entry);
            if (id == null) {
                continue;
            }
            Item item = Registries.ITEM.get(id);
            if (item != net.minecraft.item.Items.AIR
                    && item.getName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    // --- Kamera -----------------------------------------------------------

    /**
     * Auf die Fortschrittskante fahren: den guenstigsten kaufbaren Knoten, sonst den am
     * weitesten fortgeschrittenen besessenen, sonst den Anfang. Der Spieler soll dort
     * landen, wo er weiterkommt, statt vor der Gesamtuebersicht zu stehen.
     */
    private void focusProgress() {
        UnlockTree tree = currentTreeData();
        if (tree == null || tree.nodes().isEmpty()) {
            canvas.fit(tree);
            return;
        }
        // Ausserhalb von render(): der Zwischenspeicher koennte vom letzten Bild stammen.
        stateCache.clear();
        String buyable = null;
        int cheapest = Integer.MAX_VALUE;
        String owned = null;
        double furthest = -Double.MAX_VALUE;
        String first = null;
        double earliest = Double.MAX_VALUE;
        for (Map.Entry<String, UnlockTree.Node> entry : tree.nodes().entrySet()) {
            String key = entry.getKey();
            UnlockTree.Node node = entry.getValue();
            if (node.x() < earliest) {
                earliest = node.x();
                first = key;
            }
            UnlockTreeCanvas.State state = state(key, node);
            if (state == UnlockTreeCanvas.State.BUYABLE) {
                int cost = UnlockService.cost(data(), currentTree.getPath() + "/" + key, node);
                if (cost < cheapest || (cost == cheapest && (buyable == null || key.compareTo(buyable) < 0))) {
                    cheapest = cost;
                    buyable = key;
                }
            } else if (state == UnlockTreeCanvas.State.OWNED && node.x() > furthest) {
                furthest = node.x();
                owned = key;
            }
        }
        String target = buyable != null ? buyable : owned != null ? owned : first;
        canvas.focus(tree, target, READABLE_ZOOM);
    }

    private void fitTree() {
        canvas.fit(currentTreeData());
    }

    // --- Aktionen ---------------------------------------------------------

    private void selectTree(Identifier id) {
        currentTree = id;
        canvas.select(null);
        detailScroll = 0;
        focusAfterInit = true;
        clearAndInit();
    }

    private void purchaseSelected() {
        String selected = canvas.selected();
        UnlockTree tree = currentTreeData();
        if (selected == null || tree == null
                || state(selected, tree.nodes().get(selected)) != UnlockTreeCanvas.State.BUYABLE) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(currentTree.getPath() + "/" + selected);
        ClientPlayNetworking.send(UtopiaNetworking.BUY_NODE, buf);
        // Sofortige Rueckmeldung. Vorher passierte beim Klick sichtbar nichts, bis der
        // Server-Sync zurueckkam; die verbindliche Bestaetigung kommt weiterhin von dort.
        status = Text.translatable("screen.utopia.unlocks.sent").getString();
    }

    private void enterEditor() {
        UnlockTree tree = currentTreeData();
        if (!UtopiaCoreClient.canEditTrees || tree == null) {
            return;
        }
        draft = new UnlockTreeDraft(tree);
        editing = true;
        editorTool = EditorTool.SELECT;
        status = Text.translatable("screen.utopia.unlocks.editor.draft_active").getString();
        clearAndInit();
    }

    private void createTree() {
        if (!UtopiaCoreClient.canEditTrees) {
            return;
        }
        int number = 1;
        Identifier id;
        do {
            id = new Identifier("utopia", "custom_" + number++);
        } while (UnlockTrees.all().containsKey(id));
        UnlockTree.Node root = new UnlockTree.Node(Optional.of("Start"), Optional.empty(), Optional.empty(),
                NodeShape.DEFAULT, 0, 0, List.of(), List.of(0.0, 0.0), List.of(), List.of(), List.of(), List.of());
        currentTree = id;
        draft = new UnlockTreeDraft(new UnlockTree(Optional.of("Neuer Technikbaum"), Optional.empty(),
                Optional.empty(), 1000, Map.of("start", root)));
        editing = true;
        editorTool = EditorTool.SELECT;
        canvas.select("start");
        focusAfterInit = true;
        status = Text.translatable("screen.utopia.unlocks.editor.new_draft", id.toString()).getString();
        clearAndInit();
    }

    private void addNode() {
        if (draft == null) {
            return;
        }
        UnlockTree.Node selected = draft.nodes().get(canvas.selected());
        double x = selected == null ? 0.0 : selected.x() + 2.0;
        double y = selected == null ? 0.0 : selected.y() + 1.0;
        addNodeAt(new UnlockTreeCanvas.Position(x, y));
    }

    private void addNodeAt(UnlockTreeCanvas.Position position) {
        if (draft == null) {
            return;
        }
        String key = draft.uniqueNodeKey();
        UnlockTree.Node selected = draft.nodes().get(canvas.selected());
        List<String> parents = selected == null ? List.of() : List.of(canvas.selected());
        UnlockTree.Node node = new UnlockTree.Node(Optional.of("Neuer Knoten"), Optional.empty(), Optional.empty(),
                NodeShape.DEFAULT, 1, 0, parents, List.of(position.x(), position.y()),
                List.of(), List.of(), List.of(), List.of());
        this.client.setScreen(new UnlockNodeEditorScreen(this, key, node, true, (newKey, edited) -> {
            if (!newKey.equals(key) && draft.nodes().containsKey(newKey)) {
                status = Text.translatable("screen.utopia.unlocks.editor.error.duplicate", newKey).getString();
                return;
            }
            draft.put(newKey, edited);
            canvas.select(newKey);
            status = Text.translatable("screen.utopia.unlocks.editor.node_added").getString();
        }));
    }

    private void setEditorTool(EditorTool tool) {
        editorTool = tool;
        contextMenu.clear();
        status = Text.translatable(tool == EditorTool.MOVE
                ? "screen.utopia.unlocks.editor.move_active"
                : "screen.utopia.unlocks.editor.select_active").getString();
        if (selectToolButton instanceof RusticButton select) {
            select.selected = tool == EditorTool.SELECT;
        }
        if (moveToolButton instanceof RusticButton move) {
            move.selected = tool == EditorTool.MOVE;
        }
    }

    private void autoLayout() {
        if (draft == null) {
            return;
        }
        draft.autoLayout();
        canvas.fit(draft.build());
        status = Text.translatable("screen.utopia.unlocks.editor.layout_done").getString();
    }

    private void undoEdit() {
        if (draft != null && draft.undo()) {
            status = Text.translatable("screen.utopia.unlocks.editor.undone").getString();
        }
    }

    private void duplicateSelectedNode() {
        if (draft == null || canvas.selected() == null) {
            return;
        }
        UnlockTree.Node source = draft.nodes().get(canvas.selected());
        if (source == null) {
            return;
        }
        String key = draft.uniqueNodeKey();
        draft.put(key, source.withPosition(source.x() + 0.5, source.y() + 0.5));
        canvas.select(key);
        status = Text.translatable("screen.utopia.unlocks.editor.duplicated", key).getString();
    }

    private void deleteSelectedNode() {
        if (draft == null || canvas.selected() == null) {
            return;
        }
        String key = canvas.selected();
        UnlockTree.Node node = draft.nodes().get(key);
        if (node == null) {
            return;
        }
        if (node.parents().isEmpty()) {
            status = Text.translatable("screen.utopia.unlocks.editor.error.root").getString();
            return;
        }
        boolean required = draft.nodes().values().stream().anyMatch(candidate -> candidate.parents().contains(key));
        if (required) {
            status = Text.translatable("screen.utopia.unlocks.editor.error.needed", key).getString();
            return;
        }
        draft.remove(key);
        canvas.select(null);
        status = Text.translatable("screen.utopia.unlocks.editor.removed", key).getString();
    }

    private void nudgeSelected(double deltaX, double deltaY) {
        if (draft == null || canvas.selected() == null) {
            return;
        }
        UnlockTree.Node node = draft.nodes().get(canvas.selected());
        if (node != null) {
            draft.move(canvas.selected(), node.x() + deltaX, node.y() + deltaY);
        }
    }

    private void editSelectedNode() {
        if (draft == null || canvas.selected() == null) {
            return;
        }
        String key = canvas.selected();
        UnlockTree.Node node = draft.nodes().get(key);
        if (node == null) {
            return;
        }
        this.client.setScreen(new UnlockNodeEditorScreen(this, key, node, false, (ignored, edited) -> {
            draft.put(key, edited);
            status = Text.translatable("screen.utopia.unlocks.editor.node_updated").getString();
        }));
    }

    private void editTreeSettings() {
        if (draft == null || currentTree == null) {
            return;
        }
        UnlockTree tree = draft.build();
        this.client.setScreen(new UnlockTreeSettingsScreen(this, currentTree.toString(), tree.name().orElse(""),
                tree.description().orElse(""), tree.icon().map(Identifier::toString).orElse(""), tree.order(),
                draft::setMetadata));
    }

    private void saveTree() {
        if (draft == null || currentTree == null) {
            return;
        }
        UnlockTree tree = draft.build();
        String problem = UnlockTreeValidator.validate(currentTree, tree);
        if (problem != null) {
            status = Text.translatable("screen.utopia.unlocks.editor.not_saved", problem).getString();
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeIdentifier(currentTree);
        buf.encode(net.minecraft.nbt.NbtOps.INSTANCE, UnlockTree.CODEC, tree);
        ClientPlayNetworking.send(UtopiaNetworking.SAVE_TREE, buf);
        editing = false;
        draft = null;
        contextMenu.clear();
        status = Text.translatable("screen.utopia.unlocks.editor.sent").getString();
        clearAndInit();
    }

    private void discardEditor() {
        editing = false;
        draft = null;
        contextMenu.clear();
        if (currentTree != null && UnlockTrees.tree(currentTree) == null) {
            currentTree = UnlockTrees.ordered().stream().findFirst().orElse(null);
        }
        status = Text.translatable("screen.utopia.unlocks.editor.discarded").getString();
        focusAfterInit = true;
        clearAndInit();
    }

    // --- Kontextmenue -----------------------------------------------------

    private boolean openContextMenu(UnlockTree tree, double mouseX, double mouseY) {
        if (!editing || tree == null || !canvas.contains(mouseX, mouseY)) {
            return false;
        }
        contextMenu.clear();
        String hit = canvas.nodeAt(tree, mouseX, mouseY);
        if (hit != null) {
            canvas.select(hit);
            contextMenu.add(new EditorMenuEntry(Text.translatable("screen.utopia.unlocks.editor.node"),
                    this::editSelectedNode, false));
            contextMenu.add(new EditorMenuEntry(Text.translatable("screen.utopia.unlocks.editor.tool.move"),
                    () -> setEditorTool(EditorTool.MOVE), false));
            contextMenu.add(new EditorMenuEntry(Text.translatable("screen.utopia.unlocks.editor.duplicate"),
                    this::duplicateSelectedNode, false));
            contextMenu.add(new EditorMenuEntry(Text.translatable("screen.utopia.unlocks.editor.delete"),
                    this::deleteSelectedNode, true));
        } else {
            UnlockTreeCanvas.Position position = canvas.modelAt(mouseX, mouseY, !hasShiftDown());
            contextMenu.add(new EditorMenuEntry(Text.translatable("screen.utopia.unlocks.editor.add_here"),
                    () -> addNodeAt(position), false));
            contextMenu.add(new EditorMenuEntry(Text.translatable("screen.utopia.unlocks.editor.tree"),
                    this::editTreeSettings, false));
            contextMenu.add(new EditorMenuEntry(Text.translatable("screen.utopia.unlocks.editor.auto_layout"),
                    this::autoLayout, false));
            contextMenu.add(new EditorMenuEntry(Text.translatable("screen.utopia.unlocks.overview"),
                    this::fitTree, false));
        }
        contextMenuWidth = Math.max(124, contextMenu.stream()
                .mapToInt(entry -> this.textRenderer.getWidth(entry.label()) + 24).max().orElse(124));
        int menuHeight = contextMenu.size() * 20 + 4;
        contextMenuX = Math.max(canvas.left() + 4,
                Math.min((int) mouseX, canvas.right() - contextMenuWidth - 4));
        contextMenuY = Math.max(canvas.top() + 4,
                Math.min((int) mouseY, canvas.bottom() - menuHeight - 4));
        return true;
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY, int button) {
        if (contextMenu.isEmpty()) {
            return false;
        }
        int menuHeight = contextMenu.size() * 20 + 4;
        if (button == 0 && mouseX >= contextMenuX && mouseX < contextMenuX + contextMenuWidth
                && mouseY >= contextMenuY && mouseY < contextMenuY + menuHeight) {
            int index = Math.min(contextMenu.size() - 1, ((int) mouseY - contextMenuY - 2) / 20);
            EditorMenuEntry entry = contextMenu.get(Math.max(0, index));
            contextMenu.clear();
            entry.action().run();
            return true;
        }
        contextMenu.clear();
        return false;
    }

    private void renderContextMenu(DrawContext context, int mouseX, int mouseY) {
        if (contextMenu.isEmpty()) {
            return;
        }
        int bottom = contextMenuY + contextMenu.size() * 20 + 4;
        context.fill(contextMenuX - 2, contextMenuY - 2,
                contextMenuX + contextMenuWidth + 2, bottom + 2, 0xE8211009);
        context.fill(contextMenuX, contextMenuY, contextMenuX + contextMenuWidth, bottom, 0xF24A2B19);
        context.fill(contextMenuX + 1, contextMenuY + 1,
                contextMenuX + contextMenuWidth - 1, contextMenuY + 2, 0xFFD7A65B);
        for (int i = 0; i < contextMenu.size(); i++) {
            int top = contextMenuY + 2 + i * 20;
            boolean hovered = mouseX >= contextMenuX && mouseX < contextMenuX + contextMenuWidth
                    && mouseY >= top && mouseY < top + 20;
            if (hovered) {
                context.fill(contextMenuX + 2, top, contextMenuX + contextMenuWidth - 2, top + 20, 0xFF6A4326);
            }
            EditorMenuEntry entry = contextMenu.get(i);
            context.drawTextWithShadow(this.textRenderer, entry.label(), contextMenuX + 10, top + 6,
                    entry.danger() ? 0xFFFF9A78 : COLOR_LIGHT);
        }
    }

    // --- Eingabe ----------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleContextMenuClick(mouseX, mouseY, button)) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (insideInspector(mouseX, mouseY)) {
            // Die Auflage liegt ueber der Karte; ein Klick darin darf dort nichts ausloesen.
            return true;
        }
        UnlockTree tree = currentTreeData();
        if (button == 1 && openContextMenu(tree, mouseX, mouseY)) {
            detailScroll = 0;
            return true;
        }
        if (tree != null && canvas.mouseClicked(tree, mouseX, mouseY, button)) {
            detailScroll = 0;
            // Klick auf die Karte gibt die Tastatur wieder frei, sonst schluckt das
            // Suchfeld die Editor-Kuerzel.
            this.setFocused(null);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        boolean moving = editing && editorTool == EditorTool.MOVE;
        if (canvas.mouseDragged(mouseX, mouseY, deltaX, deltaY, moving, !hasShiftDown(),
                (key, x, y) -> {
                    if (draft != null) {
                        // Erst hier, nicht schon beim Ziehbeginn: Ein Schwenk der Ansicht
                        // soll keinen Eintrag in der Rueckgaengig-Liste hinterlassen.
                        draft.beginGesture();
                        draft.move(key, x, y);
                    }
                })) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draft != null) {
            draft.endGesture();
        }
        if (canvas.mouseReleased()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (insideInspector(mouseX, mouseY)) {
            UnlockTree tree = currentTreeData();
            UnlockTree.Node node = tree == null ? null : tree.nodes().get(canvas.selected());
            int available = Math.max(40, bodyBottom - bodyTop - 150);
            int max = node == null ? 0 : Math.max(0, node.unlocks().size() * ENTRY_HEIGHT - available);
            detailScroll = Math.max(0, Math.min(max, detailScroll - (int) Math.round(amount * 24)));
            return true;
        }
        if (canvas.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search != null && search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                search.setText("");
                this.setFocused(null);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !contextMenu.isEmpty()) {
            contextMenu.clear();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown() && search != null) {
            this.setFocused(search);
            return true;
        }
        if (editing) {
            if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
                undoEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V) {
                setEditorTool(EditorTool.SELECT);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_M) {
                setEditorTool(EditorTool.MOVE);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_E && canvas.selected() != null) {
                editSelectedNode();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                deleteSelectedNode();
                return true;
            }
            if (hasControlDown()) {
                double step = hasShiftDown() ? 0.1 : 0.5;
                if (keyCode == GLFW.GLFW_KEY_LEFT) {
                    nudgeSelected(-step, 0.0);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                    nudgeSelected(step, 0.0);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_UP) {
                    nudgeSelected(0.0, -step);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_DOWN) {
                    nudgeSelected(0.0, step);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean insideInspector(double mouseX, double mouseY) {
        return inspectorVisible(currentTreeData()) && mouseX >= inspectorLeft && mouseX < inspectorRight
                && mouseY >= bodyTop && mouseY < bodyBottom;
    }

    @Override
    public void resize(net.minecraft.client.MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        // Nach einer Groessenaenderung stimmt die Kameramitte noch, nur die Bildausschnitte
        // sind neu — deshalb kein erneutes Anfahren der Fortschrittskante.
        canvas.snapToTarget();
    }

    @Override
    public void close() {
        if (editing) {
            editing = false;
            draft = null;
        }
        contextMenu.clear();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // --- Hilfsmittel ------------------------------------------------------

    static Text displayName(Optional<String> value, String fallback) {
        if (value.isEmpty()) {
            return Text.literal(fallback);
        }
        String name = value.get();
        return I18n.hasTranslation(name) ? Text.translatable(name) : Text.literal(name);
    }

    private static Text join(List<Text> parts) {
        net.minecraft.text.MutableText joined = Text.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                joined.append(", ");
            }
            joined.append(parts.get(i));
        }
        return joined;
    }

    /** Kuerzt mit Auslassungszeichen. Vorher lief zu langer Text ueber seinen Hintergrund. */
    private Text trim(Text text, int width) {
        if (width <= 0) {
            return Text.empty();
        }
        if (this.textRenderer.getWidth(text) <= width) {
            return text;
        }
        String plain = text.getString();
        int ellipsis = this.textRenderer.getWidth("...");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < plain.length(); i++) {
            if (this.textRenderer.getWidth(builder.toString() + plain.charAt(i)) + ellipsis > width) {
                break;
            }
            builder.append(plain.charAt(i));
        }
        return Text.literal(builder.toString().stripTrailing() + "...");
    }

    // --- Rahmen und Flaechen ---------------------------------------------

    /**
     * Grundflaeche der Oberflaeche.
     *
     * Bewusst ruhig: eine dunkle Holzflaeche, ein Schlagschatten, eine warme Randlinie.
     * Die frueheren vier gestapelten Rahmenkanten in vier Braunstufen haben Gewicht an
     * sich gezogen, das dem Inhalt fehlte.
     */
    static void drawWoodPanel(DrawContext context, int left, int top, int right, int bottom) {
        context.fill(left + 5, top + 6, right + 5, bottom + 6, 0xA0000000);
        context.drawTexture(DARK_WOOD, left, top, right - left, bottom - top,
                0.0F, 0.0F, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        // Leicht abdunkeln, damit die Karte als hellste Flaeche stehen bleibt.
        context.fill(left, top, right, bottom, 0x50120C08);
        context.fill(left, top, right, top + 1, 0xFFC89B5E);
        context.fill(left, bottom - 1, right, bottom, 0xFF120C08);
        context.fill(left, top, left + 1, bottom, 0xFF8A6B44);
        context.fill(right - 1, top, right, bottom, 0xFF120C08);
    }

    /** Schmaler Rahmen: die Karte soll wirken, nicht ihr Rahmen. */
    private static void drawCanvasFrame(DrawContext context, int left, int top, int right, int bottom) {
        context.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF120C08);
        context.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF8A6B44);
    }

    /**
     * Knopf im Holz-Stil.
     *
     * Als eigene Klasse statt als Uebermalung: Frueher zeichnete erst der Vanilla-Knopf
     * seine Textur samt Beschriftung, dann malte der Screen seine eigene darueber. Beides
     * lag deckungsgleich uebereinander — doppelter Text und doppelte Zeichenarbeit.
     */
    private class RusticButton extends ButtonWidget {

        private boolean selected;

        RusticButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            int left = getX();
            int top = getY();
            int right = left + getWidth();
            int bottom = top + getHeight();
            boolean hovered = isHovered() && active;
            // Flach und dunkel, nur eine Kantenlinie. Der ausgewaehlte Zustand wird durch
            // eine warme Fuellung getragen, nicht durch eine zweite Rahmenstufe.
            int face = selected ? 0xFF6B4A22 : active ? (hovered ? 0xFF3A2A1C : 0xFF251B12) : 0xFF1C150F;
            int edge = selected ? COLOR_ACCENT : hovered ? 0xFF8A6B44 : 0xFF3A2A1C;
            context.fill(left, top, right, bottom, edge);
            context.fill(left + 1, top + 1, right - 1, bottom - 1, face);
            Text label = trim(getMessage(), getWidth() - 8);
            int textY = top + (getHeight() - 8) / 2;
            int centerX = left + getWidth() / 2;
            context.drawCenteredTextWithShadow(UnlockScreen.this.textRenderer, label, centerX, textY,
                    selected ? COLOR_HEADING : active ? COLOR_LIGHT : COLOR_DISABLED);
        }
    }

    private record EditorMenuEntry(Text label, Runnable action, boolean danger) {
    }
}
