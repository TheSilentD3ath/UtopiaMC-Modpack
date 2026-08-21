package dev.utopia.core.mixin;

import dev.utopia.core.skill.TechXp;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zweiter Weg fuer die Technik-Erfahrung: Bloecke, die nicht ueber ein
 * BlockItem gesetzt werden.
 *
 * Alleine reicht das nicht - Create ueberschreibt {@code onPlaced} in seinen
 * Basisklassen und ruft die Oberklasse nicht auf, weil sie in Vanilla leer ist.
 * Deshalb liegt der Hauptweg in {@link BlockItemPlaceXpMixin}. Doppelte Aufrufe
 * sind harmlos, es zaehlt jede Blockart nur einmal.
 */
@Mixin(Block.class)
public abstract class TechBuildXpMixin {

    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void utopia$techExperience(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack, CallbackInfo info) {
        if (world instanceof ServerWorld serverWorld && placer instanceof ServerPlayerEntity player) {
            TechXp.reward(player, serverWorld, pos, state);
        }
    }
}
