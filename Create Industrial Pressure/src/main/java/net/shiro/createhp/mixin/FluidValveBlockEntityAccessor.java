package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity;

import net.createmod.catnip.animation.LerpedFloat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Creates Ventil oeffnet und schliesst ueber einen Zeiger, der der Drehung folgt; das Feld ist paketprivat.
 * Unsere Ventile steuern ihn selbst (Redstone, Sollwert, Ablassen) und brauchen dafuer Zugriff.
 */
@Mixin(value = FluidValveBlockEntity.class, remap = false)
public interface FluidValveBlockEntityAccessor {
	@Accessor("pointer")
	LerpedFloat createhp$getPointer();
}
