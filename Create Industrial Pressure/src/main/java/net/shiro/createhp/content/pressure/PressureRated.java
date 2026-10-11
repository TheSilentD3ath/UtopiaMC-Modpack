package net.shiro.createhp.content.pressure;

/** Ein Block der Mod mit Druckgrenze: Rohre und Ventile. Der Werkstoff bestimmt Grenze und Verhalten bei Ueberlast. */
public interface PressureRated {
	PipeMaterial getMaterial();
}
