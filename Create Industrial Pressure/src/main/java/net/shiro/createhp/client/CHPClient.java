package net.shiro.createhp.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.renderer.RenderType;
import net.shiro.createhp.config.CHPConfig;
import net.shiro.createhp.registry.CHPBlocks;

/**
 * Client-side setup. The fluid pipe model (Create's geometry) has transparent regions, so the block
 * must render on the CUTOUT layer; on the default SOLID layer those transparent areas render as
 * opaque/garbled, which is the "broken pipe" look. Pumps are set to cutout too to match Create.
 */
public class CHPClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register our blue pipe-fitting partial models for baking (must happen before model bake).
		CHPPartialModels.init();
		CHPConfig.registerClient();
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> PressureWarningScreen.openOver(client, screen));

		// Verbundene Texturen der Drucktanks muessen vor dem ersten Zusammenstellen der Texturen bekannt sein.
		CHPTankModels.init();

		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(),
				CHPBlocks.HIGH_PRESSURE_PIPE.get(),
				CHPBlocks.HIGH_PRESSURE_GLASS_PIPE.get(),
				CHPBlocks.NETHERITE_PRESSURE_PIPE.get(),
				CHPBlocks.NETHERITE_GLASS_PIPE.get(),
				CHPBlocks.PUMP_T1.get(),
				CHPBlocks.PUMP_T2.get(),
				CHPBlocks.PUMP_T3.get(),
				CHPBlocks.PUMP_T4.get(),
				CHPBlocks.PUMP_T5.get(),
				CHPBlocks.PRESSURE_VALVE.get(),
				CHPBlocks.NETHERITE_PRESSURE_VALVE.get(),
				CHPBlocks.PRESSURE_REGULATOR.get(),
				CHPBlocks.NETHERITE_PRESSURE_REGULATOR.get(),
				CHPBlocks.RELIEF_VALVE.get(),
				CHPBlocks.NETHERITE_RELIEF_VALVE.get(),
				CHPBlocks.STEAM_OUTLET.get());
		// Wie Creates Tank: Mipmapping fuer die Fensterkanten aus der Entfernung.
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutoutMipped(),
				CHPBlocks.PRESSURE_TANK.get(),
				CHPBlocks.NETHERITE_PRESSURE_TANK.get());
	}
}
