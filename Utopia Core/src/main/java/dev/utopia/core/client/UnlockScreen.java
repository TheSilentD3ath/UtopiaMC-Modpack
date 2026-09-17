package dev.utopia.core.client;

import dev.utopia.core.UtopiaCore;
import dev.utopia.core.character.CharacterAccess;
import dev.utopia.core.character.CharacterData;
import dev.utopia.core.network.UtopiaNetworking;
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
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Rustikaler, frei zoombarer Freischaltbaum mit server-autoritativem OP-Editor. */
public class UnlockScreen extends Screen {

    private enum EditorTool { SELECT, MOVE }

    private static final Identifier LIGHT_WOOD = UtopiaCore.id("textures/gui/unlock/light_wood.png");
    private static final Identifier DARK_WOOD = UtopiaCore.id("textures/gui/unlock/dark_wood.png");
    private static final int TEXTURE_SIZE = 1254;
    private static final int MAX_PANEL_WIDTH = 760;
    private static final int MAX_PANEL_HEIGHT = 660;
    private static final int OUTER = 8;
    private static final int HEADER_BOTTOM = 62;
    private static final int SIDEBAR_WIDTH = 164;
    private static final int DETAIL_WIDTH = 282;
    private static final int COLOR_TEXT = 0xFF2C1B10;
    private static final int COLOR_MUTED = 0xFF735638;
    private static final int COLOR_LIGHT = 0xFFF5DCA5;

    private Identifier currentTree;
    private UnlockTreeDraft draft;
    private boolean editing;
    private boolean centerAfterInit = true;
    private UnlockTreeCanvas canvas;
    private final List<ButtonWidget> rusticButtons = new ArrayList<>();
    private final List<ButtonWidget> treeTabs = new ArrayList<>();
    private final List<ButtonWidget> editorToolButtons = new ArrayList<>();
    private final List<EditorMenuEntry> contextMenu = new ArrayList<>();
    private ButtonWidget purchaseButton;
    private ButtonWidget editNodeButton;
    private ButtonWidget selectedTreeTab;
    private ButtonWidget selectToolButton;
    private ButtonWidget moveToolButton;
    private EditorTool editorTool = EditorTool.SELECT;
    private int editorToolsTop;
    private int contextMenuX;
    private int contextMenuY;
    private int contextMenuWidth;
    private int detailScroll;
    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private String status;

    public UnlockScreen() {
        super(Text.translatable("screen.utopia.unlocks"));
    }

    private CharacterData data() {
        return ((CharacterAccess) this.client.player).utopia$getCharacter();
    }

    private int overallLevel() {
        return ((PlayerStatsManagerAccess) this.client.player).getPlayerStatsManager().getOverallLevel();
    }

    @Override
    protected void init() {
        rusticButtons.clear();
        treeTabs.clear();
        editorToolButtons.clear();
        contextMenu.clear();
        editNodeButton = null;
        selectedTreeTab = null;
        selectToolButton = null;
        moveToolButton = null;
        if (canvas == null) {
            canvas = new UnlockTreeCanvas(this.textRenderer);
        }
        int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(320, this.width - 8));
        int panelHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(240, this.height - 8));
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        panelRight = panelLeft + panelWidth;
        panelBottom = panelTop + panelHeight;
        int canvasLeft = panelLeft + SIDEBAR_WIDTH + 14;
        canvas.bounds(canvasLeft, panelTop + HEADER_BOTTOM + 8, panelRight - 17, panelBottom - 64);

        List<Identifier> ids = new ArrayList<>(UnlockTrees.ordered());
        if (currentTree != null && !ids.contains(currentTree)) {
            ids.add(currentTree);
        }
        if (currentTree == null && !ids.isEmpty()) {
            currentTree = ids.get(0);
            centerAfterInit = true;
        }
        int tabY = panelTop + HEADER_BOTTOM + 18;
        for (Identifier id : ids) {
            UnlockTree tree = id.equals(currentTree) ? currentTreeData() : UnlockTrees.tree(id);
            Text label = tree == null ? Text.literal(id.getPath()) : displayName(tree.name(), id.getPath());
            ButtonWidget tab = addRustic(ButtonWidget.builder(label, button -> selectTree(id))
                    .dimensions(panelLeft + 18, tabY, SIDEBAR_WIDTH - 27, 24).build());
            treeTabs.add(tab);
            if (id.equals(currentTree)) {
                selectedTreeTab = tab;
            }
            tab.active = !editing && !id.equals(currentTree);
            tabY += 25;
        }

        if (UtopiaCoreClient.canEditTrees) {
            if (!editing) {
                addRustic(ButtonWidget.builder(Text.literal("Editor oeffnen"), button -> enterEditor())
                        .dimensions(panelLeft + 18, panelBottom - 94, SIDEBAR_WIDTH - 27, 24).build());
                addRustic(ButtonWidget.builder(Text.literal("Neuer Baum"), button -> createTree())
                        .dimensions(panelLeft + 18, panelBottom - 65, SIDEBAR_WIDTH - 27, 20).build());
            } else {
                editorToolsTop = Math.max(tabY + 18, panelBottom - 205);
                selectToolButton = addEditorTool(ButtonWidget.builder(Text.literal("Auswahl"),
                                button -> setEditorTool(EditorTool.SELECT))
                        .dimensions(panelLeft + 18, editorToolsTop, 65, 22).build());
                moveToolButton = addEditorTool(ButtonWidget.builder(Text.literal("Bewegen"),
                                button -> setEditorTool(EditorTool.MOVE))
                        .dimensions(panelLeft + 87, editorToolsTop, SIDEBAR_WIDTH - 96, 22).build());
                addRustic(ButtonWidget.builder(Text.literal("+ Knoten"), button -> addNode())
                        .dimensions(panelLeft + 18, editorToolsTop + 27, SIDEBAR_WIDTH - 27, 22).build());
                editNodeButton = addRustic(ButtonWidget.builder(Text.literal("Knoten"), button -> editSelectedNode())
                        .dimensions(panelLeft + 18, editorToolsTop + 54, SIDEBAR_WIDTH - 27, 22).build());
                addRustic(ButtonWidget.builder(Text.literal("Baum"), button -> editTreeSettings())
                        .dimensions(panelLeft + 18, editorToolsTop + 81, SIDEBAR_WIDTH - 27, 22).build());
                addRustic(ButtonWidget.builder(Text.literal("Speichern"), button -> saveTree())
                        .dimensions(panelRight - 196, panelBottom - 50, 84, 24).build());
                addRustic(ButtonWidget.builder(Text.literal("Verwerfen"), button -> discardEditor())
                        .dimensions(panelRight - 107, panelBottom - 50, 84, 24).build());
            }
        }

        addRustic(ButtonWidget.builder(Text.literal("−"), button -> canvas.zoomBy(-1.0))
                .dimensions(canvasLeft + 5, panelBottom - 48, 28, 24).build());
        addRustic(ButtonWidget.builder(Text.literal("+"), button -> canvas.zoomBy(1.0))
                .dimensions(canvasLeft + 67, panelBottom - 48, 28, 24).build());
        addRustic(ButtonWidget.builder(Text.literal("Zentrieren"), button -> centerTree())
                .dimensions(canvasLeft + 101, panelBottom - 48, 100, 24).build());

        purchaseButton = addRustic(ButtonWidget.builder(Text.literal("Freischalten"), button -> purchaseSelected())
                .dimensions(detailLeft() + 12, detailBottom() - 31, DETAIL_WIDTH - 24, 21).build());
        addRustic(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(panelLeft + 18, panelBottom - 36, SIDEBAR_WIDTH - 27, 20).build());

        if (centerAfterInit) {
            centerTree();
            centerAfterInit = false;
        }
    }

    private int detailLeft() {
        return Math.max(panelLeft + SIDEBAR_WIDTH + 230, panelRight - DETAIL_WIDTH - 30);
    }

    private int detailTop() {
        return Math.max(panelTop + HEADER_BOTTOM + 30, panelBottom - 280);
    }

    private int detailBottom() {
        return panelBottom - 66;
    }

    private ButtonWidget addRustic(ButtonWidget button) {
        rusticButtons.add(button);
        return addDrawableChild(button);
    }

    private ButtonWidget addEditorTool(ButtonWidget button) {
        editorToolButtons.add(button);
        return addRustic(button);
    }

    private void selectTree(Identifier id) {
        currentTree = id;
        canvas.select(null);
        detailScroll = 0;
        centerAfterInit = true;
        clearAndInit();
    }

    private UnlockTree currentTreeData() {
        if (editing && draft != null) {
            return draft.build();
        }
        return currentTree == null ? null : UnlockTrees.tree(currentTree);
    }

    private UnlockTreeCanvas.State state(String key, UnlockTree.Node node) {
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        drawWoodPanel(context, panelLeft + OUTER, panelTop + OUTER,
                panelRight - OUTER, panelBottom - OUTER);
        int headerLeft = panelLeft + 13;
        int headerRight = panelRight - 13;
        int headerCenterY = panelTop + (14 + HEADER_BOTTOM) / 2;
        drawLightPanel(context, headerLeft, panelTop + 14, headerRight, panelTop + HEADER_BOTTOM);
        context.fill(headerLeft, panelTop + HEADER_BOTTOM - 4,
                headerRight, panelTop + HEADER_BOTTOM, 0xFF59351E);
        drawDarkPanel(context, panelLeft + 13, panelTop + HEADER_BOTTOM + 5,
                panelLeft + SIDEBAR_WIDTH + 4, panelBottom - 13);
        int canvasLeft = panelLeft + SIDEBAR_WIDTH + 14;
        context.fill(panelLeft + SIDEBAR_WIDTH + 3, panelTop + HEADER_BOTTOM + 5,
                panelLeft + SIDEBAR_WIDTH + 7, panelBottom - 13, 0xFF76502E);
        context.fill(panelLeft + SIDEBAR_WIDTH + 7, panelTop + HEADER_BOTTOM + 5,
                panelLeft + SIDEBAR_WIDTH + 9, panelBottom - 13, 0xFF1C0E09);
        drawCanvasFrame(context, canvas.left(), canvas.top(), canvas.right(), canvas.bottom());
        drawToolbarPanel(context, canvas.left() - 3, panelBottom - 54,
                canvas.right() + 3, panelBottom - 15);

        UnlockTree tree = currentTreeData();
        boolean detailsVisible = tree != null && canvas.selected() != null;
        Text heading = tree == null ? Text.translatable("screen.utopia.unlocks")
                : displayName(tree.name(), currentTree == null ? "Technologie" : currentTree.getPath())
                        .copy().append("-Technologie");
        if (tree != null) {
            Item treeIcon = tree.icon().map(Registries.ITEM::get).orElse(null);
            if (treeIcon != null && treeIcon != net.minecraft.item.Items.AIR) {
                context.drawItem(new ItemStack(treeIcon), headerLeft + 9, headerCenterY - 8);
            }
        }
        int headerTextY = headerCenterY - this.textRenderer.fontHeight / 2;
        context.drawText(this.textRenderer, heading, headerLeft + 32, headerTextY, COLOR_TEXT, false);
        Text points = Text.translatable("screen.utopia.unlocks.points", data().unlockPoints());
        int pointsRight = headerRight - 7;
        int pointsLeft = pointsRight - 16 - this.textRenderer.getWidth(points);
        drawDarkPanel(context, pointsLeft, headerCenterY - 13, pointsRight, headerCenterY + 13);
        context.drawText(this.textRenderer, points, pointsLeft + 8, headerTextY, COLOR_LIGHT, false);

        if (tree == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("screen.utopia.unlocks.empty"),
                    (panelLeft + panelRight) / 2, (panelTop + panelBottom) / 2, COLOR_MUTED);
        } else {
            canvas.render(context, tree, this::state, editing, mouseX, mouseY);
        }

        if (editing) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("Bearbeitung"), panelLeft + 20,
                    editorToolsTop - 14, 0xFFD8B97B);
        } else if (UtopiaCoreClient.canEditTrees) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("Bearbeitung"), panelLeft + 20,
                    panelBottom - 111, 0xFFD8B97B);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(canvas.zoomPercent() + "%"),
                canvasLeft + 50, panelBottom - 40, COLOR_LIGHT);
        String editorHint = editorTool == EditorTool.MOVE
                ? "Verschieben · Knoten ziehen · Shift = frei"
                : "Auswahl · Ziehen = Ansicht · Rechtsklick = Aktionen";
        Text hint = Text.literal(editing ? editorHint : "Spieleransicht · Ziehen zum Verschieben");
        int hintX = canvasLeft + 215;
        context.drawTextWithShadow(this.textRenderer, hint, hintX, panelBottom - 41, COLOR_LIGHT);
        if (status != null) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(status),
                    canvasLeft + 184, panelBottom - 29, 0xFFFFC96A);
        }

        updateButtonState(tree);
        super.render(context, mouseX, mouseY, delta);
        for (ButtonWidget button : rusticButtons) {
            if (!detailsVisible || button != purchaseButton) {
                drawRusticButton(context, button, mouseX, mouseY);
            }
        }
        if (detailsVisible) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0F, 0.0F, 400.0F);
            drawInspectorPanel(context, detailLeft(), detailTop(), panelRight - 29, detailBottom());
            renderDetails(context, tree, detailLeft(), detailTop(), detailBottom());
            drawRusticButton(context, purchaseButton, mouseX, mouseY);
            context.getMatrices().pop();
        }
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 600.0F);
        renderContextMenu(context, mouseX, mouseY);
        context.getMatrices().pop();
    }

    private void renderDetails(DrawContext context, UnlockTree tree, int left, int top, int bottom) {
        String selected = canvas.selected();
        int x = left + 12;
        int width = panelRight - left - 45;
        if (selected == null || !tree.nodes().containsKey(selected)) {
            return;
        }
        UnlockTree.Node node = tree.nodes().get(selected);
        String nodeId = currentTree.getPath() + "/" + selected;
        context.drawText(this.textRenderer, displayName(node.name(), selected), x, top + 12, COLOR_TEXT, false);
        int y = top + 28;
        if (node.description().isPresent()) {
            Text description = displayName(node.description(), node.description().get());
            context.drawTextWrapped(this.textRenderer, description, x, y, width, COLOR_MUTED);
            y += this.textRenderer.wrapLines(description, width).size() * 9 + 5;
        }
        int cost = UnlockService.cost(data(), nodeId, node);
        int level = UnlockService.requiredLevel(data(), nodeId, node);
        context.drawText(this.textRenderer, Text.translatable("screen.utopia.unlocks.cost", cost), x, y,
                COLOR_MUTED, false);
        y += 12;
        if (level > 0) {
            context.drawText(this.textRenderer, Text.translatable("screen.utopia.unlocks.level", level), x, y,
                    overallLevel() >= level ? COLOR_MUTED : 0xFFAA3D2D, false);
            y += 12;
        }
        if (!node.parents().isEmpty()) {
            context.drawText(this.textRenderer, Text.literal("Vorgaenger: " + String.join(", ", node.parents())),
                    x, y, COLOR_MUTED, false);
            y += 15;
        }
        context.fill(x, y, x + width, y + 1, 0x886B4727);
        y += 8;
        context.drawText(this.textRenderer, Text.translatable("screen.utopia.unlocks.contains"), x, y,
                COLOR_TEXT, false);
        y += 13;

        int listBottom = editing ? bottom - 9 : bottom - 37;
        context.enableScissor(left + 5, y - 4, panelRight - 31, listBottom);
        int entryY = y - detailScroll;
        for (String unlock : node.unlocks()) {
            drawUnlockEntry(context, unlock, x, entryY, width);
            entryY += 20;
        }
        if (node.unlocks().isEmpty()) {
            context.drawText(this.textRenderer, Text.literal("Keine direkten Eintraege"), x, entryY,
                    COLOR_MUTED, false);
        }
        context.disableScissor();
    }

    private void drawUnlockEntry(DrawContext context, String entry, int x, int y, int width) {
        Text label;
        Item item = null;
        if (entry.endsWith(":*")) {
            label = Text.translatable("screen.utopia.unlocks.addon_all", entry.substring(0, entry.length() - 2));
        } else if (entry.startsWith("#")) {
            label = Text.literal(entry + " (Tag)");
        } else {
            Identifier id = Identifier.tryParse(entry);
            item = id == null ? null : Registries.ITEM.get(id);
            label = item == null || item == net.minecraft.item.Items.AIR ? Text.literal(entry) : item.getName();
        }
        if (item != null && item != net.minecraft.item.Items.AIR) {
            context.drawItem(new ItemStack(item), x, y - 4);
        }
        context.drawText(this.textRenderer, label, x + 20, y, COLOR_TEXT, false);
        if (this.textRenderer.getWidth(label) > width - 22) {
            context.drawText(this.textRenderer, Text.literal(entry), x + 20, y + 9, COLOR_MUTED, false);
        }
    }

    private void updateButtonState(UnlockTree tree) {
        String selected = canvas.selected();
        boolean hasNode = tree != null && selected != null && tree.nodes().containsKey(selected);
        if (editNodeButton != null) {
            editNodeButton.active = hasNode;
        }
        if (purchaseButton != null) {
            purchaseButton.visible = !editing && hasNode;
            purchaseButton.active = hasNode
                    && state(selected, tree.nodes().get(selected)) == UnlockTreeCanvas.State.BUYABLE;
        }
    }

    private void purchaseSelected() {
        String selected = canvas.selected();
        UnlockTree tree = currentTreeData();
        if (selected == null || tree == null || state(selected, tree.nodes().get(selected)) != UnlockTreeCanvas.State.BUYABLE) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(currentTree.getPath() + "/" + selected);
        ClientPlayNetworking.send(UtopiaNetworking.BUY_NODE, buf);
    }

    private void enterEditor() {
        UnlockTree tree = currentTreeData();
        if (!UtopiaCoreClient.canEditTrees || tree == null) {
            return;
        }
        draft = new UnlockTreeDraft(tree);
        editing = true;
        editorTool = EditorTool.SELECT;
        status = "Entwurf aktiv – Spielerdaten bleiben unveraendert";
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
                0, 0, List.of(), List.of(0.0, 0.0), List.of(), List.of(), List.of(), List.of());
        currentTree = id;
        draft = new UnlockTreeDraft(new UnlockTree(Optional.of("Neuer Technikbaum"), Optional.empty(),
                Optional.empty(), 1000, Map.of("start", root)));
        editing = true;
        editorTool = EditorTool.SELECT;
        canvas.select("start");
        centerAfterInit = true;
        status = "Neuer Entwurf: " + id;
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
                1, 0, parents, List.of(position.x(), position.y()), List.of(), List.of(), List.of(), List.of());
        this.client.setScreen(new UnlockNodeEditorScreen(this, key, node, true, (newKey, edited) -> {
            if (!newKey.equals(key) && draft.nodes().containsKey(newKey)) {
                status = "Knoten-ID existiert bereits: " + newKey;
                return;
            }
            draft.put(newKey, edited);
            canvas.select(newKey);
            status = "Knoten im Entwurf angelegt";
        }));
    }

    private void setEditorTool(EditorTool tool) {
        editorTool = tool;
        contextMenu.clear();
        status = tool == EditorTool.MOVE
                ? "Verschieben aktiv – nur Knoten-Drag aendert Positionen"
                : "Auswahl aktiv – Ziehen verschiebt nur die Ansicht";
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
        status = "Knoten als " + key + " dupliziert";
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
            status = "Wurzelknoten koennen nicht geloescht werden";
            return;
        }
        boolean required = draft.nodes().values().stream().anyMatch(candidate -> candidate.parents().contains(key));
        if (required) {
            status = "Nicht geloescht: andere Knoten benoetigen " + key;
            return;
        }
        draft.remove(key);
        canvas.select(null);
        status = "Knoten " + key + " aus dem Entwurf entfernt";
    }

    private void nudgeSelected(double deltaX, double deltaY) {
        if (draft == null || canvas.selected() == null) {
            return;
        }
        UnlockTree.Node node = draft.nodes().get(canvas.selected());
        if (node != null) {
            draft.move(canvas.selected(), node.x() + deltaX, node.y() + deltaY);
            status = "Knotenposition: " + formatCoordinate(node.x() + deltaX)
                    + ", " + formatCoordinate(node.y() + deltaY);
        }
    }

    private static String formatCoordinate(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
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
            status = "Knoten im Entwurf aktualisiert";
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

    private boolean openContextMenu(UnlockTree tree, double mouseX, double mouseY) {
        if (!editing || tree == null || !canvas.contains(mouseX, mouseY)) {
            return false;
        }
        contextMenu.clear();
        String hit = canvas.nodeAt(tree, mouseX, mouseY);
        if (hit != null) {
            canvas.select(hit);
            contextMenu.add(new EditorMenuEntry(Text.literal("Knoten bearbeiten"), this::editSelectedNode, false));
            contextMenu.add(new EditorMenuEntry(Text.literal("Knoten verschieben"),
                    () -> setEditorTool(EditorTool.MOVE), false));
            contextMenu.add(new EditorMenuEntry(Text.literal("Duplizieren"), this::duplicateSelectedNode, false));
            contextMenu.add(new EditorMenuEntry(Text.literal("Loeschen"), this::deleteSelectedNode, true));
        } else {
            UnlockTreeCanvas.Position position = canvas.modelAt(mouseX, mouseY, !hasShiftDown());
            contextMenu.add(new EditorMenuEntry(Text.literal("Knoten hier anlegen"),
                    () -> addNodeAt(position), false));
            contextMenu.add(new EditorMenuEntry(Text.literal("Baum bearbeiten"), this::editTreeSettings, false));
            contextMenu.add(new EditorMenuEntry(Text.literal("Zentrieren"), this::centerTree, false));
        }
        contextMenuWidth = contextMenu.stream()
                .mapToInt(entry -> this.textRenderer.getWidth(entry.label()) + 24)
                .max().orElse(124);
        contextMenuWidth = Math.max(124, contextMenuWidth);
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
        context.fill(contextMenuX, contextMenuY,
                contextMenuX + contextMenuWidth, bottom, 0xF24A2B19);
        context.fill(contextMenuX + 1, contextMenuY + 1,
                contextMenuX + contextMenuWidth - 1, contextMenuY + 2, 0xFFD7A65B);
        for (int i = 0; i < contextMenu.size(); i++) {
            int top = contextMenuY + 2 + i * 20;
            boolean hovered = mouseX >= contextMenuX && mouseX < contextMenuX + contextMenuWidth
                    && mouseY >= top && mouseY < top + 20;
            if (hovered) {
                context.fill(contextMenuX + 2, top, contextMenuX + contextMenuWidth - 2,
                        top + 20, 0xFF6A4326);
            }
            EditorMenuEntry entry = contextMenu.get(i);
            context.drawTextWithShadow(this.textRenderer, entry.label(), contextMenuX + 10, top + 6,
                    entry.danger() ? 0xFFFF9A78 : COLOR_LIGHT);
        }
    }

    private void saveTree() {
        if (draft == null || currentTree == null) {
            return;
        }
        UnlockTree tree = draft.build();
        String problem = UnlockTreeValidator.validate(currentTree, tree);
        if (problem != null) {
            status = "Nicht gespeichert: " + problem;
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeIdentifier(currentTree);
        buf.encode(net.minecraft.nbt.NbtOps.INSTANCE, UnlockTree.CODEC, tree);
        ClientPlayNetworking.send(UtopiaNetworking.SAVE_TREE, buf);
        editing = false;
        draft = null;
        contextMenu.clear();
        status = "An Server gesendet";
        clearAndInit();
    }

    private void discardEditor() {
        editing = false;
        draft = null;
        contextMenu.clear();
        if (currentTree != null && UnlockTrees.tree(currentTree) == null) {
            currentTree = UnlockTrees.ordered().stream().findFirst().orElse(null);
        }
        status = "Entwurf verworfen";
        centerAfterInit = true;
        clearAndInit();
    }

    private void centerTree() {
        if (canvas != null) {
            canvas.center(currentTreeData());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleContextMenuClick(mouseX, mouseY, button)) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (insideDetails(mouseX, mouseY)) {
            return true;
        }
        UnlockTree tree = currentTreeData();
        if (button == 1 && openContextMenu(tree, mouseX, mouseY)) {
            detailScroll = 0;
            return true;
        }
        if (tree != null && canvas.mouseClicked(tree, mouseX, mouseY, button)) {
            detailScroll = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (canvas.mouseDragged(mouseX, mouseY, deltaX, deltaY,
                editing && editorTool == EditorTool.MOVE, !hasShiftDown(),
                (key, x, y) -> {
                    if (draft != null) {
                        draft.move(key, x, y);
                    }
                })) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (canvas.mouseReleased()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (insideDetails(mouseX, mouseY)) {
            UnlockTree tree = currentTreeData();
            UnlockTree.Node node = tree == null ? null : tree.nodes().get(canvas.selected());
            int available = Math.max(40, detailBottom() - detailTop() - 105);
            int max = node == null ? 0 : Math.max(0, node.unlocks().size() * 20 - available);
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !contextMenu.isEmpty()) {
            contextMenu.clear();
            return true;
        }
        if (editing) {
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

    private boolean insideDetails(double mouseX, double mouseY) {
        return canvas != null && canvas.selected() != null
                && mouseX >= detailLeft() && mouseX < panelRight - 29
                && mouseY >= detailTop() && mouseY < detailBottom();
    }

    static Text displayName(Optional<String> value, String fallback) {
        if (value.isEmpty()) {
            return Text.literal(fallback);
        }
        String name = value.get();
        return I18n.hasTranslation(name) ? Text.translatable(name) : Text.literal(name);
    }

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
        context.fill(left - 5, top - 5, right + 5, bottom + 5, 0xFF17100B);
        context.fill(left - 3, top - 3, right + 3, bottom + 3, 0xFFBB9560);
        context.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF3A1E11);
    }

    private static void drawToolbarPanel(DrawContext context, int left, int top, int right, int bottom) {
        context.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF24120B);
        context.fill(left, top, right, bottom, 0xFF7D502B);
        context.drawTexture(DARK_WOOD, left + 2, top + 2, right - left - 4, bottom - top - 4,
                0.0F, 0.0F, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        context.fill(left + 2, top + 2, right - 2, bottom - 2, 0x302B170E);
    }

    private static void drawInspectorPanel(DrawContext context, int left, int top, int right, int bottom) {
        context.fill(left + 3, top + 4, right + 5, bottom + 6, 0x78000000);
        context.fill(left - 4, top - 4, right + 4, bottom + 4, 0xFF4C2A18);
        drawLightPanel(context, left, top, right, bottom);
        context.fill(left - 2, top - 2, right + 2, top, 0xFFF2DDA8);
        context.fill(left - 2, bottom, right + 2, bottom + 2, 0xFFF2DDA8);
        context.fill(left - 2, top, left, bottom, 0xFFF2DDA8);
        context.fill(right, top, right + 2, bottom, 0xFFF2DDA8);
    }

    private void drawRusticButton(DrawContext context, ButtonWidget button, int mouseX, int mouseY) {
        if (!button.visible) {
            return;
        }
        int left = button.getX();
        int top = button.getY();
        int right = left + button.getWidth();
        int bottom = top + button.getHeight();
        boolean hovered = mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
        boolean selectedTab = button == selectedTreeTab;
        boolean selectedTool = editorToolButtons.contains(button)
                && ((button == selectToolButton && editorTool == EditorTool.SELECT)
                        || (button == moveToolButton && editorTool == EditorTool.MOVE));
        boolean selectedControl = selectedTab || selectedTool;
        int face = selectedControl ? 0xFFD4AE69
                : button.active ? (hovered ? 0xFF694224 : 0xFF4A2B19) : 0xFF2B1B13;
        int edge = selectedControl ? 0xFFE6CA8F
                : hovered && button.active ? 0xFFD7A65B : 0xFF24120B;
        context.fill(left, top, right, bottom, edge);
        context.fill(left + 2, top + 2, right - 2, bottom - 2, face);
        context.fill(left + 3, top + 3, right - 3, top + 4,
                selectedControl ? 0xFFF1DDA7 : 0xFF7D502B);
        context.fill(left + 3, bottom - 4, right - 3, bottom - 3, 0xFF28140C);
        if (button.active && hovered) {
            context.fill(left + 2, top + 2, right - 2, bottom - 2, 0x1830FF8A);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, button.getMessage(),
                left + button.getWidth() / 2, top + (button.getHeight() - 8) / 2,
                selectedControl ? COLOR_TEXT : button.active ? COLOR_LIGHT : 0xFF8C755A);
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

    private record EditorMenuEntry(Text label, Runnable action, boolean danger) {
    }
}
