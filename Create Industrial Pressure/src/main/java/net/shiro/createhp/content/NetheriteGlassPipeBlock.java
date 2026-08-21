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
 * See-through Netherite Pressure Pipe (wrench-created from {@link NetheritePressurePipeBlock}).
 * Mirrors {@link HighPressureGlassPipeBlock} but for the netherite tier: it converts back to the
 * netherite pipe (keeping the tier) and uses its own block entity type.
 */
public class NetheriteGlassPipeBlock extends GlassFluidPipeBlock implements PressureGlassPipe {

	public NetheriteGlassPipeBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends StraightPipeBlockEntity> getBlockEntityType() {
		return CHPBlockEntities.NETHERITE_GLASS_PIPE.get();
	}

	@Override
	public BlockState toRegularPipe(LevelAccessor world, BlockPos pos, BlockState state) {
		Direction.Axis axis = state.getValue(RotatedPillarBlock.AXIS);
		NetheritePressurePipeBlock pipe = (NetheritePressurePipeBlock) CHPBlocks.NETHERITE_PRESSURE_PIPE.get();
		Direction side = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
		return pipe.updateBlockState(pipe.getAxisState(axis), side, null, (BlockAndTintGetter) world, pos);
	}
}
