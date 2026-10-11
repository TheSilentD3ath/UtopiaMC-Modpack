package net.shiro.createhp.content.tank;

import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.content.pressure.PressureRated;

import java.util.function.Supplier;

/**
 * Drucktank. Ein Fluessigkeitstank von Create mit eigenem Werkstoff: Er verbindet sich zu Multiblocks, zeigt
 * Fenster per Schraubenschluessel, gibt ein Komparatorsignal und kann Kessel fuer Dampfmaschinen sein, genau
 * wie Creates Tank. Anders sind nur Fassungsvermoegen und Grenzwert (siehe {@link PipeMaterial}).
 *
 * Jeder Werkstoff hat einen eigenen Block-Entity-Typ: Create verbindet nur Tanks desselben Typs, sonst wuerden
 * Kupfer- und Netherittanks zu einem gemischten Multiblock verschmelzen.
 */
public class PressureTankBlock extends FluidTankBlock implements PressureRated {

	private final PipeMaterial material;
	private final Supplier<? extends BlockEntityType<? extends PressureTankBlockEntity>> blockEntityType;

	public PressureTankBlock(Properties properties, PipeMaterial material,
			Supplier<? extends BlockEntityType<? extends PressureTankBlockEntity>> blockEntityType) {
		super(properties, false);
		this.material = material;
		this.blockEntityType = blockEntityType;
	}

	@Override
	public PipeMaterial getMaterial() {
		return material;
	}

	@Override
	public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() {
		return blockEntityType.get();
	}
}
