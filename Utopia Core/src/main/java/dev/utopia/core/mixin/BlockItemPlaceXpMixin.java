package dev.utopia.core.mixin;

import dev.utopia.core.skill.TechXp;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Der eigentliche Einhaengepunkt fuer die Technik-Erfahrung.
 *
 * Zuerst hing das an {@code Block.onPlaced} - das war falsch: die Methode ist in
 * Vanilla leer, und Create ueberschreibt sie in {@code KineticBlock} und
 * {@code CogWheelBlock}, ohne die Oberklasse aufzurufen. Fuer genau die Bloecke,
 * um die es geht, lief der Einhaengepunkt also nie.
 *
 * {@code BlockItem.place} liegt davor und wird bei jeder Platzierung durch einen
 * Spieler durchlaufen.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceXpMixin {

    /**
     * Gesperrte Bloecke lassen sich nicht setzen. Muss vor dem Setzen greifen,
     * deshalb ein zweiter Einstieg am Methodenanfang.
     */
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"), cancellable = true)
    private void utopia$blockLocked(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> info) {
        if (context.getPlayer() == null) {
            return;
        }
        net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ITEM.getId(context.getStack().getItem());
        if (dev.utopia.core.unlock.UnlockEnforcement.isBlocked(context.getPlayer(), id)) {
            dev.utopia.core.unlock.UnlockEnforcement.notifyLocked(context.getPlayer(), id);
            info.setReturnValue(ActionResult.FAIL);
        }
    }

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN"))
    private void utopia$techExperience(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> info) {
        if (!info.getReturnValue().isAccepted()) {
            return;
        }
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)
                || !(context.getWorld() instanceof ServerWorld world)) {
            return;
        }
        BlockPos pos = context.getBlockPos();
        TechXp.reward(player, world, pos, world.getBlockState(pos));
    }
}
