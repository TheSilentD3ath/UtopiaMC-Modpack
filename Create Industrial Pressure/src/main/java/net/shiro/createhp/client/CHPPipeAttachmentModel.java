package net.shiro.createhp.client;

import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.data.Iterate;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Identical to Create's PipeAttachmentModel, except the fittings (rims/connectors/casing) are drawn
 * from {@link CHPPartialModels} (our blue createhp:block/fluid_pipe/... partials) instead of Create's
 * copper ones. Registered for our pipe via CreateRegistrate.blockModel(() -> CHPPipeAttachmentModel::withAO).
 */
public class CHPPipeAttachmentModel extends ForwardingBakedModel {

	private final boolean ao;
	private final CHPPartialModels partials;

	/** Copper / blue high pressure pipe. */
	public static CHPPipeAttachmentModel withAO(BakedModel template) {
		return new CHPPipeAttachmentModel(template, true, CHPPartialModels.COPPER);
	}

	/** Netherite pipe. */
	public static CHPPipeAttachmentModel netheriteWithAO(BakedModel template) {
		return new CHPPipeAttachmentModel(template, true, CHPPartialModels.NETHERITE);
	}

	public CHPPipeAttachmentModel(BakedModel template, boolean ao, CHPPartialModels partials) {
		this.wrapped = template;
		this.ao = ao;
		this.partials = partials;
	}

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public boolean useAmbientOcclusion() {
		return ao;
	}

	@Override
	public void emitBlockQuads(BlockAndTintGetter world, BlockState state, BlockPos pos,
			Supplier<RandomSource> randomSupplier, RenderContext context) {
		PipeModelData data = new PipeModelData();
		BracketedBlockEntityBehaviour bracket = BlockEntityBehaviour.get(world, pos, BracketedBlockEntityBehaviour.TYPE);
		Object renderData = ((RenderAttachedBlockView) world).getBlockEntityRenderAttachment(pos);
		if (renderData instanceof AttachmentTypes[] attachments) {
			for (int i = 0; i < attachments.length; i++) {
				data.putAttachment(Iterate.directions[i], attachments[i]);
			}
		}
		if (bracket != null) {
			data.putBracket(bracket.getBracket());
		}
		data.setEncased(FluidPipeBlock.shouldDrawCasing(world, pos, state));
		super.emitBlockQuads(world, state, pos, randomSupplier, context);
		addQuads(world, state, pos, randomSupplier, context, data);
	}

	private void addQuads(BlockAndTintGetter world, BlockState state, BlockPos pos,
			Supplier<RandomSource> randomSupplier, RenderContext context, PipeModelData pipeData) {
		BakedModel bracket = pipeData.getBracket();
		if (bracket != null) {
			((FabricBakedModel) bracket).emitBlockQuads(world, state, pos, randomSupplier, context);
		}
		for (Direction d : Iterate.directions) {
			AttachmentTypes type = pipeData.getAttachment(d);
			for (ComponentPartials partial : type.partials) {
				Map<Direction, PartialModel> byDir = partials.attachments.get(partial);
				if (byDir == null) continue;
				PartialModel pm = byDir.get(d);
				BakedModel m = pm == null ? null : pm.get();
				if (m != null) ((FabricBakedModel) m).emitBlockQuads(world, state, pos, randomSupplier, context);
			}
		}
		if (pipeData.isEncased()) {
			BakedModel casing = partials.casing.get();
			if (casing != null) ((FabricBakedModel) casing).emitBlockQuads(world, state, pos, randomSupplier, context);
		}
	}

	private static class PipeModelData {
		private final AttachmentTypes[] attachments = new AttachmentTypes[6];
		private boolean encased;
		private BakedModel bracket;

		PipeModelData() {
			Arrays.fill(attachments, AttachmentTypes.NONE);
		}

		void putBracket(BlockState state) {
			if (state != null) {
				bracket = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
			}
		}

		BakedModel getBracket() {
			return bracket;
		}

		void putAttachment(Direction face, AttachmentTypes rim) {
			attachments[face.get3DDataValue()] = rim;
		}

		AttachmentTypes getAttachment(Direction face) {
			return attachments[face.get3DDataValue()];
		}

		void setEncased(boolean encased) {
			this.encased = encased;
		}

		boolean isEncased() {
			return encased;
		}
	}
}
