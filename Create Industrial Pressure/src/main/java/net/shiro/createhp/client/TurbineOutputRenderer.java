package net.shiro.createhp.client;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.turbine.TurbineOutputBlockEntity;

/** Halbe Welle zur Abtriebsseite, wie bei Creates Motor; ohne Flywheel-Backend gezeichnet. */
public class TurbineOutputRenderer extends KineticBlockEntityRenderer<TurbineOutputBlockEntity> {

	public TurbineOutputRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected SuperByteBuffer getRotatedModel(TurbineOutputBlockEntity be, BlockState state) {
		return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state);
	}
}
