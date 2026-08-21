package dev.utopia.core.client;

import dev.utopia.core.character.CharacterAccess;
import dev.utopia.core.character.CharacterData;
import dev.utopia.core.network.UtopiaNetworking;
import dev.utopia.core.unlock.UnlockService;
import dev.utopia.core.unlock.UnlockTree;
import dev.utopia.core.unlock.UnlockTrees;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.levelz.access.PlayerStatsManagerAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Die Freischalt-Bäume.
 *
 * Links die Baum-Reiter, rechts das Knotenraster mit Verbindungslinien. Ein
 * Klick auf einen kaufbaren Knoten schickt den Kauf an den Server — entschieden
 * wird dort, der Screen zeigt nur, was er aus den synchronisierten Daten
 * ableiten kann.
 */
public class UnlockScreen extends Screen {

    private static final int CELL = 44;
    private static final int NODE = 26;
    private static final int GRID_LEFT = 118;
    private static final int GRID_TOP = 54;

    private static final int COLOR_BACK = 0xF0100C14;
    private static final int COLOR_EDGE = 0xFF4B3B58;
    private static final int COLOR_ACCENT = 0xFFE0A94B;
    private static final int COLOR_TEXT = 0xFFE8E4EF;
    private static final int COLOR_MUTED = 0xFF9B93A8;
    private static final int COLOR_OWNED = 0xFF7BD86B;
    private static final int COLOR_LOCKED = 0xFF5A5566;

    private Identifier currentTree;
    private final List<ButtonWidget> tabs = new ArrayList<>();

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
        tabs.clear();
        List<Identifier> ids = UnlockTrees.ordered();
        if (currentTree == null && !ids.isEmpty()) {
            currentTree = ids.get(0);
        }
        int y = GRID_TOP;
        for (Identifier id : ids) {
            UnlockTree tree = UnlockTrees.tree(id);
            Text label = tree.name().map(Text::translatable).orElse(Text.literal(id.getPath()));
            ButtonWidget button = ButtonWidget.builder(label, b -> {
                currentTree = id;
                clearAndInit();
            }).dimensions(14, y, 96, 20).build();
            button.active = !id.equals(currentTree);
            tabs.add(addDrawableChild(button));
            y += 24;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(14, this.height - 32, 96, 20).build());
    }

    // --- Zustand eines Knotens -------------------------------------------

    private enum State {
        OWNED, BUYABLE, TOO_EXPENSIVE, BLOCKED
    }

    private State stateOf(String nodeId, UnlockTree.Node node) {
        CharacterData data = data();
        if (data.unlocks().contains(UnlockService.nodeId(nodeId))) {
            return State.OWNED;
        }
        for (String parent : node.parents()) {
            if (!data.unlocks().contains(UnlockService.nodeId(UnlockTrees.treeOf(nodeId) + "/" + parent))) {
                return State.BLOCKED;
            }
        }
        if (overallLevel() < UnlockService.requiredLevel(data, nodeId, node)) {
            return State.BLOCKED;
        }
        return data.unlockPoints() >= UnlockService.cost(data, nodeId, node) ? State.BUYABLE : State.TOO_EXPENSIVE;
    }

    private int nodeX(UnlockTree.Node node) {
        return GRID_LEFT + node.column() * CELL;
    }

    private int nodeY(UnlockTree.Node node) {
        return GRID_TOP + node.row() * CELL;
    }

    // --- Zeichnen ---------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.fill(8, 8, this.width - 8, this.height - 8, COLOR_BACK);
        context.fill(8, 8, this.width - 8, 30, 0xFF2A2135);
        context.fill(8, 29, this.width - 8, 30, COLOR_ACCENT);

        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.utopia.unlocks"), 16, 15, COLOR_ACCENT);
        Text points = Text.translatable("screen.utopia.unlocks.points", data().unlockPoints());
        context.drawTextWithShadow(this.textRenderer, points,
                this.width - 16 - this.textRenderer.getWidth(points), 15, COLOR_TEXT);

        UnlockTree tree = currentTree == null ? null : UnlockTrees.tree(currentTree);
        if (tree == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("screen.utopia.unlocks.empty"),
                    this.width / 2, this.height / 2, COLOR_MUTED);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // Verbindungslinien zuerst, damit die Knoten darauf liegen
        for (Map.Entry<String, UnlockTree.Node> entry : tree.nodes().entrySet()) {
            UnlockTree.Node node = entry.getValue();
            for (String parentKey : node.parents()) {
                UnlockTree.Node parent = tree.nodes().get(parentKey);
                if (parent == null) {
                    continue;
                }
                int x1 = nodeX(parent) + NODE / 2;
                int y1 = nodeY(parent) + NODE / 2;
                int x2 = nodeX(node) + NODE / 2;
                int y2 = nodeY(node) + NODE / 2;
                boolean done = data().unlocks().contains(
                        UnlockService.nodeId(currentTree.getPath() + "/" + parentKey));
                int color = done ? COLOR_OWNED : COLOR_LOCKED;
                // rechtwinklig: erst waagerecht, dann senkrecht
                context.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + 1, color);
                context.fill(x2, Math.min(y1, y2), x2 + 1, Math.max(y1, y2) + 1, color);
            }
        }

        List<Text> tooltip = null;
        for (Map.Entry<String, UnlockTree.Node> entry : tree.nodes().entrySet()) {
            String nodeId = currentTree.getPath() + "/" + entry.getKey();
            UnlockTree.Node node = entry.getValue();
            State state = stateOf(nodeId, node);
            int x = nodeX(node);
            int y = nodeY(node);

            int border = switch (state) {
                case OWNED -> COLOR_OWNED;
                case BUYABLE -> COLOR_ACCENT;
                case TOO_EXPENSIVE -> COLOR_MUTED;
                default -> COLOR_LOCKED;
            };
            context.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, border);
            context.fill(x, y, x + NODE, y + NODE, state == State.OWNED ? 0xFF1B2A1B : 0xFF1B1720);

            Item item = node.icon().map(Registries.ITEM::get).orElse(null);
            if (item != null) {
                context.drawItem(new ItemStack(item), x + 5, y + 5);
            }
            if (state != State.OWNED) {
                Text cost = Text.literal(String.valueOf(UnlockService.cost(data(), nodeId, node)));
                context.drawTextWithShadow(this.textRenderer, cost, x + NODE - 5, y + NODE - 8,
                        state == State.BUYABLE ? COLOR_ACCENT : COLOR_MUTED);
            }

            if (mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE) {
                tooltip = tooltipFor(nodeId, node, state);
            }
        }

        super.render(context, mouseX, mouseY, delta);
        if (tooltip != null) {
            context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
        }
    }

    private List<Text> tooltipFor(String nodeId, UnlockTree.Node node, State state) {
        List<Text> lines = new ArrayList<>();
        lines.add(node.name().map(Text::translatable).orElse(Text.literal(nodeId)).copy().formatted(Formatting.WHITE));

        int cost = UnlockService.cost(data(), nodeId, node);
        int level = UnlockService.requiredLevel(data(), nodeId, node);
        lines.add(Text.translatable("screen.utopia.unlocks.cost", cost).formatted(Formatting.GRAY));
        if (level > 0) {
            lines.add(Text.translatable("screen.utopia.unlocks.level", level)
                    .formatted(overallLevel() >= level ? Formatting.GRAY : Formatting.RED));
        }

        if (!node.unlocks().isEmpty()) {
            lines.add(Text.empty());
            lines.add(Text.translatable("screen.utopia.unlocks.contains").formatted(Formatting.GRAY));
            int shown = 0;
            for (String entry : node.unlocks()) {
                if (shown >= 6) {
                    lines.add(Text.literal("  ...").formatted(Formatting.DARK_GRAY));
                    break;
                }
                Text name;
                if (entry.startsWith("#")) {
                    name = Text.literal(entry);
                } else {
                    Identifier id = Identifier.tryParse(entry);
                    Item item = id == null ? null : Registries.ITEM.get(id);
                    name = item == null ? Text.literal(entry) : item.getName();
                }
                lines.add(Text.literal("  ").append(name).formatted(Formatting.DARK_GRAY));
                shown++;
            }
        }

        lines.add(Text.empty());
        lines.add(switch (state) {
            case OWNED -> Text.translatable("screen.utopia.unlocks.owned").formatted(Formatting.GREEN);
            case BUYABLE -> Text.translatable("screen.utopia.unlocks.click").formatted(Formatting.GOLD);
            case TOO_EXPENSIVE -> Text.translatable("message.utopia.unlock.points").formatted(Formatting.RED);
            default -> Text.translatable("screen.utopia.unlocks.blocked").formatted(Formatting.RED);
        });
        return lines;
    }

    // --- Eingabe ---------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        UnlockTree tree = currentTree == null ? null : UnlockTrees.tree(currentTree);
        if (tree != null && button == 0) {
            for (Map.Entry<String, UnlockTree.Node> entry : tree.nodes().entrySet()) {
                UnlockTree.Node node = entry.getValue();
                int x = nodeX(node);
                int y = nodeY(node);
                if (mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE) {
                    String nodeId = currentTree.getPath() + "/" + entry.getKey();
                    if (stateOf(nodeId, node) == State.BUYABLE) {
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeString(nodeId);
                        ClientPlayNetworking.send(UtopiaNetworking.BUY_NODE, buf);
                        this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance
                                .master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
