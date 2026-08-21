package dev.utopia.core.unlock;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Durchsetzung der Freischaltungen beim Benutzen.
 *
 * **Warum eigene Ereignisse und nicht die von LevelZ:** LevelZ prüft nur, was in
 * seinen eigenen Listen steht — der Code lautet überall
 * {@code if (customList.contains(id)) ... pruefen}. Ein Create-Zahnrad steht in
 * keiner LevelZ-Liste, also wurde nie geprüft und meine Ergänzung in der
 * Prüfmethode nie erreicht. Deshalb hängen wir uns hier selbst ein, unabhängig
 * von den geerbten Listen.
 *
 * Kreativmodus umgeht die Sperre — genau wie bei LevelZ. Zum Testen also
 * Überlebensmodus.
 */
public final class UnlockEnforcement {

    private UnlockEnforcement() {
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (isBlocked(player, Registries.ITEM.getId(stack.getItem()))) {
                notifyLocked(player, Registries.ITEM.getId(stack.getItem()));
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(ItemStack.EMPTY);
        });

        // Rechtsklick auf einen gesperrten Block (Bedienen, Oeffnen, ...)
        UseBlockCallback.EVENT.register((player, world, hand, result) -> {
            BlockPos pos = ((BlockHitResult) result).getBlockPos();
            var id = Registries.BLOCK.getId(world.getBlockState(pos).getBlock());
            if (isBlocked(player, id)) {
                notifyLocked(player, id);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

    public static boolean isBlocked(PlayerEntity player, net.minecraft.util.Identifier id) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        return UnlockService.isLocked(player, id);
    }

    public static void notifyLocked(PlayerEntity player, net.minecraft.util.Identifier id) {
        String node = UnlockService.missingNode(player, id);
        if (node != null && !player.getWorld().isClient()) {
            player.sendMessage(Text.translatable("message.utopia.unlock.locked", node).formatted(Formatting.RED), true);
        }
    }
}
