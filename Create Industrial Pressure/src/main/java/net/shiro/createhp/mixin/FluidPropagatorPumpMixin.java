package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.HighPressurePumpBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * THE network-update fix.
 *
 * When a pipe network changes, FluidPropagator.propagateChangedPipe() walks the network and collects
 * the pumps it should tell to re-search (updatePipesOnSide). But it only accepts a pump if
 * {@code AllBlocks.MECHANICAL_PUMP.has(state)} — i.e. only Create's vanilla pump. Our high pressure
 * pump is a PumpBlockEntity but a different block, so it's skipped and never re-notified — which is
 * exactly why adding a tank / breaking a pipe didn't update until the pump was manually restarted.
 *
 * We redirect that single {@code BlockEntry.has(state)} call to ALSO return true for our pump. After
 * that, the inherited FACING check + discoveredPumps + updatePipesOnSide all work (our pump IS a
 * PumpBlockEntity). remap=false on the class (Create method); remap=true on the @At so the MC
 * BlockState in the target descriptor is mapped to runtime names while the Registrate owner is left.
 */
@Mixin(value = FluidPropagator.class, remap = false)
public class FluidPropagatorPumpMixin {

	@Redirect(
			method = "propagateChangedPipe",
			at = @At(
					value = "INVOKE",
					target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z",
					remap = true
			),
			require = 0  // don't crash if targeting drifts on a future Create build; vanilla pumps still work
	)
	private static boolean createhp$alsoAcceptHighPressurePump(BlockEntry<?> instance, BlockState state) {
		return instance.has(state) || state.getBlock() instanceof HighPressurePumpBlock;
	}
}
