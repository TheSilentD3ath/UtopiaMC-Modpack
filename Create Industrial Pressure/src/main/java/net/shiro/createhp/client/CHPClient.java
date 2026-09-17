package net.shiro.createhp.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;
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

		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(),
				CHPBlocks.HIGH_PRESSURE_PIPE.get(),
				CHPBlocks.HIGH_PRESSURE_GLASS_PIPE.get(),
				CHPBlocks.NETHERITE_PRESSURE_PIPE.get(),
				CHPBlocks.NETHERITE_GLASS_PIPE.get(),
				CHPBlocks.PUMP_T1.get(),
				CHPBlocks.PUMP_T2.get(),
				CHPBlocks.PUMP_T3.get(),
				CHPBlocks.PUMP_T4.get(),
				CHPBlocks.PUMP_T5.get());
	}
}
