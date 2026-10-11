package net.shiro.createhp.content.turbine;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Abtrieb der Dampfturbine: sitzt in der Mitte der Vorderwand, die Welle zeigt nach aussen (FACING). Er prueft
 * die Turbine dahinter und gibt ihre Drehung ab.
 *
 * Wie bei Creates Motor zeigt FACING beim Setzen zum Spieler; wer vor der Vorderwand steht und den Abtrieb
 * hineinsetzt, bekommt die Welle also nach aussen.
 */
public class TurbineOutputBlock extends DirectionalKineticBlock implements IBE<TurbineOutputBlockEntity> {

	private final Supplier<? extends BlockEntityType<? extends TurbineOutputBlockEntity>> blockEntityType;

	public TurbineOutputBlock(Properties properties,
			Supplier<? extends BlockEntityType<? extends TurbineOutputBlockEntity>> blockEntityType) {
		super(properties);
		this.blockEntityType = blockEntityType;
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == state.getValue(FACING);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING).getAxis();
	}

	// Die Leistung haengt vom Dampf ab, nicht vom Block; Creates feste Angabe im Item-Tooltip waere falsch.
	@Override
	public boolean hideStressImpact() {
		return true;
	}

	@Override
	public Class<TurbineOutputBlockEntity> getBlockEntityClass() {
		return TurbineOutputBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends TurbineOutputBlockEntity> getBlockEntityType() {
		return blockEntityType.get();
	}
}
