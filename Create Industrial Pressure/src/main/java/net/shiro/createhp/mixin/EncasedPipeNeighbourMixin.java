package net.shiro.createhp.mixin;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.PressureEncasedPipeBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ein Rohr meldet seinen Pumpen, wenn neben einem offenen Anschluss ein Block kommt oder geht. Fuer ummantelte Rohre
 * fragt Create dabei fest nach seinem eigenen Block ({@code AllBlocks.ENCASED_FLUID_PIPE.has}); unsere ummantelten
 * Druckrohre wuerden einen neuen Tank am Anschluss sonst erst bemerken, wenn sich im Netz etwas anderes aendert.
 */
@Mixin(value = FluidPropagator.class, remap = false)
public class EncasedPipeNeighbourMixin {

	@Redirect(
			method = "validateNeighbourChange",
			at = @At(
					value = "INVOKE",
					target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z",
					remap = true
			)
	)
	private static boolean createhp$ourEncasedPipes(BlockEntry<?> entry, BlockState state) {
		return entry.has(state) || entry == AllBlocks.ENCASED_FLUID_PIPE && state.getBlock() instanceof PressureEncasedPipeBlock;
	}
}
