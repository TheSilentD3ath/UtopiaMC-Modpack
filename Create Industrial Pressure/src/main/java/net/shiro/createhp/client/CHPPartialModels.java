package net.shiro.createhp.client;

import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.shiro.createhp.CreateHP;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Copies of Create's pipe-attachment partial models (rims / connectors / casing), one set per pipe
 * material. Each set points at createhp:block/&lt;base&gt;/... models, which use that material's
 * recolored texture. {@link CHPPipeAttachmentModel} draws the fittings from one of these sets so the
 * fittings match the pipe (blue copper, dark netherite, ...) instead of Create's copper ones.
 *
 * PartialModel.of(..) auto-registers each model for baking, so all sets must be constructed during
 * client init (before models bake) — CHPClient.init() forces that via {@link #init()}.
 */
public final class CHPPartialModels {

	/** Copper / blue high pressure pipe fittings. */
	public static final CHPPartialModels COPPER = new CHPPartialModels("fluid_pipe");
	/** Netherite pipe fittings. */
	public static final CHPPartialModels NETHERITE = new CHPPartialModels("netherite_fluid_pipe");

	public final Map<ComponentPartials, Map<Direction, PartialModel>> attachments =
			new EnumMap<>(ComponentPartials.class);
	public final PartialModel casing;

	private CHPPartialModels(String base) {
		this.casing = block(base + "/casing");
		for (ComponentPartials cp : ComponentPartials.values()) {
			Map<Direction, PartialModel> map = new HashMap<>();
			for (Direction d : Iterate.directions) {
				map.put(d, block(base + "/" + cp.name().toLowerCase(Locale.ROOT) + "/" + d.getName()));
			}
			attachments.put(cp, map);
		}
	}

	private static PartialModel block(String path) {
		return PartialModel.of(new ResourceLocation(CreateHP.ID, "block/" + path));
	}

	/** Force static-init so every set's partial models register for baking. Called from CHPClient. */
	public static void init() {}
}
