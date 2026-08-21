package net.shiro.createhp.content;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.config.CHPConfig;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Block entity for the high pressure pumps. Inherits ALL of Create's pump behaviour and adds:
 *  - higher Stress (SU) cost, scaled per tier;
 *  - a speed-scaled effective reach (see {@link #getEffectiveRange()}), consumed by the range mixins;
 *  - the BURST mechanic: any non-high-pressure pipe in this running pump's network cracks, then breaks.
 *
 * The burst scan is bounded (by reach), throttled (every {@link CHPConfig#BURST_SCAN_INTERVAL} ticks),
 * and only runs server-side while the pump is actually spinning — so big networks don't get tick-heavy.
 */
public class HighPressurePumpBlockEntity extends PumpBlockEntity {

	private final PumpTier tier;

	/** Regular pipes currently over-pressured by this pump -> how many scans they've survived. */
	private final Map<BlockPos, Integer> crackProgress = new HashMap<>();

	public HighPressurePumpBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		Block b = state.getBlock();
		this.tier = (b instanceof HighPressurePumpBlock hp) ? hp.tier : PumpTier.TIER_1;
	}

	@Override
	public Block getStressConfigKey() {
		return AllBlocks.MECHANICAL_PUMP.get();
	}

	@Override
	public float calculateStressApplied() {
		float base = super.calculateStressApplied();
		float impact = (float) (base * tier.suMultiplier);
		this.lastStressApplied = impact;
		return impact;
	}

	public int getEffectiveRange() {
		float speed = Math.abs(getSpeed());
		return speed < 1f ? 0 : Math.round(tier.maxRange * Math.min(1f, speed / CHPConfig.MAX_SPEED));
	}

	public PumpTier getTier() {
		return tier;
	}

	/**
	 * Multiplier applied to the pressure this pump puts into the network. Create derives fluid
	 * THROUGHPUT from pressure (transferSpeed = max(1, pressure/2)), so a higher value = filling /
	 * emptying tanks & boilers faster and pulling more water per tick. Read by the throughput mixins.
	 */
	public double getPressureMultiplier() {
		return tier.suMultiplier;   // tier 1/2/3 = 4x / 8x / 16x, same "strength" as its SU cost
	}

	@Override
	public void tick() {
		// While our fluid behaviour ticks (inside super.tick), expose this pump's boosted push
		// pressure so a tank attached DIRECTLY to the output face fills fast (no pipe to boost).
		CHPPumpContext.set(Math.abs(getSpeed()) * (float) getPressureMultiplier());
		try {
			super.tick();
		} finally {
			CHPPumpContext.clear();
		}
		if (level == null || level.isClientSide)
			return;
		if (Math.abs(getSpeed()) < 1f) {           // not spinning -> no pressure -> relieve any cracks
			if (!crackProgress.isEmpty())
				clearAllCracks();
			return;
		}
		if (level.getGameTime() % CHPConfig.BURST_SCAN_INTERVAL == 0)
			burstScan();
	}

	/** One throttled pass: find over-pressured regular pipes, advance their cracks, break the worst. */
	private void burstScan() {
		Set<BlockPos> overPressured = findRegularPipesInNetwork();

		// pipes that left the network (removed / disconnected) heal
		Iterator<Map.Entry<BlockPos, Integer>> it = crackProgress.entrySet().iterator();
		while (it.hasNext()) {
			BlockPos p = it.next().getKey();
			if (!overPressured.contains(p)) {
				level.destroyBlockProgress(crackerId(p), p, -1);
				it.remove();
			}
		}

		for (BlockPos p : overPressured) {
			int progress = crackProgress.getOrDefault(p, 0) + 1;
			if (progress >= CHPConfig.BURST_THRESHOLD) {
				crackProgress.remove(p);
				level.destroyBlockProgress(crackerId(p), p, -1);
				level.playSound(null, p, SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 1.2f, 0.7f);
				level.destroyBlock(p, true);          // burst: break + drop + spill its fluid
			} else {
				crackProgress.put(p, progress);
				int stage = Math.min(9, progress * 10 / CHPConfig.BURST_THRESHOLD);
				level.destroyBlockProgress(crackerId(p), p, stage);
			}
		}
	}

	/** Bounded BFS over the connected pipe network; collects pipes that are NOT our HP variants. */
	private Set<BlockPos> findRegularPipesInNetwork() {
		Set<BlockPos> regular = new HashSet<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<Node> frontier = new ArrayDeque<>();
		int range = Math.max(1, getEffectiveRange());

		for (Direction d : Direction.values())
			frontier.add(new Node(worldPosition.relative(d), 1));

		while (!frontier.isEmpty()) {
			Node node = frontier.poll();
			if (node.dist > range || !visited.add(node.pos))
				continue;
			FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, node.pos);
			if (pipe == null)
				continue;                              // not a pipe
			BlockState state = level.getBlockState(node.pos);
			Block b = state.getBlock();
			if (isBurstablePipe(b))
				regular.add(node.pos);                 // a non-HP pipe under our pressure -> burst candidate
			List<Direction> connections = FluidPropagator.getPipeConnections(state, pipe);
			for (Direction dir : connections)
				frontier.add(new Node(node.pos.relative(dir), node.dist + 1));
		}
		return regular;
	}

	/**
	 * Only ACTUAL pipe blocks burst — never pumps, tanks, valves, etc. (those also report a fluid
	 * behaviour, which is why the pump was wrongly bursting). HP pipes + HP glass are immune.
	 * FluidPipeBlock covers fluid/encased/smart pipes; AxisPipeBlock covers glass pipes.
	 */
	private static boolean isBurstablePipe(Block b) {
		// Any of our pressure pipes / glass variants (copper, netherite, ...) are immune.
		if (b instanceof PressurePipe || b instanceof PressureGlassPipe)
			return false;
		return b instanceof FluidPipeBlock || b instanceof AxisPipeBlock;
	}

	private void clearAllCracks() {
		for (BlockPos p : crackProgress.keySet())
			level.destroyBlockProgress(crackerId(p), p, -1);
		crackProgress.clear();
	}

	private int crackerId(BlockPos p) {
		return p.hashCode();   // stable per-position id so each pipe cracks independently
	}

	private record Node(BlockPos pos, int dist) {}
}
