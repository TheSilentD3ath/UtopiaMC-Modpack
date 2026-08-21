package dev.utopia.core.client;

import dev.utopia.core.character.CharacterTrait;
import dev.utopia.core.character.CharacterTraits;
import dev.utopia.core.character.TraitType;
import dev.utopia.core.network.UtopiaNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Charaktererstellung: Origin -> Gender -> Klasse -> Bestaetigen.
 *
 * Aufbau angelehnt an den Origins-Auswahlbildschirm: undurchsichtiger
 * Hintergrund, ein zentrales Panel mit Titelleiste und Item-Icon, darunter
 * Beschreibung und Wirkungen, geblaettert wird mit Pfeilen.
 *
 * Der Screen deckt das Spiel vollstaendig ab und laesst sich nicht schliessen,
 * bis die Auswahl steht.
 */
public class CharacterCreationScreen extends Screen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 182;
    private static final int TITLE_BAR_HEIGHT = 22;

    private static final int COLOR_PANEL = 0xF0100C14;
    private static final int COLOR_PANEL_EDGE = 0xFF4B3B58;
    private static final int COLOR_TITLE_BAR = 0xFF2A2135;
    private static final int COLOR_ACCENT = 0xFFE0A94B;
    private static final int COLOR_TEXT = 0xFFE8E4EF;
    private static final int COLOR_MUTED = 0xFF9B93A8;
    private static final int COLOR_GOOD = 0xFF7BD86B;
    private static final int COLOR_BAD = 0xFFE0705F;

    private static final int STEP_COUNT = 3;

    private final Identifier[] chosen = new Identifier[STEP_COUNT];
    private int step;
    private int index;

    private List<Identifier> options = List.of();
    private List<OrderedText> description = List.of();
    private final List<Text> effectLines = new ArrayList<>();

    private int panelX;
    private int panelY;
    private ButtonWidget confirmButton;

    public CharacterCreationScreen() {
        super(Text.translatable("screen.utopia.character_creation"));
    }

    // --- Aufbau ----------------------------------------------------------

    @Override
    protected void init() {
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = Math.max(40, (this.height - PANEL_HEIGHT) / 2 - 10);

        this.options = selectable(currentType());
        this.index = Math.min(this.index, Math.max(0, this.options.size() - 1));

        int buttonY = panelY + PANEL_HEIGHT + 8;

        addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> cycle(-1))
                .dimensions(panelX, buttonY, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> cycle(1))
                .dimensions(panelX + PANEL_WIDTH - 20, buttonY, 20, 20).build());

        this.confirmButton = addDrawableChild(ButtonWidget.builder(confirmLabel(), button -> advance())
                .dimensions(panelX + 46, buttonY, PANEL_WIDTH - 92, 20).build());

        if (step > 0) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.utopia.back"), button -> {
                step--;
                index = Math.max(0, indexOf(selectable(currentType()), chosen[step]));
                clearAndInit();
            }).dimensions(panelX + PANEL_WIDTH / 2 - 40, buttonY + 24, 80, 20).build());
        }

        refresh();
    }

    private void refresh() {
        this.effectLines.clear();
        CharacterTrait trait = current();
        if (trait == null) {
            this.description = List.of();
            if (this.confirmButton != null) {
                this.confirmButton.active = false;
            }
            return;
        }
        if (this.confirmButton != null) {
            this.confirmButton.active = true;
            this.confirmButton.setMessage(confirmLabel());
        }

        Text descText = trait.description().map(Text::translatable).orElse(Text.empty());
        this.description = this.textRenderer.wrapLines(descText, PANEL_WIDTH - 24);
        buildEffectLines(trait);
    }

    /** Uebersetzt die Trait-Daten in lesbare Zeilen - das ersetzt Origins' "Impact"-Anzeige. */
    private void buildEffectLines(CharacterTrait trait) {
        for (CharacterTrait.AttributeMod mod : trait.attributes()) {
            String key = "attribute.name." + mod.attribute().getPath();
            Text name = Text.translatable(key);
            String value;
            if (mod.operation() == EntityAttributeModifier.Operation.ADDITION) {
                value = format(mod.value(), false);
            } else {
                value = format(mod.value() * 100.0D, true);
            }
            effectLines.add(Text.literal(value + " ").append(name)
                    .formatted(mod.value() < 0 ? Formatting.RED : Formatting.GREEN));
        }

        trait.skills().startLevels().forEach((skill, level) -> effectLines.add(
                Text.translatable("screen.utopia.effect.start_level", capitalize(skill), level)
                        .formatted(Formatting.AQUA)));

        if (trait.skills().bonusPoints() > 0) {
            effectLines.add(Text.translatable("screen.utopia.effect.bonus_points", trait.skills().bonusPoints())
                    .formatted(Formatting.AQUA));
        }

        trait.skills().xpMultiplier().forEach((skill, value) -> effectLines.add(
                Text.translatable("screen.utopia.effect.xp", capitalize(skill), format((value - 1.0D) * 100.0D, true))
                        .formatted(Formatting.YELLOW)));

        // effect_multiplier steht in den Daten, wirkt aber noch nicht - solange
        // wird er auch nicht angezeigt. Ein Versprechen, das das Spiel nicht
        // einloest, ist schlimmer als eine fehlende Zeile.

        for (CharacterTrait.EffectSpec spec : trait.effects()) {
            StatusEffect effect = Registries.STATUS_EFFECT.get(spec.effect());
            if (effect != null) {
                effectLines.add(Text.literal("+ ").append(effect.getName()).formatted(Formatting.LIGHT_PURPLE));
            }
        }

        int items = trait.loadout().items().size();
        if (items > 0) {
            effectLines.add(Text.translatable("screen.utopia.effect.loadout", items).formatted(Formatting.GOLD));
        }
    }

    private static String format(double value, boolean percent) {
        String number = value == Math.floor(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.1f", value);
        return (value >= 0 ? "+" : "") + number + (percent ? "%" : "");
    }

    private static String capitalize(String text) {
        int colon = text.indexOf(':');
        String name = colon >= 0 ? text.substring(colon + 1) : text;
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // --- Zustand ---------------------------------------------------------

    private TraitType currentType() {
        return switch (step) {
            case 1 -> TraitType.GENDER;
            case 2 -> TraitType.CLASS;
            default -> TraitType.ORIGIN;
        };
    }

    private CharacterTrait current() {
        if (options.isEmpty() || index < 0 || index >= options.size()) {
            return null;
        }
        return CharacterTraits.get(currentType(), options.get(index));
    }

    private List<Identifier> selectable(TraitType type) {
        List<Identifier> result = new ArrayList<>();
        for (Identifier id : CharacterTraits.ordered(type)) {
            CharacterTrait trait = CharacterTraits.get(type, id);
            if (trait != null && trait.selectable() && trait.requires().allows(chosen[0], chosen[1], chosen[2])) {
                result.add(id);
            }
        }
        return result;
    }

    private static int indexOf(List<Identifier> list, Identifier id) {
        return id == null ? 0 : Math.max(0, list.indexOf(id));
    }

    private void cycle(int direction) {
        if (options.isEmpty()) {
            return;
        }
        index = Math.floorMod(index + direction, options.size());
        refresh();
    }

    private void advance() {
        if (options.isEmpty()) {
            return;
        }
        chosen[step] = options.get(index);
        if (step < STEP_COUNT - 1) {
            step++;
            index = 0;
            clearAndInit();
        } else {
            send();
        }
    }

    private void send() {
        if (chosen[0] == null || chosen[1] == null || chosen[2] == null) {
            step = 0;
            index = 0;
            clearAndInit();
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeIdentifier(chosen[0]);
        buf.writeIdentifier(chosen[1]);
        buf.writeIdentifier(chosen[2]);
        ClientPlayNetworking.send(UtopiaNetworking.SELECT, buf);
        if (this.confirmButton != null) {
            this.confirmButton.active = false;
        }
    }

    private Text confirmLabel() {
        return step < STEP_COUNT - 1
                ? Text.translatable("screen.utopia.continue")
                : Text.translatable("screen.utopia.begin");
    }

    // --- Rendering -------------------------------------------------------

    @Override
    public void renderBackground(DrawContext context) {
        // Immer undurchsichtig: die Welt darf waehrend der Auswahl nicht sichtbar sein.
        context.setShaderColor(0.22F, 0.20F, 0.26F, 1.0F);
        context.drawTexture(Screen.OPTIONS_BACKGROUND_TEXTURE, 0, 0, 0.0F, 0.0F, this.width, this.height, 32, 32);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        context.fillGradient(0, 0, this.width, this.height, 0x80000000, 0xC0000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        TextRenderer font = this.textRenderer;
        CharacterTrait trait = current();

        // Kopfzeile: Schritt-Anzeige
        Text stepText = Text.translatable("screen.utopia.step." + stepKey());
        context.drawCenteredTextWithShadow(font, stepText, this.width / 2, panelY - 26, COLOR_ACCENT);
        context.drawCenteredTextWithShadow(font,
                Text.translatable("screen.utopia.step_counter", step + 1, STEP_COUNT),
                this.width / 2, panelY - 14, COLOR_MUTED);

        // Panel
        context.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, COLOR_PANEL_EDGE);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_PANEL);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + TITLE_BAR_HEIGHT, COLOR_TITLE_BAR);
        context.fill(panelX, panelY + TITLE_BAR_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + TITLE_BAR_HEIGHT, COLOR_ACCENT);

        if (trait == null) {
            context.drawCenteredTextWithShadow(font, Text.translatable("screen.utopia.no_options"),
                    this.width / 2, panelY + PANEL_HEIGHT / 2, COLOR_BAD);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // Titelleiste: Icon + Name
        int textX = panelX + 10;
        if (trait.icon().isPresent()) {
            Item item = Registries.ITEM.get(trait.icon().get());
            if (item != null) {
                context.drawItem(new ItemStack(item), panelX + 5, panelY + 3);
                textX = panelX + 27;
            }
        }
        Text name = trait.name().map(Text::translatable).orElse(Text.literal(options.get(index).getPath()));
        context.drawTextWithShadow(font, name, textX, panelY + 7, COLOR_ACCENT);

        // Seitenanzeige rechts in der Titelleiste
        Text page = Text.literal((index + 1) + "/" + options.size());
        context.drawTextWithShadow(font, page, panelX + PANEL_WIDTH - font.getWidth(page) - 6, panelY + 7, COLOR_MUTED);

        // Beschreibung
        int y = panelY + TITLE_BAR_HEIGHT + 8;
        for (OrderedText line : description) {
            context.drawTextWithShadow(font, line, panelX + 12, y, COLOR_TEXT);
            y += 10;
        }

        // Trennlinie + Wirkungen
        if (!effectLines.isEmpty()) {
            y += 4;
            context.fill(panelX + 12, y, panelX + PANEL_WIDTH - 12, y + 1, 0x40FFFFFF);
            y += 7;
            int limit = panelY + PANEL_HEIGHT - 10;
            for (Text line : effectLines) {
                if (y + 10 > limit) {
                    context.drawTextWithShadow(font, Text.literal("..."), panelX + 12, y, COLOR_MUTED);
                    break;
                }
                context.drawTextWithShadow(font, line, panelX + 12, y, COLOR_TEXT);
                y += 10;
            }
        }

        // Bereits getroffene Auswahl unter den Buttons
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < step; i++) {
            if (chosen[i] != null) {
                summary.append(summary.length() == 0 ? "" : "  ·  ").append(capitalize(chosen[i].getPath()));
            }
        }
        if (summary.length() > 0) {
            context.drawCenteredTextWithShadow(font, Text.literal(summary.toString()),
                    this.width / 2, panelY + PANEL_HEIGHT + 56, COLOR_MUTED);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private String stepKey() {
        return switch (step) {
            case 1 -> "gender";
            case 2 -> "class";
            default -> "origin";
        };
    }

    // --- Eingabe ---------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount != 0) {
            cycle(amount > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case 263 -> { // Pfeil links
                cycle(-1);
                return true;
            }
            case 262 -> { // Pfeil rechts
                cycle(1);
                return true;
            }
            case 257, 335 -> { // Enter
                advance();
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
