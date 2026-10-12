package net.shiro.createhp.config;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.shiro.createhp.CreateHP;

/**
 * Einstellungen. Die Server-Config liegt in config/createhp-server.toml (Forge Config API Port legt
 * Server-Configs auf Fabric standardmaessig global ab, nicht pro Welt), damit ein Serverbetreiber den
 * Blockschaden der Netherit-Explosion abschalten kann, ohne die Mod anzufassen.
 * Die Werte der Pumpenstufen bleiben in {@link net.shiro.createhp.content.PumpTier}.
 *
 * Die Client-Config (config/createhp-client.toml) merkt sich nur, ob der Spieler den einmaligen Warnhinweis zum
 * Drucksystem bestaetigt hat.
 */
public final class CHPConfig {
	private CHPConfig() {}

	/** Drehzahl, bei der eine Pumpe ihre volle Reichweite hat. Creates Hoechstdrehzahl ist 256. */
	public static final float MAX_SPEED = 256f;

	public static final ForgeConfigSpec SERVER_SPEC;
	public static final ForgeConfigSpec.BooleanValue EXPLOSION_BREAKS_BLOCKS;
	public static final ForgeConfigSpec.DoubleValue EXPLOSION_POWER;
	public static final ForgeConfigSpec.IntValue BURST_SCAN_INTERVAL;
	public static final ForgeConfigSpec.IntValue BURST_THRESHOLD;
	public static final ForgeConfigSpec.BooleanValue CREATE_TANKS_LIMITED;

	public static final ForgeConfigSpec CLIENT_SPEC;
	public static final ForgeConfigSpec.BooleanValue PRESSURE_WARNING_SEEN;

	static {
		ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
		b.push("overpressure");
		EXPLOSION_BREAKS_BLOCKS = b
				.comment("Whether an overloaded netherite pipe's explosion breaks blocks. Off: it still damages",
						"players and mobs and destroys the pipe itself.")
				.define("explosionBreaksBlocks", true);
		EXPLOSION_POWER = b
				.comment("Explosion power of an overloaded netherite pipe. TNT is 4.")
				.defineInRange("explosionPower", 4.0, 0.0, 16.0);
		BURST_SCAN_INTERVAL = b
				.comment("Ticks between two pressure checks of a running high pressure pump's network.")
				.defineInRange("scanIntervalTicks", 30, 5, 200);
		BURST_THRESHOLD = b
				.comment("Checks an overloaded pipe survives before it bursts. Time to burst is about",
						"scanIntervalTicks * burstAfterChecks ticks; the cracks grow in the meantime.")
				.defineInRange("burstAfterChecks", 4, 1, 40);
		CREATE_TANKS_LIMITED = b
				.comment("Whether Create's own fluid tanks hold only 16 bar, like Create's pipes. A high pressure pump",
						"pushing more into one makes it burst. Turn off for worlds with existing setups that fill",
						"Create tanks from high pressure pumps.")
				.define("createTanksLimited", true);
		b.pop();
		SERVER_SPEC = b.build();

		ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
		PRESSURE_WARNING_SEEN = c
				.comment("Whether the one-time warning about the pressure system was confirmed. Set to false to show it",
						"again on the next start.")
				.define("pressureWarningSeen", false);
		CLIENT_SPEC = c.build();
	}

	/**
	 * Ob Creates Tanks 16 bar halten. Vor dem Laden der Server-Config (Client im Hauptmenue) gilt die Voreinstellung,
	 * statt dass get() eine Ausnahme wirft.
	 */
	public static boolean createTanksLimited() {
		try {
			return CREATE_TANKS_LIMITED.get();
		} catch (IllegalStateException notLoaded) {
			return true;
		}
	}

	public static void register() {
		ForgeConfigRegistry.INSTANCE.register(CreateHP.ID, ModConfig.Type.SERVER, SERVER_SPEC);
	}

	/** Nur auf dem Client; ein Server braucht den Warnhinweis nicht. */
	public static void registerClient() {
		ForgeConfigRegistry.INSTANCE.register(CreateHP.ID, ModConfig.Type.CLIENT, CLIENT_SPEC);
	}

	/** Vor dem Laden der Client-Config gilt der Hinweis als gesehen, damit er nicht in einem halben Zustand aufgeht. */
	public static boolean pressureWarningSeen() {
		try {
			return PRESSURE_WARNING_SEEN.get();
		} catch (IllegalStateException notLoaded) {
			return true;
		}
	}

	public static void markPressureWarningSeen() {
		try {
			PRESSURE_WARNING_SEEN.set(true);
			PRESSURE_WARNING_SEEN.save();
		} catch (IllegalStateException notLoaded) {
			// Ohne geladene Config laesst sich nichts speichern; der Hinweis kommt dann beim naechsten Start wieder.
		}
	}
}
