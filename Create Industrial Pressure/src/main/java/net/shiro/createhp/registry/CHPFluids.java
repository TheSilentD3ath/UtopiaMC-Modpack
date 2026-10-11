package net.shiro.createhp.registry;

import com.simibubi.create.content.fluids.VirtualFluid;
import com.tterrag.registrate.util.entry.FluidEntry;

import net.shiro.createhp.CreateHP;
import net.shiro.createhp.content.steam.SteamFluidAttributes;

/**
 * Fluessigkeiten der Mod. Dampf ist "virtuell" wie Creates Tee: Es gibt ihn nur in Rohren, Tanks und Maschinen,
 * nicht als Block in der Welt und nicht im Eimer. Die Texturen liegen unter block/, damit sie ohne eigene
 * Atlas-Datei im Block-Atlas landen.
 */
public final class CHPFluids {
	private CHPFluids() {}

	public static final FluidEntry<VirtualFluid> STEAM = CreateHP.REGISTRATE
			.virtualFluid("steam", CreateHP.id("block/fluid/steam_still"), CreateHP.id("block/fluid/steam_flow"))
			.fluidAttributes(SteamFluidAttributes::new)
			.register();

	public static void register() {}
}
