package net.shiro.createhp.content;

/**
 * Thread-local "boosted output pressure" for a high pressure pump that is currently ticking.
 *
 * Create's pump zeroes the inbound pressure slot on its PUSH side (PumpFluidTransferBehaviour.tick:
 * {@code pressure.set(!pull, 0.0f)}), so a tank bolted DIRECTLY onto the pump's output face only
 * transfers at Create's floor rate — even though the pump itself is high pressure. There's no pipe
 * in between for the network boost (PumpThroughputMixin) to act on.
 *
 * A high pressure pump stashes its boosted pressure here for the duration of its own tick (around
 * super.tick(), which is where the fluid behaviour ticks). The output-face mixin reads it and lifts
 * that hard 0.0f to the boosted value, so a directly-attached tank fills fast too. Vanilla pumps
 * never set this (value stays null) and are untouched.
 *
 * Pumps tick sequentially on the server thread and the value is cleared in a finally block, so a
 * single slot is enough — no stack, no allocation on the hot path.
 */
public final class CHPPumpContext {
	private CHPPumpContext() {}

	private static final ThreadLocal<Float> OUTPUT_PRESSURE = new ThreadLocal<>();

	public static void set(float pressure) {
		OUTPUT_PRESSURE.set(pressure);
	}

	public static void clear() {
		OUTPUT_PRESSURE.remove();
	}

	/** Boosted push-side pressure, or null if no high pressure pump is ticking on this thread. */
	public static Float get() {
		return OUTPUT_PRESSURE.get();
	}
}
