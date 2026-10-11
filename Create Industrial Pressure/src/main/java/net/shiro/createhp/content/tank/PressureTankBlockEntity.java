package net.shiro.createhp.content.tank;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.content.pressure.PressureTooltip;
import net.shiro.createhp.content.steam.BoilerRules;

import java.util.List;

/**
 * Creates Tank-Block-Entity mit groesserem Fassungsvermoegen und Druckanzeige.
 *
 * Create liest das Fassungsvermoegen aus einer statischen Methode (getCapacityMultiplier), die sich nicht
 * ueberschreiben laesst. TankCapacityMixin leitet ihre Aufrufe in der Tank-Block-Entity auf
 * {@link #capacityPerBlock()} um. Ohne das wuerde Create beim Laden alles abschneiden, was ueber 8 Eimer je
 * Block hinausgeht.
 */
public class PressureTankBlockEntity extends FluidTankBlockEntity {

	/** Auf dem Client wird die Druckanzeige nur zweimal pro Sekunde neu berechnet. */
	private static final int CLIENT_REFRESH_TICKS = 10;

	private float clientBar;
	private long clientNextRefresh;

	/** Drucktanks duerfen breiter werden als Creates Tank (3), damit 25 Brenner unter einen Kessel passen. */
	public static final int MAX_WIDTH = 5;

	public PressureTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		// Kesselregeln (Entscheidung des Maintainers): Kupfer 1 Stufe je Block bis 50, Netherit 2 je Block bis 150
		// und dreifache Hitze je Brenner. Ein 5x5-Boden aus ueberhitzten Lohenbrennern erreicht damit genau die
		// Hoechststufe.
		PipeMaterial material = getMaterial();
		((BoilerRules) boiler).createhp$setRules(material.boilerLevelCap, material.boilerLevelsPerBlock,
				material.boilerHeatFactor);
	}

	@Override
	public int getMaxWidth() {
		return MAX_WIDTH;
	}

	public PipeMaterial getMaterial() {
		// Laeuft schon im Konstruktor der Oberklasse (createInventory); nur den Blockzustand verwenden.
		return getBlockState().getBlock() instanceof PressureTankBlock tank ? tank.getMaterial() : PipeMaterial.COPPER;
	}

	/** Fassungsvermoegen je Block in Tropfen (81 000 je Eimer). */
	public long capacityPerBlock() {
		return getMaterial().tankBuckets * FluidConstants.BUCKET;
	}

	/** Hoechster Druck an einem Rohranschluss des ganzen Tanks, in bar. */
	public float pressureBar() {
		if (level != null && level.isClientSide) {
			long now = level.getGameTime();
			if (now >= clientNextRefresh) {
				clientNextRefresh = now + CLIENT_REFRESH_TICKS;
				clientBar = TankPressure.barAt(this);
			}
			return clientBar;
		}
		return TankPressure.barAt(this);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		PressureTooltip.addTank(tooltip, pressureBar(), getBlockState());
		return true;
	}
}
