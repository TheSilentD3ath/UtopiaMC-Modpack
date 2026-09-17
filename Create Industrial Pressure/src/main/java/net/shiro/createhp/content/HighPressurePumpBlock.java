package net.shiro.createhp.content;

import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.shiro.createhp.registry.CHPBlockEntities;

/**
 * Pressure pump shared by all five tiers. Extends Create's {@link PumpBlock} so it is a mechanical pump —
 * directional, kinetic, pushes/pulls fluid exactly like vanilla. The only differences live in the
 * block entity (more SU, longer reach scaled by speed). Each block instance carries its {@link PumpTier}.
 */
public class HighPressurePumpBlock extends PumpBlock {

	public final PumpTier tier;

	public HighPressurePumpBlock(Properties properties, PumpTier tier) {
		super(properties);
		this.tier = tier;
	}

	@Override
	public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
		return CHPBlockEntities.HIGH_PRESSURE_PUMP.get();
	}
}
