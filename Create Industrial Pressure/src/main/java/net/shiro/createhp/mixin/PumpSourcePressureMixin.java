package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.shiro.createhp.content.HighPressurePumpBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Durchsatz an den eigenen Anschluessen der Pumpe. Creates innere PumpFluidTransferBehaviour.tick()
 * setzt pro Anschluss {@code pressure.set(pull, abs(getSpeed()))} und den Gegen-Slot auf 0.
 *
 * Wir skalieren nur getSpeed(). Damit steigt der Druck auf beiden Seiten in der richtigen Richtung
 * (am Einlass eingehend, am Ausgang ausgehend), und ein Tank direkt an der Pumpe wird mit der Rate
 * der Stufe befuellt. Den Gegen-Slot nicht anfassen: Ein Vorgaenger hat ihn ebenfalls angehoben,
 * dadurch drehte sich der Druck an beiden Seiten um, und ein Tank direkt am Ausgang bekam gar nichts.
 */
@Mixin(targets = "com.simibubi.create.content.fluids.pump.PumpBlockEntity$PumpFluidTransferBehaviour", remap = false)
public class PumpSourcePressureMixin {

	// Der Aufruf ist gegen PumpBlockEntity kompiliert (die aeussere Klasse), nicht gegen
	// KineticBlockEntity. Mit dem falschen Owner greift der Redirect nie, und require = 0 verschweigt es.
	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lcom/simibubi/create/content/fluids/pump/PumpBlockEntity;getSpeed()F"
			),
			require = 0
	)
	private float createhp$boostFacePressure(PumpBlockEntity pump) {
		float speed = pump.getSpeed();
		if (pump instanceof HighPressurePumpBlockEntity hp) {
			return speed * (float) hp.getPressureMultiplier();
		}
		return speed;
	}
}
