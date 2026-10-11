package net.shiro.createhp.content.turbine;

import net.shiro.createhp.content.pressure.PipeMaterial;

import org.jetbrains.annotations.Nullable;

/** Ein Block, aus dem sich eine Dampfturbine zusammensetzt. Der Abtrieb gehoert nicht dazu; er prueft die anderen. */
public interface TurbinePart {

	enum Role {
		/** Gehaeuse: Mantel um den Rotor, Vorder- und Rueckwand. */
		CASING,
		/** Rotor: die 3x3 Bloecke in der Mitte jeder Lage. */
		ROTOR,
		/** Dampfeinlass: sitzt anstelle eines Gehaeuseblocks, nimmt Dampf aus Rohren an. */
		INLET,
		/** Kondensatauslass: sitzt anstelle eines Gehaeuseblocks, gibt das Wasser ab. */
		CONDENSATE;

		/** Alles, was im Mantel und in den Waenden stehen darf. */
		public boolean isShell() {
			return this != ROTOR;
		}
	}

	Role getTurbineRole();

	/** Werkstoff des Teils, oder null fuer Teile, die in Turbinen jedes Werkstoffs passen. */
	@Nullable
	PipeMaterial getTurbineMaterial();
}
