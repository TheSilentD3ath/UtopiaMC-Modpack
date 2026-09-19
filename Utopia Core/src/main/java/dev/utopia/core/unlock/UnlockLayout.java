package dev.utopia.core.unlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Geschichtetes Auto-Layout fuer Freischalt-Baeume.
 *
 * <p><b>Wozu:</b> Ein neu angelegter Baum hat keine Positionen, und ein von Hand
 * gewachsener steht irgendwann im Hochformat auf einem Querformat-Bildschirm. Das
 * Verfahren legt Spalten nach Fortschritt an, damit die Karte von links nach rechts
 * gelesen werden kann.
 *
 * <p><b>Verfahren</b>
 * <ol>
 * <li>Spalte je Knoten = laengster Pfad von einer Wurzel. Kein Knoten steht damit links
 *     von einer seiner Voraussetzungen.</li>
 * <li>Reihenfolge innerhalb der Spalte = Tiefensuche ueber die <em>erste</em> Elternkette.
 *     Geschwisterzweige bleiben dadurch als Block beieinander.</li>
 * </ol>
 *
 * <p><b>Warum die erste Elternkette und nicht alle Kanten:</b> Die Karte zeichnet dauerhaft
 * nur die erste Voraussetzung je Knoten; die uebrigen erscheinen erst beim Darueberfahren.
 * Ein Baryzentrum-Verfahren ueber alle Kanten — der uebliche zweite Sugiyama-Schritt —
 * optimiert also ueberwiegend Linien, die niemand sieht, und zerlegt dabei die, die man
 * sieht. Auf dem Create-Baum gemessen: Baryzentrum ueber alle 141 Kanten ergibt 65
 * Ueberkreuzungen unter den 67 gezeichneten, die Tiefensuche ueber die gezeichnete Kette
 * ergibt 24.
 *
 * <p><b>Was es nicht kann:</b> Null Ueberkreuzungen sind so nicht erreichbar. Eine
 * gezeichnete Kante ueberspringt immer dann Spalten, wenn der Knoten noch eine tiefer
 * liegende zweite Voraussetzung hat — er kann dann nicht weiter nach links, ohne diese zu
 * verletzen. Ein von Hand gesetzter Baum kann das besser: der Create-Baum steht auf 0.
 * Das Verfahren ist ein Startpunkt, keine Verbesserung einer durchdachten Anordnung.
 *
 * <p>Deterministisch: Gleiche Eingabe erzeugt dieselbe Ausgabe, damit ein erneuter Aufruf
 * im Editor keine Bewegung ohne Grund erzeugt.
 */
public final class UnlockLayout {

    /** Waagerechter Abstand zweier Fortschrittsebenen, in Modelleinheiten. */
    public static final double COLUMN_SPACING = 2.6;
    /**
     * Senkrechter Abstand zweier Knoten derselben Ebene.
     *
     * Haengt an der Knotengroesse: ein Knoten ist eine Modelleinheit breit, bei 1,5 bleibt
     * eine halbe Knotenbreite Luft zwischen zwei Nachbarn. Enger wuerde die Spalte zu einer
     * durchgehenden Kette verschmelzen.
     */
    public static final double ROW_SPACING = 1.5;

    private static final int MAX_RANK_ITERATIONS = 512;

    private UnlockLayout() {
    }

    public static UnlockTree applyTo(UnlockTree tree) {
        return tree.withNodes(layout(tree.nodes(), COLUMN_SPACING, ROW_SPACING));
    }

    /**
     * @return dieselben Knoten mit neuen Positionen; Reihenfolge und Inhalt bleiben
     *         unveraendert, damit nur {@code position} im gespeicherten JSON wandert
     */
    public static Map<String, UnlockTree.Node> layout(Map<String, UnlockTree.Node> nodes,
            double columnSpacing, double rowSpacing) {
        if (nodes.isEmpty()) {
            return nodes;
        }

        Map<String, List<String>> parents = new HashMap<>();
        Map<String, List<String>> drawnChildren = new HashMap<>();
        for (String key : nodes.keySet()) {
            parents.put(key, new ArrayList<>());
            drawnChildren.put(key, new ArrayList<>());
        }
        nodes.forEach((key, node) -> {
            for (String parent : node.parents()) {
                // Unbekannte Eltern kommen in geprueften Baeumen nicht vor; falls doch,
                // werden sie hier still uebergangen statt das Layout scheitern zu lassen.
                if (nodes.containsKey(parent)) {
                    parents.get(key).add(parent);
                }
            }
            String drawn = drawnParent(nodes, node);
            if (drawn != null) {
                drawnChildren.get(drawn).add(key);
            }
        });

        Map<String, Integer> rank = rank(nodes, parents);
        Map<String, Integer> order = walkOrder(nodes, drawnChildren);

        List<String> keys = new ArrayList<>(nodes.keySet());
        keys.sort(Comparator.<String>comparingInt(order::get).thenComparing(Comparator.naturalOrder()));

        Map<Integer, List<String>> columns = new LinkedHashMap<>();
        keys.forEach(key -> columns.computeIfAbsent(rank.get(key), column -> new ArrayList<>()).add(key));

        Map<String, double[]> positions = new HashMap<>();
        columns.forEach((column, members) -> {
            double offset = (members.size() - 1) / 2.0;
            for (int row = 0; row < members.size(); row++) {
                positions.put(members.get(row),
                        new double[] { column * columnSpacing, (row - offset) * rowSpacing });
            }
        });

        // Ueber die Originalreihenfolge laufen, damit die JSON-Reihenfolge stabil bleibt.
        Map<String, UnlockTree.Node> result = new LinkedHashMap<>();
        nodes.forEach((key, node) -> {
            double[] position = positions.get(key);
            result.put(key, position == null ? node : node.withPosition(round(position[0]), round(position[1])));
        });
        return result;
    }

    /** Die Voraussetzung, die auf der Karte dauerhaft als Linie erscheint. */
    private static String drawnParent(Map<String, UnlockTree.Node> nodes, UnlockTree.Node node) {
        for (String parent : node.parents()) {
            if (nodes.containsKey(parent)) {
                return parent;
            }
        }
        return null;
    }

    /** Ebene = laengster Pfad von einer Wurzel. Wurzeln liegen auf Ebene 0. */
    private static Map<String, Integer> rank(Map<String, UnlockTree.Node> nodes,
            Map<String, List<String>> parents) {
        Map<String, Integer> rank = new HashMap<>();
        nodes.keySet().forEach(key -> rank.put(key, 0));
        for (int iteration = 0; iteration < MAX_RANK_ITERATIONS; iteration++) {
            boolean changed = false;
            for (String key : nodes.keySet()) {
                int highest = 0;
                for (String parent : parents.get(key)) {
                    highest = Math.max(highest, rank.get(parent) + 1);
                }
                if (highest != rank.get(key)) {
                    rank.put(key, highest);
                    changed = true;
                }
            }
            if (!changed) {
                return rank;
            }
        }
        // Nur erreichbar, wenn doch ein Zyklus durchgerutscht ist: die bis hier erreichten
        // Ebenen sind dann brauchbar genug fuer eine Darstellung.
        return rank;
    }

    /**
     * Besuchsreihenfolge einer Tiefensuche ueber die gezeichnete Elternkette.
     *
     * <p>Der dickste Zweig zuerst: dann steht der Hauptpfad des Baums oben und faechert
     * nach unten auf, statt dass die Karte mit einer Sackgasse beginnt.
     */
    private static Map<String, Integer> walkOrder(Map<String, UnlockTree.Node> nodes,
            Map<String, List<String>> drawnChildren) {
        Map<String, Integer> weight = new HashMap<>();
        nodes.keySet().forEach(key -> weight(key, drawnChildren, weight));

        List<String> roots = new ArrayList<>();
        nodes.forEach((key, node) -> {
            if (drawnParent(nodes, node) == null) {
                roots.add(key);
            }
        });
        roots.sort(branchOrder(weight));

        Map<String, Integer> order = new HashMap<>();
        // Eigener Stapel statt Rekursion: ein beschaedigter Baum soll den Editor nicht
        // mit einem Stapelueberlauf beenden.
        List<String> stack = new ArrayList<>(roots);
        java.util.Collections.reverse(stack);
        while (!stack.isEmpty()) {
            String key = stack.remove(stack.size() - 1);
            if (order.containsKey(key)) {
                continue;
            }
            order.put(key, order.size());
            List<String> children = new ArrayList<>(drawnChildren.get(key));
            children.sort(branchOrder(weight));
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.add(children.get(i));
            }
        }
        // Was die Kette nicht erreicht hat, haengt hinten dran statt zu verschwinden.
        nodes.keySet().forEach(key -> order.putIfAbsent(key, order.size()));
        return order;
    }

    private static Comparator<String> branchOrder(Map<String, Integer> weight) {
        return Comparator.<String>comparingInt(key -> -weight.getOrDefault(key, 1))
                .thenComparing(Comparator.naturalOrder());
    }

    /** Anzahl Knoten im Zweig unterhalb dieses Knotens, ihn selbst eingeschlossen. */
    private static int weight(String key, Map<String, List<String>> drawnChildren,
            Map<String, Integer> cache) {
        Integer known = cache.get(key);
        if (known != null) {
            return known;
        }
        // Vorbelegen: bricht die Rekursion, falls doch ein Zyklus in den Daten steht.
        cache.put(key, 1);
        int total = 1;
        for (String child : drawnChildren.get(key)) {
            total += weight(child, drawnChildren, cache);
        }
        cache.put(key, total);
        return total;
    }

    /** Eine Nachkommastelle: die gespeicherte Datei bleibt lesbar und diff-freundlich. */
    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
