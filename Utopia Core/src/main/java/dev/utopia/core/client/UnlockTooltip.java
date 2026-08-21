package dev.utopia.core.client;

import dev.utopia.core.unlock.UnlockService;
import dev.utopia.core.unlock.UnlockTree;
import dev.utopia.core.unlock.UnlockTrees;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Zeigt an gesperrten Gegenstaenden, welcher Knoten fehlt.
 *
 * Ohne diese Zeile wirkt ein gesperrter Gegenstand wie ein Fehler - man sieht
 * ihn, kann ihn aber nicht bauen und erfaehrt nicht warum.
 */
public final class UnlockTooltip {

    private UnlockTooltip() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || UnlockTrees.isEmpty()) {
                return;
            }
            Identifier id = Registries.ITEM.getId(stack.getItem());
            String node = UnlockService.missingNode(client.player, id);
            if (node == null) {
                return;
            }
            UnlockTree.Node definition = UnlockTrees.node(node);
            Text name = definition != null && definition.name().isPresent()
                    ? Text.translatable(definition.name().get())
                    : Text.literal(node);
            lines.add(Text.translatable("tooltip.utopia.locked", name).formatted(Formatting.RED));
            if (client.player.isCreative()) {
                lines.add(Text.translatable("tooltip.utopia.locked.creative").formatted(Formatting.DARK_GRAY));
            }
        });
    }
}
