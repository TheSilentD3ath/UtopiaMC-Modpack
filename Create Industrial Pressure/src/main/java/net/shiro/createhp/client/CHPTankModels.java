package net.shiro.createhp.client;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.tterrag.registrate.util.nullness.NonNullFunction;

import net.minecraft.client.resources.model.BakedModel;
import net.shiro.createhp.CreateHP;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.mixin.FluidTankModelInvoker;

import java.util.Locale;

/**
 * Verbundene Texturen der Drucktanks, aufgebaut wie Creates AllSpriteShifts fuer den Fluessigkeitstank: Seite,
 * Deckel und Innenseite, je mit einer "_connected"-Variante fuer grosse Tanks.
 *
 * Die Eintraege muessen vor dem ersten Zusammenstellen der Texturen existieren; CHPClient ruft dafuer init().
 */
public final class CHPTankModels {
	private CHPTankModels() {}

	private record Shifts(CTSpriteShiftEntry side, CTSpriteShiftEntry top, CTSpriteShiftEntry inner) {}

	private static final Shifts COPPER = shifts(PipeMaterial.COPPER);
	private static final Shifts NETHERITE = shifts(PipeMaterial.NETHERITE);

	private static Shifts shifts(PipeMaterial material) {
		String base = "pressure_tank/" + material.name().toLowerCase(Locale.ROOT) + "_";
		return new Shifts(ct(base + "tank"), ct(base + "top"), ct(base + "inner"));
	}

	private static CTSpriteShiftEntry ct(String name) {
		return CTSpriteShifter.getCT(AllCTTypes.RECTANGLE, CreateHP.id("block/" + name),
				CreateHP.id("block/" + name + "_connected"));
	}

	public static NonNullFunction<BakedModel, BakedModel> factory(PipeMaterial material) {
		Shifts shifts = material == PipeMaterial.NETHERITE ? NETHERITE : COPPER;
		return model -> FluidTankModelInvoker.createhp$create(model, shifts.side, shifts.top, shifts.inner);
	}

	public static void init() {}
}
