package net.shiro.createhp.content;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.pressure.PressureTooltip;

import java.util.List;

/** Wie {@link PressurePipeBlockEntity}, fuer die Glasvariante. */
public class PressureGlassPipeBlockEntity extends StraightPipeBlockEntity implements IHaveGoggleInformation {

	public PressureGlassPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		return PressureTooltip.addPipe(tooltip, getBehaviour(FluidTransportBehaviour.TYPE), getBlockState());
	}
}
