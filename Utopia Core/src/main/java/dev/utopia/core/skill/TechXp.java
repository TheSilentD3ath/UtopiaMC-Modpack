package dev.utopia.core.skill;

import dev.utopia.core.character.CharacterAccess;
import dev.utopia.core.character.CharacterData;
import net.levelz.entity.LevelExperienceOrbEntity;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Erfahrung fuers erste Bauen einer Maschinenart - zaehlt als Technik.
 *
 * Einmal pro Blockart und Spieler. Belohnt wird das Kennenlernen eines
 * Bauteils, nicht die Menge; eine Reihe Foerderbaender ist so keine Farm.
 *
 * Die Logik liegt hier und nicht im Mixin, weil zwei Wege hineinfuehren:
 * {@code BlockItem.place} faengt praktisch jede Platzierung durch einen Spieler,
 * {@code Block.onPlaced} bleibt als zweiter Weg fuer Bloecke, die anders gesetzt
 * werden. Doppelte Aufrufe sind harmlos - beim zweiten gibt es die Blockart
 * schon.
 */
public final class TechXp {

    private static final int FIRST_BUILD_XP = 8;

    private TechXp() {
    }

    public static void reward(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state) {
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (!id.getNamespace().startsWith("create")) {
            return;
        }
        CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
        if (!data.markTechBlock(id)) {
            return;
        }
        LevelExperienceOrbEntity.spawn(world, Vec3d.ofCenter(pos).add(0.0D, 0.5D, 0.0D),
                XpBonus.apply(player, XpBonus.tech(), FIRST_BUILD_XP));
    }
}
