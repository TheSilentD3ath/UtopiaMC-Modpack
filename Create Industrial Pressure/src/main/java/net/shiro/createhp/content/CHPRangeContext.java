package net.shiro.createhp.content;

import java.util.ArrayDeque;

/**
 * Thread-local "current pump reach" override.
 *
 * Create's pump reach comes from the static FluidPropagator.getPumpRange(), which has no per-pump
 * context. Rather than surgically editing Create's network BFS, a high pressure pump pushes its
 * effective reach onto this stack while its network update runs (see PumpRangeMixin), and the
 * getPumpRange hook (FluidPropagatorMixin) reads it. Normal pumps never push, so they're untouched.
 *
 * This adds no per-tick work and no allocation on the hot path beyond a single push/pop per pump
 * network update — safe for large networks.
 */
public final class CHPRangeContext {
	private CHPRangeContext() {}

	private static final ThreadLocal<ArrayDeque<Integer>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

	public static void push(int range) {
		STACK.get().push(range);
	}

	public static void pop() {
		ArrayDeque<Integer> s = STACK.get();
		if (!s.isEmpty()) s.pop();
	}

	/** Top override, or null if no high pressure pump is currently updating on this thread. */
	public static Integer peek() {
		return STACK.get().peek();
	}
}
