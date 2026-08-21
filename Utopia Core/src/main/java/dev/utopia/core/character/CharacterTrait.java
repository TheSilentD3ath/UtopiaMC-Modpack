package dev.utopia.core.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Eine Trait-Definition (Origin, Gender oder Klasse). Alle drei Achsen benutzen
 * bewusst dasselbe Schema - dadurch gibt es nur einen Parser, einen Merge-Pfad
 * und ein Sync-Format, statt drei fast identischer Systeme.
 */
public record CharacterTrait(
        Optional<String> name,
        Optional<String> description,
        Optional<Identifier> icon,
        int order,
        boolean selectable,
        List<AttributeMod> attributes,
        List<EffectSpec> effects,
        SkillSpec skills,
        Loadout loadout,
        List<Identifier> unlocks,
        UnlockSpec unlock,
        Requirements requires) {

    public static final Codec<CharacterTrait> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name").forGetter(CharacterTrait::name),
            Codec.STRING.optionalFieldOf("description").forGetter(CharacterTrait::description),
            Identifier.CODEC.optionalFieldOf("icon").forGetter(CharacterTrait::icon),
            Codec.INT.optionalFieldOf("order", 0).forGetter(CharacterTrait::order),
            Codec.BOOL.optionalFieldOf("selectable", true).forGetter(CharacterTrait::selectable),
            AttributeMod.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(CharacterTrait::attributes),
            EffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(CharacterTrait::effects),
            SkillSpec.CODEC.optionalFieldOf("skills", SkillSpec.EMPTY).forGetter(CharacterTrait::skills),
            Loadout.CODEC.optionalFieldOf("loadout", Loadout.EMPTY).forGetter(CharacterTrait::loadout),
            Identifier.CODEC.listOf().optionalFieldOf("unlocks", List.of()).forGetter(CharacterTrait::unlocks),
            UnlockSpec.CODEC.optionalFieldOf("unlock", UnlockSpec.EMPTY).forGetter(CharacterTrait::unlock),
            Requirements.CODEC.optionalFieldOf("requires", Requirements.NONE).forGetter(CharacterTrait::requires))
            .apply(instance, CharacterTrait::new));

    /** Ein Attribut-Modifier. Die UUID wird deterministisch aus Trait-Id + Attribut erzeugt. */
    public record AttributeMod(Identifier attribute, EntityAttributeModifier.Operation operation, double value) {
        public static final Codec<EntityAttributeModifier.Operation> OPERATION_CODEC = Codec.STRING.xmap(
                s -> switch (s.toLowerCase(Locale.ROOT)) {
                    case "multiply_base", "multiply_total_base" -> EntityAttributeModifier.Operation.MULTIPLY_BASE;
                    case "multiply_total", "multiply" -> EntityAttributeModifier.Operation.MULTIPLY_TOTAL;
                    default -> EntityAttributeModifier.Operation.ADDITION;
                },
                op -> switch (op) {
                    case MULTIPLY_BASE -> "multiply_base";
                    case MULTIPLY_TOTAL -> "multiply_total";
                    default -> "addition";
                });

        public static final Codec<AttributeMod> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("attribute").forGetter(AttributeMod::attribute),
                OPERATION_CODEC.optionalFieldOf("operation", EntityAttributeModifier.Operation.ADDITION).forGetter(AttributeMod::operation),
                Codec.DOUBLE.fieldOf("value").forGetter(AttributeMod::value))
                .apply(instance, AttributeMod::new));
    }

    /** Dauerhafter Statuseffekt. Wird mit unendlicher Dauer gesetzt und nur bei Aenderung angefasst. */
    public record EffectSpec(Identifier effect, int amplifier, boolean showIcon, boolean showParticles,
            boolean toggleable) {
        public static final Codec<EffectSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("effect").forGetter(EffectSpec::effect),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(EffectSpec::amplifier),
                Codec.BOOL.optionalFieldOf("show_icon", true).forGetter(EffectSpec::showIcon),
                Codec.BOOL.optionalFieldOf("show_particles", false).forGetter(EffectSpec::showParticles),
                // Abschaltbar per Taste - z.B. die Nachtsicht der Neko, die man
                // nicht immer haben will.
                Codec.BOOL.optionalFieldOf("toggle", false).forGetter(EffectSpec::toggleable))
                .apply(instance, EffectSpec::new));
    }

    /**
     * Skill-Anteil eines Traits.
     * start_levels: Startlevel statt 0. bonus_points: freie Punkte beim Start.
     * xp_multiplier: Faktor auf gewonnene XP. effect_multiplier: Faktor auf die
     * positiven Effekte des Skills (z.B. Mining-Speed pro Level).
     */
    public record SkillSpec(Map<String, Integer> startLevels, int bonusPoints,
            Map<String, Double> xpMultiplier, Map<String, Double> effectMultiplier) {
        public static final SkillSpec EMPTY = new SkillSpec(Map.of(), 0, Map.of(), Map.of());

        public static final Codec<SkillSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("start_levels", Map.of()).forGetter(SkillSpec::startLevels),
                Codec.INT.optionalFieldOf("bonus_points", 0).forGetter(SkillSpec::bonusPoints),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("xp_multiplier", Map.of()).forGetter(SkillSpec::xpMultiplier),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("effect_multiplier", Map.of()).forGetter(SkillSpec::effectMultiplier))
                .apply(instance, SkillSpec::new));
    }

    /**
     * Item-Angabe im Loadout. Bewusst NICHT ItemStack.CODEC: dessen Item-Feld
     * scheitert hart, wenn der Mod fehlt, und reisst das ganze Trait mit.
     * Hier wird die Id erst beim Austeilen aufgeloest - fehlt sie, gibt es eine
     * Warnung und der Rest der Ausruestung wird trotzdem verteilt.
     */
    public record ItemSpec(Identifier id, int count, Optional<net.minecraft.nbt.NbtCompound> tag) {
        public static final Codec<ItemSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(ItemSpec::id),
                Codec.INT.optionalFieldOf("Count", 1).forGetter(ItemSpec::count),
                net.minecraft.nbt.NbtCompound.CODEC.optionalFieldOf("tag").forGetter(ItemSpec::tag))
                .apply(instance, ItemSpec::new));
    }

    /**
     * Startausruestung. slot: mainhand, offhand, head, chest, legs, feet oder inventory.
     * Verzauberungen werden als {@code {"minecraft:power": 2}} angegeben und im Code
     * gesetzt - direkt in NBT geschriebene Level haetten den falschen Datentyp.
     */
    public record LoadoutEntry(ItemSpec item, String slot, Map<String, Integer> enchantments) {
        public static final Codec<LoadoutEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemSpec.CODEC.fieldOf("item").forGetter(LoadoutEntry::item),
                Codec.STRING.optionalFieldOf("slot", "inventory").forGetter(LoadoutEntry::slot),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("enchantments", Map.of())
                        .forGetter(LoadoutEntry::enchantments))
                .apply(instance, LoadoutEntry::new));
    }

    public record Loadout(boolean clearInventory, List<LoadoutEntry> items) {
        public static final Loadout EMPTY = new Loadout(false, List.of());

        public static final Codec<Loadout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("clear_inventory", false).forGetter(Loadout::clearInventory),
                LoadoutEntry.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Loadout::items))
                .apply(instance, Loadout::new));
    }

    /**
     * Einfluss auf die Freischalt-Baeume.
     *
     * @param pointsPerLevel zusaetzliche Freischaltpunkte pro Gesamtlevel
     * @param discount       Baum -> wieviel guenstiger jeder Knoten dort ist
     * @param levelOffset    Baum -> um wieviel die Levelanforderung sinkt (negativ = frueher)
     */
    public record UnlockSpec(int pointsPerLevel, Map<String, Integer> discount, Map<String, Integer> levelOffset) {
        public static final UnlockSpec EMPTY = new UnlockSpec(0, Map.of(), Map.of());

        public static final Codec<UnlockSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("points_per_level", 0).forGetter(UnlockSpec::pointsPerLevel),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("discount", Map.of()).forGetter(UnlockSpec::discount),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("level_offset", Map.of()).forGetter(UnlockSpec::levelOffset))
                .apply(instance, UnlockSpec::new));
    }

    /** Auswahl-Filter: welche Kombination ist erlaubt. Leer = alles erlaubt. */
    public record Requirements(List<Identifier> origins, List<Identifier> genders, List<Identifier> classes,
            Optional<String> permission) {
        public static final Requirements NONE = new Requirements(List.of(), List.of(), List.of(), Optional.empty());

        public static final Codec<Requirements> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.listOf().optionalFieldOf("origins", List.of()).forGetter(Requirements::origins),
                Identifier.CODEC.listOf().optionalFieldOf("genders", List.of()).forGetter(Requirements::genders),
                Identifier.CODEC.listOf().optionalFieldOf("classes", List.of()).forGetter(Requirements::classes),
                Codec.STRING.optionalFieldOf("permission").forGetter(Requirements::permission))
                .apply(instance, Requirements::new));

        public boolean allows(Identifier origin, Identifier gender, Identifier clazz) {
            return (origins.isEmpty() || origin == null || origins.contains(origin))
                    && (genders.isEmpty() || gender == null || genders.contains(gender))
                    && (classes.isEmpty() || clazz == null || classes.contains(clazz));
        }
    }
}
