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
		b.pop();
		SERVER_SPEC = b.build();
	}

	public static void register() {
		ForgeConfigRegistry.INSTANCE.register(CreateHP.ID, ModConfig.Type.SERVER, SERVER_SPEC);
	}
}
