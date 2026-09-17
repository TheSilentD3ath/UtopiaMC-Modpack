package dev.utopia.core.unlock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.utopia.core.UtopiaCore;
import net.fabricmc.loader.api.FabricLoader;
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
import java.util.Set;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

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

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path EDITOR_ROOT = FabricLoader.getInstance().getConfigDir()
            .resolve("utopiacore").resolve("trees");

    /** Ungefilterte Datapack-Baeume; Editor-Dateien werden daruebergelegt. */
    private static Map<Identifier, UnlockTree> resourceTrees = Map.of();
    /** Aktive Quellen vor requires_mods-Filterung, damit der Editor Unsichtbares erhaelt. */
    private static Map<Identifier, UnlockTree> configuredTrees = Map.of();
    private static Map<Identifier, UnlockTree> trees = Map.of();
    private static List<Identifier> ordered = List.of();
    /** Item- oder Block-Id -> Knoten-Id ("create/basics"). */
    private static Map<Identifier, String> byId = Map.of();
    /** Namespace-Wildcard (z.B. "createaddition:*") -> Knoten-Id. */
    private static Map<String, String> byNamespace = Map.of();
    /** Bewusst freie Ids innerhalb eines gesperrten Namespace. */
    private static Set<Identifier> excludedIds = Set.of();
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
                .ifPresent(tree -> {
                    String problem = UnlockTreeValidator.validate(id, tree);
                    if (problem == null) {
                        parsed.put(id, tree);
                    } else {
                        UtopiaCore.LOGGER.error("Baum {} ist ungueltig: {}", id, problem);
                    }
                }));

        resourceTrees = Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
        parsed.putAll(loadOverrides());
        install(parsed);
    }

    private static void install(Map<Identifier, UnlockTree> sources) {
        configuredTrees = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        Map<Identifier, UnlockTree> parsed = new LinkedHashMap<>();
        sources.forEach((id, tree) -> parsed.put(id, availableTree(id, tree)));

        List<Identifier> order = new ArrayList<>(parsed.keySet());
        order.sort(Comparator.comparingInt((Identifier id) -> parsed.get(id).order()).thenComparing(Identifier::toString));

        Map<Identifier, String> ids = new HashMap<>();
        Map<String, String> namespaces = new HashMap<>();
        Set<Identifier> exclusions = new java.util.HashSet<>();
        List<Map.Entry<TagKey<net.minecraft.item.Item>, String>> itemTags = new ArrayList<>();
        List<Map.Entry<TagKey<net.minecraft.block.Block>, String>> blockTags = new ArrayList<>();

        parsed.forEach((treeId, tree) -> tree.nodes().forEach((key, node) -> {
            String nodeId = treeId.getPath() + "/" + key;
            for (String entry : node.unlocks()) {
                if (entry.endsWith(":*") && entry.indexOf(':') == entry.length() - 2) {
                    namespaces.put(entry.substring(0, entry.length() - 2), nodeId);
                } else if (entry.startsWith("#")) {
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
            for (String entry : node.excludes()) {
                Identifier id = Identifier.tryParse(entry);
                if (id != null) {
                    exclusions.add(id);
                }
            }
        }));

        trees = Collections.unmodifiableMap(parsed);
        ordered = List.copyOf(order);
        byId = Collections.unmodifiableMap(ids);
        byNamespace = Collections.unmodifiableMap(namespaces);
        excludedIds = Set.copyOf(exclusions);
        byItemTag = List.copyOf(itemTags);
        byBlockTag = List.copyOf(blockTags);
        UtopiaCore.LOGGER.info("{} Freischalt-Baeume geladen, {} gesperrte Ids, {} Namensraeume, {} Ausnahmen",
                parsed.size(), ids.size(), namespaces.size(), exclusions.size());
    }

    private static Map<Identifier, UnlockTree> loadOverrides() {
        Map<Identifier, UnlockTree> result = new LinkedHashMap<>();
        if (!Files.isDirectory(EDITOR_ROOT)) {
            return result;
        }
        try (Stream<Path> paths = Files.walk(EDITOR_ROOT)) {
            paths.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> readOverride(path, result));
        } catch (IOException error) {
            UtopiaCore.LOGGER.error("Editor-Baeume aus {} konnten nicht gelesen werden", EDITOR_ROOT, error);
        }
        return result;
    }

    private static void readOverride(Path path, Map<Identifier, UnlockTree> result) {
        Path relative = EDITOR_ROOT.relativize(path);
        if (relative.getNameCount() < 2) {
            UtopiaCore.LOGGER.warn("Ignoriere Editor-Baum ohne Namespace: {}", path);
            return;
        }
        String namespace = relative.getName(0).toString();
        String treePath = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
        treePath = treePath.substring(0, treePath.length() - ".json".length());
        Identifier id = Identifier.tryParse(namespace + ":" + treePath);
        if (id == null) {
            UtopiaCore.LOGGER.warn("Ignoriere Editor-Baum mit ungueltigem Pfad: {}", path);
            return;
        }
        try {
            JsonElement json = com.google.gson.JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            UnlockTree.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> UtopiaCore.LOGGER.error("Editor-Baum {} ist nicht lesbar: {}", id, error))
                    .ifPresent(tree -> {
                        String problem = UnlockTreeValidator.validate(id, tree);
                        if (problem == null) {
                            result.put(id, tree);
                        } else {
                            UtopiaCore.LOGGER.error("Editor-Baum {} ist ungueltig: {}", id, problem);
                        }
                    });
        } catch (IOException | RuntimeException error) {
            UtopiaCore.LOGGER.error("Editor-Baum {} konnte nicht gelesen werden", id, error);
        }
    }

    /**
     * Speichert einen OP-Editor-Baum getrennt von Datapacks und Spielerfortschritt.
     * Ein eingebauter Baum wird damit nur ueberschrieben, nie veraendert.
     *
     * @return null bei Erfolg, sonst eine fuer den Benutzer geeignete Fehlermeldung
     */
    public static String saveOverride(Identifier id, UnlockTree tree) {
        tree = preserveUnavailableNodes(id, tree);
        String problem = UnlockTreeValidator.validate(id, tree);
        if (problem != null) {
            return problem;
        }
        JsonElement encoded = UnlockTree.CODEC.encodeStart(JsonOps.INSTANCE, tree)
                .resultOrPartial(error -> UtopiaCore.LOGGER.error("Baum {} konnte nicht serialisiert werden: {}", id, error))
                .orElse(null);
        if (encoded == null) {
            return "Baum konnte nicht serialisiert werden";
        }

        Path destination = EDITOR_ROOT.resolve(id.getNamespace())
                .resolve(id.getPath().replace('/', java.io.File.separatorChar) + ".json").normalize();
        if (!destination.startsWith(EDITOR_ROOT)) {
            return "Ungueltiger Speicherpfad";
        }
        try {
            Files.createDirectories(destination.getParent());
            if (Files.exists(destination)) {
                Files.copy(destination, destination.resolveSibling(destination.getFileName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Path temporary = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".tmp");
            Files.writeString(temporary, GSON.toJson(encoded) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            UtopiaCore.LOGGER.error("Baum {} konnte nicht nach {} gespeichert werden", id, destination, error);
            return "Speichern fehlgeschlagen: " + error.getMessage();
        }

        Map<Identifier, UnlockTree> merged = new LinkedHashMap<>(resourceTrees);
        merged.putAll(loadOverrides());
        install(merged);
        return null;
    }

    private static UnlockTree preserveUnavailableNodes(Identifier id, UnlockTree edited) {
        UnlockTree configured = configuredTrees.get(id);
        if (configured == null) {
            return edited;
        }
        Map<String, UnlockTree.Node> nodes = new LinkedHashMap<>(edited.nodes());
        // Die aktuelle Editor-Version bietet bewusst noch kein Loeschen. Was
        // der gefilterte Client nicht erhalten hat (optionaler Knoten oder ein
        // davon abhaengiges Kind), wird deshalb unveraendert zurueckgelegt.
        configured.nodes().forEach(nodes::putIfAbsent);
        return new UnlockTree(edited.name(), edited.description(), edited.icon(), edited.order(),
                Collections.unmodifiableMap(nodes));
    }

    /** Entfernt optionale Addon-Knoten und anschließend verwaiste Kinder. */
    private static UnlockTree availableTree(Identifier treeId, UnlockTree tree) {
        Map<String, UnlockTree.Node> nodes = new LinkedHashMap<>();
        tree.nodes().forEach((key, node) -> {
            boolean available = node.requiresMods().stream().allMatch(FabricLoader.getInstance()::isModLoaded);
            if (available) {
                nodes.put(key, node);
            } else {
                UtopiaCore.LOGGER.info("Optionaler Knoten {}/{} ausgeblendet; benoetigte Mods: {}",
                        treeId, key, node.requiresMods());
            }
        });

        boolean changed;
        do {
            changed = nodes.entrySet().removeIf(entry -> entry.getValue().parents().stream()
                    .anyMatch(parent -> !nodes.containsKey(parent)));
        } while (changed);
        return new UnlockTree(tree.name(), tree.description(), tree.icon(), tree.order(),
                Collections.unmodifiableMap(nodes));
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
        if (excludedIds.contains(id)) {
            return null;
        }
        String namespace = byNamespace.get(id.getNamespace());
        if (namespace != null) {
            return namespace;
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
        return byId.isEmpty() && byNamespace.isEmpty() && byItemTag.isEmpty() && byBlockTag.isEmpty();
    }

    /**
     * Client-Seite: dieselben Baeume, derselbe Index. Tags bleiben serverseitig -
     * der Client graut Tag-Eintraege also nicht aus, die Sperre selbst haelt
     * trotzdem, weil der Server entscheidet.
     */
    public static void acceptSynced(Map<Identifier, UnlockTree> synced, List<Identifier> order) {
        Map<Identifier, String> ids = new HashMap<>();
        Map<String, String> namespaces = new HashMap<>();
        Set<Identifier> exclusions = new java.util.HashSet<>();
        synced.forEach((treeId, tree) -> tree.nodes().forEach((key, node) -> {
            String nodeId = treeId.getPath() + "/" + key;
            for (String entry : node.unlocks()) {
                if (entry.endsWith(":*") && entry.indexOf(':') == entry.length() - 2) {
                    namespaces.put(entry.substring(0, entry.length() - 2), nodeId);
                } else if (!entry.startsWith("#")) {
                    Identifier id = Identifier.tryParse(entry);
                    if (id != null) {
                        ids.put(id, nodeId);
                    }
                }
            }
            for (String entry : node.excludes()) {
                Identifier id = Identifier.tryParse(entry);
                if (id != null) {
                    exclusions.add(id);
                }
            }
        }));
        trees = Collections.unmodifiableMap(synced);
        ordered = List.copyOf(order);
        byId = Collections.unmodifiableMap(ids);
        byNamespace = Collections.unmodifiableMap(namespaces);
        excludedIds = Set.copyOf(exclusions);
    }
}
