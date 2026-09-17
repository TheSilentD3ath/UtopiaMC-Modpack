package dev.utopia.core.unlock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ein Freischalt-Baum, z.B. Create oder Winery.
 *
 * Ein Baum ist eine Datei: {@code data/<namespace>/utopia/trees/<name>.json}.
 * Knoten, Kosten und Positionen liegen zusammen, weil sie zusammen gepflegt und
 * zusammen gezeichnet werden.
 */
public record UnlockTree(Optional<String> name, Optional<String> description, Optional<Identifier> icon, int order,
        Map<String, Node> nodes) {

    public static final Codec<UnlockTree> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name").forGetter(UnlockTree::name),
            Codec.STRING.optionalFieldOf("description").forGetter(UnlockTree::description),
            Identifier.CODEC.optionalFieldOf("icon").forGetter(UnlockTree::icon),
            Codec.INT.optionalFieldOf("order", 0).forGetter(UnlockTree::order),
            Codec.unboundedMap(Codec.STRING, Node.CODEC).fieldOf("nodes").forGetter(UnlockTree::nodes))
            .apply(instance, UnlockTree::new));

    /**
     * Ein Knoten im Baum.
     *
     * @param cost     Freischaltpunkte
     * @param level    Mindest-Gesamtlevel
     * @param parents  Knoten desselben Baums, die vorher stehen muessen
     * @param position freie Canvas-Koordinaten {@code [x, y]}; ein moegliches
     *                 Editor-Raster ist keine Spieler-Darstellung
     * @param unlocks  Ids von Bloecken/Gegenstaenden, {@code #} fuer Tags und
     *                 {@code namespace:*} fuer alle Inhalte eines Addons
     * @param excludes Ids, die von einem {@code namespace:*}-Eintrag dieses
     *                 Knotens bewusst frei bleiben (z.B. reine Dekoration)
     * @param legacyOwners alte Knoten-Ids, deren Besitzer diesen neu
     *                     aufgeteilten Knoten ohne Neukauf behalten
     * @param requiresMods optionale Fabric-Mod-Ids; fehlt eine, wird der Knoten
     *                     vor Indexierung und Sync entfernt
     */
    public record Node(Optional<String> name, Optional<String> description, Optional<Identifier> icon, int cost,
            int level, List<String> parents, List<Double> position, List<String> unlocks, List<String> excludes,
            List<String> legacyOwners, List<String> requiresMods) {

        public static final Codec<Node> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("name").forGetter(Node::name),
                Codec.STRING.optionalFieldOf("description").forGetter(Node::description),
                Identifier.CODEC.optionalFieldOf("icon").forGetter(Node::icon),
                Codec.INT.optionalFieldOf("cost", 1).forGetter(Node::cost),
                Codec.INT.optionalFieldOf("level", 0).forGetter(Node::level),
                Codec.STRING.listOf().optionalFieldOf("parents", List.of()).forGetter(Node::parents),
                Codec.DOUBLE.listOf().optionalFieldOf("position", List.of(0.0, 0.0)).forGetter(Node::position),
                Codec.STRING.listOf().optionalFieldOf("unlocks", List.of()).forGetter(Node::unlocks),
                Codec.STRING.listOf().optionalFieldOf("excludes", List.of()).forGetter(Node::excludes),
                Codec.STRING.listOf().optionalFieldOf("legacy_owners", List.of()).forGetter(Node::legacyOwners),
                Codec.STRING.listOf().optionalFieldOf("requires_mods", List.of()).forGetter(Node::requiresMods))
                .apply(instance, Node::new));

        public double x() {
            return position.size() > 0 ? position.get(0) : 0.0;
        }

        public double y() {
            return position.size() > 1 ? position.get(1) : 0.0;
        }

        public Node withPosition(double x, double y) {
            return new Node(name, description, icon, cost, level, parents, List.of(x, y), unlocks, excludes,
                    legacyOwners, requiresMods);
        }
    }
}
