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
 * <p><b>Warum:</b> Von Hand gesetzte Positionen wachsen mit der Zeit in die Hoehe. Der
 * Create-Baum stand bei 30 x 43 Einheiten — Hochformat auf einem Querformat-Bildschirm.
 * Die Folge war, dass "alles einpassen" immer am Mindestzoom endete und niemand mehr
 * erkannte, was er vor sich hat.
 *
 * <p><b>Verfahren</b> (vereinfachtes Sugiyama, ohne Dummy-Knoten):
 * <ol>
 * <li>Ebene je Knoten = laengster Pfad von einer Wurzel. Das ergibt Spalten, die den
 *     Fortschritt von links nach rechts erzaehlen.</li>
 * <li>Reihenfolge innerhalb einer Spalte ueber abwechselnde Baryzentrum-Durchlaeufe:
 *     ein Knoten wandert zur mittleren Hoehe seiner Nachbarn.</li>
 * <li>Uebernommen wird der Durchlauf mit der geringsten aufsummierten Kantenhoehe —
 *     das ist die Anordnung mit den wenigsten sichtbaren Ueberkreuzungen.</li>
 * </ol>
 *
 * <p>Lange Kanten ueber mehrere Spalten laufen bewusst hinter den Knoten durch, statt
 * dass Zwischenknoten eingezogen werden: Die Karte soll die fachliche Struktur zeigen,
 * nicht die Hilfskonstruktion des Layouts.
 *
 * <p>Deterministisch: Gleiche Eingabe erzeugt dieselbe Ausgabe, damit ein erneuter
 * Aufruf im Editor keine Bewegung ohne Grund erzeugt.
 */
public final class UnlockLayout {

    /** Waagerechter Abstand zweier Fortschrittsebenen, in Modelleinheiten. */
    public static final double COLUMN_SPACING = 2.4;
    /** Senkrechter Abstand zweier Knoten derselben Ebene. */
    public static final double ROW_SPACING = 1.3;

    private static final int SWEEPS = 8;
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
        Map<String, List<String>> children = new HashMap<>();
        for (String key : nodes.keySet()) {
            parents.put(key, new ArrayList<>());
            children.put(key, new ArrayList<>());
        }
        nodes.forEach((key, node) -> {
            for (String parent : node.parents()) {
                // Unbekannte Eltern kommen in geprueften Baeumen nicht vor; falls doch,
                // werden sie hier still uebergangen statt das Layout scheitern zu lassen.
                if (nodes.containsKey(parent)) {
                    parents.get(key).add(parent);
                    children.get(parent).add(key);
                }
            }
        });

        Map<String, Integer> rank = rank(nodes, parents);
        List<List<String>> ranks = groupByRank(nodes, rank);

        List<List<String>> best = copy(ranks);
        double bestCost = cost(best, parents);
        for (int sweep = 0; sweep < SWEEPS; sweep++) {
            order(ranks, sweep % 2 == 0 ? parents : children, sweep % 2 == 0);
            double current = cost(ranks, parents);
            if (current < bestCost) {
                bestCost = current;
                best = copy(ranks);
            }
        }

        Map<String, UnlockTree.Node> result = new LinkedHashMap<>();
        Map<String, double[]> positions = new HashMap<>();
        for (int column = 0; column < best.size(); column++) {
            List<String> members = best.get(column);
            double offset = (members.size() - 1) / 2.0;
            for (int row = 0; row < members.size(); row++) {
                positions.put(members.get(row),
                        new double[] { column * columnSpacing, (row - offset) * rowSpacing });
            }
        }
        // Ueber die Originalreihenfolge laufen, damit die JSON-Reihenfolge stabil bleibt.
        nodes.forEach((key, node) -> {
            double[] position = positions.get(key);
            result.put(key, position == null ? node : node.withPosition(round(position[0]), round(position[1])));
        });
        return result;
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
        // Nur erreichbar, wenn doch ein Zyklus durchgerutscht ist: die bis hier
        // erreichten Ebenen sind dann brauchbar genug fuer eine Darstellung.
        return rank;
    }

    private static List<List<String>> groupByRank(Map<String, UnlockTree.Node> nodes,
            Map<String, Integer> rank) {
        int columns = rank.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        List<List<String>> ranks = new ArrayList<>(columns);
        for (int i = 0; i < columns; i++) {
            ranks.add(new ArrayList<>());
        }
        // Start aus der bestehenden Hoehe: Wer schon nebeneinander lag, bleibt es eher.
        List<String> keys = new ArrayList<>(nodes.keySet());
        keys.sort(Comparator.comparingDouble((String key) -> nodes.get(key).y()).thenComparing(key -> key));
        keys.forEach(key -> ranks.get(rank.get(key)).add(key));
        return ranks;
    }

    private static void order(List<List<String>> ranks, Map<String, List<String>> neighbours,
            boolean forward) {
        Map<String, Double> position = normalizedPositions(ranks);
        List<Integer> columns = new ArrayList<>();
        for (int i = 0; i < ranks.size(); i++) {
            columns.add(forward ? i : ranks.size() - 1 - i);
        }
        for (int column : columns) {
            List<String> members = ranks.get(column);
            if (members.size() < 2) {
                continue;
            }
            Map<String, Double> barycentre = new HashMap<>();
            for (String key : members) {
                List<String> related = neighbours.get(key);
                double sum = 0.0;
                int count = 0;
                for (String neighbour : related) {
                    Double value = position.get(neighbour);
                    if (value != null) {
                        sum += value;
                        count++;
                    }
                }
                // Ohne Nachbarn in dieser Richtung bleibt der Knoten, wo er ist.
                barycentre.put(key, count == 0 ? position.getOrDefault(key, 0.5) : sum / count);
            }
            members.sort(Comparator.<String>comparingDouble(barycentre::get)
                    .thenComparing(Comparator.naturalOrder()));
            position = normalizedPositions(ranks);
        }
    }

    /** Hoehe je Knoten auf 0..1, damit unterschiedlich volle Spalten vergleichbar sind. */
    private static Map<String, Double> normalizedPositions(List<List<String>> ranks) {
        Map<String, Double> position = new HashMap<>();
        for (List<String> members : ranks) {
            double divisor = Math.max(1, members.size() - 1);
            for (int i = 0; i < members.size(); i++) {
                position.put(members.get(i), members.size() == 1 ? 0.5 : i / divisor);
            }
        }
        return position;
    }

    /** Aufsummierte Hoehendifferenz aller Kanten: je kleiner, desto ruhiger die Karte. */
    private static double cost(List<List<String>> ranks, Map<String, List<String>> parents) {
        Map<String, Double> position = normalizedPositions(ranks);
        double total = 0.0;
        for (Map.Entry<String, List<String>> entry : parents.entrySet()) {
            Double child = position.get(entry.getKey());
            if (child == null) {
                continue;
            }
            for (String parent : entry.getValue()) {
                Double value = position.get(parent);
                if (value != null) {
                    total += Math.abs(child - value);
                }
            }
        }
        return total;
    }

    private static List<List<String>> copy(List<List<String>> ranks) {
        List<List<String>> out = new ArrayList<>(ranks.size());
        ranks.forEach(members -> out.add(new ArrayList<>(members)));
        return out;
    }

    /** Eine Nachkommastelle: die gespeicherte Datei bleibt lesbar und diff-freundlich. */
    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
