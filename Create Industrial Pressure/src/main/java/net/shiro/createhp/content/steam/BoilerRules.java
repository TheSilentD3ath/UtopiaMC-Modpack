package net.shiro.createhp.content.steam;

/**
 * Zusatzregeln fuer Creates Kessel (BoilerData), eingebaut ueber BoilerDataMixin. Creates Werte gelten, solange
 * niemand etwas setzt; Drucktanks setzen ihre eigenen.
 */
public interface BoilerRules {

	/**
	 * @param levelCap        hoechste Kesselstufe (Create: 18)
	 * @param levelsPerBlock  Stufen je Tankblock; 0 heisst Creates Regel (eine Stufe je vier Bloecke)
	 * @param heatFactor      Faktor auf die Hitze jedes Brenners unter dem Kessel
	 */
	void createhp$setRules(int levelCap, int levelsPerBlock, float heatFactor);

	int createhp$getLevelCap();

	/** Anzahl der Dampfauslaesse am Kessel; jeder macht ihn aktiv wie eine Dampfmaschine. */
	int createhp$getOutlets();
}
