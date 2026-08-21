package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.minecraft.core.Direction;
import net.shiro.createhp.content.HighPressurePumpBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Throughput boost (network side). When a high pressure pump distributes pressure into its pipe
 * network, multiply that pressure by the pump's tier factor. Create computes fluid transfer speed
 * from pressure (transferSpeed = max(1, pressure/2) * 81 droplets), so higher pressure = filling /
 * emptying tanks, boilers, etc. much faster. Vanilla pumps are untouched.
 */
@Mixin(value = PumpBlockEntity.class, remap = false)
public class PumpThroughputMixin {

	@Redirect(
			method = "distributePressureTo",
			at = @At(
					value = "INVOKE",
					target = "Lcom/simibubi/create/content/fluids/FluidTransportBehaviour;addPressure(Lnet/minecraft/core/Direction;ZF)V",
					remap = true
			),
			require = 0
	)
	private void createhp$boostNetworkPressure(FluidTransportBehaviour behaviour, Direction side, boolean inbound, float pressure) {
		if ((Object) this instanceof HighPressurePumpBlockEntity hp) {
			pressure *= (float) hp.getPressureMultiplier();
		}
		behaviour.addPressure(side, inbound, pressure);
	}
}
