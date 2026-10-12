package net.shiro.createhp.client;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.content.turbine.TurbineOutputBlock;
import net.shiro.createhp.content.turbine.TurbinePart;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Verbindet die Aussenteile einer Turbine untereinander, nicht nur gleiche Bloecke wie Creates Standard: Gehaeuse,
 * Einlass, Kondensatauslass und Abtrieb sollen als eine Wand erscheinen. Werkstoffe mischen sich nicht; Teile ohne
 * Werkstoff (Kondensatauslass, Abtrieb) verbinden sich mit beiden.
 */
public class TurbineCTBehaviour extends ConnectedTextureBehaviour.Base {

	/** Boegen je Blockzustand: Abtrieb und Kondensatauslass haben eine Kupfer- und eine Netherit-Ausfuehrung. */
	private final Function<BlockState, CTSpriteShiftEntry[]> shifts;

	public TurbineCTBehaviour(CTSpriteShiftEntry... shifts) {
		this(state -> shifts);
	}

	public TurbineCTBehaviour(Function<BlockState, CTSpriteShiftEntry[]> shifts) {
		this.shifts = shifts;
	}

	// Ein Block kann mehrere Boegen haben (Abtrieb: Seiten und Vorderseite); welcher gilt, sagt das Sprite des Quads.
	@Nullable
	@Override
	public CTSpriteShiftEntry getShift(BlockState state, Direction direction, @Nullable TextureAtlasSprite sprite) {
		CTSpriteShiftEntry[] candidates = shifts.apply(state);
		if (sprite == null)
			return candidates[0];
		for (CTSpriteShiftEntry shift : candidates)
			if (shift.getOriginal() == sprite)
				return shift;
		return null;
	}

	@Override
	public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos,
			BlockPos otherPos, Direction face) {
		return !isBeingBlocked(state, reader, pos, otherPos, face) && joins(state.getBlock(), other.getBlock());
	}

	private static boolean joins(Block own, Block other) {
		if (!isShell(own) || !isShell(other))
			return false;
		PipeMaterial a = materialOf(own);
		PipeMaterial b = materialOf(other);
		return a == null || b == null || a == b;
	}

	private static boolean isShell(Block block) {
		return block instanceof TurbineOutputBlock
				|| block instanceof TurbinePart part && part.getTurbineRole().isShell();
	}

	@Nullable
	private static PipeMaterial materialOf(Block block) {
		return block instanceof TurbinePart part ? part.getTurbineMaterial() : null;
	}
}
