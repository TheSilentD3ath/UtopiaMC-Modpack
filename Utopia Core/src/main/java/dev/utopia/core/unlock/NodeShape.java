package dev.utopia.core.unlock;

import com.mojang.serialization.Codec;

import java.util.Locale;

/**
 * Umriss eines Knotens auf der Karte.
 *
 * Die Form transportiert die *Art* des Knotens (Grundlage, Material, Maschine,
 * Logistik, Endgame), nicht seinen Zustand — der Zustand laeuft ueber Rahmen und
 * Abzeichen. Zwei getrennte Kanaele, damit beides gleichzeitig ablesbar bleibt
 * und nichts allein an der Farbe haengt: bei 16 px Knotengroesse und auf
 * Holzmaserung ist Farbe der schwaechste verfuegbare Kanal.
 */
public enum NodeShape {

    CIRCLE("circle"),
    SQUARE("square"),
    RSQUARE("rsquare"),
    DIAMOND("diamond"),
    HEXAGON("hexagon"),
    GEAR("gear");

    public static final NodeShape DEFAULT = CIRCLE;

    /** Akzeptiert unbekannte Namen und faellt auf den Kreis zurueck, statt den Baum zu verwerfen. */
    public static final Codec<NodeShape> CODEC = Codec.STRING.xmap(NodeShape::of, NodeShape::id);

    private final String id;

    NodeShape(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static NodeShape of(String value) {
        if (value == null) {
            return DEFAULT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (NodeShape shape : values()) {
            if (shape.id.equals(normalized)) {
                return shape;
            }
        }
        return DEFAULT;
    }
}
