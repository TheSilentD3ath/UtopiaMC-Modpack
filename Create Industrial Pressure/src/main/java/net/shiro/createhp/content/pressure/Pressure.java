package net.shiro.createhp.content.pressure;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;

import net.createmod.catnip.data.Couple;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.PressureGlassPipeBlock;
import net.shiro.createhp.content.PressurePipeBlock;

import org.jetbrains.annotations.Nullable;

/**
 * Druck in bar und Grenzwerte der Rohre.
 *
 * Create speichert an jedem Rohranschluss zwei Druckwerte (eingehend, ausgehend), die die Pumpen beim
 * Netzaufbau setzen. Wir lesen nur diese Werte; ein eigenes Druckmodell gibt es nicht. Dadurch stimmt die
 * Anzeige immer mit dem ueberein, was Create fuer den Durchfluss tatsaechlich verwendet.
 */
public final class Pressure {
	private Pressure() {}

	/**
	 * Create-Druckeinheiten pro bar. Creates Pumpe gibt bei 256 RPM 256 Einheiten ab; das sind hier 16 bar,
	 * die Hochdruckpumpen bei voller Drehzahl 64 bis 1024 bar.
	 */
	public static final float UNITS_PER_BAR = 16f;

	/** Grenzwert fuer Creates eigene Rohre: genau das, was Creates Pumpe allein erzeugen kann. */
	public static final float CREATE_PIPE_RATING_BAR = 16f;

	/** Kein Grenzwert: Pumpen, Tanks und Bloecke anderer Mods platzen nie durch uns. */
	public static final float UNRATED = Float.POSITIVE_INFINITY;

	/** Hoechster Druck an einem der Anschluesse, in bar. Mehrere Pumpen in einer Leitung addieren sich. */
	public static float barAt(@Nullable FluidTransportBehaviour pipe) {
		if (pipe == null || pipe.interfaces == null)
			return 0f;
		float max = 0f;
		for (PipeConnection connection : pipe.interfaces.values()) {
			if (connection == null)
				continue;
			Couple<Float> pressure = connection.getPressure();
			max = Math.max(max, Math.max(pressure.getFirst(), pressure.getSecond()));
		}
		return max / UNITS_PER_BAR;
	}

	/** Grenzwert in bar, oder {@link #UNRATED}. */
	public static float ratingBar(BlockState state) {
		Block block = state.getBlock();
		// Unsere Rohre zuerst: sie erben von Creates Rohrklassen und wuerden sonst als Create-Rohr gelten.
		PipeMaterial material = materialOf(block);
		if (material != null)
			return material.ratingBar;
		if (block instanceof FluidPipeBlock || block instanceof AxisPipeBlock || block instanceof EncasedPipeBlock
				|| block instanceof SmartFluidPipeBlock || block instanceof FluidValveBlock)
			return CREATE_PIPE_RATING_BAR;
		return UNRATED;
	}

	@Nullable
	public static PipeMaterial materialOf(Block block) {
		if (block instanceof PressurePipeBlock pipe)
			return pipe.getMaterial();
		if (block instanceof PressureGlassPipeBlock glass)
			return glass.getMaterial();
		return null;
	}

	/** Etwas Spielraum gegen Rundungsfehler: Ein Netz genau am Grenzwert soll nicht platzen. */
	public static boolean isOverloaded(float bar, float ratingBar) {
		return bar > ratingBar + 0.01f;
	}
}
