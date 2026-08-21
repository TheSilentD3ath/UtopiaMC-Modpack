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
public record UnlockTree(Optional<String> name, Optional<Identifier> icon, int order,
        Map<String, Node> nodes) {

    public static final Codec<UnlockTree> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name").forGetter(UnlockTree::name),
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
     * @param position Raster fuer die Oberflaeche: [Spalte, Zeile]
     * @param unlocks  Ids von Bloecken und Gegenstaenden, {@code #} fuer Tags
     */
    public record Node(Optional<String> name, Optional<Identifier> icon, int cost, int level,
            List<String> parents, List<Integer> position, List<String> unlocks) {

        public static final Codec<Node> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("name").forGetter(Node::name),
                Identifier.CODEC.optionalFieldOf("icon").forGetter(Node::icon),
                Codec.INT.optionalFieldOf("cost", 1).forGetter(Node::cost),
                Codec.INT.optionalFieldOf("level", 0).forGetter(Node::level),
                Codec.STRING.listOf().optionalFieldOf("parents", List.of()).forGetter(Node::parents),
                Codec.INT.listOf().optionalFieldOf("position", List.of(0, 0)).forGetter(Node::position),
                Codec.STRING.listOf().optionalFieldOf("unlocks", List.of()).forGetter(Node::unlocks))
                .apply(instance, Node::new));

        public int column() {
            return position.size() > 0 ? position.get(0) : 0;
        }

        public int row() {
            return position.size() > 1 ? position.get(1) : 0;
        }
    }
}
