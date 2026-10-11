package net.shiro.createhp.content.turbine;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.shiro.createhp.content.pressure.PressureTooltip;

import java.util.List;

/** Kondensatauslass: die Turbine legt hier Wasser ab, Pumpen ziehen es an jeder Seite heraus. */
public class CondensateOutletBlockEntity extends TurbinePortBlockEntity {

	private static final FluidVariant WATER = FluidVariant.of(Fluids.WATER);

	public CondensateOutletBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected boolean acceptsFromPipes(FluidVariant variant) {
		return false;
	}

	@Override
	protected boolean givesToPipes() {
		return true;
	}

	/** Fuer die Turbine: legt bis zu max Tropfen Wasser ab und sagt, wie viel Platz war. */
	public long fill(long max) {
		return buffer.put(WATER, max);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		PressureTooltip.lang().translate("gui.goggles.condensate_outlet").forGoggles(tooltip);
		PressureTooltip.lang().translate("gui.goggles.condensate_buffer", getAmount() / 81, BUFFER / 81)
				.style(ChatFormatting.AQUA)
				.forGoggles(tooltip, 1);
		PressureTooltip.lang().translate(isLinked() ? "gui.goggles.turbine_linked" : "gui.goggles.turbine_unlinked")
				.style(isLinked() ? ChatFormatting.GREEN : ChatFormatting.RED)
				.forGoggles(tooltip, 1);
		return true;
	}
}
