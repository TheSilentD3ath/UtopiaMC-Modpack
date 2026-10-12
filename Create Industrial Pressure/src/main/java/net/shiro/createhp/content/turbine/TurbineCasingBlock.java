package net.shiro.createhp.content.turbine;

import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.world.level.block.Block;
import net.shiro.createhp.content.pressure.PipeMaterial;

/** Gehaeuse der Dampfturbine: Mantel und Waende. Ein schlichter Block; die Turbine liest nur Rolle und Werkstoff. */
public class TurbineCasingBlock extends Block implements TurbinePart, IWrenchable {

	private final PipeMaterial material;

	public TurbineCasingBlock(Properties properties, PipeMaterial material) {
		super(properties);
		this.material = material;
	}

	@Override
	public Role getTurbineRole() {
		return Role.CASING;
	}

	@Override
	public PipeMaterial getTurbineMaterial() {
		return material;
	}
}
