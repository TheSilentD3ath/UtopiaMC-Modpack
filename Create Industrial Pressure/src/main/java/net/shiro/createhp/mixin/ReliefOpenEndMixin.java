package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.FluidPropagator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.shiro.createhp.content.valve.PressureValveBlock;
import net.shiro.createhp.content.valve.ValveKind;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ein Ueberdruckventil am Ende eines Abzweigs ist kein offenes Rohrende.
 *
 * Creates Rohre und Ventile kippen an einem freien Ende Fluessigkeit in die Welt. Das Ueberdruckventil sitzt aber
 * meist genau so, am Ende eines T-Stuecks, und soll nur beim Ablassen etwas abgeben, und das als Dampfwolke.
 */
@Mixin(value = FluidPropagator.class, remap = false)
public class ReliefOpenEndMixin {

	@Inject(method = "isOpenEnd", at = @At("HEAD"), cancellable = true)
	private static void createhp$reliefIsClosed(BlockGetter reader, BlockPos pos, Direction side,
			CallbackInfoReturnable<Boolean> cir) {
		if (reader.getBlockState(pos).getBlock() instanceof PressureValveBlock valve && valve.getKind() == ValveKind.RELIEF)
			cir.setReturnValue(false);
	}
}
