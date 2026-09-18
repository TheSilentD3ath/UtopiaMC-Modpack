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

    private static final Identifier LIGHT_WOOD = UtopiaCore.id("textures/gui/unlock/light_wood.png");
    private static final Identifier DARK_WOOD = UtopiaCore.id("textures/gui/unlock/dark_wood.png");
    private static final int TEXTURE_SIZE = 512;

    private static final int MAX_PANEL_WIDTH = 960;
    private static final int MAX_PANEL_HEIGHT = 700;
    private static final int OUTER = 8;
    private static final int GAP = 8;
    private static final int HEADER_HEIGHT = 44;
    private static final int TOOLBAR_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 30;
    private static final int SIDEBAR_WIDTH = 150;
    private static final int INSPECTOR_WIDTH = 214;
    private static final int MIN_CANVAS_WIDTH = 150;
    private static final int ENTRY_HEIGHT = 20;

    // Kontrastgeprueft gegen die gemessene Helligkeit der Holztexturen.
    private static final int COLOR_TEXT = 0xFF2C1B10;
    private static final int COLOR_MUTED = 0xFF4A3524;
    private static final int COLOR_LIGHT = 0xFFF5DCA5;
    private static final int COLOR_WARN = 0xFF861C14;
    private static final int COLOR_ACCENT = 0xFFD8B97B;

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
    private int toolbarTop;
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
        int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(260, this.width - 12));
        int panelHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(200, this.height - 12));
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        panelRight = panelLeft + panelWidth;
        panelBottom = panelTop + panelHeight;

        innerLeft = panelLeft + OUTER + 5;
        innerRight = panelRight - OUTER - 5;
        headerTop = panelTop + OUTER + 5;
        headerBottom = headerTop + HEADER_HEIGHT;
        footerTop = panelBottom - OUTER - 5 - FOOTER_HEIGHT;
        bodyTop = headerBottom + GAP;
        bodyBottom = footerTop - GAP;

        int treeCount = UnlockTrees.ordered().size();
        showSidebar = treeCount > 1 || editing;
        showInspector = true;

        int available = innerRight - innerLeft;
        // Der Inspektor waechst mit, bleibt aber ein Beiwerk: bei fester Breite nahm er auf
        // kleineren Oberflaechenskalierungen ueber 40 % des Panels ein, obwohl die Karte der
        // eigentliche Inhalt ist.
        inspectorWidth = MathHelper.clamp(available * 28 / 100, 150, INSPECTOR_WIDTH);
        int needed = MIN_CANVAS_WIDTH + (showSidebar ? SIDEBAR_WIDTH + GAP : 0) + inspectorWidth + GAP;
        if (available < needed) {
            showInspector = false;
            needed = MIN_CANVAS_WIDTH + (showSidebar ? SIDEBAR_WIDTH + GAP : 0);
            if (available < needed && showSidebar) {
                showSidebar = false;
            }
        }

        sidebarLeft = innerLeft;
        sidebarRight = showSidebar ? sidebarLeft + SIDEBAR_WIDTH : sidebarLeft;
        inspectorRight = innerRight;
        inspectorLeft = showInspector ? inspectorRight - inspectorWidth : inspectorRight;
        canvasLeft = sidebarRight + (showSidebar ? GAP : 0);
        canvasRight = inspectorLeft - (showInspector ? GAP : 0);
        toolbarTop = bodyBottom - TOOLBAR_HEIGHT;
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
        canvas.bounds(canvasLeft, bodyTop, canvasRight, toolbarTop - 4);

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
        buildToolbar();
        buildFooter();
        if (showInspector) {
            purchaseButton = addRustic(new RusticButton(inspectorLeft + 8, bodyBottom - 26,
                    inspectorWidth - 16, 22, Text.translatable("screen.utopia.unlocks.purchase"),
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
        search = new TextFieldWidget(this.textRenderer, searchLeft, headerTop + 12, searchWidth, 18,
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

    private void buildToolbar() {
        int y = toolbarTop + 3;
        addRustic(new RusticButton(canvasLeft + 4, y, 22, 22, Text.literal("−"),
                button -> canvas.zoomBy(-1.0)));
        addRustic(new RusticButton(canvasLeft + 70, y, 22, 22, Text.literal("+"),
                button -> canvas.zoomBy(1.0)));
        int overviewWidth = Math.min(96, Math.max(40, canvasRight - canvasLeft - 100));
        addRustic(new RusticButton(canvasLeft + 96, y, overviewWidth, 22,
                Text.translatable("screen.utopia.unlocks.overview"), button -> fitTree()));
    }

    private void buildFooter() {
        int y = footerTop + 4;
        int x = innerRight;
        RusticButton done = new RusticButton(x - 70, y, 70, 22, Text.translatable("gui.done"),
                button -> close());
        addRustic(done);
        x -= 74;
        footerButtonsLeft = x;
        if (!UtopiaCoreClient.canEditTrees) {
            return;
        }
        if (editing) {
            addRustic(new RusticButton(x - 76, y, 76, 22,
                    Text.translatable("screen.utopia.unlocks.editor.discard"), button -> discardEditor()));
            x -= 80;
            addRustic(new RusticButton(x - 76, y, 76, 22,
                    Text.translatable("screen.utopia.unlocks.editor.save"), button -> saveTree()));
            footerButtonsLeft = x - 76;
        } else {
            addRustic(new RusticButton(x - 96, y, 96, 22,
                    Text.translatable("screen.utopia.unlocks.editor.open"), button -> enterEditor()));
            x -= 100;
            addRustic(new RusticButton(x - 86, y, 86, 22,
                    Text.translatable("screen.utopia.unlocks.editor.new_tree"), button -> createTree()));
            footerButtonsLeft = x - 86;
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

        drawWoodPanel(context, panelLeft + OUTER, panelTop + OUTER, panelRight - OUTER, panelBottom - OUTER);
        drawLightPanel(context, innerLeft - 4, headerTop - 4, innerRight + 4, headerBottom);
        if (showSidebar) {
            drawDarkPanel(context, sidebarLeft - 4, bodyTop - 4, sidebarRight + 4, bodyBottom + 4);
        }
        if (showInspector) {
            drawLightPanel(context, inspectorLeft - 4, bodyTop - 4, inspectorRight + 4, bodyBottom + 4);
        }
        drawCanvasFrame(context, canvasLeft, bodyTop, canvasRight, toolbarTop - 4);
        drawDarkPanel(context, canvasLeft, toolbarTop, canvasRight, bodyBottom);

        renderHeader(context, tree);

        if (tree == null) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("screen.utopia.unlocks.empty"),
                    (canvasLeft + canvasRight) / 2, (bodyTop + toolbarTop) / 2, COLOR_LIGHT);
        } else {
            canvas.render(context, tree, this::state, editing, mouseX, mouseY);
        }

        renderToolbar(context);
        renderFooter(context);
        updateButtonState(tree);
        super.render(context, mouseX, mouseY, delta);
        renderSearchPlaceholder(context);

        if (showInspector) {
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
            context.drawText(this.textRenderer, trim(heading, headingLimit), x, textY, COLOR_TEXT, false);
        }

        Text points = pointsText();
        int pointsRight = innerRight;
        int pointsLeft = pointsRight - this.textRenderer.getWidth(points) - 14;
        drawDarkPanel(context, pointsLeft, centerY - 11, pointsRight, centerY + 11);
        context.drawText(this.textRenderer, points, pointsLeft + 7, textY, COLOR_LIGHT, false);
    }

    /**
     * Platzhalter im leeren Suchfeld.
     *
     * Muss nach {@code super.render} laufen: Das Textfeld zeichnet seinen eigenen
     * Hintergrund und wuerde den Platzhalter sonst wieder zudecken.
     */
    private void renderSearchPlaceholder(DrawContext context) {
        if (search != null && search.getText().isEmpty() && !search.isFocused()) {
            context.drawText(this.textRenderer, trim(Text.translatable("screen.utopia.unlocks.search"),
                    search.getWidth() - 8), search.getX() + 4, search.getY() + 5, 0xFF8A7355, false);
        }
    }

    private void renderToolbar(DrawContext context) {
        int textY = toolbarTop + (TOOLBAR_HEIGHT - this.textRenderer.fontHeight) / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(canvas.zoomPercent() + "%"),
                canvasLeft + 48, textY, COLOR_LIGHT);
        int hintLeft = canvasLeft + 196;
        int room = canvasRight - hintLeft - 6;
        if (room < 60) {
            return;
        }
        Text hint;
        if (!editing) {
            hint = Text.translatable("screen.utopia.unlocks.hint.player");
        } else if (editorTool == EditorTool.MOVE) {
            hint = Text.translatable("screen.utopia.unlocks.hint.move");
        } else {
            hint = Text.translatable("screen.utopia.unlocks.hint.select");
        }
        context.drawTextWithShadow(this.textRenderer, trim(hint, room), hintLeft, textY, COLOR_ACCENT);
    }

    /** Legende: Abzeichen plus Wort. Ohne sie raet man, was die Zustaende bedeuten. */
    private void renderFooter(DrawContext context) {
        int y = footerTop + (FOOTER_HEIGHT - 10) / 2;
        int x = innerLeft;
        int limit = footerContentRight();
        for (UnlockTreeCanvas.State state : UnlockTreeCanvas.State.values()) {
            // Eigene Kurzform: die vollen Zustandstexte passen nicht zu viert nebeneinander.
            Text label = Text.translatable("screen.utopia.unlocks.legend." + state.name().toLowerCase(Locale.ROOT));
            int width = 12 + this.textRenderer.getWidth(label) + 10;
            if (x + width > limit) {
                break;
            }
            int color = UnlockTreeCanvas.outlineColor(state);
            float r = (color >> 16 & 255) / 255.0F;
            float g = (color >> 8 & 255) / 255.0F;
            float b = (color & 255) / 255.0F;
            context.setShaderColor(r, g, b, 1.0F);
            context.drawTexture(UnlockTreeCanvas.badgeTexture(state), x, y - 1, 10, 10,
                    0.0F, 0.0F, 32, 32, 32, 32);
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            context.drawText(this.textRenderer, label, x + 13, y, COLOR_LIGHT, false);
            x += width;
        }
        if (status != null && x < limit - 40) {
            context.drawTextWithShadow(this.textRenderer, trim(Text.literal(status), limit - x - 8),
                    x + 8, y, 0xFFFFC96A);
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

    private void renderInspector(DrawContext context, UnlockTree tree) {
        int x = inspectorLeft + 8;
        int width = inspectorWidth - 16;
        String selected = canvas.selected();
        if (tree == null) {
            return;
        }
        if (selected == null || !tree.nodes().containsKey(selected)) {
            context.drawTextWrapped(this.textRenderer, Text.translatable("screen.utopia.unlocks.no_selection"),
                    x, bodyTop + 8, width, COLOR_MUTED);
            if (tree.description().isPresent()) {
                context.drawTextWrapped(this.textRenderer, displayName(tree.description(), ""),
                        x, bodyTop + 40, width, COLOR_MUTED);
            }
            return;
        }

        UnlockTree.Node node = tree.nodes().get(selected);
        String nodeId = currentTree.getPath() + "/" + selected;
        int y = bodyTop + 8;
        context.drawTextWrapped(this.textRenderer, displayName(node.name(), selected), x, y, width, COLOR_TEXT);
        y += this.textRenderer.wrapLines(displayName(node.name(), selected), width).size() * 10 + 3;

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
                x, y, COLOR_TEXT, false);
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

        context.fill(x, y, x + width, y + 1, 0x886B4727);
        y += 6;
        context.drawText(this.textRenderer, Text.translatable("screen.utopia.unlocks.contains"), x, y,
                COLOR_TEXT, false);
        y += 12;

        int listBottom = showInspector && purchaseButton != null ? purchaseButton.getY() - 6 : bodyBottom;
        if (listBottom <= y) {
            return;
        }
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
        context.drawText(this.textRenderer, trim(label, width - 22), x + 20, y, COLOR_TEXT, false);
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
            purchaseButton.visible = !editing && hasNode;
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
        return showInspector && mouseX >= inspectorLeft && mouseX < inspectorRight
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

    static void drawWoodPanel(DrawContext context, int left, int top, int right, int bottom) {
        context.fill(left + 4, top + 5, right + 4, bottom + 5, 0xB0000000);
        context.drawTexture(DARK_WOOD, left, top, right - left, bottom - top,
                0.0F, 0.0F, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        context.fill(left, top, right, top + 3, 0xFF28140C);
        context.fill(left, bottom - 4, right, bottom, 0xFF28140C);
        context.fill(left, top, left + 3, bottom, 0xFF28140C);
        context.fill(right - 3, top, right, bottom, 0xFF28140C);
        context.fill(left + 3, top + 3, right - 3, top + 6, 0xFF8F5B2E);
        context.fill(left + 3, bottom - 7, right - 3, bottom - 4, 0xFF3A1E11);
        context.fill(left + 3, top + 3, left + 6, bottom - 4, 0xFF8F5B2E);
        context.fill(right - 6, top + 3, right - 3, bottom - 4, 0xFF3A1E11);
    }

    private static void drawLightPanel(DrawContext context, int left, int top, int right, int bottom) {
        context.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF59351E);
        context.drawTexture(LIGHT_WOOD, left, top, right - left, bottom - top,
                0.0F, 0.0F, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        context.fill(left, top, right, bottom, 0x0A28140C);
    }

    private static void drawDarkPanel(DrawContext context, int left, int top, int right, int bottom) {
        context.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF28140C);
        context.drawTexture(DARK_WOOD, left, top, right - left, bottom - top,
                0.0F, 0.0F, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        context.fill(left, top, right, bottom, 0x18301A10);
    }

    private static void drawCanvasFrame(DrawContext context, int left, int top, int right, int bottom) {
        context.fill(left - 4, top - 4, right + 4, bottom + 4, 0xFF17100B);
        context.fill(left - 3, top - 3, right + 3, bottom + 3, 0xFFBB9560);
        context.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF3A1E11);
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
            int face = selected ? 0xFFD4AE69 : active ? (hovered ? 0xFF694224 : 0xFF4A2B19) : 0xFF2B1B13;
            int edge = selected ? 0xFFE6CA8F : hovered ? 0xFFD7A65B : 0xFF24120B;
            context.fill(left, top, right, bottom, edge);
            context.fill(left + 2, top + 2, right - 2, bottom - 2, face);
            context.fill(left + 3, top + 3, right - 3, top + 4, selected ? 0xFFF1DDA7 : 0xFF7D502B);
            context.fill(left + 3, bottom - 4, right - 3, bottom - 3, 0xFF28140C);
            if (hovered) {
                context.fill(left + 2, top + 2, right - 2, bottom - 2, 0x1830FF8A);
            }
            Text label = trim(getMessage(), getWidth() - 8);
            int textY = top + (getHeight() - 8) / 2;
            int centerX = left + getWidth() / 2;
            if (selected) {
                // Dunkle Schrift auf heller Flaeche braucht keinen Schatten; mit Schatten
                // sah die Beschriftung ausgewaehlter Knoepfe doppelt gedruckt aus.
                context.drawText(UnlockScreen.this.textRenderer, label,
                        centerX - UnlockScreen.this.textRenderer.getWidth(label) / 2, textY,
                        COLOR_TEXT, false);
            } else {
                context.drawCenteredTextWithShadow(UnlockScreen.this.textRenderer, label, centerX,
                        textY, active ? COLOR_LIGHT : 0xFFAD9678);
            }
        }
    }

    private record EditorMenuEntry(Text label, Runnable action, boolean danger) {
    }
}
