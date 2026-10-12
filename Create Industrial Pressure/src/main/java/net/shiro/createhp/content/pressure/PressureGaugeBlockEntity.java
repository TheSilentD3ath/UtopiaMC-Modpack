package net.shiro.createhp.content.pressure;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Ein gerades Rohr (Creates StraightPipeBlockEntity), das zusaetzlich seinen Druck an Komparatoren meldet. */
public class PressureGaugeBlockEntity extends StraightPipeBlockEntity implements IHaveGoggleInformation {

	/** Der Druck aendert sich nur, wenn eine Pumpe neu verteilt; alle vier Ticks reicht. */
	private static final int MEASURE_TICKS = 4;

	private int comparator;

	public PressureGaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide || level.getGameTime() % MEASURE_TICKS != 0)
			return;
		int now = signalFor(Pressure.barAt(getBehaviour(FluidTransportBehaviour.TYPE)));
		if (now != comparator) {
			comparator = now;
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
		}
	}

	/** 0 ohne Druck, sonst 1 bis 15 nach Druck relativ zur Grenze; 15 heisst Grenze erreicht. */
	private int signalFor(float bar) {
		if (bar <= 0f)
			return 0;
		float rating = Pressure.ratingBar(getBlockState());
		return Mth.clamp(Mth.floor(15f * bar / rating), 1, 15);
	}

	public int getComparatorSignal() {
		return comparator;
	}

	// Gespeichert, damit ein Komparator nach dem Laden nicht kurz auf 0 faellt, bis die erste Messung laeuft.
	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putInt("Comparator", comparator);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		comparator = tag.getInt("Comparator");
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		float bar = Pressure.barAt(getBehaviour(FluidTransportBehaviour.TYPE));
		PressureTooltip.addPipe(tooltip, bar, getBlockState());
		// Auf dem Client aus dem angezeigten Druck berechnet; derselbe Wert, den der Server ausgibt.
		PressureTooltip.lang().translate("gui.goggles.gauge_signal", signalFor(bar))
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
		return true;
	}
}
