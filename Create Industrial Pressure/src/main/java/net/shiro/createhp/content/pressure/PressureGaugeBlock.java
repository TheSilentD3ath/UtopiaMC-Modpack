package net.shiro.createhp.content.pressure;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Druckmesser: ein gerades Rohrstueck mit Anzeige. Er leitet wie ein Rohr und gibt an einen Komparator den Druck
 * relativ zu seiner Grenze aus, 15 = Grenze erreicht. Damit lassen sich Pumpen, Regler und Turbinen per Redstone
 * steuern, bevor etwas platzt. Er haelt den Druck seines Werkstoffs und platzt oder explodiert wie ein Rohr.
 */
public class PressureGaugeBlock extends AxisPipeBlock implements IBE<PressureGaugeBlockEntity>, PressureRated {

	// Rohr wie Creates Glasrohr, dazu das Gehaeuse der Anzeige in der Mitte (3 bis 13 Pixel).
	private static final VoxelShape HOUSING = Block.box(3, 3, 3, 13, 13, 13);

	private final PipeMaterial material;
	private final Supplier<? extends BlockEntityType<? extends PressureGaugeBlockEntity>> blockEntityType;

	public PressureGaugeBlock(Properties properties, PipeMaterial material,
			Supplier<? extends BlockEntityType<? extends PressureGaugeBlockEntity>> blockEntityType) {
		super(properties);
		this.material = material;
		this.blockEntityType = blockEntityType;
	}

	@Override
	public PipeMaterial getMaterial() {
		return material;
	}

	// Creates Rohr wird mit Kupfergehaeuse zum ummantelten Rohr; der Druckmesser wuerde dabei verschwinden.
	@Override
	public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		return InteractionResult.PASS;
	}

	@Override
	public ItemStack getPickedStack(BlockState state, BlockGetter view, BlockPos pos, @Nullable Player player,
			@Nullable HitResult result) {
		return new ItemStack(this);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Axis axis = state.getValue(AXIS);
		return Shapes.or(AllShapes.EIGHT_VOXEL_POLE.get(axis), HOUSING);
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof PressureGaugeBlockEntity gauge ? gauge.getComparatorSignal() : 0;
	}

	@Override
	public Class<PressureGaugeBlockEntity> getBlockEntityClass() {
		return PressureGaugeBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PressureGaugeBlockEntity> getBlockEntityType() {
		return blockEntityType.get();
	}
}
