package net.shiro.createhp.content;

import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.registry.CHPBlockEntities;
import net.shiro.createhp.registry.CHPBlocks;

/**
 * The see-through high pressure pipe (created by wrenching a {@link HighPressurePipeBlock}).
 * Extends Create's GlassFluidPipeBlock so it's a real straight transparent fluid pipe, but:
 *  - uses our own block entity type;
 *  - {@link #toRegularPipe} converts BACK to our HP pipe (not Create's vanilla fluid_pipe), so
 *    wrenching it keeps the high-pressure property.
 * (The reverse direction — HP pipe -> this glass — is handled by FluidPipeWrenchMixin.)
 */
public class HighPressureGlassPipeBlock extends GlassFluidPipeBlock implements PressureGlassPipe {

	public HighPressureGlassPipeBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends StraightPipeBlockEntity> getBlockEntityType() {
		return CHPBlockEntities.HIGH_PRESSURE_GLASS_PIPE.get();
	}

	@Override
	public BlockState toRegularPipe(LevelAccessor world, BlockPos pos, BlockState state) {
		Direction.Axis axis = state.getValue(RotatedPillarBlock.AXIS);
		HighPressurePipeBlock pipe = (HighPressurePipeBlock) CHPBlocks.HIGH_PRESSURE_PIPE.get();
		Direction side = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
		return pipe.updateBlockState(pipe.getAxisState(axis), side, null, (BlockAndTintGetter) world, pos);
	}
}
