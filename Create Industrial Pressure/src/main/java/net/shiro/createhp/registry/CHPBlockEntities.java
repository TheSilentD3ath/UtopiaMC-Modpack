package net.shiro.createhp.registry;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.fluids.pipes.GlassPipeVisual;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import com.simibubi.create.content.fluids.pipes.TransparentStraightPipeRenderer;
import com.simibubi.create.content.fluids.pump.PumpRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import net.shiro.createhp.CreateHP;
import net.shiro.createhp.content.HighPressurePumpBlockEntity;

public class CHPBlockEntities {
	private static final CreateRegistrate REGISTRATE = CreateHP.REGISTRATE;

	/** Reuses Create's FluidPipeBlockEntity so our pipe joins the fluid network out of the box. */
	public static final BlockEntityEntry<FluidPipeBlockEntity> HIGH_PRESSURE_PIPE =
			REGISTRATE.blockEntity("high_pressure_pipe", FluidPipeBlockEntity::new)
					.validBlock(CHPBlocks.HIGH_PRESSURE_PIPE)
					.register();

	/** Glass (see-through) HP pipe BE — reuses Create's StraightPipeBlockEntity, with Create's own
	 *  glass-pipe visual + renderer so the fluid shows flowing inside. */
	public static final BlockEntityEntry<StraightPipeBlockEntity> HIGH_PRESSURE_GLASS_PIPE =
			REGISTRATE.blockEntity("high_pressure_glass_pipe", StraightPipeBlockEntity::new)
					.visual(() -> GlassPipeVisual::new, false)   // shows fluid flowing inside the glass
					.validBlock(CHPBlocks.HIGH_PRESSURE_GLASS_PIPE)
					.renderer(() -> TransparentStraightPipeRenderer::new)
					.register();

	/** Netherite solid pipe BE — same Create FluidPipeBlockEntity, joins the fluid network. */
	public static final BlockEntityEntry<FluidPipeBlockEntity> NETHERITE_PRESSURE_PIPE =
			REGISTRATE.blockEntity("netherite_pressure_pipe", FluidPipeBlockEntity::new)
					.validBlock(CHPBlocks.NETHERITE_PRESSURE_PIPE)
					.register();

	/** Netherite glass pipe BE — Create's StraightPipeBlockEntity + glass visual/renderer. */
	public static final BlockEntityEntry<StraightPipeBlockEntity> NETHERITE_GLASS_PIPE =
			REGISTRATE.blockEntity("netherite_glass_pipe", StraightPipeBlockEntity::new)
					.visual(() -> GlassPipeVisual::new, false)
					.validBlock(CHPBlocks.NETHERITE_GLASS_PIPE)
					.renderer(() -> TransparentStraightPipeRenderer::new)
					.register();

	/** Our pump BE (extends Create's PumpBlockEntity), valid for all three tier blocks. */
	public static final BlockEntityEntry<HighPressurePumpBlockEntity> HIGH_PRESSURE_PUMP =
			REGISTRATE.blockEntity("high_pressure_pump", HighPressurePumpBlockEntity::new)
					// Spinning cog (Flywheel visual) — re-enabled now that we compile against 6.0.8.1, so
					// ofZ matches runtime. Uses Create's copper cog model (so it won't be blue). Must come
					// before .validBlocks() (that returns the base builder without .visual()).
					.visual(() -> SingleAxisRotatingVisual.ofZ(AllPartialModels.MECHANICAL_PUMP_COG))
					.validBlocks(CHPBlocks.PUMP_T1, CHPBlocks.PUMP_T2, CHPBlocks.PUMP_T3,
							CHPBlocks.PUMP_T4, CHPBlocks.PUMP_T5)
					.renderer(() -> PumpRenderer::new)
					.register();

	public static void register() {}
}
