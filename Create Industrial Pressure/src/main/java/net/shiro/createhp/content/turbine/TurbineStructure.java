package net.shiro.createhp.content.turbine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.pressure.PipeMaterial;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Aufbau einer Dampfturbine, vom Abtrieb aus gesehen.
 *
 * Der Abtrieb sitzt in der Mitte der 5x5-Vorderwand; seine Welle zeigt nach vorn (FACING). Dahinter folgen eine
 * oder mehr Rotorlagen (3x3 Rotor, ringsum 16 Mantelbloecke) und zum Schluss eine volle 5x5-Rueckwand.
 * Einlaesse und Kondensatauslaesse duerfen jeden Gehaeuseblock in Mantel und Waenden ersetzen. Alle Teile mit
 * Werkstoff muessen denselben haben; er bestimmt Leistung und Druckgrenze der ganzen Turbine.
 *
 * Gesucht wird nur von einem Abtrieb aus, darum hat jede Turbine genau einen: Ein zweiter Abtrieb in einer Wand
 * ist kein Gehaeuseteil und macht die Turbine unvollstaendig.
 */
public final class TurbineStructure {
	private TurbineStructure() {}

	/** Hoechstzahl der Rotorlagen. Laenger wird die Suche teurer, und eine Kupferturbine schafft dann schon 1600 mB/t. */
	public static final int MAX_LENGTH = 16;

	private static final int HALF = 2;

	/**
	 * Ergebnis einer Pruefung. Ist die Turbine nicht vollstaendig, nennt problem den ersten Block, der fehlt oder
	 * nicht passt, damit der Spieler ihn mit der Brille findet.
	 */
	public record Result(boolean formed, @Nullable PipeMaterial material, int length, List<BlockPos> inlets,
			List<BlockPos> condensateOutlets, @Nullable BlockPos problem, boolean tooLong) {

		static Result broken(@Nullable PipeMaterial material, BlockPos problem) {
			return new Result(false, material, 0, List.of(), List.of(), problem, false);
		}

		/** Mehr Rotorlagen als erlaubt; problem ist die erste zu viel. */
		static Result tooLong(@Nullable PipeMaterial material, BlockPos problem) {
			return new Result(false, material, 0, List.of(), List.of(), problem, true);
		}
	}

	public static Result scan(Level level, BlockPos output, Direction facing) {
		Direction back = facing.getOpposite();
		Direction[] across = across(facing.getAxis());
		Scan scan = new Scan(level);

		// Vorderwand: alles ausser dem Abtrieb selbst.
		for (int i = -HALF; i <= HALF; i++)
			for (int j = -HALF; j <= HALF; j++)
				if ((i != 0 || j != 0) && !scan.shell(at(output, back, across, 0, i, j)))
					return Result.broken(scan.material, scan.problem);

		int length = 0;
		for (int depth = 1; depth <= MAX_LENGTH + 1; depth++) {
			// Die Mitte entscheidet, was die Lage sein soll; der erste Block, der nicht dazu passt, ist das Problem.
			boolean rotorSlice = scan.isRotor(at(output, back, across, depth, 0, 0));
			if (rotorSlice && depth > MAX_LENGTH)
				return Result.tooLong(scan.material, at(output, back, across, depth, 0, 0));
			for (int i = -HALF; i <= HALF; i++) {
				for (int j = -HALF; j <= HALF; j++) {
					BlockPos pos = at(output, back, across, depth, i, j);
					boolean inner = Math.abs(i) < HALF && Math.abs(j) < HALF;
					boolean ok = rotorSlice && inner ? scan.rotor(pos) : scan.shell(pos);
					if (!ok)
						return Result.broken(scan.material, scan.problem);
				}
			}
			if (!rotorSlice) {
				// Rueckwand. Ohne eine einzige Rotorlage dazwischen ist das keine Turbine.
				if (length == 0)
					return Result.broken(scan.material, at(output, back, across, depth, 0, 0));
				return new Result(true, scan.material, length, scan.inlets, scan.condensate, null, false);
			}
			length++;
		}
		return Result.broken(scan.material, at(output, back, across, MAX_LENGTH + 1, 0, 0));
	}

	private static BlockPos at(BlockPos output, Direction back, Direction[] across, int depth, int i, int j) {
		return output.relative(back, depth).relative(across[0], i).relative(across[1], j);
	}

	/** Zwei Richtungen quer zur Achse, immer die positiven, damit die Suche nicht von der Blickrichtung abhaengt. */
	private static Direction[] across(Direction.Axis axis) {
		return switch (axis) {
			case X -> new Direction[] { Direction.UP, Direction.SOUTH };
			case Y -> new Direction[] { Direction.EAST, Direction.SOUTH };
			case Z -> new Direction[] { Direction.EAST, Direction.UP };
		};
	}

	/** Zustand einer laufenden Pruefung: Werkstoff, gefundene Anschluesse und der erste fehlerhafte Block. */
	private static final class Scan {
		final Level level;
		@Nullable PipeMaterial material;
		@Nullable BlockPos problem;
		final List<BlockPos> inlets = new ArrayList<>();
		final List<BlockPos> condensate = new ArrayList<>();

		Scan(Level level) {
			this.level = level;
		}

		@Nullable
		TurbinePart part(BlockPos pos) {
			// Ein entladener Teil zaehlt als fehlend; getBlockState wuerde den Chunk sonst nachladen.
			if (!level.isLoaded(pos))
				return null;
			BlockState state = level.getBlockState(pos);
			return state.getBlock() instanceof TurbinePart part ? part : null;
		}

		boolean isRotor(BlockPos pos) {
			TurbinePart part = part(pos);
			return part != null && part.getTurbineRole() == TurbinePart.Role.ROTOR;
		}

		boolean rotor(BlockPos pos) {
			TurbinePart part = part(pos);
			return part != null && part.getTurbineRole() == TurbinePart.Role.ROTOR && matches(part) || fail(pos);
		}

		boolean shell(BlockPos pos) {
			TurbinePart part = part(pos);
			if (part == null || !part.getTurbineRole().isShell() || !matches(part))
				return fail(pos);
			if (part.getTurbineRole() == TurbinePart.Role.INLET)
				inlets.add(pos.immutable());
			else if (part.getTurbineRole() == TurbinePart.Role.CONDENSATE)
				condensate.add(pos.immutable());
			return true;
		}

		/** Das erste Teil mit Werkstoff legt ihn fest; jedes weitere muss ihn teilen. */
		boolean matches(TurbinePart part) {
			PipeMaterial own = part.getTurbineMaterial();
			if (own == null)
				return true;
			if (material == null)
				material = own;
			return material == own;
		}

		boolean fail(BlockPos pos) {
			problem = pos.immutable();
			return false;
		}
	}
}
