package net.shiro.createhp.content.steam;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Dampfauslass. Sitzt wie eine Dampfmaschine an einem Tank und macht ihn zum Kessel; seine freien Kesselstufen
 * werden zu Dampf, den Rohre an der Vorderseite abholen. FACING zeigt vom Tank weg, zur Rohrseite.
 */
public class SteamOutletBlock extends DirectionalBlock implements IBE<SteamOutletBlockEntity>, IWrenchable {

	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		// Modell zeigt nach Norden: Platte am Tank (Sueden), Hals, Stutzen mit Rand.
		for (Direction d : Direction.values())
			SHAPES.put(d, Shapes.or(box(d, 2, 2, 14, 14, 14, 16), box(d, 4, 4, 8, 12, 12, 14), box(d, 3, 3, 5, 13, 13, 8)));
	}

	private final Supplier<? extends BlockEntityType<? extends SteamOutletBlockEntity>> blockEntityType;

	public SteamOutletBlock(Properties properties, Supplier<? extends BlockEntityType<? extends SteamOutletBlockEntity>> blockEntityType) {
		super(properties);
		this.blockEntityType = blockEntityType;
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	public static Direction getFacing(BlockState state) {
		return state.getValue(FACING);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// An die geklickte Flaeche angesetzt, Oeffnung von ihr weg.
		return defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	// Wie bei Creates Dampfmaschine: Der Tank prueft beim Setzen und Abbauen, ob er noch ein Kessel ist.
	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		FluidTankBlock.updateBoilerState(state, level, pos.relative(getFacing(state).getOpposite()));
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		IBE.onRemove(state, level, pos, newState);
		FluidTankBlock.updateBoilerState(state, level, pos.relative(getFacing(state).getOpposite()));
	}

	@Override
	public Class<SteamOutletBlockEntity> getBlockEntityClass() {
		return SteamOutletBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SteamOutletBlockEntity> getBlockEntityType() {
		return blockEntityType.get();
	}

	/** Form aus dem Nordmodell in die Blickrichtung gedreht. */
	private static VoxelShape box(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
		double[] a = rotate(facing, x1, y1, z1);
		double[] b = rotate(facing, x2, y2, z2);
		return Block.box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
				Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
	}

	private static double[] rotate(Direction facing, double x, double y, double z) {
		return switch (facing) {
			case NORTH -> new double[] { x, y, z };
			case SOUTH -> new double[] { 16 - x, y, 16 - z };
			case EAST -> new double[] { 16 - z, y, x };
			case WEST -> new double[] { z, y, 16 - x };
			case UP -> new double[] { x, 16 - z, y };
			case DOWN -> new double[] { x, z, 16 - y };
		};
	}
}
