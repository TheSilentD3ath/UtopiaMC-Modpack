package net.shiro.createhp.content.turbine;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.content.pressure.PressureRated;

import java.util.function.Supplier;

/**
 * Dampfeinlass der Turbine. Er ist das Teil mit der Druckgrenze: Die Hochdruckpumpen pruefen ihn wie einen Tank am
 * Ende ihrer Leitung. Kupfer platzt, Netherit explodiert.
 */
public class SteamInletBlock extends Block implements IBE<SteamInletBlockEntity>, PressureRated, TurbinePart, IWrenchable {

	private final PipeMaterial material;
	private final Supplier<? extends BlockEntityType<? extends SteamInletBlockEntity>> blockEntityType;

	public SteamInletBlock(Properties properties, PipeMaterial material,
			Supplier<? extends BlockEntityType<? extends SteamInletBlockEntity>> blockEntityType) {
		super(properties);
		this.material = material;
		this.blockEntityType = blockEntityType;
	}

	@Override
	public PipeMaterial getMaterial() {
		return material;
	}

	@Override
	public Role getTurbineRole() {
		return Role.INLET;
	}

	@Override
	public PipeMaterial getTurbineMaterial() {
		return material;
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		IBE.onRemove(state, level, pos, newState);
	}

	@Override
	public Class<SteamInletBlockEntity> getBlockEntityClass() {
		return SteamInletBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SteamInletBlockEntity> getBlockEntityType() {
		return blockEntityType.get();
	}
}
