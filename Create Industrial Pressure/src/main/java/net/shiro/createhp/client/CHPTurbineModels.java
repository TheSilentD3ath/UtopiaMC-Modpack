package net.shiro.createhp.client;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;

import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.CreateHP;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.content.turbine.TurbineOutputBlock;

/**
 * Verbundene Texturen der Dampfturbine. Gehaeuse, Einlass, Kondensatauslass und Abtrieb verschmelzen zu einer
 * Wand mit Rahmen nur am Aussenrand (TurbineCTBehaviour); jeder Teil hat dafuer einen eigenen Kachelbogen nach
 * Creates RECTANGLE-Aufbau, wie der Fluessigkeitstank. Die Boegen zeichnet tools/turbine_assets.py.
 *
 * Die Eintraege muessen vor dem ersten Zusammenstellen der Texturen existieren; CHPClient ruft dafuer init().
 */
public final class CHPTurbineModels {
	private CHPTurbineModels() {}

	private static final CTSpriteShiftEntry COPPER_CASING = ct("copper_casing");
	private static final CTSpriteShiftEntry NETHERITE_CASING = ct("netherite_casing");
	private static final CTSpriteShiftEntry COPPER_INLET = ct("copper_steam_inlet");
	private static final CTSpriteShiftEntry NETHERITE_INLET = ct("netherite_steam_inlet");
	private static final CTSpriteShiftEntry CONDENSATE = ct("condensate_outlet");
	private static final CTSpriteShiftEntry NETHERITE_CONDENSATE = ct("netherite_condensate_outlet");
	private static final CTSpriteShiftEntry OUTPUT_FRONT = ct("output_front");
	private static final CTSpriteShiftEntry NETHERITE_OUTPUT_FRONT = ct("netherite_output_front");

	private static CTSpriteShiftEntry ct(String name) {
		return CTSpriteShifter.getCT(AllCTTypes.RECTANGLE, CreateHP.id("block/turbine/" + name),
				CreateHP.id("block/turbine/" + name + "_connected"));
	}

	private static boolean netherite(PipeMaterial material) {
		return material == PipeMaterial.NETHERITE;
	}

	public static ConnectedTextureBehaviour casing(PipeMaterial material) {
		return new TurbineCTBehaviour(netherite(material) ? NETHERITE_CASING : COPPER_CASING);
	}

	public static ConnectedTextureBehaviour inlet(PipeMaterial material) {
		return new TurbineCTBehaviour(netherite(material) ? NETHERITE_INLET : COPPER_INLET);
	}

	public static ConnectedTextureBehaviour condensate() {
		return new TurbineCTBehaviour(state -> netheriteLook(state)
				? new CTSpriteShiftEntry[] { NETHERITE_CONDENSATE }
				: new CTSpriteShiftEntry[] { CONDENSATE });
	}

	/** Seiten aus Gehaeuse, Vorderseite mit Lager; beide verbunden, im Werkstoff der Turbine. */
	public static ConnectedTextureBehaviour output() {
		return new TurbineCTBehaviour(state -> netheriteLook(state)
				? new CTSpriteShiftEntry[] { NETHERITE_CASING, NETHERITE_OUTPUT_FRONT }
				: new CTSpriteShiftEntry[] { COPPER_CASING, OUTPUT_FRONT });
	}

	private static boolean netheriteLook(BlockState state) {
		return state.hasProperty(TurbineOutputBlock.NETHERITE) && state.getValue(TurbineOutputBlock.NETHERITE);
	}

	public static void init() {}
}
