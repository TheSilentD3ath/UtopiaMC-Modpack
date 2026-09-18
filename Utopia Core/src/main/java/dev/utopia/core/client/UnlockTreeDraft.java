package dev.utopia.core.client;

import dev.utopia.core.unlock.UnlockLayout;
import dev.utopia.core.unlock.UnlockTree;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Veraenderliche Arbeitskopie. Erst Speichern ersetzt die Server-Konfiguration.
 *
 * <p>Jede Aenderung legt vorher einen Schnappschuss ab. Ohne das war ein Fehlgriff im
 * Editor — ein versehentlich verschobener oder geloeschter Knoten — nicht mehr
 * zurueckzunehmen; man konnte nur den ganzen Entwurf verwerfen.
 *
 * <p>Ein Schnappschuss ist die komplette Knotenkarte. Bei hoechstens ein paar tausend
 * Knoten ist das ein paar Kilobyte je Schritt und damit billiger als eine
 * Befehlshistorie, die jede Operation einzeln umkehren muesste.
 */
final class UnlockTreeDraft {

    private static final int MAX_HISTORY = 64;

    private Optional<String> name;
    private Optional<String> description;
    private Optional<Identifier> icon;
    private int order;
    private final LinkedHashMap<String, UnlockTree.Node> nodes;
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    /** Waehrend eines Ziehvorgangs sammeln wir nur einen Schnappschuss, nicht einen je Bild. */
    private boolean coalescing;

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
        record();
        nodes.put(key, node);
    }

    void move(String key, double x, double y) {
        UnlockTree.Node node = nodes.get(key);
        if (node == null || (node.x() == x && node.y() == y)) {
            return;
        }
        if (!coalescing) {
            record();
        }
        nodes.put(key, node.withPosition(x, y));
    }

    void remove(String key) {
        if (nodes.containsKey(key)) {
            record();
            nodes.remove(key);
        }
    }

    /** Ordnet alle Knoten nach Fortschrittsebene neu an. */
    void autoLayout() {
        record();
        Map<String, UnlockTree.Node> arranged = UnlockLayout.layout(nodes,
                UnlockLayout.COLUMN_SPACING, UnlockLayout.ROW_SPACING);
        nodes.clear();
        nodes.putAll(arranged);
    }

    void setMetadata(String name, String description, Identifier icon, int order) {
        record();
        this.name = optional(name);
        this.description = optional(description);
        this.icon = Optional.ofNullable(icon);
        this.order = order;
    }

    /** Beginn einer zusammenhaengenden Geste (Knoten ziehen): ein Schnappschuss fuer alles. */
    void beginGesture() {
        if (!coalescing) {
            record();
            coalescing = true;
        }
    }

    void endGesture() {
        coalescing = false;
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    /** @return true, wenn tatsaechlich etwas zurueckgenommen wurde */
    boolean undo() {
        Snapshot previous = undo.pollLast();
        if (previous == null) {
            return false;
        }
        name = previous.name();
        description = previous.description();
        icon = previous.icon();
        order = previous.order();
        nodes.clear();
        nodes.putAll(previous.nodes());
        coalescing = false;
        return true;
    }

    private void record() {
        undo.addLast(new Snapshot(name, description, icon, order, new LinkedHashMap<>(nodes)));
        while (undo.size() > MAX_HISTORY) {
            undo.pollFirst();
        }
    }

    String uniqueNodeKey() {
        int number = 1;
        while (nodes.containsKey("node_" + number)) {
            number++;
        }
        return "node_" + number;
    }

    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private record Snapshot(Optional<String> name, Optional<String> description, Optional<Identifier> icon,
            int order, Map<String, UnlockTree.Node> nodes) {
    }
}
