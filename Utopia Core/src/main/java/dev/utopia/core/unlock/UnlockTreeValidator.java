package dev.utopia.core.unlock;

import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Server-seitige Strukturpruefung fuer Datapack- und Editor-Baeume. */
public final class UnlockTreeValidator {

    private static final Pattern NODE_KEY = Pattern.compile("[a-z0-9_.-]+");
    private static final int MAX_NODES = 2048;
    private static final int MAX_ENTRIES_PER_NODE = 4096;

    private UnlockTreeValidator() {
    }

    public static String validate(Identifier treeId, UnlockTree tree) {
        if (treeId == null || tree == null) {
            return "Baum-ID oder Baum fehlt";
        }
        if (tree.nodes().size() > MAX_NODES) {
            return "Zu viele Knoten (maximal " + MAX_NODES + ")";
        }
        for (Map.Entry<String, UnlockTree.Node> entry : tree.nodes().entrySet()) {
            String key = entry.getKey();
            UnlockTree.Node node = entry.getValue();
            if (!NODE_KEY.matcher(key).matches()) {
                return "Ungueltige Knoten-ID: " + key;
            }
            if (!Double.isFinite(node.x()) || !Double.isFinite(node.y())) {
                return "Ungueltige Position bei " + key;
            }
            if (node.cost() < 0 || node.level() < 0) {
                return "Negative Kosten oder Level bei " + key;
            }
            if (node.unlocks().size() > MAX_ENTRIES_PER_NODE || node.excludes().size() > MAX_ENTRIES_PER_NODE) {
                return "Zu viele Eintraege bei " + key;
            }
            for (String parent : node.parents()) {
                if (parent.equals(key)) {
                    return "Knoten " + key + " darf nicht von sich selbst abhaengen";
                }
                if (!tree.nodes().containsKey(parent)) {
                    return "Unbekannter Elternknoten " + parent + " bei " + key;
                }
            }
        }

        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String key : tree.nodes().keySet()) {
            if (hasCycle(key, tree, visiting, visited)) {
                return "Zyklische Abhaengigkeit bei " + key;
            }
        }
        return null;
    }

    private static boolean hasCycle(String key, UnlockTree tree, Set<String> visiting, Set<String> visited) {
        if (visited.contains(key)) {
            return false;
        }
        if (!visiting.add(key)) {
            return true;
        }
        for (String parent : tree.nodes().get(key).parents()) {
            if (hasCycle(parent, tree, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(key);
        visited.add(key);
        return false;
    }
}
