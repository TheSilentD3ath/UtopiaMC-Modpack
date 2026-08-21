package net.shiro.createhp.content;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.registry.CHPBlockEntities;
import net.shiro.createhp.registry.CHPBlocks;

/**
 * Netherite Pressure Pipe — the top visual/material tier. Functionally identical to the copper
 * {@link HighPressurePipeBlock} (joins Create's fluid network, survives any high pressure pump,
 * never bursts); it just looks like a netherite pipe and wrenches into the netherite glass variant.
 */
public class NetheritePressurePipeBlock extends FluidPipeBlock implements PressurePipe {

	public NetheritePressurePipeBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
		return CHPBlockEntities.NETHERITE_PRESSURE_PIPE.get();
	}

	@Override
	public BlockState getGlassPipeDefaultState() {
		return CHPBlocks.NETHERITE_GLASS_PIPE.getDefaultState();
	}
}
