package net.shiro.createhp.content.pressure;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;

import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.CreateHP;

import java.util.List;

/** Druckzeilen fuer die Ingenieursbrille, im Stil von Creates eigenen Brillen-Tooltips. */
public final class PressureTooltip {
	private PressureTooltip() {}

	/** Ab diesem Anteil am Grenzwert wird der Wert gelb: Der Spieler soll vor dem Platzen gewarnt sein. */
	private static final float WARN_RATIO = 0.75f;

	public static boolean addPipe(List<Component> tooltip, FluidTransportBehaviour pipe, BlockState state) {
		float bar = Pressure.barAt(pipe);
		float rating = Pressure.ratingBar(state);
		lang().translate("gui.goggles.pipe_pressure").forGoggles(tooltip);

		ChatFormatting color = bar > rating ? ChatFormatting.RED
				: bar >= rating * WARN_RATIO ? ChatFormatting.GOLD : ChatFormatting.AQUA;
		lang().translate("gui.goggles.pressure_of_limit", format(bar), format(rating))
				.style(color)
				.forGoggles(tooltip, 1);

		if (Pressure.isOverloaded(bar, rating)) {
			PipeMaterial material = Pressure.materialOf(state.getBlock());
			boolean explodes = material != null && material.explodes;
			lang().translate(explodes ? "gui.goggles.overload_explodes" : "gui.goggles.overload_bursts")
					.style(ChatFormatting.RED)
					.forGoggles(tooltip, 1);
		}
		return true;
	}

	/** Pumpen haben keinen Grenzwert, nur den Druck, den sie abgeben. */
	public static void addPump(List<Component> tooltip, FluidTransportBehaviour pump) {
		lang().translate("gui.goggles.pump_pressure").forGoggles(tooltip);
		lang().translate("gui.goggles.pressure_value", format(Pressure.barAt(pump)))
				.style(ChatFormatting.AQUA)
				.forGoggles(tooltip, 1);
	}

	/** Ganze bar reichen; Nachkommastellen nur unter 10 bar, wo sie einen Unterschied machen. */
	public static String format(float bar) {
		if (Float.isInfinite(bar))
			return "-";
		return bar < 10f ? String.format(java.util.Locale.ROOT, "%.1f", bar) : Integer.toString(Math.round(bar));
	}

	private static LangBuilder lang() {
		return new LangBuilder(CreateHP.ID);
	}
}
