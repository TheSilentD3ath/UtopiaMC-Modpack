package net.shiro.createhp.mixin;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.PressureEncasedPipeBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ein Rohr zeichnet zu einem ummantelten Rohr hin einen Rand, zu einem Block mit Fluessigkeitsspeicher einen Abfluss.
 * Create erkennt das ummantelte Rohr dabei nur an seinem eigenen Block; unseres soll denselben Rand bekommen. Der
 * zweite {@code has}-Aufruf der Methode (Schlauchrolle) bleibt unberuehrt.
 */
@Mixin(value = FluidTransportBehaviour.class, remap = false)
public class EncasedPipeRimMixin {

	@Redirect(
			method = "getRenderedRimAttachment",
			at = @At(
					value = "INVOKE",
					target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z",
					remap = true
			)
	)
	private boolean createhp$ourEncasedPipes(BlockEntry<?> entry, BlockState state) {
		return entry.has(state) || entry == AllBlocks.ENCASED_FLUID_PIPE && state.getBlock() instanceof PressureEncasedPipeBlock;
	}
}
