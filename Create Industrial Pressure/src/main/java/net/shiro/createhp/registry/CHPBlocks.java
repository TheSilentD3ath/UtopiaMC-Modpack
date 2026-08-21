package net.shiro.createhp.registry;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.world.level.block.Blocks;
import net.shiro.createhp.CreateHP;
import net.shiro.createhp.client.CHPPipeAttachmentModel;
import net.shiro.createhp.content.HighPressureGlassPipeBlock;
import net.shiro.createhp.content.HighPressurePipeBlock;
import net.shiro.createhp.content.HighPressurePumpBlock;
import net.shiro.createhp.content.NetheriteGlassPipeBlock;
import net.shiro.createhp.content.NetheritePressurePipeBlock;
import net.shiro.createhp.content.PumpTier;

public class CHPBlocks {
	private static final CreateRegistrate REGISTRATE = CreateHP.REGISTRATE;

	public static final BlockEntry<HighPressurePipeBlock> HIGH_PRESSURE_PIPE =
			REGISTRATE.block("high_pressure_pipe", HighPressurePipeBlock::new)
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
	public static final BlockEntry<HighPressureGlassPipeBlock> HIGH_PRESSURE_GLASS_PIPE =
			REGISTRATE.block("high_pressure_glass_pipe", HighPressureGlassPipeBlock::new)
					.initialProperties(() -> Blocks.COPPER_BLOCK)
					.properties(p -> p.noOcclusion())
					.item()
					.build()
					.register();

	// --- Netherite tier (top material tier; same function, netherite look) ---
	public static final BlockEntry<NetheritePressurePipeBlock> NETHERITE_PRESSURE_PIPE =
			REGISTRATE.block("netherite_pressure_pipe", NetheritePressurePipeBlock::new)
					.initialProperties(() -> Blocks.NETHERITE_BLOCK)
					.properties(p -> p.noOcclusion())
					.onRegister(CreateRegistrate.blockModel(() -> CHPPipeAttachmentModel::netheriteWithAO))
					.item()
					.build()
					.register();

	public static final BlockEntry<NetheriteGlassPipeBlock> NETHERITE_GLASS_PIPE =
			REGISTRATE.block("netherite_glass_pipe", NetheriteGlassPipeBlock::new)
					.initialProperties(() -> Blocks.NETHERITE_BLOCK)
					.properties(p -> p.noOcclusion())
					.item()
					.build()
					.register();

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
