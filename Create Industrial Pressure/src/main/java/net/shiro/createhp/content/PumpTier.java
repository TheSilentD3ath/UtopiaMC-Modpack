package net.shiro.createhp.content;

/**
 * The high pressure pump tiers. Range is the MAX reach at full rotation speed;
 * the actual reach scales with the pump's current RPM (see HighPressurePumpBlockEntity).
 * suMultiplier scales BOTH Stress Units AND fluid throughput relative to a vanilla mechanical pump.
 *
 * Copper tiers: 64/128/256 reach at 4x/8x/16x. Netherite tiers continue the doubling: the netherite
 * pump reaches 512 at 32x, and the advanced netherite pump reaches 1024 at 64x.
 */
public enum PumpTier {
	TIER_1("high_pressure_pump", 64, 4.0),
	TIER_2("high_pressure_pump_2", 128, 8.0),
	TIER_3("high_pressure_pump_3", 256, 16.0),
	TIER_4("netherite_pressure_pump", 512, 32.0),
	TIER_5("advanced_netherite_pump", 1024, 64.0);

	public final String id;
	public final int maxRange;
	public final double suMultiplier;

	PumpTier(String id, int maxRange, double suMultiplier) {
		this.id = id;
		this.maxRange = maxRange;
		this.suMultiplier = suMultiplier;
	}
}
