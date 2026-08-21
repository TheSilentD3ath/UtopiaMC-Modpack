package net.shiro.createhp.content;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Marker for any solid high-pressure pipe block in this mod (copper HP pipe, netherite pipe, ...).
 * Used to make them all immune to bursting and to route wrench -> glass conversion to the matching
 * see-through variant, without a growing instanceof chain.
 */
public interface PressurePipe {
	/** The default state of the see-through glass variant this pipe turns into when wrenched. */
	BlockState getGlassPipeDefaultState();
}
