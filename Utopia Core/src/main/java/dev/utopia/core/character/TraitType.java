package dev.utopia.core.character;

/**
 * Die drei Achsen der Charaktererstellung. Reihenfolge = Reihenfolge im Auswahl-Flow.
 */
public enum TraitType {
    ORIGIN("utopia/origins"),
    GENDER("utopia/genders"),
    CLASS("utopia/classes");

    private final String directory;

    TraitType(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return this.directory;
    }

    public String key() {
        return this.name().toLowerCase();
    }
}
