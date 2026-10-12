package net.shiro.createhp.content.valve;

import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import io.github.fabricators_of_create.porting_lib.transfer.TransferUtil;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Was Regler und Ueberdruckventile am Durchsatz eines Create-Fluessigkeitsnetzes aendern.
 *
 * Create rechnet den Durchsatz eines Netzes einmal aus dem Druck an seinem Anfang (der Quelle) aus. Liegt der
 * Regler hinter der Pumpe, sieht die Quelle davon nichts; deshalb kappt FluidNetworkMixin den Durchsatz jedes
 * Netzes, das durch einen Regler oder den Strang eines ablassenden Ueberdruckventils fliesst. Das Ablassen
 * selbst entnimmt der Quelle zusaetzlich den Ueberschuss: Die Fluessigkeit geht als Dampfwolke verloren.
 */
public final class ValveFlow {
	private ValveFlow() {}

	/** Ein ablassendes Ueberdruckventil und der Anteil dieses Netzes an dem, was dort ankommt. */
	public record Vent(PressureValveBlockEntity relief, float share) {}

	/** Durchsatzgrenze in Tropfen pro Tick und die Ueberdruckventile, die dieses Netz speist. */
	public record Effect(long cap, List<Vent> vents) {
		public static final Effect NONE = new Effect(Long.MAX_VALUE, List.of());
	}

	/**
	 * Teuer genug fuer jeden Tick; FluidNetworkMixin ruft es nur alle 20 Ticks.
	 *
	 * Create bildet fuer jede Quelle ein eigenes Netz, bei zwei Pumpen vor einem Regler also zwei. Jedes bekommt
	 * darum nur seinen Anteil an der Grenze, nach dem Druck, den seine Pumpe beitraegt; sonst kaeme hinter dem
	 * Regler das Doppelte an.
	 */
	public static Effect evaluate(Level level, BlockPos start, Set<BlockPos> visited) {
		long cap = Long.MAX_VALUE;
		for (BlockPos pos : visited) {
			// Ohne isLoaded wuerde getBlockEntity einen entladenen Chunk synchron nachladen.
			if (!level.isLoaded(pos))
				continue;
			if (level.getBlockEntity(pos) instanceof PressureValveBlockEntity valve
					&& valve.getKind() == ValveKind.REGULATOR)
				cap = Math.min(cap, valve.flowCapDroplets(valve.shareOf(start, visited)));
		}

		List<Vent> vents = new ArrayList<>();
		for (PressureValveBlockEntity valve : PressureValveBlockEntity.venting()) {
			if (valve.isRemoved() || valve.getLevel() != level || !valve.zoneTouches(visited))
				continue;
			float share = valve.shareOf(start, visited);
			vents.add(new Vent(valve, share));
			cap = Math.min(cap, valve.flowCapDroplets(share));
		}

		if (cap == Long.MAX_VALUE && vents.isEmpty())
			return Effect.NONE;
		return new Effect(cap, vents);
	}

	/** Laesst den Anteil dieses Netzes am Ueberschuss aller seiner Ueberdruckventile aus seiner Quelle ab. */
	public static void vent(Level level, @Nullable Storage<FluidVariant> source, FluidStack fluid, Effect effect) {
		if (effect.vents().isEmpty() || source == null || fluid.isEmpty())
			return;
		long amount = 0;
		long now = level.getGameTime();
		for (Vent vent : effect.vents())
			if (!vent.relief().isRemoved())
				amount += vent.relief().claimVent(now, vent.share());
		if (amount <= 0)
			return;
		try (Transaction t = TransferUtil.getTransaction()) {
			source.extract(fluid.getType(), amount, t);
			t.commit();
		}
	}
}
