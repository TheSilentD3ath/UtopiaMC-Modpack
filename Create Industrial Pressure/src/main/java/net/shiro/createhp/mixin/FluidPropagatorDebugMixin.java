package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.FluidPropagator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.CreateHP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DEBUG (temporary). Logs whenever Create re-evaluates a fluid pipe network. If adding a tank /
 * breaking a pipe near a high pressure line does NOT print this, then the network-update chain
 * isn't firing for our block and we know where to look. Remove once the pipe is confirmed working.
 */
@Mixin(value = FluidPropagator.class, remap = false)
public class FluidPropagatorDebugMixin {

	@Inject(method = "propagateChangedPipe", at = @At("HEAD"), require = 0)
	private static void createhp$logPropagate(LevelAccessor world, BlockPos pos, BlockState state, CallbackInfo ci) {
		// Create only calls this server-side, so no client guard needed.
		CreateHP.LOGGER.info("[CHP DEBUG] propagateChangedPipe @ {} ({})", pos, state.getBlock());
	}
}
