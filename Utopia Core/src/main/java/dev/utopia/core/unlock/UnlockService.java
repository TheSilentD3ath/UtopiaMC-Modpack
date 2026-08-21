package dev.utopia.core.unlock;

import dev.utopia.core.UtopiaCore;
import dev.utopia.core.character.CharacterAccess;
import dev.utopia.core.character.CharacterData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Freischaltpunkte, Knotenkauf und die Frage "darf der Spieler das schon?".
 *
 * Die Sperrprüfung selbst hängt in den beiden Entscheidungsstellen, die LevelZ
 * ohnehin hat ({@code playerLevelisHighEnough} und {@code listContainsItemOrBlock}).
 * Dadurch wirken Freischaltungen sofort überall, wo LevelZ heute sperrt:
 * Crafting, Benutzen, Tooltips, Ausgrauen in REI und EMI.
 */
public final class UnlockService {

    /** Grundmenge pro Gesamtlevel, bevor Klasse und Herkunft daraufkommen. */
    public static final int BASE_POINTS_PER_LEVEL = 1;

    private UnlockService() {
    }

    public static Identifier nodeId(String path) {
        return new Identifier("utopia", path);
    }

    // --- Punkte ----------------------------------------------------------

    public static int pointsPerLevel(CharacterData data) {
        return Math.max(0, BASE_POINTS_PER_LEVEL + data.bundle().unlockPointsPerLevel);
    }

    /** Aufruf bei jedem Gesamtlevel-Aufstieg. */
    public static void grantForLevel(PlayerEntity player) {
        CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
        int points = pointsPerLevel(data);
        if (points > 0) {
            data.addUnlockPoints(points);
        }
    }

    // --- Kosten und Anforderungen ----------------------------------------

    public static int cost(CharacterData data, String nodeId, UnlockTree.Node node) {
        int discount = data.bundle().unlockDiscount.getOrDefault(UnlockTrees.treeOf(nodeId), 0);
        return Math.max(0, node.cost() - discount);
    }

    public static int requiredLevel(CharacterData data, String nodeId, UnlockTree.Node node) {
        int offset = data.bundle().unlockLevelOffset.getOrDefault(UnlockTrees.treeOf(nodeId), 0);
        return Math.max(0, node.level() + offset);
    }

    // --- Kauf ------------------------------------------------------------

    /** @return null wenn kaufbar, sonst der Grund als Übersetzungsschlüssel. */
    @Nullable
    public static String whyNotBuyable(PlayerEntity player, CharacterData data, String nodeId, int overallLevel) {
        UnlockTree.Node node = UnlockTrees.node(nodeId);
        if (node == null) {
            return "message.utopia.unlock.unknown";
        }
        if (data.unlocks().contains(nodeId(nodeId))) {
            return "message.utopia.unlock.already";
        }
        for (String parent : node.parents()) {
            if (!data.unlocks().contains(nodeId(UnlockTrees.treeOf(nodeId) + "/" + parent))) {
                return "message.utopia.unlock.parent";
            }
        }
        if (overallLevel < requiredLevel(data, nodeId, node)) {
            return "message.utopia.unlock.level";
        }
        if (data.unlockPoints() < cost(data, nodeId, node)) {
            return "message.utopia.unlock.points";
        }
        return null;
    }

    public static boolean buy(PlayerEntity player, CharacterData data, String nodeId, int overallLevel) {
        if (whyNotBuyable(player, data, nodeId, overallLevel) != null) {
            return false;
        }
        UnlockTree.Node node = UnlockTrees.node(nodeId);
        data.setUnlockPoints(data.unlockPoints() - cost(data, nodeId, node));
        data.unlock(nodeId(nodeId));
        UtopiaCore.LOGGER.info("{} schaltet {} frei", player.getName().getString(), nodeId);
        return true;
    }

    // --- Sperre ----------------------------------------------------------

    /** Gehoert die Id zu einem Knoten, den dieser Spieler noch nicht hat? */
    public static boolean isLocked(PlayerEntity player, Identifier id) {
        if (UnlockTrees.isEmpty() || id == null) {
            return false;
        }
        String node = UnlockTrees.nodeFor(id);
        if (node == null) {
            return false;
        }
        return !((CharacterAccess) player).utopia$getCharacter().unlocks().contains(nodeId(node));
    }

    /** Bequemer Einstieg fuer die geerbten Pfade, die mit rohen Item-Ids arbeiten. */
    public static boolean isLockedItem(PlayerEntity player, int rawItemId) {
        if (UnlockTrees.isEmpty()) {
            return false;
        }
        net.minecraft.item.Item item = Registries.ITEM.get(rawItemId);
        return item != net.minecraft.item.Items.AIR && isLocked(player, Registries.ITEM.getId(item));
    }

    /** Text fuer Tooltips und Rueckmeldungen: welcher Knoten fehlt. */
    @Nullable
    public static String missingNode(PlayerEntity player, Identifier id) {
        String node = UnlockTrees.nodeFor(id);
        if (node == null) {
            return null;
        }
        return ((CharacterAccess) player).utopia$getCharacter().unlocks().contains(nodeId(node)) ? null : node;
    }
}
