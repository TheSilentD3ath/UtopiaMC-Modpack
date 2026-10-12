package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.tank.FluidTankModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;

import net.minecraft.client.resources.model.BakedModel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Creates Tank-Modell (verbundene Texturen, ausgeblendete Innenflaechen im Multiblock) hat einen privaten
 * Konstruktor und nur Fabriken fuer Creates eigene Texturen. Ueber den Konstruktor bekommen die Drucktanks
 * dasselbe Modell mit ihren eigenen Texturen, ohne Creates Code zu kopieren.
 */
@Mixin(value = FluidTankModel.class, remap = false)
public interface FluidTankModelInvoker {
	@Invoker("<init>")
	static FluidTankModel createhp$create(BakedModel originalModel, CTSpriteShiftEntry side, CTSpriteShiftEntry top,
			CTSpriteShiftEntry inner) {
		throw new AssertionError();
	}
}
