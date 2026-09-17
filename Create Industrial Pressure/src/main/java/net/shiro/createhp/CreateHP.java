package net.shiro.createhp;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.data.CreateRegistrate;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;

import net.shiro.createhp.registry.CHPBlockEntities;
import net.shiro.createhp.registry.CHPBlocks;
import net.shiro.createhp.registry.CHPCreativeTab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for Create: Industrial Pressure.
 */
public class CreateHP implements ModInitializer {
	public static final String ID = "createhp";
	public static final String NAME = "Create: Industrial Pressure";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	// Create's Registrate variant — gives us Create-flavoured blocks/items/BEs.
	public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[{}] loading alongside {}", NAME, Create.NAME);

		// Force class-load of the registry holders so their static entries are defined...
		CHPBlocks.register();
		CHPBlockEntities.register();

		// ...then flush everything to the game registries (Fabric Registrate requirement).
		REGISTRATE.register();

		// Creative tab (after items exist) — groups all of the mod's blocks/items in one place.
		CHPCreativeTab.register();
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(ID, path);
	}
}
