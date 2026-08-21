package dev.utopia.core.unlock;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.utopia.core.UtopiaCore;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datapack-Registry der Freischalt-Bäume.
 *
 * Zusätzlich zur reinen Baum-Liste wird beim Laden die Umkehrung gebaut:
 * **Block- oder Item-Id -> Knoten**. Genau die braucht die Sperrprüfung, und sie
 * darf im Spielverlauf nicht neu gerechnet werden.
 *
 * Tags werden nicht aufgelöst, sondern beim Nachschlagen geprüft: ein Knoten mit
 * {@code "#create:seats"} hat eine Handvoll Tag-Einträge, die Prüfung kostet also
 * nichts, und wir hängen nicht an der Ladereihenfolge von Tags und Datapacks.
 */
public class UnlockTrees extends JsonDataLoader implements IdentifiableResourceReloadListener {

    private static final Gson GSON = new Gson();

    private static Map<Identifier, UnlockTree> trees = Map.of();
    private static List<Identifier> ordered = List.of();
    /** Item- oder Block-Id -> Knoten-Id ("create/basics"). */
    private static Map<Identifier, String> byId = Map.of();
    /** Tag -> Knoten-Id, beim Nachschlagen geprüft. */
    private static List<Map.Entry<TagKey<net.minecraft.item.Item>, String>> byItemTag = List.of();
    private static List<Map.Entry<TagKey<net.minecraft.block.Block>, String>> byBlockTag = List.of();

    public UnlockTrees() {
        super(GSON, "utopia/trees");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
        Map<Identifier, UnlockTree> parsed = new LinkedHashMap<>();
        prepared.forEach((id, json) -> UnlockTree.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> UtopiaCore.LOGGER.error("Baum {} konnte nicht geladen werden: {}", id, error))
                .ifPresent(tree -> parsed.put(id, tree)));

        List<Identifier> order = new ArrayList<>(parsed.keySet());
        order.sort(Comparator.comparingInt((Identifier id) -> parsed.get(id).order()).thenComparing(Identifier::toString));

        Map<Identifier, String> ids = new HashMap<>();
        List<Map.Entry<TagKey<net.minecraft.item.Item>, String>> itemTags = new ArrayList<>();
        List<Map.Entry<TagKey<net.minecraft.block.Block>, String>> blockTags = new ArrayList<>();

        parsed.forEach((treeId, tree) -> tree.nodes().forEach((key, node) -> {
            String nodeId = treeId.getPath() + "/" + key;
            for (String entry : node.unlocks()) {
                if (entry.startsWith("#")) {
                    Identifier tag = Identifier.tryParse(entry.substring(1));
                    if (tag == null) {
                        continue;
                    }
                    itemTags.add(Map.entry(TagKey.of(Registries.ITEM.getKey(), tag), nodeId));
                    blockTags.add(Map.entry(TagKey.of(Registries.BLOCK.getKey(), tag), nodeId));
                } else {
                    Identifier id = Identifier.tryParse(entry);
                    if (id != null) {
                        ids.put(id, nodeId);
                    }
                }
            }
        }));

        trees = Collections.unmodifiableMap(parsed);
        ordered = List.copyOf(order);
        byId = Collections.unmodifiableMap(ids);
        byItemTag = List.copyOf(itemTags);
        byBlockTag = List.copyOf(blockTags);
        UtopiaCore.LOGGER.info("{} Freischalt-Baeume geladen, {} gesperrte Ids", parsed.size(), ids.size());
    }

    @Override
    public Identifier getFabricId() {
        return UtopiaCore.id("unlock_trees");
    }

    // --- Zugriff ---------------------------------------------------------

    public static Map<Identifier, UnlockTree> all() {
        return trees;
    }

    public static List<Identifier> ordered() {
        return ordered;
    }

    public static UnlockTree tree(Identifier id) {
        return trees.get(id);
    }

    /** Knoten zu einer Knoten-Id ("create/basics"), oder null. */
    public static UnlockTree.Node node(String nodeId) {
        int slash = nodeId.indexOf('/');
        if (slash < 0) {
            return null;
        }
        for (Map.Entry<Identifier, UnlockTree> entry : trees.entrySet()) {
            if (entry.getKey().getPath().equals(nodeId.substring(0, slash))) {
                return entry.getValue().nodes().get(nodeId.substring(slash + 1));
            }
        }
        return null;
    }

    public static String treeOf(String nodeId) {
        int slash = nodeId.indexOf('/');
        return slash < 0 ? nodeId : nodeId.substring(0, slash);
    }

    /**
     * Welcher Knoten haelt diese Id gesperrt? null = frei.
     * Erst die direkte Zuordnung, dann die wenigen Tags.
     */
    public static String nodeFor(Identifier id) {
        String direct = byId.get(id);
        if (direct != null) {
            return direct;
        }
        if (!byItemTag.isEmpty()) {
            net.minecraft.item.Item item = Registries.ITEM.get(id);
            if (item != net.minecraft.item.Items.AIR) {
                for (Map.Entry<TagKey<net.minecraft.item.Item>, String> entry : byItemTag) {
                    if (Registries.ITEM.getEntry(item).isIn(entry.getKey())) {
                        return entry.getValue();
                    }
                }
            }
        }
        if (!byBlockTag.isEmpty()) {
            net.minecraft.block.Block block = Registries.BLOCK.get(id);
            if (block != net.minecraft.block.Blocks.AIR) {
                for (Map.Entry<TagKey<net.minecraft.block.Block>, String> entry : byBlockTag) {
                    if (Registries.BLOCK.getEntry(block).isIn(entry.getKey())) {
                        return entry.getValue();
                    }
                }
            }
        }
        return null;
    }

    public static boolean isEmpty() {
        return byId.isEmpty() && byItemTag.isEmpty() && byBlockTag.isEmpty();
    }

    /**
     * Client-Seite: dieselben Baeume, derselbe Index. Tags bleiben serverseitig -
     * der Client graut Tag-Eintraege also nicht aus, die Sperre selbst haelt
     * trotzdem, weil der Server entscheidet.
     */
    public static void acceptSynced(Map<Identifier, UnlockTree> synced, List<Identifier> order) {
        Map<Identifier, String> ids = new HashMap<>();
        synced.forEach((treeId, tree) -> tree.nodes().forEach((key, node) -> {
            String nodeId = treeId.getPath() + "/" + key;
            for (String entry : node.unlocks()) {
                if (!entry.startsWith("#")) {
                    Identifier id = Identifier.tryParse(entry);
                    if (id != null) {
                        ids.put(id, nodeId);
                    }
                }
            }
        }));
        trees = Collections.unmodifiableMap(synced);
        ordered = List.copyOf(order);
        byId = Collections.unmodifiableMap(ids);
    }
}
