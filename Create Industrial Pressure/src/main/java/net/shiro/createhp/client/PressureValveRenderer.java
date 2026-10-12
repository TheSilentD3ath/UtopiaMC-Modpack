package net.shiro.createhp.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.valve.PressureValveBlock;
import net.shiro.createhp.content.valve.PressureValveBlockEntity;

/**
 * Zeichnet Handrad und, nur beim normalen Absperrventil, die Welle.
 *
 * Creates FluidValveVisual zeichnet immer eine Welle; Regler und Ueberdruckventil haben aber keine. Deshalb
 * ein eigener Renderer ohne Flywheel-Visual. Fuer die wenigen Ventile einer Anlage ist das schnell genug.
 * Die Rechnung fuer das Handrad ist dieselbe wie in Creates FluidValveRenderer.
 */
public class PressureValveRenderer extends SafeBlockEntityRenderer<PressureValveBlockEntity> {

	public PressureValveRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	protected void renderSafe(PressureValveBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
			int light, int overlay) {
		BlockState state = be.getBlockState();
		if (!(state.getBlock() instanceof PressureValveBlock valve))
			return;

		Axis shaftAxis = valve.getRotationAxis(state);
		if (valve.isRotationDriven(state)) {
			SuperByteBuffer shaft = CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK,
					KineticBlockEntityRenderer.shaft(shaftAxis));
			KineticBlockEntityRenderer.renderRotatingBuffer(be, shaft, ms, buffer.getBuffer(RenderType.solid()), light);
		}

		Direction facing = state.getValue(FluidValveBlock.FACING);
		Axis pipeAxis = FluidValveBlock.getPipeAxis(state);
		float pointerRotation = Mth.lerp(be.getPointer().getValue(partialTicks), 0, -90);
		int pointerRotationOffset = pipeAxis.isHorizontal() && shaftAxis == Axis.X || pipeAxis.isVertical() ? 90 : 0;

		CachedBuffers.partial(CHPPartialModels.valvePointer(valve.getMaterial(), valve.getKind()), state)
				.center()
				.rotateYDegrees(AngleHelper.horizontalAngle(facing))
				.rotateXDegrees(facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90)
				.rotateYDegrees(pointerRotationOffset + pointerRotation)
				.uncenter()
				.light(light)
				.renderInto(ms, buffer.getBuffer(RenderType.solid()));
	}
}
