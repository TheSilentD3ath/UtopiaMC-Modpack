package net.shiro.createhp.content;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.content.pressure.PressureRated;

import java.util.function.Supplier;

/**
 * Druckrohr im Kupfergehaeuse, je Werkstoff. Creates ummanteltes Rohr verweist an mehreren Stellen fest auf sein
 * Kupferrohr (Schraubenschluessel, Mittelklick, Bauplan) und auf seinen eigenen Block-Entity-Typ. Dort setzen wir das
 * Druckrohr desselben Werkstoffs ein, sonst wuerde aus einem Hochdruckrohr beim Abnehmen des Gehaeuses ein Kupferrohr
 * mit 16 bar Grenze. Die Grenze selbst kommt ueber {@link PressureRated}.
 */
public class PressureEncasedPipeBlock extends EncasedPipeBlock implements PressureRated {
	private final PipeMaterial material;
	private final Supplier<PressurePipeBlock> pipe;
	private final Supplier<? extends BlockEntityType<? extends FluidPipeBlockEntity>> blockEntityType;

	public PressureEncasedPipeBlock(Properties properties, PipeMaterial material, Supplier<PressurePipeBlock> pipe,
			Supplier<? extends BlockEntityType<? extends FluidPipeBlockEntity>> blockEntityType) {
		super(properties, AllBlocks.COPPER_CASING::get);
		this.material = material;
		this.pipe = pipe;
		this.blockEntityType = blockEntityType;
	}

	@Override
	public PipeMaterial getMaterial() {
		return material;
	}

	@Override
	public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
		return blockEntityType.get();
	}

	// Wie Creates onWrenched, nur wird es wieder das Druckrohr. Das Gehaeuse gibt es dabei wie bei Create nicht zurueck.
	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (level.isClientSide)
			return InteractionResult.SUCCESS;

		level.levelEvent(2001, pos, Block.getId(state));
		PressurePipeBlock solid = pipe.get();
		BlockState equivalent = transferSixWayProperties(state, solid.defaultBlockState());
		Direction firstOpen = Direction.UP;
		for (Direction d : Iterate.directions)
			if (state.getValue(FACING_TO_PROPERTY_MAP.get(d))) {
				firstOpen = d;
				break;
			}

		FluidTransportBehaviour.cacheFlows(level, pos);
		level.setBlockAndUpdate(pos, solid.updateBlockState(equivalent, firstOpen, null, level, pos));
		FluidTransportBehaviour.loadFlows(level, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	public ItemStack getPickedStack(BlockState state, BlockGetter view, BlockPos pos, Player player, HitResult result) {
		return new ItemStack(pipe.get());
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, BlockEntity blockEntity) {
		return ItemRequirement.of(pipe.get().defaultBlockState(), blockEntity);
	}
}
