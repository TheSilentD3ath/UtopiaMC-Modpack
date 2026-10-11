package net.shiro.createhp.content;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.config.CHPConfig;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.content.pressure.Pressure;
import net.shiro.createhp.content.pressure.PressureTooltip;

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
 *  - the overpressure check: any pipe in this running pump's network whose pressure is above its rating
 *    cracks, then bursts; netherite pipes explode (see {@link Pressure} and {@link PipeMaterial}).
 *
 * The check is bounded (by reach), throttled (every {@link CHPConfig#BURST_SCAN_INTERVAL} ticks),
 * and only runs server-side while the pump is actually spinning — so big networks don't get tick-heavy.
 */
public class HighPressurePumpBlockEntity extends PumpBlockEntity {

	private final PumpTier tier;

	/** Overloaded pipes in this pump's network -> how many checks they have survived. */
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
		super.tick();
		if (level == null || level.isClientSide)
			return;
		if (Math.abs(getSpeed()) < 1f) {           // not spinning -> no pressure -> relieve any cracks
			if (!crackProgress.isEmpty())
				clearAllCracks();
			return;
		}
		if (level.getGameTime() % CHPConfig.BURST_SCAN_INTERVAL.get() == 0)
			burstScan();
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		PressureTooltip.addPump(tooltip, getBehaviour(FluidTransportBehaviour.TYPE));
		return true;
	}

	/** One throttled pass: find overloaded pipes, advance their cracks, burst the ones that had enough. */
	private void burstScan() {
		Set<BlockPos> overloaded = findOverloadedPipes();
		int threshold = CHPConfig.BURST_THRESHOLD.get();

		// Rohre, die nicht mehr ueberlastet sind (Druck gesenkt, abgebaut, abgetrennt), erholen sich.
		Iterator<Map.Entry<BlockPos, Integer>> it = crackProgress.entrySet().iterator();
		while (it.hasNext()) {
			BlockPos p = it.next().getKey();
			if (!overloaded.contains(p)) {
				level.destroyBlockProgress(crackerId(p), p, -1);
				it.remove();
			}
		}

		for (BlockPos p : overloaded) {
			int progress = crackProgress.getOrDefault(p, 0) + 1;
			if (progress >= threshold) {
				crackProgress.remove(p);
				level.destroyBlockProgress(crackerId(p), p, -1);
				burst(p);
			} else {
				crackProgress.put(p, progress);
				int stage = Math.min(9, progress * 10 / threshold);
				level.destroyBlockProgress(crackerId(p), p, stage);
			}
		}
	}

	private void burst(BlockPos p) {
		PipeMaterial material = Pressure.materialOf(level.getBlockState(p).getBlock());
		if (material != null && material.explodes) {
			// Erst das Rohr entfernen: Netherit hat die Explosionsfestigkeit eines Netheritblocks und wuerde
			// die eigene Explosion sonst ueberstehen. Es droppt nichts, es ist ja explodiert.
			level.removeBlock(p, false);
			Level.ExplosionInteraction interaction = CHPConfig.EXPLOSION_BREAKS_BLOCKS.get()
					? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
			level.explode(null, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
					CHPConfig.EXPLOSION_POWER.get().floatValue(), interaction);
			return;
		}
		level.playSound(null, p, SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 1.2f, 0.7f);
		level.destroyBlock(p, true);          // burst: break + drop + spill its fluid
	}

	/**
	 * Bounded BFS over the pipe network this pump feeds; collects every pipe whose pressure is above its
	 * rating, and every tank block fed above its rating. The pressure read is the pipe's total, so other
	 * pumps feeding the same pipe count as well.
	 */
	private Set<BlockPos> findOverloadedPipes() {
		Set<BlockPos> overloaded = new HashSet<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<Node> frontier = new ArrayDeque<>();
		int range = Math.max(1, getEffectiveRange());

		FluidTransportBehaviour own = getBehaviour(FluidTransportBehaviour.TYPE);
		if (own == null)
			return overloaded;
		visited.add(worldPosition);
		addOverloadedTanks(worldPosition, own, overloaded);
		for (var d : FluidPropagator.getPipeConnections(getBlockState(), own))
			frontier.add(new Node(worldPosition.relative(d), 1));

		while (!frontier.isEmpty()) {
			Node node = frontier.poll();
			if (node.dist > range || !visited.add(node.pos))
				continue;
			FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, node.pos);
			if (pipe == null)
				continue;                              // not a pipe
			BlockState state = level.getBlockState(node.pos);
			if (Pressure.isOverloaded(Pressure.barAt(pipe), Pressure.ratingBar(state)))
				overloaded.add(node.pos);
			addOverloadedTanks(node.pos, pipe, overloaded);
			for (var dir : FluidPropagator.getPipeConnections(state, pipe))
				frontier.add(new Node(node.pos.relative(dir), node.dist + 1));
		}
		return overloaded;
	}

	/**
	 * Tanks am Ende einer Leitung: Ein Tank hat keinen eigenen Druck, er bekommt den des Anschlusses, der in ihn
	 * speist. Ueberlastet ist dann der Tankblock an genau diesem Anschluss; er platzt, nicht der ganze Multiblock.
	 */
	private void addOverloadedTanks(BlockPos at, FluidTransportBehaviour pipe, Set<BlockPos> overloaded) {
		if (pipe.interfaces == null)
			return;
		for (Map.Entry<Direction, PipeConnection> entry : pipe.interfaces.entrySet()) {
			BlockPos neighbour = at.relative(entry.getKey());
			if (!level.isLoaded(neighbour))
				continue;
			BlockState state = level.getBlockState(neighbour);
			if (!(state.getBlock() instanceof FluidTankBlock))
				continue;
			Couple<Float> pressure = entry.getValue().getPressure();
			float bar = Math.max(pressure.getFirst(), pressure.getSecond()) / Pressure.UNITS_PER_BAR;
			if (Pressure.isOverloaded(bar, Pressure.ratingBar(state)))
				overloaded.add(neighbour);
		}
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
