package net.shiro.createhp.content.valve;

/** Die drei Ventilarten. Jede gibt es in Kupfer und Netherit, jede als normale und als Smart-Variante. */
public enum ValveKind {
	/** Sperrt ganz: Fluessigkeit und Druck. Normal per Drehung wie Creates Ventil, smart per Redstone. */
	SHUTOFF,
	/** Begrenzt den Druck hinter sich auf den Sollwert und drosselt damit den Durchfluss. Smart: Sollwert per Redstone. */
	REGULATOR,
	/** Haelt den ganzen Strang auf dem Ablassdruck und laesst den Ueberschuss als Wolke ab. Smart: Redstone beim Ablassen. */
	RELIEF;

	public boolean hasSetpoint() {
		return this != SHUTOFF;
	}
}
