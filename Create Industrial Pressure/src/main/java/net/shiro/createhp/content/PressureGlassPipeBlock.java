package net.shiro.createhp.content;

import com.simibubi.create.content.decoration.encasing.EncasedBlock;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;

import net.createmod.catnip.data.Iterate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.content.pressure.PressureRated;

import java.util.function.Supplier;

/** See-through pressure pipe shared by every material tier. */
public class PressureGlassPipeBlock extends GlassFluidPipeBlock implements PressureRated {
	private final Supplier<? extends BlockEntityType<? extends StraightPipeBlockEntity>> blockEntityType;
	private final Supplier<PressurePipeBlock> solidPipe;
	private final PipeMaterial material;

	public PressureGlassPipeBlock(Properties properties, PipeMaterial material,
			Supplier<? extends BlockEntityType<? extends StraightPipeBlockEntity>> blockEntityType,
			Supplier<PressurePipeBlock> solidPipe) {
		super(properties);
		this.material = material;
		this.blockEntityType = blockEntityType;
		this.solidPipe = solidPipe;
	}

	@Override
	public PipeMaterial getMaterial() {
		return material;
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

	// Creates Glasrohr wird mit Kupfergehaeuse immer zu Creates ummanteltem Kupferrohr (16 bar). Unseres soll die
	// ummantelte Form seines festen Druckrohrs werden; welche das ist, steht in Creates EncasingRegistry.
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		for (Block variant : EncasingRegistry.getVariants(solidPipe.get())) {
			if (!(variant instanceof EncasedBlock encased) || encased.getCasing().asItem() != held.getItem())
				continue;
			if (level.isClientSide)
				return InteractionResult.SUCCESS;
			BlockState encasedState = variant.defaultBlockState();
			for (Direction d : Iterate.directionsInAxis(state.getValue(RotatedPillarBlock.AXIS)))
				encasedState = encasedState.setValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(d), true);
			FluidTransportBehaviour.cacheFlows(level, pos);
			level.setBlockAndUpdate(pos, encasedState);
			FluidTransportBehaviour.loadFlows(level, pos);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	// Create gibt beim Mittelklick auf ein Glasrohr immer sein Kupfer-Fluessigkeitsrohr zurueck.
	// Unsere Glasrohre haben kein eigenes Item, also muss es das feste Druckrohr desselben Materials sein.
	@Override
	public ItemStack getPickedStack(BlockState state, BlockGetter view, BlockPos pos, Player player, HitResult result) {
		return new ItemStack(solidPipe.get());
	}
}
