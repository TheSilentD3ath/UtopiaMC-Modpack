package net.shiro.createhp.content.pressure;

/**
 * Werkstoff der Druckteile (Rohre, Ventile, Tanks): legt fest, wie viel Druck sie aushalten, was bei Ueberlast
 * passiert, wie viel ein Drucktank je Block fasst und was er als Kessel leistet.
 *
 * Die Grenzwerte entsprechen genau dem Hoechstdruck der passenden Pumpenstufe bei 256 RPM
 * (Kupfer: Stufe 3, Netherit: Stufe 5). Ein sauber aufgebautes Netz platzt also nie; Ueberlast entsteht
 * erst, wenn mehrere Pumpen in dieselbe Leitung druecken oder eine staerkere Stufe ein schwaecheres
 * Rohr speist.
 */
public enum PipeMaterial {
	COPPER(256f, false, 32, 50, 1, 1f),
	NETHERITE(1024f, true, 128, 150, 2, 3f);

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

	PipeMaterial(float ratingBar, boolean explodes, int tankBuckets, int boilerLevelCap, int boilerLevelsPerBlock,
			float boilerHeatFactor) {
		this.ratingBar = ratingBar;
		this.explodes = explodes;
		this.tankBuckets = tankBuckets;
		this.boilerLevelCap = boilerLevelCap;
		this.boilerLevelsPerBlock = boilerLevelsPerBlock;
		this.boilerHeatFactor = boilerHeatFactor;
	}
}
