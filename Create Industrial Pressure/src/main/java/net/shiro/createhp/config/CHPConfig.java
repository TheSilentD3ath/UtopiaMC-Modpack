package net.shiro.createhp.config;

/**
 * Tunables. Plain constants for now (wire to Forge Config API Port later if you want a config file).
 *
 * Per-tier range + SU live on {@link net.shiro.createhp.content.PumpTier}
 * (Tier 1/2/3 = 64/128/256 blocks at full speed, 4x/8x/16x SU).
 */
public final class CHPConfig {
	private CHPConfig() {}

	/** Rotation speed (RPM) at which a pump reaches its tier's full range. Create's default cap is 256. */
	public static final float MAX_SPEED = 256f;

	// --- Pipe bursting (regular pipes over-pressured by a high pressure pump) ---
	/** Ticks between burst scans per running HP pump. Higher = lighter on big networks. */
	public static final int BURST_SCAN_INTERVAL = 30;
	/** Scans a regular pipe survives before it shatters (so total time ≈ INTERVAL * THRESHOLD ticks). */
	public static final int BURST_THRESHOLD = 4;
}
