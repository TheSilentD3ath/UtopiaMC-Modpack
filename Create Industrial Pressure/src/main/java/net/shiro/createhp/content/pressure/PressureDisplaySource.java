package net.shiro.createhp.content.pressure;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.PercentOrProgressBarDisplaySource;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.shiro.createhp.CreateHP;
import net.shiro.createhp.content.valve.PressureValveBlockEntity;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Display-Link-Quelle fuer den Druck an einem Rohr, Ventil oder einer Pumpe. Aufgebaut wie Creates
 * Stress-Anzeige, damit sie sich am Display genauso bedienen laesst: Balken, Prozent oder Zahlen.
 *
 * Modi: 0 Auslastung als Balken, 1 Auslastung in Prozent, 2 aktueller Druck, 3 Grenzwert.
 * Bei Bloecken ohne Grenzwert (Pumpen) zeigen 0 und 1 nichts Sinnvolles; dort ist Modus 2 gemeint.
 */
public class PressureDisplaySource extends PercentOrProgressBarDisplaySource {

	private static final String KEY = "pipe_pressure";

	@Override
	protected MutableComponent formatNumeric(DisplayLinkContext context, Float value) {
		int mode = getMode(context);
		if (mode == 1)
			return super.formatNumeric(context, value);
		return Component.literal(PressureTooltip.format(value) + " ")
				.append(Component.translatable(CreateHP.ID + ".generic.unit.bar"));
	}

	@Nullable
	@Override
	protected Float getProgress(DisplayLinkContext context) {
		BlockEntity be = context.getSourceBlockEntity();
		if (be == null)
			return null;
		FluidTransportBehaviour pipe = BlockEntityBehaviour.get(be, FluidTransportBehaviour.TYPE);
		if (pipe == null)
			return null;
		float bar = be instanceof PressureValveBlockEntity valve ? valve.displayBar(pipe) : Pressure.barAt(pipe);
		float rating = Pressure.ratingBar(be.getBlockState());
		return switch (getMode(context)) {
			case 0, 1 -> Float.isInfinite(rating) ? 0f : bar / rating;
			case 2 -> bar;
			case 3 -> Float.isInfinite(rating) ? null : rating;
			default -> 0f;
		};
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
				Component.translatable(prefix + "current"),
				Component.translatable(prefix + "limit"));
		builder.addSelectionScrollInput(0, 120,
				(si, l) -> si.forOptions(options).titled(Component.translatable(prefix + "display")),
				"Mode");
	}

	@Override
	protected String getTranslationKey() {
		return KEY;
	}
}
