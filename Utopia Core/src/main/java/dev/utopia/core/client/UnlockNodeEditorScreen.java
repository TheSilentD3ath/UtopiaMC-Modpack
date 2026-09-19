package dev.utopia.core.client;

import dev.utopia.core.unlock.NodeShape;
import dev.utopia.core.unlock.UnlockTree;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/** Kompakter Formular-Editor fuer genau einen Knoten. */
final class UnlockNodeEditorScreen extends Screen {

    private final Screen parent;
    private final String originalKey;
    private final UnlockTree.Node original;
    private final boolean newNode;
    private final BiConsumer<String, UnlockTree.Node> onSave;
    private TextFieldWidget key;
    private TextFieldWidget name;
    private TextFieldWidget description;
    private TextFieldWidget icon;
    private TextFieldWidget cost;
    private TextFieldWidget level;
    private TextFieldWidget parents;
    private TextFieldWidget unlocks;
    private TextFieldWidget excludes;
    private TextFieldWidget requiredMods;
    private TextFieldWidget legacyOwners;
    private NodeShape shape;
    private ButtonWidget shapeButton;
    private String error;

    UnlockNodeEditorScreen(Screen parent, String key, UnlockTree.Node node, boolean newNode,
            BiConsumer<String, UnlockTree.Node> onSave) {
        super(Text.translatable(newNode ? "screen.utopia.unlocks.editor.node.new"
                : "screen.utopia.unlocks.editor.node.edit"));
        this.parent = parent;
        this.originalKey = key;
        this.original = node;
        this.newNode = newNode;
        this.onSave = onSave;
        this.shape = node.shape();
    }

    @Override
    protected void init() {
        int columnWidth = Math.min(310, (this.width - 54) / 2);
        int left = this.width / 2 - columnWidth - 9;
        int right = this.width / 2 + 9;
        key = field(left, 52, columnWidth, originalKey, 80);
        key.setEditable(newNode);
        name = field(left, 84, columnWidth, original.name().orElse(""), 256);
        description = field(left, 116, columnWidth, original.description().orElse(""), 1024);
        icon = field(left, 148, columnWidth, original.icon().map(Identifier::toString).orElse(""), 128);
        cost = field(left, 180, columnWidth, Integer.toString(original.cost()), 8);
        level = field(left, 212, columnWidth, Integer.toString(original.level()), 8);

        parents = field(right, 52, columnWidth, String.join(", ", original.parents()), 8192);
        unlocks = field(right, 84, columnWidth, String.join(", ", original.unlocks()), 32767);
        excludes = field(right, 116, columnWidth, String.join(", ", original.excludes()), 32767);
        requiredMods = field(right, 148, columnWidth, String.join(", ", original.requiresMods()), 8192);
        legacyOwners = field(right, 180, columnWidth, String.join(", ", original.legacyOwners()), 8192);
        shapeButton = addDrawableChild(ButtonWidget.builder(shapeLabel(), button -> cycleShape())
                .dimensions(right, 212, columnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.utopia.unlocks.editor.apply"), button -> save())
                .dimensions(this.width / 2 - 104, this.height - 34, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(this.width / 2 + 4, this.height - 34, 100, 20).build());
        setInitialFocus(newNode ? key : name);
    }

    private TextFieldWidget field(int x, int y, int width, String value, int maxLength) {
        TextFieldWidget widget = new TextFieldWidget(this.textRenderer, x, y, width, 20, Text.empty());
        widget.setMaxLength(maxLength);
        widget.setText(value);
        return addDrawableChild(widget);
    }

    private void cycleShape() {
        NodeShape[] values = NodeShape.values();
        shape = values[(shape.ordinal() + 1) % values.length];
        if (shapeButton != null) {
            shapeButton.setMessage(shapeLabel());
        }
    }

    private Text shapeLabel() {
        return Text.translatable("screen.utopia.unlocks.editor.shape",
                Text.translatable("shape.utopia." + shape.id()));
    }

    private void save() {
        String nodeKey = key.getText().trim();
        Identifier iconId = icon.getText().isBlank() ? null : Identifier.tryParse(icon.getText().trim());
        if (nodeKey.isBlank()) {
            error = "screen.utopia.unlocks.editor.error.key";
            return;
        }
        if (!icon.getText().isBlank() && iconId == null) {
            error = "screen.utopia.unlocks.editor.error.icon";
            return;
        }
        int parsedCost;
        int parsedLevel;
        try {
            parsedCost = Integer.parseInt(cost.getText().trim());
            parsedLevel = Integer.parseInt(level.getText().trim());
        } catch (NumberFormatException ignored) {
            error = "screen.utopia.unlocks.editor.error.number";
            return;
        }
        UnlockTree.Node node = new UnlockTree.Node(optional(name.getText()), optional(description.getText()),
                Optional.ofNullable(iconId), shape, parsedCost, parsedLevel, entries(parents.getText()),
                original.position(), entries(unlocks.getText()), entries(excludes.getText()),
                entries(legacyOwners.getText()), entries(requiredMods.getText()));
        onSave.accept(nodeKey, node);
        close();
    }

    private static Optional<String> optional(String value) {
        return value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static List<String> entries(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .distinct()
                .toList();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        UnlockScreen.drawWoodPanel(context, 12, 12, this.width - 12, this.height - 10);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 24, 0xFFFFE4A8);
        int columnWidth = Math.min(310, (this.width - 54) / 2);
        int left = this.width / 2 - columnWidth - 9;
        int right = this.width / 2 + 9;
        label(context, "key", left, 42);
        label(context, "name", left, 74);
        label(context, "description", left, 106);
        label(context, "icon", left, 138);
        label(context, "cost", left, 170);
        label(context, "level", left, 202);
        label(context, "parents", right, 42);
        label(context, "unlocks", right, 74);
        label(context, "excludes", right, 106);
        label(context, "mods", right, 138);
        label(context, "legacy", right, 170);
        label(context, "shape", right, 202);
        if (error != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable(error),
                    this.width / 2, this.height - 48, 0xFFFF7777);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void label(DrawContext context, String key, int x, int y) {
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("screen.utopia.unlocks.editor.field." + key), x, y, 0xFFF4D69B);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
