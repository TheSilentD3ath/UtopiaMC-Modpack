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

    public enum Buyability {
        BUYABLE(null),
        UNKNOWN("message.utopia.unlock.unknown"),
        ALREADY_OWNED("message.utopia.unlock.already"),
        MISSING_PARENT("message.utopia.unlock.parent"),
        LEVEL_TOO_LOW("message.utopia.unlock.level"),
        NOT_ENOUGH_POINTS("message.utopia.unlock.points");

        private final String messageKey;

        Buyability(String messageKey) {
            this.messageKey = messageKey;
        }

        @Nullable
        public String messageKey() {
            return messageKey;
        }
    }

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

    public static Buyability buyability(CharacterData data, String nodeId, int overallLevel) {
        UnlockTree.Node node = UnlockTrees.node(nodeId);
        if (node == null) {
            return Buyability.UNKNOWN;
        }
        if (owns(data, nodeId)) {
            return Buyability.ALREADY_OWNED;
        }
        for (String parent : node.parents()) {
            if (!owns(data, UnlockTrees.treeOf(nodeId) + "/" + parent)) {
                return Buyability.MISSING_PARENT;
            }
        }
        if (overallLevel < requiredLevel(data, nodeId, node)) {
            return Buyability.LEVEL_TOO_LOW;
        }
        if (data.unlockPoints() < cost(data, nodeId, node)) {
            return Buyability.NOT_ENOUGH_POINTS;
        }
        return Buyability.BUYABLE;
    }

    /** @return null wenn kaufbar, sonst der Grund als Übersetzungsschlüssel. */
    @Nullable
    public static String whyNotBuyable(CharacterData data, String nodeId, int overallLevel) {
        return buyability(data, nodeId, overallLevel).messageKey();
    }

    public static boolean buy(PlayerEntity player, CharacterData data, String nodeId, int overallLevel) {
        if (whyNotBuyable(data, nodeId, overallLevel) != null) {
            return false;
        }
        UnlockTree.Node node = UnlockTrees.node(nodeId);
        data.setUnlockPoints(data.unlockPoints() - cost(data, nodeId, node));
        data.unlock(nodeId(nodeId));
        UtopiaCore.LOGGER.info("{} schaltet {} frei", player.getName().getString(), nodeId);
        return true;
    }

    // --- Sperre ----------------------------------------------------------

    /** Direkter Besitz oder ein dokumentierter Besitzer des alten Sammelknotens. */
    public static boolean owns(CharacterData data, String nodePath) {
        if (data.unlocks().contains(nodeId(nodePath))) {
            return true;
        }
        UnlockTree.Node node = UnlockTrees.node(nodePath);
        if (node != null) {
            for (String legacy : node.legacyOwners()) {
                if (data.unlocks().contains(nodeId(legacy))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Gehoert die Id zu einem Knoten, den dieser Spieler noch nicht hat? */
    public static boolean isLocked(PlayerEntity player, Identifier id) {
        if (UnlockTrees.isEmpty() || id == null) {
            return false;
        }
        String node = UnlockTrees.nodeFor(id);
        if (node == null) {
            return false;
        }
        return !owns(((CharacterAccess) player).utopia$getCharacter(), node);
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
        return owns(((CharacterAccess) player).utopia$getCharacter(), node) ? null : node;
    }
}
