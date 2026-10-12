package net.shiro.createhp.registry;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankItem;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntry;

import java.util.function.Supplier;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.shiro.createhp.CreateHP;
import net.shiro.createhp.client.CHPPipeAttachmentModel;
import net.shiro.createhp.client.CHPTankModels;
import net.shiro.createhp.client.CHPTurbineModels;
import net.shiro.createhp.content.HighPressurePumpBlock;
import net.shiro.createhp.content.PressureEncasedPipeBlock;
import net.shiro.createhp.content.PressureGlassPipeBlock;
import net.shiro.createhp.content.PressurePipeBlock;
import net.shiro.createhp.content.PressurePipeBlockEntity;
import net.shiro.createhp.content.PumpTier;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.content.pressure.PressureGaugeBlock;
import net.shiro.createhp.content.steam.SteamOutletBlock;
import net.shiro.createhp.content.tank.PressureTankBlock;
import net.shiro.createhp.content.tank.PressureTankBlockEntity;
import net.shiro.createhp.content.turbine.CondensateOutletBlock;
import net.shiro.createhp.content.turbine.SteamInletBlock;
import net.shiro.createhp.content.turbine.TurbineCasingBlock;
import net.shiro.createhp.content.turbine.TurbineOutputBlock;
import net.shiro.createhp.content.turbine.TurbineRotorBlock;
import net.shiro.createhp.content.valve.PressureValveBlock;
import net.shiro.createhp.content.valve.ValveKind;

public class CHPBlocks {
	private static final CreateRegistrate REGISTRATE = CreateHP.REGISTRATE;

	public static final BlockEntry<PressurePipeBlock> HIGH_PRESSURE_PIPE =
			REGISTRATE.block("high_pressure_pipe", p -> new PressurePipeBlock(p, PipeMaterial.COPPER,
					() -> CHPBlockEntities.HIGH_PRESSURE_PIPE.get(),
					() -> CHPBlocks.HIGH_PRESSURE_GLASS_PIPE.getDefaultState()))
					.initialProperties(() -> Blocks.COPPER_BLOCK)
					.properties(p -> p.noOcclusion())
					// THE missing piece: Create 6 renders pipes via a custom ForwardingBakedModel that
					// wraps the baked model. Without this, the part-models render as broken stubs. This
					// is the exact call Create uses for its own fluid_pipe (verified from decompiled source).
					.onRegister(CreateRegistrate.blockModel(() -> CHPPipeAttachmentModel::withAO))
					.item()
					.build()
					.register();

	// See-through variant (wrench-created from the pipe). Blockstate/model shipped as static resources.
	public static final BlockEntry<PressureGlassPipeBlock> HIGH_PRESSURE_GLASS_PIPE =
			REGISTRATE.block("high_pressure_glass_pipe", p -> new PressureGlassPipeBlock(p, PipeMaterial.COPPER,
					() -> CHPBlockEntities.HIGH_PRESSURE_GLASS_PIPE.get(),
					() -> HIGH_PRESSURE_PIPE.get()))
					.initialProperties(() -> Blocks.COPPER_BLOCK)
					.properties(p -> p.noOcclusion())
					.onRegister(CreateRegistrate.blockModel(() -> CHPPipeAttachmentModel::withAO))
					.register();

	// --- Netherite tier (top material tier; same function, netherite look) ---
	public static final BlockEntry<PressurePipeBlock> NETHERITE_PRESSURE_PIPE =
			REGISTRATE.block("netherite_pressure_pipe", p -> new PressurePipeBlock(p, PipeMaterial.NETHERITE,
					() -> CHPBlockEntities.NETHERITE_PRESSURE_PIPE.get(),
					() -> CHPBlocks.NETHERITE_GLASS_PIPE.getDefaultState()))
					.initialProperties(() -> Blocks.NETHERITE_BLOCK)
					.properties(p -> p.noOcclusion())
					.onRegister(CreateRegistrate.blockModel(() -> CHPPipeAttachmentModel::netheriteWithAO))
					.item()
					.build()
					.register();

	public static final BlockEntry<PressureGlassPipeBlock> NETHERITE_GLASS_PIPE =
			REGISTRATE.block("netherite_glass_pipe", p -> new PressureGlassPipeBlock(p, PipeMaterial.NETHERITE,
					() -> CHPBlockEntities.NETHERITE_GLASS_PIPE.get(),
					() -> NETHERITE_PRESSURE_PIPE.get()))
					.initialProperties(() -> Blocks.NETHERITE_BLOCK)
					.properties(p -> p.noOcclusion())
					.onRegister(CreateRegistrate.blockModel(() -> CHPPipeAttachmentModel::netheriteWithAO))
					.register();

	// --- Ummantelte Druckrohre: Kupfergehaeuse auf ein Druckrohr oder Glasdruckrohr, wie bei Creates Fluessigkeitsrohr ---
	public static final BlockEntry<PressureEncasedPipeBlock> ENCASED_HIGH_PRESSURE_PIPE =
			encasedPipe("encased_high_pressure_pipe", PipeMaterial.COPPER, HIGH_PRESSURE_PIPE,
					() -> CHPBlockEntities.HIGH_PRESSURE_PIPE.get());
	public static final BlockEntry<PressureEncasedPipeBlock> ENCASED_NETHERITE_PRESSURE_PIPE =
			encasedPipe("encased_netherite_pressure_pipe", PipeMaterial.NETHERITE, NETHERITE_PRESSURE_PIPE,
					() -> CHPBlockEntities.NETHERITE_PRESSURE_PIPE.get());

	private static BlockEntry<PressureEncasedPipeBlock> encasedPipe(String id, PipeMaterial material,
			BlockEntry<PressurePipeBlock> pipe, Supplier<BlockEntityType<PressurePipeBlockEntity>> blockEntityType) {
		return REGISTRATE.block(id, p -> new PressureEncasedPipeBlock(p, material, pipe::get, blockEntityType))
				// Wie das Rohr darin: Das Gehaeuse soll nicht aendern, wie lange man am Netherit-Rohr abbaut.
				.initialProperties(() -> base(material))
				.properties(p -> p.noOcclusion())
				// Wie Creates ummanteltes Rohr: die geschlossenen Seiten sind Kupfergehaeuse und verbinden sich mit
				// Gehaeusen daneben. Lambdas, damit der Server die Client-Klassen nicht laedt.
				.onRegister(CreateRegistrate.connectedTextures(() -> new EncasedCTBehaviour(AllSpriteShifts.COPPER_CASING)))
				.onRegister(CreateRegistrate.casingConnectivity((block, cc) -> cc.make(block, AllSpriteShifts.COPPER_CASING,
						(state, face) -> !state.getValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(face)))))
				// Der Abfluss zu einem Tank wird wie am Rohr in den Teilen des Werkstoffs gezeichnet.
				.onRegister(CreateRegistrate.blockModel(() -> CHPPipeAttachmentModel.factory(material)))
				.transform(EncasingRegistry.addVariantTo(pipe))
				.register();
	}

	// --- Druckventile: je Art in Kupfer und Netherit; Blockstates und Modelle liegen als feste Ressourcen bei ---
	public static final BlockEntry<PressureValveBlock> PRESSURE_VALVE =
			valve("pressure_valve", ValveKind.SHUTOFF, PipeMaterial.COPPER);
	public static final BlockEntry<PressureValveBlock> NETHERITE_PRESSURE_VALVE =
			valve("netherite_pressure_valve", ValveKind.SHUTOFF, PipeMaterial.NETHERITE);
	public static final BlockEntry<PressureValveBlock> PRESSURE_REGULATOR =
			valve("pressure_regulator", ValveKind.REGULATOR, PipeMaterial.COPPER);
	public static final BlockEntry<PressureValveBlock> NETHERITE_PRESSURE_REGULATOR =
			valve("netherite_pressure_regulator", ValveKind.REGULATOR, PipeMaterial.NETHERITE);
	public static final BlockEntry<PressureValveBlock> RELIEF_VALVE =
			valve("relief_valve", ValveKind.RELIEF, PipeMaterial.COPPER);
	public static final BlockEntry<PressureValveBlock> NETHERITE_RELIEF_VALVE =
			valve("netherite_relief_valve", ValveKind.RELIEF, PipeMaterial.NETHERITE);

	private static BlockEntry<PressureValveBlock> valve(String id, ValveKind kind, PipeMaterial material) {
		return REGISTRATE.block(id, p -> new PressureValveBlock(p, kind, material,
						() -> CHPBlockEntities.PRESSURE_VALVE.get()))
				// Wie die Rohre desselben Materials, damit ein Ventil sich nicht anders abbauen laesst als sein Rohr.
				.initialProperties(() -> material == PipeMaterial.NETHERITE ? Blocks.NETHERITE_BLOCK : Blocks.COPPER_BLOCK)
				.properties(p -> p.noOcclusion())
				// Creates Ventil zeichnet seine Rohranschluesse wie ein Rohr; wir mit den Teilen des passenden Materials.
				.onRegister(CreateRegistrate.blockModel(() -> CHPPipeAttachmentModel.factory(material)))
				.item()
				.build()
				.register();
	}

	// --- Druckmesser: Rohrstueck mit Komparatorausgang, je Werkstoff ---
	public static final BlockEntry<PressureGaugeBlock> PRESSURE_GAUGE =
			gauge("pressure_gauge", PipeMaterial.COPPER);
	public static final BlockEntry<PressureGaugeBlock> NETHERITE_PRESSURE_GAUGE =
			gauge("netherite_pressure_gauge", PipeMaterial.NETHERITE);

	private static BlockEntry<PressureGaugeBlock> gauge(String id, PipeMaterial material) {
		return REGISTRATE.block(id, p -> new PressureGaugeBlock(p, material, () -> CHPBlockEntities.PRESSURE_GAUGE.get()))
				.initialProperties(() -> base(material))
				.properties(p -> p.noOcclusion())
				.item()
				.build()
				.register();
	}

	// --- Drucktanks: Creates Tank mit mehr Inhalt und Grenzwert; Modelle und Blockstates als feste Ressourcen ---
	public static final BlockEntry<PressureTankBlock> PRESSURE_TANK =
			tank("pressure_tank", PipeMaterial.COPPER, () -> CHPBlockEntities.PRESSURE_TANK.get());
	public static final BlockEntry<PressureTankBlock> NETHERITE_PRESSURE_TANK =
			tank("netherite_pressure_tank", PipeMaterial.NETHERITE, () -> CHPBlockEntities.NETHERITE_PRESSURE_TANK.get());

	private static BlockEntry<PressureTankBlock> tank(String id, PipeMaterial material,
			Supplier<? extends BlockEntityType<? extends PressureTankBlockEntity>> blockEntityType) {
		return REGISTRATE.block(id, p -> new PressureTankBlock(p, material, blockEntityType))
				.initialProperties(() -> material == PipeMaterial.NETHERITE ? Blocks.NETHERITE_BLOCK : Blocks.COPPER_BLOCK)
				// Wie Creates Tank: durchsichtig fuer das Fenster, aber Redstone-leitend wie ein voller Block.
				.properties(p -> p.noOcclusion().isRedstoneConductor((state, level, pos) -> true))
				.onRegister(CreateRegistrate.blockModel(() -> CHPTankModels.factory(material)))
				// Creates Tank-Item: setzt beim Bauen auf breite Tanks ganze Lagen und behandelt Tank-NBT.
				.item(FluidTankItem::new)
				.build()
				.register();
	}

	// Dampfauslass: an einen Kessel gesetzt, gibt er dessen freie Stufen als Dampf an Rohre ab.
	public static final BlockEntry<SteamOutletBlock> STEAM_OUTLET =
			REGISTRATE.block("steam_outlet", p -> new SteamOutletBlock(p, () -> CHPBlockEntities.STEAM_OUTLET.get()))
					.initialProperties(() -> Blocks.COPPER_BLOCK)
					.properties(p -> p.noOcclusion())
					.item()
					.build()
					.register();

	// --- Dampfturbine: Gehaeuse, Rotor und Einlass je Werkstoff; Kondensatauslass und Abtrieb passen in beide.
	// Die Aussenteile haben verbundene Texturen (CHPTurbineModels), damit eine Wand wie eine Platte aussieht. ---
	public static final BlockEntry<TurbineCasingBlock> TURBINE_CASING =
			turbineCasing("turbine_casing", PipeMaterial.COPPER);
	public static final BlockEntry<TurbineCasingBlock> NETHERITE_TURBINE_CASING =
			turbineCasing("netherite_turbine_casing", PipeMaterial.NETHERITE);
	public static final BlockEntry<TurbineRotorBlock> TURBINE_ROTOR =
			turbineRotor("turbine_rotor", PipeMaterial.COPPER);
	public static final BlockEntry<TurbineRotorBlock> NETHERITE_TURBINE_ROTOR =
			turbineRotor("netherite_turbine_rotor", PipeMaterial.NETHERITE);
	public static final BlockEntry<SteamInletBlock> STEAM_INLET =
			steamInlet("steam_inlet", PipeMaterial.COPPER);
	public static final BlockEntry<SteamInletBlock> NETHERITE_STEAM_INLET =
			steamInlet("netherite_steam_inlet", PipeMaterial.NETHERITE);

	public static final BlockEntry<CondensateOutletBlock> CONDENSATE_OUTLET =
			REGISTRATE.block("condensate_outlet", p -> new CondensateOutletBlock(p, () -> CHPBlockEntities.CONDENSATE_OUTLET.get()))
					.initialProperties(() -> Blocks.COPPER_BLOCK)
					.onRegister(CreateRegistrate.connectedTextures(() -> CHPTurbineModels.condensate()))
					.item()
					.build()
					.register();

	public static final BlockEntry<TurbineOutputBlock> TURBINE_OUTPUT =
			REGISTRATE.block("turbine_output", p -> new TurbineOutputBlock(p, () -> CHPBlockEntities.TURBINE_OUTPUT.get()))
					.initialProperties(() -> Blocks.COPPER_BLOCK)
					.onRegister(CreateRegistrate.connectedTextures(() -> CHPTurbineModels.output()))
					.item()
					.build()
					.register();

	// Netherit-Teile halten wie Netheritbloecke auch einer Explosion am eigenen Einlass stand.
	private static Block base(PipeMaterial material) {
		return material == PipeMaterial.NETHERITE ? Blocks.NETHERITE_BLOCK : Blocks.COPPER_BLOCK;
	}

	private static BlockEntry<TurbineCasingBlock> turbineCasing(String id, PipeMaterial material) {
		return REGISTRATE.block(id, p -> new TurbineCasingBlock(p, material))
				.initialProperties(() -> base(material))
				.onRegister(CreateRegistrate.connectedTextures(() -> CHPTurbineModels.casing(material)))
				.item()
				.build()
				.register();
	}

	private static BlockEntry<TurbineRotorBlock> turbineRotor(String id, PipeMaterial material) {
		return REGISTRATE.block(id, p -> new TurbineRotorBlock(p, material))
				.initialProperties(() -> base(material))
				.item()
				.build()
				.register();
	}

	private static BlockEntry<SteamInletBlock> steamInlet(String id, PipeMaterial material) {
		return REGISTRATE.block(id, p -> new SteamInletBlock(p, material, () -> CHPBlockEntities.STEAM_INLET.get()))
				.initialProperties(() -> base(material))
				.onRegister(CreateRegistrate.connectedTextures(() -> CHPTurbineModels.inlet(material)))
				.item()
				.build()
				.register();
	}

	// Five pump tiers (3 copper + 2 netherite). Blockstate/models/lang are shipped as static resources.
	public static final BlockEntry<HighPressurePumpBlock> PUMP_T1 = pump(PumpTier.TIER_1);
	public static final BlockEntry<HighPressurePumpBlock> PUMP_T2 = pump(PumpTier.TIER_2);
	public static final BlockEntry<HighPressurePumpBlock> PUMP_T3 = pump(PumpTier.TIER_3);
	public static final BlockEntry<HighPressurePumpBlock> PUMP_T4 = pump(PumpTier.TIER_4);
	public static final BlockEntry<HighPressurePumpBlock> PUMP_T5 = pump(PumpTier.TIER_5);

	private static BlockEntry<HighPressurePumpBlock> pump(PumpTier tier) {
		return REGISTRATE.block(tier.id, p -> new HighPressurePumpBlock(p, tier))
				.initialProperties(() -> AllBlocks.MECHANICAL_PUMP.get()) // copy vanilla pump props
				.properties(p -> p.noOcclusion())
				.item()
				.build()
				.register();
	}

	public static void register() {}
}
