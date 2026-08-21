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

		BlockRenderLayerMap.INSTANCE.putBlock(CHPBlocks.HIGH_PRESSURE_PIPE.get(), RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(CHPBlocks.HIGH_PRESSURE_GLASS_PIPE.get(), RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(CHPBlocks.NETHERITE_PRESSURE_PIPE.get(), RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(CHPBlocks.NETHERITE_GLASS_PIPE.get(), RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(CHPBlocks.PUMP_T1.get(), RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(CHPBlocks.PUMP_T2.get(), RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(CHPBlocks.PUMP_T3.get(), RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(CHPBlocks.PUMP_T4.get(), RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(CHPBlocks.PUMP_T5.get(), RenderType.cutout());
	}
}
