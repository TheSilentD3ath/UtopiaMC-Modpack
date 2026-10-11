package net.shiro.createhp.content.turbine;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.pressure.Pressure;
import net.shiro.createhp.content.pressure.PressureTooltip;
import net.shiro.createhp.registry.CHPFluids;

import java.util.List;

/** Dampfeinlass: nimmt Dampf aus Rohren an jeder Seite an; die Turbine holt ihn hier ab. */
public class SteamInletBlockEntity extends TurbinePortBlockEntity {

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

	/** Fuer die Turbine: entnimmt bis zu max Tropfen Dampf. */
	public long drain(long max) {
		return buffer.take(max);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		PressureTooltip.addInlet(tooltip, Pressure.barFeeding(level, worldPosition), getBlockState());
		PressureTooltip.lang().translate("gui.goggles.inlet_buffer", getAmount() / 81, BUFFER / 81)
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
		PressureTooltip.lang().translate(isLinked() ? "gui.goggles.turbine_linked" : "gui.goggles.turbine_unlinked")
				.style(isLinked() ? ChatFormatting.GREEN : ChatFormatting.RED)
				.forGoggles(tooltip, 1);
		return true;
	}
}
