package net.shiro.createhp.content.pressure;

/**
 * Werkstoff eines Druckrohrs: legt fest, wie viel Druck es aushaelt und was bei Ueberlast passiert.
 *
 * Die Grenzwerte entsprechen genau dem Hoechstdruck der passenden Pumpenstufe bei 256 RPM
 * (Kupfer: Stufe 3, Netherit: Stufe 5). Ein sauber aufgebautes Netz platzt also nie; Ueberlast entsteht
 * erst, wenn mehrere Pumpen in dieselbe Leitung druecken oder eine staerkere Stufe ein schwaecheres
 * Rohr speist.
 */
public enum PipeMaterial {
	COPPER(256f, false),
	NETHERITE(1024f, true);

	/** Hoechster Druck in bar, den das Rohr dauerhaft aushaelt. */
	public final float ratingBar;
	/** Netherit platzt nicht einfach, es explodiert: Der Druck darin ist so hoch, dass er die Umgebung mitnimmt. */
	public final boolean explodes;

	PipeMaterial(float ratingBar, boolean explodes) {
		this.ratingBar = ratingBar;
		this.explodes = explodes;
	}
}
