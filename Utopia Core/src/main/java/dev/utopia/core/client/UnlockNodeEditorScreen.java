package dev.utopia.core.client;

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
    private String error;

    UnlockNodeEditorScreen(Screen parent, String key, UnlockTree.Node node, boolean newNode,
            BiConsumer<String, UnlockTree.Node> onSave) {
        super(Text.literal(newNode ? "Knoten anlegen" : "Knoten bearbeiten"));
        this.parent = parent;
        this.originalKey = key;
        this.original = node;
        this.newNode = newNode;
        this.onSave = onSave;
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

        addDrawableChild(ButtonWidget.builder(Text.literal("Uebernehmen"), button -> save())
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

    private void save() {
        String nodeKey = key.getText().trim();
        Identifier iconId = icon.getText().isBlank() ? null : Identifier.tryParse(icon.getText().trim());
        if (nodeKey.isBlank()) {
            error = "Die Knoten-ID darf nicht leer sein";
            return;
        }
        if (!icon.getText().isBlank() && iconId == null) {
            error = "Ungueltige Icon-ID";
            return;
        }
        int parsedCost;
        int parsedLevel;
        try {
            parsedCost = Integer.parseInt(cost.getText().trim());
            parsedLevel = Integer.parseInt(level.getText().trim());
        } catch (NumberFormatException ignored) {
            error = "Kosten und Level muessen ganze Zahlen sein";
            return;
        }
        UnlockTree.Node node = new UnlockTree.Node(optional(name.getText()), optional(description.getText()),
                Optional.ofNullable(iconId), parsedCost, parsedLevel, entries(parents.getText()), original.position(),
                entries(unlocks.getText()), entries(excludes.getText()), entries(legacyOwners.getText()),
                entries(requiredMods.getText()));
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
        label(context, "Knoten-ID", left, 42);
        label(context, "Name / Uebersetzungsschluessel", left, 74);
        label(context, "Beschreibung", left, 106);
        label(context, "Icon (namespace:item)", left, 138);
        label(context, "Kosten", left, 170);
        label(context, "Mindestlevel", left, 202);
        label(context, "Vorgaenger (Komma-Liste)", right, 42);
        label(context, "Freischaltungen (Komma-Liste)", right, 74);
        label(context, "Ausnahmen (Komma-Liste)", right, 106);
        label(context, "Benoetigte Mods (Komma-Liste)", right, 138);
        label(context, "Alte Besitzer-IDs (Komma-Liste)", right, 170);
        if (error != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, error, this.width / 2, this.height - 48, 0xFFFF7777);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void label(DrawContext context, String value, int x, int y) {
        context.drawTextWithShadow(this.textRenderer, Text.literal(value), x, y, 0xFFF4D69B);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
