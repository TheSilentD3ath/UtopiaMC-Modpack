package dev.utopia.core.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Metadaten-Formular; die Baum-ID bleibt als stabiler Fortschrittsschluessel unveraendert. */
final class UnlockTreeSettingsScreen extends Screen {

    interface SaveHandler {
        void save(String name, String description, Identifier icon, int order);
    }

    private final Screen parent;
    private final String treeId;
    private final String initialName;
    private final String initialDescription;
    private final String initialIcon;
    private final int initialOrder;
    private final SaveHandler handler;
    private TextFieldWidget name;
    private TextFieldWidget description;
    private TextFieldWidget icon;
    private TextFieldWidget order;
    private String error;

    UnlockTreeSettingsScreen(Screen parent, String treeId, String name, String description, String icon,
            int order, SaveHandler handler) {
        super(Text.translatable("screen.utopia.unlocks.editor.tree"));
        this.parent = parent;
        this.treeId = treeId;
        this.initialName = name;
        this.initialDescription = description;
        this.initialIcon = icon;
        this.initialOrder = order;
        this.handler = handler;
    }

    @Override
    protected void init() {
        int width = Math.min(420, this.width - 50);
        int x = (this.width - width) / 2;
        name = field(x, 88, width, initialName, 256);
        description = field(x, 124, width, initialDescription, 1024);
        icon = field(x, 160, width, initialIcon, 128);
        order = field(x, 196, width, Integer.toString(initialOrder), 8);
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.utopia.unlocks.editor.apply"), button -> save())
                .dimensions(this.width / 2 - 104, this.height - 44, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(this.width / 2 + 4, this.height - 44, 100, 20).build());
        setInitialFocus(name);
    }

    private TextFieldWidget field(int x, int y, int width, String value, int maxLength) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, width, 20, Text.empty());
        field.setText(value);
        field.setMaxLength(maxLength);
        return addDrawableChild(field);
    }

    private void save() {
        Identifier iconId = icon.getText().isBlank() ? null : Identifier.tryParse(icon.getText().trim());
        if (!icon.getText().isBlank() && iconId == null) {
            error = "screen.utopia.unlocks.editor.error.icon";
            return;
        }
        try {
            handler.save(name.getText(), description.getText(), iconId, Integer.parseInt(order.getText().trim()));
            close();
        } catch (NumberFormatException ignored) {
            error = "screen.utopia.unlocks.editor.error.order";
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        UnlockScreen.drawWoodPanel(context, 18, 18, this.width - 18, this.height - 16);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 32, 0xFFFFE4A8);
        int width = Math.min(420, this.width - 50);
        int x = (this.width - width) / 2;
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("screen.utopia.unlocks.editor.stable_id", treeId), x, 60, 0xFFBFA77D);
        label(context, "name", x, 78);
        label(context, "description", x, 114);
        label(context, "icon", x, 150);
        label(context, "order", x, 186);
        if (error != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable(error),
                    this.width / 2, this.height - 58, 0xFFFF7777);
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
