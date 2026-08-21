package dev.utopia.core.character;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Charakterzustand eines Spielers. Liegt am PlayerEntity, wird in dessen NBT
 * gespeichert und ueberlebt Tod und Dimensionswechsel (copyFrom).
 *
 * Alles, was aus den Traits berechnet wird (Attribute, Effekte, Skill-Boni),
 * steht hier NICHT drin - es wird bei Aenderung einmal neu angewendet.
 */
public class CharacterData {

    public static final String NBT_KEY = "Utopia";

    private Identifier origin;
    private Identifier gender;
    private Identifier clazz;
    private boolean complete;

    /** Zusaetzlich aktive Trait-Buendel, z.B. nach abgeschlossener Transition. */
    private final Set<Identifier> grantedTraits = new LinkedHashSet<>();
    /** Freigeschaltete Tech-Knoten (spaeter: Create-Rezepte usw.). */
    private final Set<Identifier> unlocks = new LinkedHashSet<>();
    /**
     * Create-Bloecke, fuer die es schon Technik-Erfahrung gab. Pro Blockart
     * einmal - sonst waere eine Reihe Foerderbaender eine Erfahrungsfarm.
     */
    private final Set<Identifier> techBlocks = new LinkedHashSet<>();
    /**
     * Knoten, die aus einem Trait geschenkt sind (Startknoten der Klasse).
     * Getrennt von den gekauften: wer die Klasse wechselt, verliert das Geschenk
     * wieder, seine gekauften Knoten aber nicht.
     */
    private final Set<Identifier> grantedUnlocks = new LinkedHashSet<>();

    public Set<Identifier> grantedUnlocks() {
        return grantedUnlocks;
    }

    /** Offene Freischaltpunkte fuer die Baeume. Eigener Topf neben LevelZ' Skillpunkten. */
    private int unlockPoints;

    public int unlockPoints() {
        return unlockPoints;
    }

    public void setUnlockPoints(int points) {
        this.unlockPoints = Math.max(0, points);
    }

    public void addUnlockPoints(int points) {
        setUnlockPoints(this.unlockPoints + points);
    }

    /** Vom Spieler abgeschaltete Faehigkeiten (Effekt-Ids). */
    private final Set<Identifier> disabledAbilities = new LinkedHashSet<>();

    public Set<Identifier> disabledAbilities() {
        return disabledAbilities;
    }

    /** @return true, wenn diese Blockart zum ersten Mal gebaut wurde. */
    public boolean markTechBlock(Identifier block) {
        return techBlocks.add(block);
    }

    /** Signalisiert, dass Bundle neu berechnet und synchronisiert werden muss. */
    private transient boolean dirty = true;
    /** Zwischengespeichertes Merge-Ergebnis. Nur bei dirty neu gebaut. */
    private transient TraitBundle bundle = TraitBundle.EMPTY;

    /**
     * Was zuletzt tatsaechlich am Spieler gesetzt wurde. Ohne dieses Gedaechtnis
     * bleiben beim Wechsel (z.B. maennlich -> weiblich) die Attribute und
     * Effekte der alten Wahl haengen.
     *
     * Nicht gespeichert: beim Betreten der Welt wird ohnehin alles neu
     * angewendet und dabei neu aufgeschrieben.
     */
    private final transient Set<String> appliedAttributes = new LinkedHashSet<>();
    private final transient Set<Identifier> appliedEffects = new LinkedHashSet<>();

    public Set<String> appliedAttributes() {
        return appliedAttributes;
    }

    public Set<Identifier> appliedEffects() {
        return appliedEffects;
    }

    public TraitBundle bundle() {
        return bundle;
    }

    public void setBundle(TraitBundle bundle) {
        this.bundle = bundle;
    }

    public Identifier origin() {
        return origin;
    }

    public Identifier gender() {
        return gender;
    }

    public Identifier clazz() {
        return clazz;
    }

    public boolean isComplete() {
        return complete;
    }

    public Set<Identifier> grantedTraits() {
        return grantedTraits;
    }

    public Set<Identifier> unlocks() {
        return unlocks;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public void select(Identifier origin, Identifier gender, Identifier clazz) {
        this.origin = origin;
        this.gender = gender;
        this.clazz = clazz;
        this.complete = origin != null && gender != null && clazz != null;
        markDirty();
    }

    public boolean grantTrait(Identifier id) {
        if (grantedTraits.add(id)) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean unlock(Identifier node) {
        if (unlocks.add(node)) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean hasUnlock(Identifier node) {
        return unlocks.contains(node);
    }

    public void reset() {
        this.origin = null;
        this.gender = null;
        this.clazz = null;
        this.complete = false;
        this.unlockPoints = 0;
        this.grantedTraits.clear();
        this.unlocks.clear();
        this.techBlocks.clear();
        this.disabledAbilities.clear();
        this.grantedUnlocks.clear();
        markDirty();
    }

    public void copyFrom(CharacterData other) {
        this.origin = other.origin;
        this.gender = other.gender;
        this.clazz = other.clazz;
        this.complete = other.complete;
        this.unlockPoints = other.unlockPoints;
        this.grantedTraits.clear();
        this.grantedTraits.addAll(other.grantedTraits);
        this.unlocks.clear();
        this.unlocks.addAll(other.unlocks);
        this.techBlocks.clear();
        this.techBlocks.addAll(other.techBlocks);
        this.disabledAbilities.clear();
        this.disabledAbilities.addAll(other.disabledAbilities);
        this.grantedUnlocks.clear();
        this.grantedUnlocks.addAll(other.grantedUnlocks);
        markDirty();
    }

    // --- NBT -------------------------------------------------------------

    public void readNbt(NbtCompound root) {
        if (!root.contains(NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            return;
        }
        NbtCompound tag = root.getCompound(NBT_KEY);
        this.origin = readId(tag, "Origin");
        this.gender = readId(tag, "Gender");
        this.clazz = readId(tag, "Class");
        this.complete = tag.getBoolean("Complete");
        this.unlockPoints = tag.getInt("UnlockPoints");
        this.grantedTraits.clear();
        readList(tag, "Granted", this.grantedTraits);
        this.unlocks.clear();
        readList(tag, "Unlocks", this.unlocks);
        this.techBlocks.clear();
        readList(tag, "TechBlocks", this.techBlocks);
        this.disabledAbilities.clear();
        readList(tag, "Disabled", this.disabledAbilities);
        this.grantedUnlocks.clear();
        readList(tag, "GrantedUnlocks", this.grantedUnlocks);
        markDirty();
    }

    public void writeNbt(NbtCompound root) {
        NbtCompound tag = new NbtCompound();
        writeId(tag, "Origin", origin);
        writeId(tag, "Gender", gender);
        writeId(tag, "Class", clazz);
        tag.putBoolean("Complete", complete);
        tag.putInt("UnlockPoints", unlockPoints);
        writeList(tag, "Granted", grantedTraits);
        writeList(tag, "Unlocks", unlocks);
        writeList(tag, "TechBlocks", techBlocks);
        writeList(tag, "Disabled", disabledAbilities);
        writeList(tag, "GrantedUnlocks", grantedUnlocks);
        root.put(NBT_KEY, tag);
    }

    private static Identifier readId(NbtCompound tag, String key) {
        String value = tag.getString(key);
        return value == null || value.isEmpty() ? null : Identifier.tryParse(value);
    }

    private static void writeId(NbtCompound tag, String key, Identifier id) {
        if (id != null) {
            tag.putString(key, id.toString());
        }
    }

    private static void readList(NbtCompound tag, String key, Set<Identifier> target) {
        NbtList list = tag.getList(key, NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            Identifier id = Identifier.tryParse(list.getString(i));
            if (id != null) {
                target.add(id);
            }
        }
    }

    private static void writeList(NbtCompound tag, String key, Set<Identifier> source) {
        if (source.isEmpty()) {
            return;
        }
        NbtList list = new NbtList();
        for (Identifier id : source) {
            list.add(NbtString.of(id.toString()));
        }
        tag.put(key, list);
    }
}
