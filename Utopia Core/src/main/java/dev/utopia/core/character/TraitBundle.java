package dev.utopia.core.character;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Das zusammengerechnete Ergebnis aller aktiven Traits eines Spielers.
 *
 * Wird genau dann neu gebaut, wenn sich die Auswahl aendert (oder beim Join) -
 * niemals pro Tick. Alle Abfragen im Spielverlauf (XP-Multiplikator,
 * Effekt-Multiplikator) gehen gegen die fertigen Maps hier.
 */
public class TraitBundle {

    public static final TraitBundle EMPTY = new TraitBundle();
    private static final CharacterTrait.LoadoutEntry SHARED_WATER_SUPPLY = createSharedWaterSupply();

    public final List<CharacterTrait.AttributeMod> attributes = new ArrayList<>();
    public final Map<Identifier, CharacterTrait.EffectSpec> effects = new HashMap<>();
    public final Map<String, Integer> startLevels = new HashMap<>();
    public final Map<String, Float> xpMultiplier = new HashMap<>();
    public final Map<String, Float> effectMultiplier = new HashMap<>();
    public final Set<Identifier> unlocks = new LinkedHashSet<>();
    public final List<CharacterTrait.LoadoutEntry> loadout = new ArrayList<>();
    public boolean clearInventory;
    public int bonusPoints;
    /** Zusaetzliche Freischaltpunkte pro Gesamtlevel. */
    public int unlockPointsPerLevel;
    /** Baum -> Rabatt auf Knotenkosten. */
    public final Map<String, Integer> unlockDiscount = new HashMap<>();
    /** Baum -> Verschiebung der Levelanforderung. */
    public final Map<String, Integer> unlockLevelOffset = new HashMap<>();

    /** Merge-Reihenfolge: Origin -> Gender -> Klasse -> gewaehrte Traits. */
    public static TraitBundle build(CharacterData data) {
        TraitBundle bundle = new TraitBundle();
        bundle.add(CharacterTraits.get(TraitType.ORIGIN, data.origin()));
        bundle.add(CharacterTraits.get(TraitType.GENDER, data.gender()));
        bundle.add(CharacterTraits.get(TraitType.CLASS, data.clazz()));
        for (Identifier granted : data.grantedTraits()) {
            // Gewaehrte Buendel duerfen aus jeder Achse stammen.
            for (TraitType type : TraitType.values()) {
                bundle.add(CharacterTraits.get(type, granted));
            }
        }
        // Globale Startversorgung gehoert einmal in dasselbe massgebliche
        // Loadout wie Trait-Ausruestung, nicht einmal pro Auswahlachse.
        bundle.loadout.add(SHARED_WATER_SUPPLY);
        return bundle;
    }

    private static CharacterTrait.LoadoutEntry createSharedWaterSupply() {
        NbtCompound tag = new NbtCompound();
        tag.putString("Potion", "minecraft:purified_water");
        CharacterTrait.ItemSpec water = new CharacterTrait.ItemSpec(
                new Identifier("minecraft", "potion"), 6, Optional.of(tag));
        return new CharacterTrait.LoadoutEntry(water, "inventory", Map.of());
    }

    private void add(CharacterTrait trait) {
        if (trait == null) {
            return;
        }
        attributes.addAll(trait.attributes());
        for (CharacterTrait.EffectSpec effect : trait.effects()) {
            CharacterTrait.EffectSpec existing = effects.get(effect.effect());
            if (existing == null || existing.amplifier() < effect.amplifier()) {
                effects.put(effect.effect(), effect);
            }
        }
        CharacterTrait.SkillSpec skills = trait.skills();
        skills.startLevels().forEach((skill, level) -> startLevels.merge(normalize(skill), level, Integer::sum));
        skills.xpMultiplier().forEach((skill, value) -> xpMultiplier.merge(normalize(skill), value.floatValue(), (a, b) -> a * b));
        skills.effectMultiplier().forEach((skill, value) -> effectMultiplier.merge(normalize(skill), value.floatValue(), (a, b) -> a * b));
        bonusPoints += skills.bonusPoints();
        unlocks.addAll(trait.unlocks());
        unlockPointsPerLevel += trait.unlock().pointsPerLevel();
        trait.unlock().discount().forEach((tree, value) -> unlockDiscount.merge(tree, value, Integer::sum));
        trait.unlock().levelOffset().forEach((tree, value) -> unlockLevelOffset.merge(tree, value, Integer::sum));
        loadout.addAll(trait.loadout().items());
        clearInventory |= trait.loadout().clearInventory();
    }

    /** "utopia:mining" und "mining" bezeichnen denselben Skill. */
    public static String normalize(String skill) {
        int colon = skill.indexOf(':');
        return (colon >= 0 ? skill.substring(colon + 1) : skill).toLowerCase();
    }

    public float xpMultiplier(String skill) {
        return xpMultiplier.getOrDefault(skill, 1.0F);
    }

    public float effectMultiplier(String skill) {
        return effectMultiplier.getOrDefault(skill, 1.0F);
    }
}
