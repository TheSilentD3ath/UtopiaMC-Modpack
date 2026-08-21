package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.FluidPropagator;

import net.shiro.createhp.content.CHPRangeContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Context-aware pump reach. When a high pressure pump is mid network-update (see PumpRangeMixin),
 * {@link CHPRangeContext} holds that pump's effective reach; we return the larger of vanilla and that
 * value. Outside a high pressure pump's update the context is empty, so vanilla behaviour is unchanged
 * for normal pumps. remap=false: getPumpRange is Create's own (not a Minecraft) method.
 */
@Mixin(value = FluidPropagator.class, remap = false)
public class FluidPropagatorMixin {

	@Inject(method = "getPumpRange", at = @At("RETURN"), cancellable = true, require = 0)
	private static void createhp$contextualRange(CallbackInfoReturnable<Integer> cir) {
		Integer override = CHPRangeContext.peek();
		if (override != null && override > cir.getReturnValueI()) {
			cir.setReturnValue(override);
		}
	}
}
