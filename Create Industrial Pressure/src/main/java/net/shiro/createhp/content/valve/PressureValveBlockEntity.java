package net.shiro.createhp.content.valve;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity.StraightPipeFluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.content.pressure.Pressure;
import net.shiro.createhp.content.pressure.PressureTooltip;
import net.shiro.createhp.mixin.FluidValveBlockEntityAccessor;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Gemeinsame Block-Entity aller Druckventile. Erbt Creates Fluessigkeitsventil (Zeiger, Rohranschluesse,
 * Drehung beim normalen Absperrventil) und fuegt die Druckfunktionen hinzu.
 *
 * Den Druck selbst formt {@link PressureShaping} beim Verteilen der Pumpen. Hier liegen die Werte, die es dafuer
 * braucht: Sollwert, offen oder zu, und die Beitraege der Pumpen, aus denen sich der ankommende Druck ergibt.
 */
public class PressureValveBlockEntity extends FluidValveBlockEntity {

	/** Stufen des Sollwerts. Kupfer: 1 bar je Stufe, Netherit: 4 bar; so passt jeder Grenzwert genau auf 256. */
	public static final int SETPOINT_STEPS = 256;

	/** Ein halbes Create-Einheit Spielraum, damit Rundung am Sollwert kein Flackern ausloest. */
	private static final float EPS_UNITS = 0.5f;

	/** Wie schnell der Zeiger bei Redstone und Sollwert umspringt: in vier Ticks. */
	private static final float CHASE_SPEED = 0.25f;

	/** 81 Tropfen sind 1 mB; Create rechnet den Durchsatz als Druck / 2 in mB pro Tick. */
	private static final long DROPLETS_PER_MB = 81;

	/**
	 * Ueberdruckventile, die gerade ablassen. Die Fluessigkeitsnetze fragen sie ab (siehe ValveFlow), statt jedes
	 * Netz bei jedem Tick alle Bloecke durchsuchen zu lassen. Nur auf dem Server befuellt.
	 */
	private static final Set<PressureValveBlockEntity> VENTING = Collections.newSetFromMap(new WeakHashMap<>());

	private record Contribution(float units, @Nullable Set<BlockPos> zone) {}

	@Nullable
	private ScrollValueBehaviour setpoint;
	private final Map<PressureShaping.PumpSide, Contribution> contributions = new HashMap<>();
	/** Ankommender Druck fuer die Anzeige auf dem Client; der Server rechnet ihn aus den Beitraegen. */
	private float clientDemand;
	private float sentDemand = -1f;
	private int lastSignal = -1;
	private boolean firstTick = true;
	private long ventTick = Long.MIN_VALUE;
	private long ventLeft;

	public PressureValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Aufbau

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		// Creates Ventil-Rohrverhalten durch unseres ersetzen (gleicher Typ, also genau eines erlaubt).
		behaviours.removeIf(b -> b.getType() == FluidTransportBehaviour.TYPE);
		behaviours.add(new ValvePipe(this));

		// Laeuft im Konstruktor der Oberklasse: Nur den Blockzustand verwenden, keine eigenen Felder.
		ValveKind kind = kindOf(getBlockState());
		if (!kind.hasSetpoint())
			return;
		setpoint = new SetpointBehaviour(Component.translatable(kind == ValveKind.REGULATOR
				? "createhp.valve.setpoint" : "createhp.valve.relief_pressure"), this, new SetpointSlot());
		setpoint.between(0, SETPOINT_STEPS);
		// Regler auf die Grenze von Creates Rohren, Ueberdruckventile auf den eigenen Grenzwert: Beides kann nichts
		// platzen lassen, bevor der Spieler es einstellt.
		float step = materialOf(getBlockState()).ratingBar / SETPOINT_STEPS;
		setpoint.value = kind == ValveKind.REGULATOR ? Math.round(Pressure.CREATE_PIPE_RATING_BAR / step) : SETPOINT_STEPS;
		setpoint.withFormatter(v -> PressureTooltip.format(v * step));
		setpoint.withCallback(v -> repropagate());
		setpoint.onlyActiveWhen(() -> !(getKind() == ValveKind.REGULATOR && isSmart()));
		behaviours.add(setpoint);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Zustand

	private static ValveKind kindOf(BlockState state) {
		return state.getBlock() instanceof PressureValveBlock valve ? valve.getKind() : ValveKind.SHUTOFF;
	}

	private static PipeMaterial materialOf(BlockState state) {
		return state.getBlock() instanceof PressureValveBlock valve ? valve.getMaterial() : PipeMaterial.COPPER;
	}

	public ValveKind getKind() {
		return kindOf(getBlockState());
	}

	public PipeMaterial getMaterial() {
		return materialOf(getBlockState());
	}

	public boolean isSmart() {
		BlockState state = getBlockState();
		return state.hasProperty(PressureValveBlock.SMART) && state.getValue(PressureValveBlock.SMART);
	}

	public boolean isOpen() {
		BlockState state = getBlockState();
		return state.hasProperty(FluidValveBlock.ENABLED) && state.getValue(FluidValveBlock.ENABLED);
	}

	public LerpedFloat getPointer() {
		return ((FluidValveBlockEntityAccessor) this).createhp$getPointer();
	}

	/** Sollwert in bar. Der schlaue Regler nimmt ihn aus der Redstone-Staerke, alle anderen aus dem Wertefeld. */
	public float setpointBar() {
		ValveKind kind = getKind();
		if (!kind.hasSetpoint())
			return Pressure.UNRATED;
		float rating = getMaterial().ratingBar;
		if (kind == ValveKind.REGULATOR && isSmart()) {
			// lastSignal kennt nur der Server; der Client liest den Redstone selbst, die Zustaende hat er ja.
			int signal = level != null && level.isClientSide ? level.getBestNeighborSignal(worldPosition) : lastSignal;
			return rating * Math.max(0, signal) / 15f;
		}
		int value = setpoint == null ? SETPOINT_STEPS : setpoint.getValue();
		return value * rating / SETPOINT_STEPS;
	}

	public float setpointUnits() {
		return setpointBar() * Pressure.UNITS_PER_BAR;
	}

	/** Grenze hinter diesem Ventil, von der Pumpe aus gesehen, wenn vor ihm capIn anliegt. */
	float capBeyond(float capIn) {
		return switch (getKind()) {
			case SHUTOFF -> isOpen() ? capIn : 0f;
			case REGULATOR -> Math.min(capIn, setpointUnits());
			case RELIEF -> capIn;
		};
	}

	/** Regler und geschlossene Absperrventile trennen den Strang eines Ueberdruckventils ab. */
	boolean boundsZone() {
		ValveKind kind = getKind();
		return kind == ValveKind.REGULATOR || kind == ValveKind.SHUTOFF && !isOpen();
	}

	// ---------------------------------------------------------------------------------------------------------
	// Beitraege der Pumpen

	void putContribution(PressureShaping.PumpSide key, float units, @Nullable Set<BlockPos> zone) {
		contributions.put(key, new Contribution(units, zone));
	}

	void removeContribution(PressureShaping.PumpSide key) {
		contributions.remove(key);
	}

	@Nullable
	Set<BlockPos> zoneFor(PressureShaping.PumpSide key) {
		Contribution contribution = contributions.get(key);
		return contribution == null ? null : contribution.zone;
	}

	/** Druck, den alle Pumpen zusammen bis hierher bringen wuerden, vor dem Begrenzen, in Create-Einheiten. */
	public float demandUnits() {
		if (level != null && level.isClientSide)
			return clientDemand;
		float sum = 0f;
		for (Contribution contribution : contributions.values())
			sum += contribution.units;
		return sum;
	}

	/**
	 * Druck fuer Brille und Display Link. Create gibt einem toten Abzweig keinen Druck; ein Ueberdruckventil
	 * am Ende eines T-Stuecks zeigte sonst 0 bar, waehrend es ablaesst. Es zeigt deshalb den Druck seines
	 * Strangs, also hoechstens seinen Ablassdruck.
	 */
	public float displayBar(@Nullable FluidTransportBehaviour pipe) {
		float bar = Pressure.barAt(pipe);
		if (getKind() == ValveKind.RELIEF)
			bar = Math.max(bar, Math.min(demandUnits(), setpointUnits()) / Pressure.UNITS_PER_BAR);
		return bar;
	}

	public boolean isOverSetpoint() {
		return getKind().hasSetpoint() && demandUnits() > setpointUnits() + EPS_UNITS;
	}

	/**
	 * Anteil eines Fluessigkeitsnetzes an dem, was hier ankommt: die Beitraege der Pumpen, durch die es fliesst
	 * (oder an denen es beginnt, wenn die Quelle direkt an der Pumpe haengt). Findet sich keine, zaehlt das Netz
	 * ganz; lieber zu viel Durchsatz als ein Netz, das grundlos stillsteht.
	 */
	float shareOf(BlockPos start, Set<BlockPos> visited) {
		float total = 0f;
		float mine = 0f;
		for (Map.Entry<PressureShaping.PumpSide, Contribution> entry : contributions.entrySet()) {
			float units = entry.getValue().units;
			total += units;
			BlockPos pump = entry.getKey().pos();
			if (visited.contains(pump) || pump.equals(start))
				mine += units;
		}
		if (total <= 0f || mine <= 0f)
			return 1f;
		return Math.min(1f, mine / total);
	}

	/** Durchsatzgrenze fuer ein Netz mit diesem Anteil, in Tropfen; Create rechnet Druck / 2 in mB pro Tick. */
	long flowCapDroplets(float share) {
		return (long) (setpointUnits() / 2f * share * DROPLETS_PER_MB);
	}

	/** Was das Ventil pro Tick insgesamt ablaesst: der Ueberschuss in Creates Durchsatzformel, in Tropfen. */
	public long ventDropletsPerTick() {
		float excess = demandUnits() - setpointUnits();
		return excess <= EPS_UNITS ? 0 : (long) (excess / 2f * DROPLETS_PER_MB);
	}

	/**
	 * Der Anteil, den ein Netz in diesem Tick ablassen darf. Mehr als einmal den Ueberschuss gibt das Ventil pro
	 * Tick nie heraus, auch wenn zwei Netze dieselbe Pumpe enthalten und beide ihren vollen Anteil verlangen.
	 */
	long claimVent(long gameTime, float share) {
		if (ventTick != gameTime) {
			ventTick = gameTime;
			ventLeft = ventDropletsPerTick();
		}
		long claimed = Math.min(ventLeft, (long) (ventDropletsPerTick() * share));
		ventLeft -= claimed;
		return claimed;
	}

	boolean zoneTouches(Set<BlockPos> visited) {
		for (Contribution contribution : contributions.values()) {
			Set<BlockPos> zone = contribution.zone;
			if (zone == null)
				continue;
			Set<BlockPos> small = zone.size() < visited.size() ? zone : visited;
			Set<BlockPos> large = small == zone ? visited : zone;
			for (BlockPos pos : small)
				if (large.contains(pos))
					return true;
		}
		return false;
	}

	static Set<PressureValveBlockEntity> venting() {
		return VENTING;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Ablauf

	@Override
	public void onSpeedChanged(float previousSpeed) {
		// Nur das normale Absperrventil folgt der Drehung. Bei allen anderen wuerde Creates Ventil den Zeiger bei
		// Drehzahl 0 zuruecksetzen und damit Redstone oder Sollwert ueberschreiben.
		if (isRotationDriven())
			super.onSpeedChanged(previousSpeed);
	}

	private boolean isRotationDriven() {
		return getKind() == ValveKind.SHUTOFF && !isSmart();
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null)
			return;
		BlockState state = getBlockState();
		if (!(state.getBlock() instanceof PressureValveBlock))
			return;
		ValveKind kind = getKind();

		if (level.isClientSide) {
			if (kind == ValveKind.RELIEF && isOpen())
				spawnVentEffects();
			return;
		}

		if (firstTick) {
			firstTick = false;
			lastSignal = level.getBestNeighborSignal(worldPosition);
			// Die Beitraege der Pumpen werden nicht gespeichert. Nach dem Laden verteilen die Pumpen nicht von
			// selbst neu; also einmal anstossen, sonst stuende ein Ueberdruckventil bis zur naechsten Aenderung still.
			if (kind.hasSetpoint())
				repropagate();
		}

		if (kind == ValveKind.SHUTOFF) {
			if (isSmart())
				chasePointer(level.hasNeighborSignal(worldPosition));
			return;
		}

		if (kind == ValveKind.REGULATOR && isSmart()) {
			int signal = level.getBestNeighborSignal(worldPosition);
			if (signal != lastSignal) {
				lastSignal = signal;
				repropagate();
			}
		}

		if (level.getGameTime() % 20 == 0)
			dropStaleContributions();

		boolean active = isOverSetpoint();
		if (kind == ValveKind.RELIEF) {
			if (active)
				VENTING.add(this);
			else
				VENTING.remove(this);
		}
		chasePointer(active);

		float demand = demandUnits();
		if (Math.abs(demand - sentDemand) > EPS_UNITS && level.getGameTime() % 10 == 0) {
			sentDemand = demand;
			sendData();
		}
	}

	/** Beitraege von Pumpen, die weg sind oder stehen. Ihr letztes Verteilen kam hier nie mehr an. */
	private void dropStaleContributions() {
		for (Iterator<PressureShaping.PumpSide> it = contributions.keySet().iterator(); it.hasNext();) {
			PumpBlockEntity pump = PressureShaping.pumpAt(level, it.next());
			if (pump == null || pump.getSpeed() == 0)
				it.remove();
		}
	}

	/** Zeiger auf 1 heisst bei Creates Ventil "offen"; Creates tick() schaltet ENABLED, sobald er ankommt. */
	private void chasePointer(boolean open) {
		float target = open ? 1f : 0f;
		LerpedFloat pointer = getPointer();
		if (pointer.getChaseTarget() == target)
			return;
		pointer.chase(target, CHASE_SPEED, Chaser.LINEAR);
		sendData();
	}

	/** Pumpen neu verteilen lassen, damit ein neuer Sollwert oder Zustand sofort im Netz ankommt. */
	void repropagate() {
		if (level == null || level.isClientSide)
			return;
		FluidPropagator.propagateChangedPipe(level, worldPosition, getBlockState());
	}

	private void spawnVentEffects() {
		BlockState state = getBlockState();
		Direction facing = state.getValue(FluidValveBlock.FACING);
		Vec3 center = VecHelper.getCenterOf(worldPosition);
		// Die Wolke kommt aus den beiden Seiten mit dem Handrad, dort sitzt beim echten Ventil der Abblasstutzen.
		for (Direction side : new Direction[] { facing, facing.getOpposite() }) {
			if (level.random.nextInt(3) != 0)
				continue;
			Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
			Vec3 at = center.add(normal.scale(0.55));
			Vec3 motion = normal.scale(0.08 + level.random.nextFloat() * 0.06)
					.add(VecHelper.offsetRandomly(Vec3.ZERO, level.random, 0.02f));
			level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, motion.x, motion.y, motion.z);
		}
		if (level.getGameTime() % 20 == 0)
			AllSoundEvents.STEAM.playAt(level, worldPosition, 0.4f, 1.2f, true);
	}

	// SmartBlockEntity.setRemoved ist final und ruft invalidate() beim Abbauen wie beim Entladen des Chunks.
	@Override
	public void invalidate() {
		super.invalidate();
		VENTING.remove(this);
	}

	@Override
	protected void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		if (clientPacket)
			compound.putFloat("Demand", demandUnits());
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		if (clientPacket)
			clientDemand = compound.getFloat("Demand");
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return PressureTooltip.addValve(tooltip, this, getBehaviour(FluidTransportBehaviour.TYPE));
	}

	// ---------------------------------------------------------------------------------------------------------
	// Rohrverhalten und Wertefeld

	/** Wie Creates Ventil: Druck auf der Rohrachse immer, Fluessigkeit beim Absperrventil nur offen. */
	class ValvePipe extends StraightPipeFluidTransportBehaviour {
		ValvePipe(SmartBlockEntity be) {
			super(be);
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return state.getBlock() instanceof FluidValveBlock && FluidValveBlock.getPipeAxis(state) == direction.getAxis();
		}

		@Override
		public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
			if (kindOf(state) == ValveKind.SHUTOFF
					&& !(state.hasProperty(FluidValveBlock.ENABLED) && state.getValue(FluidValveBlock.ENABLED)))
				return false;
			return super.canPullFluidFrom(fluid, state, direction);
		}
	}

	/** Wertefeld in bar statt in Stufen. */
	class SetpointBehaviour extends ScrollValueBehaviour {
		SetpointBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
			super(label, be, slot);
		}

		@Override
		public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
			float step = getMaterial().ratingBar / SETPOINT_STEPS;
			// Ein Meilenstein alle 16 Stufen: bei Kupfer alle 16 bar, also genau Creates Rohrgrenze.
			return new ValueSettingsBoard(label, max, 16,
					ImmutableList.of(Component.translatable("createhp.generic.unit.bar")),
					new ValueSettingsFormatter(v -> Component.literal(PressureTooltip.format(v.value() * step) + " bar")));
		}
	}

	/**
	 * Das Wertefeld sitzt auf den Seiten, wo beim normalen Ventil die Welle durchgeht. Regler und
	 * Ueberdruckventil haben keine Welle, die Seiten sind also frei.
	 */
	static class SetpointSlot extends ValueBoxTransform.Sided {
		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 8, 14.5);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return state.getBlock() instanceof PressureValveBlock valve
					&& direction.getAxis() == valve.getRotationAxis(state);
		}
	}
}
