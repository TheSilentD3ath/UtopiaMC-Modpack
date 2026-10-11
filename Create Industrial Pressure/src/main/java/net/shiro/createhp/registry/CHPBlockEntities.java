package net.shiro.createhp.registry;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.fluids.pipes.GlassPipeVisual;
import com.simibubi.create.content.fluids.pipes.TransparentStraightPipeRenderer;
import com.simibubi.create.content.fluids.pump.PumpRenderer;
import com.simibubi.create.content.fluids.tank.FluidTankRenderer;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import net.shiro.createhp.CreateHP;
import net.shiro.createhp.client.PressureValveRenderer;
import net.shiro.createhp.client.TurbineOutputRenderer;
import net.shiro.createhp.content.HighPressurePumpBlockEntity;
import net.shiro.createhp.content.PressureGlassPipeBlockEntity;
import net.shiro.createhp.content.PressurePipeBlockEntity;
import net.shiro.createhp.content.steam.SteamOutletBlockEntity;
import net.shiro.createhp.content.tank.PressureTankBlockEntity;
import net.shiro.createhp.content.turbine.CondensateOutletBlockEntity;
import net.shiro.createhp.content.turbine.SteamInletBlockEntity;
import net.shiro.createhp.content.turbine.TurbineOutputBlockEntity;
import net.shiro.createhp.content.valve.PressureValveBlockEntity;

public class CHPBlockEntities {
	private static final CreateRegistrate REGISTRATE = CreateHP.REGISTRATE;

	/** Create's FluidPipeBlockEntity plus a goggle readout of the pressure; joins the fluid network as is. */
	public static final BlockEntityEntry<PressurePipeBlockEntity> HIGH_PRESSURE_PIPE =
			REGISTRATE.blockEntity("high_pressure_pipe", PressurePipeBlockEntity::new)
					.validBlock(CHPBlocks.HIGH_PRESSURE_PIPE)
					.register();

	/** Glass (see-through) HP pipe BE — reuses Create's StraightPipeBlockEntity, with Create's own
	 *  glass-pipe visual + renderer so the fluid shows flowing inside. */
	public static final BlockEntityEntry<PressureGlassPipeBlockEntity> HIGH_PRESSURE_GLASS_PIPE =
			REGISTRATE.blockEntity("high_pressure_glass_pipe", PressureGlassPipeBlockEntity::new)
					.visual(() -> GlassPipeVisual::new, false)   // shows fluid flowing inside the glass
					.validBlock(CHPBlocks.HIGH_PRESSURE_GLASS_PIPE)
					.renderer(() -> TransparentStraightPipeRenderer::new)
					.register();

	/** Netherite solid pipe BE — same Create FluidPipeBlockEntity, joins the fluid network. */
	public static final BlockEntityEntry<PressurePipeBlockEntity> NETHERITE_PRESSURE_PIPE =
			REGISTRATE.blockEntity("netherite_pressure_pipe", PressurePipeBlockEntity::new)
					.validBlock(CHPBlocks.NETHERITE_PRESSURE_PIPE)
					.register();

	/** Netherite glass pipe BE — Create's StraightPipeBlockEntity + glass visual/renderer. */
	public static final BlockEntityEntry<PressureGlassPipeBlockEntity> NETHERITE_GLASS_PIPE =
			REGISTRATE.blockEntity("netherite_glass_pipe", PressureGlassPipeBlockEntity::new)
					.visual(() -> GlassPipeVisual::new, false)
					.validBlock(CHPBlocks.NETHERITE_GLASS_PIPE)
					.renderer(() -> TransparentStraightPipeRenderer::new)
					.register();

	/** Our pump BE (extends Create's PumpBlockEntity), valid for all five tier blocks. */
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

	/** Eine Block-Entity fuer alle sechs Ventile; Art und Material liest sie aus ihrem Block. */
	public static final BlockEntityEntry<PressureValveBlockEntity> PRESSURE_VALVE =
			REGISTRATE.blockEntity("pressure_valve", PressureValveBlockEntity::new)
					.validBlocks(CHPBlocks.PRESSURE_VALVE, CHPBlocks.NETHERITE_PRESSURE_VALVE,
							CHPBlocks.PRESSURE_REGULATOR, CHPBlocks.NETHERITE_PRESSURE_REGULATOR,
							CHPBlocks.RELIEF_VALVE, CHPBlocks.NETHERITE_RELIEF_VALVE)
					.renderer(() -> PressureValveRenderer::new)
					.register();

	/** Je Werkstoff ein Typ: Create verbindet nur Tanks desselben Typs zu einem Multiblock. */
	public static final BlockEntityEntry<PressureTankBlockEntity> PRESSURE_TANK =
			REGISTRATE.blockEntity("pressure_tank", PressureTankBlockEntity::new)
					.validBlock(CHPBlocks.PRESSURE_TANK)
					.renderer(() -> FluidTankRenderer::new)
					.register();

	public static final BlockEntityEntry<PressureTankBlockEntity> NETHERITE_PRESSURE_TANK =
			REGISTRATE.blockEntity("netherite_pressure_tank", PressureTankBlockEntity::new)
					.validBlock(CHPBlocks.NETHERITE_PRESSURE_TANK)
					.renderer(() -> FluidTankRenderer::new)
					.register();

	public static final BlockEntityEntry<SteamOutletBlockEntity> STEAM_OUTLET =
			REGISTRATE.blockEntity("steam_outlet", SteamOutletBlockEntity::new)
					.validBlock(CHPBlocks.STEAM_OUTLET)
					.register();

	public static final BlockEntityEntry<SteamInletBlockEntity> STEAM_INLET =
			REGISTRATE.blockEntity("steam_inlet", SteamInletBlockEntity::new)
					.validBlocks(CHPBlocks.STEAM_INLET, CHPBlocks.NETHERITE_STEAM_INLET)
					.register();

	public static final BlockEntityEntry<CondensateOutletBlockEntity> CONDENSATE_OUTLET =
			REGISTRATE.blockEntity("condensate_outlet", CondensateOutletBlockEntity::new)
					.validBlock(CHPBlocks.CONDENSATE_OUTLET)
					.register();

	/** Halbe Welle zur Abtriebsseite wie bei Creates Motor, mit Flywheel und ohne. */
	public static final BlockEntityEntry<TurbineOutputBlockEntity> TURBINE_OUTPUT =
			REGISTRATE.blockEntity("turbine_output", TurbineOutputBlockEntity::new)
					.visual(() -> OrientedRotatingVisual.of(AllPartialModels.SHAFT_HALF), false)
					.validBlock(CHPBlocks.TURBINE_OUTPUT)
					.renderer(() -> TurbineOutputRenderer::new)
					.register();

	public static void register() {}
}
