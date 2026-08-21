package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.minecraft.core.Direction;
import net.shiro.createhp.content.CHPRangeContext;
import net.shiro.createhp.content.HighPressurePumpBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wraps the pump's network-update methods so that, while a HIGH PRESSURE pump is rebuilding/searching
 * its pipe network, {@link CHPRangeContext} carries that pump's effective reach. The getPumpRange hook
 * ({@link FluidPropagatorMixin}) then returns the larger value. Normal pumps push nothing.
 *
 * We wrap several entry points (the exact one that calls getPumpRange can vary); each HEAD/RETURN pair
 * is balanced because both test the same `instanceof`. remap=false: these are Create's own methods.
 */
@Mixin(value = PumpBlockEntity.class, remap = false)
public class PumpRangeMixin {

	private void createhp$push() {
		if ((Object) this instanceof HighPressurePumpBlockEntity hp) {
			CHPRangeContext.push(hp.getEffectiveRange());
		}
	}

	private void createhp$pop() {
		if ((Object) this instanceof HighPressurePumpBlockEntity) {
			CHPRangeContext.pop();
		}
	}

	@Inject(method = "updatePipeNetwork", at = @At("HEAD"))
	private void createhp$netHead(boolean rebuild, CallbackInfo ci) { createhp$push(); }

	@Inject(method = "updatePipeNetwork", at = @At("RETURN"))
	private void createhp$netRet(boolean rebuild, CallbackInfo ci) { createhp$pop(); }

	@Inject(method = "updatePipesOnSide", at = @At("HEAD"))
	private void createhp$sideHead(Direction side, CallbackInfo ci) { createhp$push(); }

	@Inject(method = "updatePipesOnSide", at = @At("RETURN"))
	private void createhp$sideRet(Direction side, CallbackInfo ci) { createhp$pop(); }

	@Inject(method = "distributePressureTo", at = @At("HEAD"))
	private void createhp$distHead(Direction side, CallbackInfo ci) { createhp$push(); }

	@Inject(method = "distributePressureTo", at = @At("RETURN"))
	private void createhp$distRet(Direction side, CallbackInfo ci) { createhp$pop(); }
}
