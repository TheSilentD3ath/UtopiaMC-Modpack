package dev.utopia.core.client;

import dev.utopia.core.unlock.UnlockTree;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/** Veraenderliche Arbeitskopie. Erst Speichern ersetzt die Server-Konfiguration. */
final class UnlockTreeDraft {

    private Optional<String> name;
    private Optional<String> description;
    private Optional<Identifier> icon;
    private int order;
    private final LinkedHashMap<String, UnlockTree.Node> nodes;

    UnlockTreeDraft(UnlockTree source) {
        this.name = source.name();
        this.description = source.description();
        this.icon = source.icon();
        this.order = source.order();
        this.nodes = new LinkedHashMap<>(source.nodes());
    }

    UnlockTree build() {
        return new UnlockTree(name, description, icon, order,
                Collections.unmodifiableMap(new LinkedHashMap<>(nodes)));
    }

    Map<String, UnlockTree.Node> nodes() {
        return nodes;
    }

    void put(String key, UnlockTree.Node node) {
        nodes.put(key, node);
    }

    void move(String key, double x, double y) {
        UnlockTree.Node node = nodes.get(key);
        if (node != null) {
            nodes.put(key, node.withPosition(x, y));
        }
    }

    void remove(String key) {
        nodes.remove(key);
    }

    String uniqueNodeKey() {
        int number = 1;
        while (nodes.containsKey("node_" + number)) {
            number++;
        }
        return "node_" + number;
    }

    void setMetadata(String name, String description, Identifier icon, int order) {
        this.name = optional(name);
        this.description = optional(description);
        this.icon = Optional.ofNullable(icon);
        this.order = order;
    }

    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }
}
