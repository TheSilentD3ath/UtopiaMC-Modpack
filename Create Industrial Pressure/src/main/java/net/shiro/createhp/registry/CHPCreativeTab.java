package net.shiro.createhp.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import net.shiro.createhp.CreateHP;

/**
 * Creative inventory tab for the mod. Holds every block/item the mod adds, in a sensible order
 * (per material: pipe, valves, tank, turbine parts, then the pump tiers). The tab icon is the Tier-1 pump.
 *
 * Registered the Fabric-native way (version-stable) rather than through Registrate's tab helper.
 * The icon + displayItems suppliers run lazily, after all items are registered, so wiring this in
 * after {@code REGISTRATE.register()} is safe.
 */
public final class CHPCreativeTab {
	private CHPCreativeTab() {}

	public static final CreativeModeTab TAB = FabricItemGroup.builder()
			.icon(() -> new ItemStack(CHPBlocks.PUMP_T1.get()))
			.title(Component.translatable("itemGroup.createhp"))
			.displayItems((params, output) -> {
				// Glass variants are intentionally omitted: they only exist by wrenching a solid
				// pressure pipe, so they shouldn't be craftable/grabbable creative items.
				output.accept(CHPBlocks.HIGH_PRESSURE_PIPE.get());
				output.accept(CHPBlocks.PRESSURE_VALVE.get());
				output.accept(CHPBlocks.PRESSURE_REGULATOR.get());
				output.accept(CHPBlocks.RELIEF_VALVE.get());
				output.accept(CHPBlocks.PRESSURE_TANK.get());
				output.accept(CHPBlocks.STEAM_OUTLET.get());
				output.accept(CHPBlocks.TURBINE_CASING.get());
				output.accept(CHPBlocks.TURBINE_ROTOR.get());
				output.accept(CHPBlocks.STEAM_INLET.get());
				output.accept(CHPBlocks.CONDENSATE_OUTLET.get());
				output.accept(CHPBlocks.TURBINE_OUTPUT.get());
				output.accept(CHPBlocks.PUMP_T1.get());
				output.accept(CHPBlocks.PUMP_T2.get());
				output.accept(CHPBlocks.PUMP_T3.get());
				output.accept(CHPBlocks.NETHERITE_PRESSURE_PIPE.get());
				output.accept(CHPBlocks.NETHERITE_PRESSURE_VALVE.get());
				output.accept(CHPBlocks.NETHERITE_PRESSURE_REGULATOR.get());
				output.accept(CHPBlocks.NETHERITE_RELIEF_VALVE.get());
				output.accept(CHPBlocks.NETHERITE_PRESSURE_TANK.get());
				output.accept(CHPBlocks.NETHERITE_TURBINE_CASING.get());
				output.accept(CHPBlocks.NETHERITE_TURBINE_ROTOR.get());
				output.accept(CHPBlocks.NETHERITE_STEAM_INLET.get());
				output.accept(CHPBlocks.PUMP_T4.get());
				output.accept(CHPBlocks.PUMP_T5.get());
			})
			.build();

	public static void register() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CreateHP.id("main"), TAB);
	}
}
