package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.PressurePipeBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Wrenching a fluid pipe runs FluidPipeBlock.onWrenched, which converts it to
 * {@code AllBlocks.GLASS_FLUID_PIPE} (Create's vanilla glass pipe). For OUR high pressure pipe we
 * want it to become our OWN high pressure glass pipe instead, so it stays high-pressure and blue.
 *
 * We redirect the single {@code AllBlocks.GLASS_FLUID_PIPE.getDefaultState()} call: if the block
 * being wrenched is ours, return our glass pipe's default state; otherwise vanilla behaviour. The
 * surrounding code then sets the axis + waterlogged on it (our glass block has both properties).
 */
@Mixin(value = FluidPipeBlock.class, remap = false)
public class FluidPipeWrenchMixin {

	@Redirect(
			method = "onWrenched",
			at = @At(
					value = "INVOKE",
					target = "Lcom/tterrag/registrate/util/entry/BlockEntry;getDefaultState()Lnet/minecraft/world/level/block/state/BlockState;",
					remap = true
			),
			require = 0
	)
	private BlockState createhp$highPressureGlassVariant(BlockEntry<?> instance) {
		// Any of our pressure pipes (copper, netherite, ...) wrenches into its OWN glass variant.
		if ((Object) this instanceof PressurePipeBlock pipe) {
			return pipe.getGlassPipeDefaultState();
		}
		return instance.getDefaultState();
	}
}
