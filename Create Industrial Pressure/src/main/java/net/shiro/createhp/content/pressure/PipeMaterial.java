package net.shiro.createhp.content.pressure;

/**
 * Werkstoff der Druckteile (Rohre, Ventile, Tanks, Turbinen): legt fest, wie viel Druck sie aushalten, was bei
 * Ueberlast passiert, wie viel ein Drucktank je Block fasst, was er als Kessel leistet und was eine Dampfturbine
 * aus diesem Werkstoff schafft.
 *
 * Die Grenzwerte entsprechen genau dem Hoechstdruck der passenden Pumpenstufe bei 256 RPM
 * (Kupfer: Stufe 3, Netherit: Stufe 5). Ein sauber aufgebautes Netz platzt also nie; Ueberlast entsteht
 * erst, wenn mehrere Pumpen in dieselbe Leitung druecken oder eine staerkere Stufe ein schwaecheres
 * Rohr speist.
 */
public enum PipeMaterial {
	COPPER(256f, false, 32, 50, 1, 1f, 100, 1.5f),
	NETHERITE(1024f, true, 128, 150, 2, 3f, 200, 2.5f);

	/** Hoechster Druck in bar, den das Rohr dauerhaft aushaelt. */
	public final float ratingBar;
	/** Netherit platzt nicht einfach, es explodiert: Der Druck darin ist so hoch, dass er die Umgebung mitnimmt. */
	public final boolean explodes;
	/** Fassungsvermoegen eines Drucktanks je Block in Eimern; Creates Tank fasst 8 (Entscheidung des Maintainers). */
	public final int tankBuckets;
	/** Hoechste Kesselstufe eines Drucktanks (Create: 18). */
	public final int boilerLevelCap;
	/** Kesselstufen je Tankblock (Create: eine je vier Bloecke). */
	public final int boilerLevelsPerBlock;
	/** Faktor auf die Hitze jedes Brenners unter einem Drucktank-Kessel. */
	public final float boilerHeatFactor;
	/** Dampf, den eine Rotorlage der Turbine hoechstens verarbeitet, in mB pro Tick (10 Kesselstufen bei Kupfer). */
	public final int turbineSteamPerSlice;
	/**
	 * Leistung der Turbine je 10 mB Dampf pro Tick, als Vielfaches einer Create-Dampfmaschine unter Volllast
	 * (Entscheidung des Maintainers: deutlich mehr als Create).
	 */
	public final float turbinePowerFactor;

	PipeMaterial(float ratingBar, boolean explodes, int tankBuckets, int boilerLevelCap, int boilerLevelsPerBlock,
			float boilerHeatFactor, int turbineSteamPerSlice, float turbinePowerFactor) {
		this.ratingBar = ratingBar;
		this.explodes = explodes;
		this.tankBuckets = tankBuckets;
		this.boilerLevelCap = boilerLevelCap;
		this.boilerLevelsPerBlock = boilerLevelsPerBlock;
		this.boilerHeatFactor = boilerHeatFactor;
		this.turbineSteamPerSlice = turbineSteamPerSlice;
		this.turbinePowerFactor = turbinePowerFactor;
	}
}
