package net.shiro.createhp.content.turbine;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.shiro.createhp.content.pressure.PipeMaterial;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Kondensatauslass der Turbine: Aus ihm ziehen Pumpen das Wasser, das aus dem Dampf wird. Ohne Werkstoff, weil
 * das Wasser drucklos herauskommt; er passt in Turbinen aus Kupfer und aus Netherit.
 */
public class CondensateOutletBlock extends Block implements IBE<CondensateOutletBlockEntity>, TurbinePart, IWrenchable {

	private final Supplier<? extends BlockEntityType<? extends CondensateOutletBlockEntity>> blockEntityType;

	public CondensateOutletBlock(Properties properties,
			Supplier<? extends BlockEntityType<? extends CondensateOutletBlockEntity>> blockEntityType) {
		super(properties);
		this.blockEntityType = blockEntityType;
		registerDefaultState(defaultBlockState().setValue(TurbineOutputBlock.NETHERITE, false));
	}

	// Aussehen nach dem Werkstoff der Turbine, siehe TurbineOutputBlock.NETHERITE.
	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TurbineOutputBlock.NETHERITE);
	}

	@Override
	public Role getTurbineRole() {
		return Role.CONDENSATE;
	}

	@Nullable
	@Override
	public PipeMaterial getTurbineMaterial() {
		return null;
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		IBE.onRemove(state, level, pos, newState);
	}

	@Override
	public Class<CondensateOutletBlockEntity> getBlockEntityClass() {
		return CondensateOutletBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CondensateOutletBlockEntity> getBlockEntityType() {
		return blockEntityType.get();
	}
}
