package net.shiro.createhp.content;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Solid pressure-rated pipe shared by every material tier.
 *
 * The block entity type and matching glass state are supplied by the registry so adding another
 * material does not require another copy of the same block implementation.
 */
public class PressurePipeBlock extends FluidPipeBlock {
	private final Supplier<? extends BlockEntityType<? extends FluidPipeBlockEntity>> blockEntityType;
	private final Supplier<BlockState> glassState;

	public PressurePipeBlock(Properties properties,
			Supplier<? extends BlockEntityType<? extends FluidPipeBlockEntity>> blockEntityType,
			Supplier<BlockState> glassState) {
		super(properties);
		this.blockEntityType = blockEntityType;
		this.glassState = glassState;
	}

	@Override
	public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
		return blockEntityType.get();
	}

	public BlockState getGlassPipeDefaultState() {
		return glassState.get();
	}
}
