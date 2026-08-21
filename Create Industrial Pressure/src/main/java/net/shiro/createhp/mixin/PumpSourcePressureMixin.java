package net.shiro.createhp.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.createmod.catnip.data.Couple;
import net.shiro.createhp.content.CHPPumpContext;
import net.shiro.createhp.content.HighPressurePumpBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Throughput boost (source side). Create's pump sets the pressure on its OWN connections (where it
 * touches a fluid source/sink — e.g. sitting in water) to {@code abs(getSpeed())} inside the inner
 * PumpFluidTransferBehaviour.tick(). We scale that by the pump's tier so a high pressure pump pulls
 * (or pushes) much more fluid per tick at its source. Vanilla pumps untouched.
 *
 * Targets the inner class by name; the getSpeed() call is on the OUTER PumpBlockEntity (the redirect
 * receiver), so we can check whether it's ours.
 */
@Mixin(targets = "com.simibubi.create.content.fluids.pump.PumpBlockEntity$PumpFluidTransferBehaviour", remap = false)
public class PumpSourcePressureMixin {

	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;getSpeed()F"
			),
			require = 0
	)
	private float createhp$boostSourcePressure(KineticBlockEntity pump) {
		float speed = pump.getSpeed();
		if (pump instanceof HighPressurePumpBlockEntity hp) {
			return speed * (float) hp.getPressureMultiplier();
		}
		return speed;
	}

	/**
	 * Push-side fix. The inner tick does {@code pressure.set(pull, abs(speed)); pressure.set(!pull, 0f)}.
	 * The second call (ordinal 1) zeroes the INBOUND slot on the pump's output face, which is what caps
	 * a directly-attached tank to Create's floor transfer rate. For a high pressure pump we replace that
	 * 0 with the boosted pressure stashed in {@link CHPPumpContext} for this tick, so a tank bolted right
	 * onto the output face fills as fast as one fed through a high pressure pipe. Vanilla pumps never set
	 * the context, so they keep the original 0.
	 */
	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/createmod/catnip/data/Couple;set(ZLjava/lang/Object;)V",
					ordinal = 1
			),
			require = 0
	)
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void createhp$boostOutputFace(Couple instance, boolean first, Object value) {
		Float boosted = CHPPumpContext.get();
		instance.set(first, boosted != null ? boosted : value);
	}
}
