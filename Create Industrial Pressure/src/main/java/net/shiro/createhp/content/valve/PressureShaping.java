package net.shiro.createhp.content.valve;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.HighPressurePumpBlockEntity;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Formt den Druck, den eine Pumpe ins Netz verteilt, nach den Ventilen auf ihrem Weg.
 *
 * Create setzt den Druck an jedem Anschluss nur in PumpBlockEntity.distributePressureTo. Genau dort greifen
 * wir ein (siehe PumpRangeMixin und PumpThroughputMixin): Vor dem Verteilen laeuft begin() die Leitung von der
 * Pumpe aus ab und bestimmt fuer jedes Rohr eine Obergrenze; limit() kappt dann jeden einzelnen Beitrag der
 * Pumpe so, dass die Summe am Anschluss die Obergrenze nicht uebersteigt. So entsteht kein zweites Druckmodell:
 * Create rechnet weiter mit seinen eigenen Werten, nur eben mit den gekappten.
 *
 * - Hinter einem geschlossenen Absperrventil ist die Grenze 0. Creates eigenes Ventil laesst den Druck durch
 *   und sperrt nur die Fluessigkeit; dann wuerden Rohre hinter einem geschlossenen Ventil platzen.
 * - Hinter einem Regler ist die Grenze sein Sollwert. "Hinter" heisst: von der Pumpe aus gesehen. Ein Regler
 *   zwischen zwei Pumpen begrenzt also beide in Richtung der jeweils anderen Seite.
 * - Ein Ueberdruckventil begrenzt seinen ganzen Strang (bis zum naechsten Regler oder geschlossenen Ventil),
 *   sobald alle Pumpen zusammen mehr liefern als sein Ablassdruck.
 *
 * Alles laeuft auf dem Server-Thread; der Stapel ist trotzdem threadlokal, weil Ponder-Szenen auf dem
 * Client-Thread verteilen.
 */
public final class PressureShaping {
	private PressureShaping() {}

	/** Obergrenze fuer die Schritte einer Suche, damit ein riesiges Netz den Server nicht aufhaelt. */
	private static final int MAX_STEPS = 8192;

	private static final float UNLIMITED = Float.POSITIVE_INFINITY;

	/** Eine Pumpenseite; jede Seite verteilt getrennt, und jede liefert einen eigenen Beitrag an ein Ventil. */
	public record PumpSide(BlockPos pos, Direction side) {}

	/** Ergebnis einer Suche: Obergrenze je Rohr in Create-Einheiten. Fehlt ein Rohr, gibt es keine Grenze. */
	private record Shape(Map<BlockPos, Float> caps) {
		static final Shape NONE = new Shape(Collections.emptyMap());
	}

	private static final ThreadLocal<ArrayDeque<Shape>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);

	/**
	 * Welche Ventile jede Pumpenseite beim letzten Verteilen erreicht hat. Erreicht sie eines nicht mehr (Rohr
	 * entfernt, Pumpe steht), muss ihr Beitrag dort weg; sonst bliebe ein Ueberdruckventil ewig offen.
	 * Schwache Schluessel: Eine abgebaute Pumpe faellt von selbst heraus.
	 */
	private static final Map<PumpBlockEntity, EnumMap<Direction, Set<BlockPos>>> REACHED = new WeakHashMap<>();

	/** Vor dem Verteilen einer Pumpenseite. Jedes begin() braucht ein end(). */
	public static void begin(PumpBlockEntity pump, Direction side) {
		Shape shape = Shape.NONE;
		try {
			shape = compute(pump, side);
		} finally {
			ACTIVE.get().push(shape);
		}
	}

	public static void end() {
		ArrayDeque<Shape> stack = ACTIVE.get();
		if (!stack.isEmpty())
			stack.pop();
	}

	/**
	 * Wie viel von einem Druckbeitrag ankommen darf. Die Summe am Anschluss zaehlt, nicht der einzelne Beitrag:
	 * Zwei Pumpen mit je 100 bar vor einem Regler auf 150 bar ergeben dahinter 150 bar, nicht 200.
	 */
	public static float limit(FluidTransportBehaviour pipe, Direction side, boolean inbound, float pressure) {
		Shape shape = ACTIVE.get().peek();
		if (shape == null || shape.caps.isEmpty())
			return pressure;
		Float cap = shape.caps.get(pipe.getPos());
		if (cap == null)
			return pressure;
		PipeConnection connection = pipe.getConnection(side);
		float current = connection == null ? 0f : connection.getPressure().get(inbound);
		return Math.max(0f, Math.min(pressure, cap - current));
	}

	/** Der Druck, den die Pumpe abgibt, in Create-Einheiten, wie ihn auch distributePressureTo verwendet. */
	private static float pumpPressure(PumpBlockEntity pump) {
		float pressure = Math.abs(pump.getSpeed());
		if (pump instanceof HighPressurePumpBlockEntity hp)
			pressure *= (float) hp.getPressureMultiplier();
		return pressure;
	}

	private record Step(BlockPos pos, float cap, int distance) {}

	private static Shape compute(PumpBlockEntity pump, Direction side) {
		Level level = pump.getLevel();
		if (level == null)
			return Shape.NONE;
		PumpSide key = new PumpSide(pump.getBlockPos(), side);
		float pressure = pumpPressure(pump);

		// Widest Path: Jedes Rohr bekommt die groesste Grenze, die auf irgendeinem Weg ankommt. Ein Umweg um
		// einen Regler herum hebt ihn also auf, wie bei einer echten Bypass-Leitung.
		Map<BlockPos, Float> best = new HashMap<>();
		Map<PressureValveBlockEntity, Float> reached = new LinkedHashMap<>();
		if (pressure > 0f) {
			// Etwas weiter als Creates eigene Suche, damit kein Rohr am Rand ohne Grenze bleibt.
			int maxDistance = FluidPropagator.getPumpRange() + 1;
			BlockPos start = pump.getBlockPos().relative(side);
			ArrayDeque<Step> queue = new ArrayDeque<>();
			best.put(start, UNLIMITED);
			queue.add(new Step(start, UNLIMITED, 1));
			int steps = 0;
			while (!queue.isEmpty() && steps++ < MAX_STEPS) {
				Step step = queue.poll();
				Float known = best.get(step.pos);
				if (known == null || step.cap < known)
					continue;
				if (!level.isLoaded(step.pos))
					continue;
				BlockState state = level.getBlockState(step.pos);
				FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, step.pos);
				if (pipe == null)
					continue;

				float beyond = step.cap;
				if (level.getBlockEntity(step.pos) instanceof PressureValveBlockEntity valve) {
					reached.merge(valve, step.cap, Math::max);
					beyond = valve.capBeyond(step.cap);
				}
				if (step.distance >= maxDistance)
					continue;

				for (Direction d : FluidPropagator.getPipeConnections(state, pipe)) {
					BlockPos next = step.pos.relative(d);
					if (!level.isLoaded(next))
						continue;
					if (level.getBlockEntity(next) instanceof PumpBlockEntity)
						continue;
					if (FluidPropagator.getPipe(level, next) == null)
						continue;
					Float nextKnown = best.get(next);
					if (nextKnown != null && nextKnown >= beyond)
						continue;
					best.put(next, beyond);
					queue.add(new Step(next, beyond, step.distance + 1));
				}
			}
		}

		updateContributions(pump, key, side, pressure, reached, best, level);

		Map<BlockPos, Float> caps = new HashMap<>();
		for (Map.Entry<BlockPos, Float> entry : best.entrySet())
			if (entry.getValue() != UNLIMITED)
				caps.put(entry.getKey(), entry.getValue());
		return caps.isEmpty() ? Shape.NONE : new Shape(caps);
	}

	/**
	 * Traegt den Beitrag dieser Pumpenseite in jedes erreichte Ventil ein und loescht ihn bei denen, die sie
	 * nicht mehr erreicht. Ueberdruckventile, die jetzt ueber ihrem Ablassdruck liegen, begrenzen ihren Strang;
	 * das schreibt diese Methode direkt in best.
	 */
	private static void updateContributions(PumpBlockEntity pump, PumpSide key, Direction side, float pressure,
			Map<PressureValveBlockEntity, Float> reached, Map<BlockPos, Float> best, Level level) {
		Set<BlockPos> now = new HashSet<>();
		for (PressureValveBlockEntity valve : reached.keySet())
			now.add(valve.getBlockPos());

		EnumMap<Direction, Set<BlockPos>> bySide = REACHED.computeIfAbsent(pump, p -> new EnumMap<>(Direction.class));
		Set<BlockPos> before = bySide.getOrDefault(side, Collections.emptySet());
		for (BlockPos pos : before)
			if (!now.contains(pos) && level.getBlockEntity(pos) instanceof PressureValveBlockEntity gone)
				gone.removeContribution(key);
		if (now.isEmpty())
			bySide.remove(side);
		else
			bySide.put(side, now);

		for (Map.Entry<PressureValveBlockEntity, Float> entry : reached.entrySet()) {
			PressureValveBlockEntity valve = entry.getKey();
			float arriving = Math.min(pressure, entry.getValue());
			Set<BlockPos> zone = valve.getKind() == ValveKind.RELIEF ? zoneOf(level, valve.getBlockPos(), best) : null;
			valve.putContribution(key, arriving, zone);
		}

		for (PressureValveBlockEntity valve : reached.keySet()) {
			if (valve.getKind() != ValveKind.RELIEF || !valve.isOverSetpoint())
				continue;
			float cap = valve.setpointUnits();
			Set<BlockPos> zone = valve.zoneFor(key);
			if (zone == null)
				continue;
			for (BlockPos pos : zone)
				best.computeIfPresent(pos, (p, c) -> Math.min(c, cap));
		}
	}

	/**
	 * Der Strang eines Ueberdruckventils innerhalb des Bereichs dieser Pumpe: alles, was ohne einen Regler oder
	 * ein geschlossenes Absperrventil zu passieren erreichbar ist. Diese Grenzen gehoeren nicht dazu; dahinter
	 * gilt deren eigener Wert.
	 */
	private static Set<BlockPos> zoneOf(Level level, BlockPos relief, Map<BlockPos, Float> area) {
		Set<BlockPos> zone = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		zone.add(relief);
		queue.add(relief);
		int steps = 0;
		while (!queue.isEmpty() && steps++ < MAX_STEPS) {
			BlockPos pos = queue.poll();
			BlockState state = level.getBlockState(pos);
			FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
			if (pipe == null)
				continue;
			for (Direction d : FluidPropagator.getPipeConnections(state, pipe)) {
				BlockPos next = pos.relative(d);
				if (!area.containsKey(next) || zone.contains(next))
					continue;
				if (level.getBlockEntity(next) instanceof PressureValveBlockEntity valve && valve.boundsZone())
					continue;
				zone.add(next);
				queue.add(next);
			}
		}
		return zone;
	}

	/** Fuer die Pruefung, ob ein eingetragener Beitrag noch von einer laufenden Pumpe stammt. */
	@Nullable
	static PumpBlockEntity pumpAt(Level level, PumpSide key) {
		if (!level.isLoaded(key.pos()))
			return null;
		return level.getBlockEntity(key.pos()) instanceof PumpBlockEntity pump ? pump : null;
	}
}
