package net.shiro.createhp.content.turbine;

import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.world.level.block.RotatedPillarBlock;
import net.shiro.createhp.content.pressure.PipeMaterial;

/**
 * Rotorblock der Dampfturbine. Die Achse ist nur fuer das Aussehen da: Die Turbine prueft sie nicht, damit ein
 * falsch gesetzter Block nicht die ganze Anlage blockiert.
 */
public class TurbineRotorBlock extends RotatedPillarBlock implements TurbinePart, IWrenchable {

	private final PipeMaterial material;

	public TurbineRotorBlock(Properties properties, PipeMaterial material) {
		super(properties);
		this.material = material;
	}

	@Override
	public Role getTurbineRole() {
		return Role.ROTOR;
	}

	@Override
	public PipeMaterial getTurbineMaterial() {
		return material;
	}
}
