package net.shiro.createhp.registry;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.tterrag.registrate.util.entry.RegistryEntry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.shiro.createhp.CreateHP;
import net.shiro.createhp.content.pressure.PressureDisplaySource;

import java.util.List;

/** Display-Link-Quellen der Mod. */
public final class CHPDisplaySources {
	private CHPDisplaySources() {}

	public static final RegistryEntry<PressureDisplaySource> PIPE_PRESSURE =
			CreateHP.REGISTRATE.displaySource("pipe_pressure", PressureDisplaySource::new).register();

	/** Erzwingt das Laden der Klasse vor REGISTRATE.register(). */
	public static void register() {}

	/**
	 * Haengt die Druckquelle an Block-Entity-Typen, nicht an Bloecke: So deckt ein Eintrag alle Zustaende ab.
	 * Creates eigene Rohre, Ventile und die Pumpe sind dabei, weil gerade sie in einem Hochdrucknetz zuerst
	 * platzen und ueberwacht werden muessen. Erst nach REGISTRATE.register() aufrufen.
	 */
	public static void associate() {
		DisplaySource source = PIPE_PRESSURE.get();
		List<BlockEntityType<?>> types = List.of(
				CHPBlockEntities.HIGH_PRESSURE_PIPE.get(),
				CHPBlockEntities.HIGH_PRESSURE_GLASS_PIPE.get(),
				CHPBlockEntities.NETHERITE_PRESSURE_PIPE.get(),
				CHPBlockEntities.NETHERITE_GLASS_PIPE.get(),
				CHPBlockEntities.HIGH_PRESSURE_PUMP.get(),
				AllBlockEntityTypes.FLUID_PIPE.get(),
				AllBlockEntityTypes.ENCASED_FLUID_PIPE.get(),
				AllBlockEntityTypes.GLASS_FLUID_PIPE.get(),
				AllBlockEntityTypes.SMART_FLUID_PIPE.get(),
				AllBlockEntityTypes.FLUID_VALVE.get(),
				AllBlockEntityTypes.MECHANICAL_PUMP.get());
		for (BlockEntityType<?> type : types)
			DisplaySource.BY_BLOCK_ENTITY.add(type, source);
	}
}
