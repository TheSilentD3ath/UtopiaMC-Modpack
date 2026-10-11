package net.shiro.createhp;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;

import net.createmod.catnip.lang.FontHelper;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;

import net.shiro.createhp.config.CHPConfig;
import net.shiro.createhp.registry.CHPBlockEntities;
import net.shiro.createhp.registry.CHPBlocks;
import net.shiro.createhp.registry.CHPCreativeTab;
import net.shiro.createhp.registry.CHPDisplaySources;

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
	// Die Tooltip-Fabrik muss vor dem ersten Item stehen: Registrate merkt sie sich pro Eintrag beim Anlegen.
	// Sie zeigt Creates Shift-Beschreibung, wo es einen ".tooltip.summary"-Text gibt; sonst aendert sich nichts.
	public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(ID)
			.setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));

	@Override
	public void onInitialize() {
		LOGGER.info("[{}] loading alongside {}", NAME, Create.NAME);

		// Force class-load of the registry holders so their static entries are defined...
		CHPBlocks.register();
		CHPBlockEntities.register();
		CHPDisplaySources.register();

		// ...then flush everything to the game registries (Fabric Registrate requirement).
		REGISTRATE.register();

		// Die Druckquelle braucht die fertigen Block-Entity-Typen, also erst nach dem Registrieren.
		CHPDisplaySources.associate();
		CHPConfig.register();

		// Creative tab (after items exist) — groups all of the mod's blocks/items in one place.
		CHPCreativeTab.register();
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(ID, path);
	}
}
