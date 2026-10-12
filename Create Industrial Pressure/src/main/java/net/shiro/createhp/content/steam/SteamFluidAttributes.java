package net.shiro.createhp.content.steam;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * Eigenschaften des Dampfs. Leichter als Luft: Creates Tanks zeichnen ihn darum von oben nach unten gefuellt,
 * wie andere Gase. Er fliesst schnell, die Zaehigkeit liegt weit unter der von Wasser.
 */
public class SteamFluidAttributes implements FluidVariantAttributeHandler {

	@Override
	public Component getName(FluidVariant fluidVariant) {
		return Component.translatable("fluid.createhp.steam");
	}

	@Override
	public int getViscosity(FluidVariant variant, @Nullable Level world) {
		return 200;
	}

	@Override
	public boolean isLighterThanAir(FluidVariant variant) {
		return true;
	}

	@Override
	public int getTemperature(FluidVariant variant) {
		return 373;
	}
}
