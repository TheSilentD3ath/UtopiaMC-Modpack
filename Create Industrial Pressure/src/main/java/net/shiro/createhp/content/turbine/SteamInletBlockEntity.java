package net.shiro.createhp.content.turbine;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.pressure.Pressure;
import net.shiro.createhp.content.pressure.PressureTooltip;
import net.shiro.createhp.registry.CHPFluids;

import java.util.List;

/**
 * Dampfeinlass: nimmt Dampf aus Rohren an jeder Seite an; die Turbine holt ihn hier ab.
 *
 * Er misst auch den Druck, mit dem der Dampf ankommt. Die Turbine braucht je nach Drehzahl einen bestimmten Druck;
 * ein Komparator am Einlass zeigt, wie viel davon da ist (15 = Bedarf erreicht). Damit lassen sich Pumpen und
 * Regler per Redstone nachfuehren.
 */
public class SteamInletBlockEntity extends TurbinePortBlockEntity {

	/** Druck und Komparatorwert aendern sich nur, wenn eine Pumpe neu verteilt; alle vier Ticks reicht. */
	private static final int MEASURE_TICKS = 4;

	private float pressureBar;
	/** Bedarf der Turbine in bar, vom Abtrieb gesetzt; 0, wenn sie aus ist. */
	private float requiredBar;
	private int comparator;

	public SteamInletBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected boolean acceptsFromPipes(FluidVariant variant) {
		return variant.getFluid().isSame(CHPFluids.STEAM.get());
	}

	@Override
	protected boolean givesToPipes() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide || level.getGameTime() % MEASURE_TICKS != 0)
			return;
		pressureBar = Pressure.barFeeding(level, worldPosition);
		int now = computeComparator();
		if (now != comparator) {
			comparator = now;
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
		}
	}

	/** Fuer die Turbine: entnimmt bis zu max Tropfen Dampf. */
	public long drain(long max) {
		return buffer.take(max);
	}

	/** Anteil der Leistung, den der Dampf dieses Einlasses bei diesem Bedarf bringt. */
	public float efficiency(float required) {
		if (required <= 0f)
			return 1f;
		return Mth.clamp(pressureBar / required, 0f, 1f);
	}

	public void setRequiredBar(float required) {
		if (required == requiredBar)
			return;
		requiredBar = required;
		comparator = computeComparator();
		if (level != null) {
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
			sendData();
		}
	}

	/**
	 * Druck relativ zum Bedarf: 15 heisst Bedarf erreicht, jeder Druck darunter mindestens 1, damit man "etwas,
	 * aber zu wenig" von "nichts" unterscheiden kann. Ohne laufende Turbine 0.
	 */
	private int computeComparator() {
		if (!isLinked() || requiredBar <= 0f || pressureBar <= 0f)
			return 0;
		return Mth.clamp(Mth.floor(15f * pressureBar / requiredBar), 1, 15);
	}

	public int getComparatorSignal() {
		return comparator;
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putInt("Comparator", comparator);
		if (clientPacket)
			tag.putFloat("Required", requiredBar);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		comparator = tag.getInt("Comparator");
		if (clientPacket)
			requiredBar = tag.getFloat("Required");
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		PressureTooltip.addInlet(tooltip, Pressure.barFeeding(level, worldPosition), getBlockState());
		if (isLinked() && requiredBar > 0f)
			PressureTooltip.lang().translate("gui.goggles.inlet_required", PressureTooltip.format(requiredBar))
					.style(ChatFormatting.GRAY)
					.forGoggles(tooltip, 1);
		PressureTooltip.lang().translate("gui.goggles.inlet_buffer", getAmount() / 81, BUFFER / 81)
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
		PressureTooltip.lang().translate(isLinked() ? "gui.goggles.turbine_linked" : "gui.goggles.turbine_unlinked")
				.style(isLinked() ? ChatFormatting.GREEN : ChatFormatting.RED)
				.forGoggles(tooltip, 1);
		return true;
	}
}
