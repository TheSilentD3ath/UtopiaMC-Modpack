package net.shiro.createhp.content.tank;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.PercentOrProgressBarDisplaySource;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.shiro.createhp.CreateHP;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Display-Link-Quelle fuer den Fuellstand eines Tanks, direkt am Tank (Create braucht dafuer sonst einen
 * schlauen Beobachter). Modi: 0 Balken, 1 Prozent, 2 Inhalt in Eimern, 3 Fassungsvermoegen in Eimern.
 */
public class TankFillDisplaySource extends PercentOrProgressBarDisplaySource {

	private static final String KEY = "tank_fill";

	@Nullable
	@Override
	protected Float getProgress(DisplayLinkContext context) {
		BlockEntity be = context.getSourceBlockEntity();
		if (!(be instanceof FluidTankBlockEntity part))
			return null;
		FluidTankBlockEntity tank = part.getControllerBE();
		if (tank == null)
			return null;
		long amount = tank.getTankInventory().getFluidAmount();
		long capacity = tank.getTankInventory().getCapacity();
		return switch (getMode(context)) {
			case 0, 1 -> capacity <= 0 ? 0f : (float) amount / capacity;
			case 2 -> (float) amount / FluidConstants.BUCKET;
			case 3 -> (float) capacity / FluidConstants.BUCKET;
			default -> 0f;
		};
	}

	@Override
	protected MutableComponent formatNumeric(DisplayLinkContext context, Float value) {
		if (getMode(context) == 1)
			return super.formatNumeric(context, value);
		String number = value < 10f ? String.format(Locale.ROOT, "%.1f", value) : Integer.toString(Math.round(value));
		return Component.literal(number + " ").append(Component.translatable(CreateHP.ID + ".generic.unit.buckets"));
	}

	private int getMode(DisplayLinkContext context) {
		return context.sourceConfig().getInt("Mode");
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}

	@Override
	protected boolean progressBarActive(DisplayLinkContext context) {
		return getMode(context) == 0;
	}

	@Override
	@Environment(EnvType.CLIENT)
	public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
		super.initConfigurationWidgets(context, builder, isFirstLine);
		if (isFirstLine)
			return;
		String prefix = CreateHP.ID + ".display_source." + KEY + ".";
		List<Component> options = List.of(
				Component.translatable(prefix + "progress_bar"),
				Component.translatable(prefix + "percent"),
				Component.translatable(prefix + "amount"),
				Component.translatable(prefix + "capacity"));
		builder.addSelectionScrollInput(0, 120,
				(si, l) -> si.forOptions(options).titled(Component.translatable(prefix + "display")),
				"Mode");
	}

	@Override
	protected String getTranslationKey() {
		return KEY;
	}
}
