package net.shiro.createhp.content;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.registry.CHPBlockEntities;
import net.shiro.createhp.registry.CHPBlocks;

/**
 * High Pressure Pipe.
 *
 * Extends Create's own {@link FluidPipeBlock} so it inherits the full pipe behaviour:
 * the six directional connection states, wrench rotation, fluid {@code FluidTransportBehaviour},
 * and participation in Create's fluid network. The only change at the block level is that we
 * point it at our OWN block entity type (registered for this block) instead of Create's, while
 * still reusing Create's {@link FluidPipeBlockEntity} class for the actual logic.
 *
 * The "high pressure" part (faster flow, longer reach, bigger buffer) is NOT done here — those
 * are network-level values in Create and are boosted from the mixin layer + CHPConfig. See README.
 */
public class HighPressurePipeBlock extends FluidPipeBlock implements PressurePipe {

	public HighPressurePipeBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
		return CHPBlockEntities.HIGH_PRESSURE_PIPE.get();
	}

	@Override
	public BlockState getGlassPipeDefaultState() {
		return CHPBlocks.HIGH_PRESSURE_GLASS_PIPE.getDefaultState();
	}
}
