package net.shiro.createhp.content.pressure;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;

import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.CreateHP;
import net.shiro.createhp.content.valve.PressureValveBlockEntity;
import net.shiro.createhp.content.valve.ValveKind;

import java.util.List;

/** Druckzeilen fuer die Ingenieursbrille, im Stil von Creates eigenen Brillen-Tooltips. */
public final class PressureTooltip {
	private PressureTooltip() {}

	/** Ab diesem Anteil am Grenzwert wird der Wert gelb: Der Spieler soll vor dem Platzen gewarnt sein. */
	private static final float WARN_RATIO = 0.75f;

	public static boolean addPipe(List<Component> tooltip, FluidTransportBehaviour pipe, BlockState state) {
		return addPipe(tooltip, Pressure.barAt(pipe), state);
	}

	public static boolean addPipe(List<Component> tooltip, float bar, BlockState state) {
		return addPressure(tooltip, "gui.goggles.pipe_pressure", bar, state);
	}

	/** Tanks: wie ein Rohr, nur mit eigener Ueberschrift; der Druck ist der hoechste an einem Anschluss. */
	public static boolean addTank(List<Component> tooltip, float bar, BlockState state) {
		return addPressure(tooltip, "gui.goggles.tank_pressure", bar, state);
	}

	/** Dampfeinlass der Turbine: Druck der Rohre, die in ihn speisen, gegen seinen Grenzwert. */
	public static boolean addInlet(List<Component> tooltip, float bar, BlockState state) {
		return addPressure(tooltip, "gui.goggles.inlet_pressure", bar, state);
	}

	private static boolean addPressure(List<Component> tooltip, String header, float bar, BlockState state) {
		float rating = Pressure.ratingBar(state);
		lang().translate(header).forGoggles(tooltip);

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

	/**
	 * Ventile: Druck und Grenzwert wie ein Rohr, dazu Zustand und Sollwert. Regeln und Ablassen kommen aus dem
	 * Blockzustand (ENABLED), damit die Brille dasselbe sagt wie die Anzeige am Ventil.
	 */
	public static boolean addValve(List<Component> tooltip, PressureValveBlockEntity valve, FluidTransportBehaviour pipe) {
		addPipe(tooltip, valve.displayBar(pipe), valve.getBlockState());
		ValveKind kind = valve.getKind();
		boolean active = valve.isOpen();
		switch (kind) {
			case SHUTOFF -> lang().translate(active ? "gui.goggles.valve.open" : "gui.goggles.valve.closed")
					.style(active ? ChatFormatting.GREEN : ChatFormatting.RED)
					.forGoggles(tooltip, 1);
			case REGULATOR -> {
				lang().translate("gui.goggles.valve.setpoint", format(valve.setpointBar()))
						.style(ChatFormatting.GRAY)
						.forGoggles(tooltip, 1);
				// Der Rohrdruck oben ist der Eingang; interessant ist, was hinten herauskommt.
				float outlet = Math.min(valve.demandUnits() / Pressure.UNITS_PER_BAR, valve.setpointBar());
				lang().translate("gui.goggles.valve.outlet", format(outlet))
						.style(ChatFormatting.GRAY)
						.forGoggles(tooltip, 1);
				if (active)
					lang().translate("gui.goggles.valve.regulating")
							.style(ChatFormatting.GOLD)
							.forGoggles(tooltip, 1);
			}
			case RELIEF -> {
				lang().translate("gui.goggles.valve.opens_at", format(valve.setpointBar()))
						.style(ChatFormatting.GRAY)
						.forGoggles(tooltip, 1);
				if (active) {
					float excess = Math.max(0f, valve.demandUnits() / Pressure.UNITS_PER_BAR - valve.setpointBar());
					lang().translate("gui.goggles.valve.venting", format(excess))
							.style(ChatFormatting.GOLD)
							.forGoggles(tooltip, 1);
				}
			}
		}
		if (valve.isSmart())
			lang().translate(kind == ValveKind.RELIEF ? "gui.goggles.valve.smart_signal" : "gui.goggles.valve.smart_redstone")
					.style(ChatFormatting.DARK_GRAY)
					.forGoggles(tooltip, 1);
		return true;
	}

	/** Ganze bar reichen; Nachkommastellen nur unter 10 bar, wo sie einen Unterschied machen. */
	public static String format(float bar) {
		if (Float.isInfinite(bar))
			return "-";
		return bar < 10f ? String.format(java.util.Locale.ROOT, "%.1f", bar) : Integer.toString(Math.round(bar));
	}

	/**
	 * Ein neuer Builder je Zeile: LangBuilder sammelt alles, was man ihm uebergibt. Mit einem gemeinsamen Builder
	 * haengt jede Zeile die vorigen noch einmal vor sich.
	 */
	public static LangBuilder lang() {
		return new LangBuilder(CreateHP.ID);
	}
}
