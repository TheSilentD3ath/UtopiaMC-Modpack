package net.shiro.createhp.content;

import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/** See-through pressure pipe shared by every material tier. */
public class PressureGlassPipeBlock extends GlassFluidPipeBlock {
	private final Supplier<? extends BlockEntityType<? extends StraightPipeBlockEntity>> blockEntityType;
	private final Supplier<PressurePipeBlock> solidPipe;

	public PressureGlassPipeBlock(Properties properties,
			Supplier<? extends BlockEntityType<? extends StraightPipeBlockEntity>> blockEntityType,
			Supplier<PressurePipeBlock> solidPipe) {
		super(properties);
		this.blockEntityType = blockEntityType;
		this.solidPipe = solidPipe;
	}

	@Override
	public BlockEntityType<? extends StraightPipeBlockEntity> getBlockEntityType() {
		return blockEntityType.get();
	}

	@Override
	public BlockState toRegularPipe(LevelAccessor world, BlockPos pos, BlockState state) {
		Direction.Axis axis = state.getValue(RotatedPillarBlock.AXIS);
		Direction side = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
		PressurePipeBlock pipe = solidPipe.get();
		return pipe.updateBlockState(pipe.getAxisState(axis), side, null, (BlockAndTintGetter) world, pos);
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, BlockEntity blockEntity) {
		return ItemRequirement.of(solidPipe.get().defaultBlockState(), blockEntity);
	}
}
