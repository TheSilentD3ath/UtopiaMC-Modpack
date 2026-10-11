package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.shiro.createhp.content.tank.PressureTankBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fassungsvermoegen der Drucktanks. Create fragt es ueberall ueber die statische Methode getCapacityMultiplier
 * ab, auch beim Laden (read), wo es alles ueber der Grenze sofort loescht. In genau diesen Methoden der
 * Tank-Block-Entity liefert der Redirect fuer Drucktanks ihren eigenen Wert; Creates Tanks bleiben unberuehrt.
 */
@Mixin(value = FluidTankBlockEntity.class, remap = false)
public abstract class TankCapacityMixin {

	@Redirect(method = {"createInventory", "read", "applyFluidTankSize", "getTankSize"}, at = @At(value = "INVOKE",
			target = "Lcom/simibubi/create/content/fluids/tank/FluidTankBlockEntity;getCapacityMultiplier()J"))
	private long createhp$capacityPerBlock() {
		if ((Object) this instanceof PressureTankBlockEntity tank)
			return tank.capacityPerBlock();
		return FluidTankBlockEntity.getCapacityMultiplier();
	}
}
