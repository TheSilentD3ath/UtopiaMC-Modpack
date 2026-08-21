package net.levelz.stats;

import net.levelz.init.ConfigInit;
import net.levelz.network.PlayerStatsServerPacket;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;

/**
 * Fähigkeiten - war in LevelZ ein festes Enum mit zwölf Werten, ist jetzt eine
 * Registry.
 *
 * Warum kein Enum mehr: der geplante Tech-Skill und klassenspezifische
 * Fähigkeiten lassen sich mit einem Enum nicht ergänzen, ohne den Mod jedes Mal
 * neu zu bauen. Die Klasse behält aber bewusst die Enum-Oberfläche
 * ({@code values()}, {@code valueOf()}, {@code name()}), damit die rund 20
 * geerbten Dateien und ~130 Mixins unverändert weiterlaufen.
 *
 * Die zwölf Ursprungs-Fähigkeiten sind hier fest eingetragen - gleiche
 * Reihenfolge, gleiche NBT-Schlüssel, gleiche Zahlen aus der Config wie vorher.
 * Bestehende Spielstände merken vom Umbau nichts.
 *
 * Was früher als {@code switch (skill)} in vier Netzwerk-Methoden stand
 * (Attribut setzen, Freischaltlisten neu berechnen), hängt jetzt als Verhalten
 * an der Fähigkeit selbst. Neue Fähigkeiten bringen ihr Verhalten also mit,
 * statt dass an vier Stellen ein Fall ergänzt werden muss.
 */
public final class Skill {

    private static final List<Skill> REGISTRY = new ArrayList<>();
    private static final Map<String, Skill> BY_NAME = new LinkedHashMap<>();
    private static final Map<String, Skill> BY_NBT = new HashMap<>();
    /** Für {@link #values()} - wird in Render- und Netzwerkschleifen benutzt, darf nicht pro Aufruf allokieren. */
    private static Skill[] cachedValues = new Skill[0];
    private static boolean frozen;

    // --- die zwölf Ursprungs-Fähigkeiten, Reihenfolge ist die des alten Enums ---

    public static final Skill HEALTH = builtin("HEALTH", "HealthLevel")
            .attribute(EntityAttributes.GENERIC_MAX_HEALTH, () -> ConfigInit.CONFIG.healthBase, () -> ConfigInit.CONFIG.healthBonus)
            .healsOnGain()
            .register();
    public static final Skill STRENGTH = builtin("STRENGTH", "StrengthLevel")
            .attribute(EntityAttributes.GENERIC_ATTACK_DAMAGE, () -> ConfigInit.CONFIG.attackBase, () -> ConfigInit.CONFIG.attackBonus)
            .register();
    public static final Skill AGILITY = builtin("AGILITY", "AgilityLevel")
            .attribute(EntityAttributes.GENERIC_MOVEMENT_SPEED, () -> ConfigInit.CONFIG.movementBase, () -> ConfigInit.CONFIG.movementBonus)
            .register();
    public static final Skill DEFENSE = builtin("DEFENSE", "DefenseLevel")
            .attribute(EntityAttributes.GENERIC_ARMOR, () -> ConfigInit.CONFIG.defenseBase, () -> ConfigInit.CONFIG.defenseBonus)
            .register();
    public static final Skill STAMINA = builtin("STAMINA", "StaminaLevel").register();
    public static final Skill LUCK = builtin("LUCK", "LuckLevel")
            .attribute(EntityAttributes.GENERIC_LUCK, () -> ConfigInit.CONFIG.luckBase, () -> ConfigInit.CONFIG.luckBonus)
            .register();
    public static final Skill ARCHERY = builtin("ARCHERY", "ArcheryLevel").register();
    public static final Skill TRADE = builtin("TRADE", "TradeLevel").register();
    public static final Skill SMITHING = builtin("SMITHING", "SmithingLevel")
            .onLevelChanged(PlayerStatsServerPacket::syncLockedSmithingItemList)
            .register();
    public static final Skill MINING = builtin("MINING", "MiningLevel")
            .onLevelChanged(PlayerStatsServerPacket::syncLockedBlockList)
            .register();
    public static final Skill FARMING = builtin("FARMING", "FarmingLevel").register();
    public static final Skill ALCHEMY = builtin("ALCHEMY", "AlchemyLevel")
            .onLevelChanged(PlayerStatsServerPacket::syncLockedBrewingItemList)
            .register();

    // --- Instanz ---------------------------------------------------------

    private final String name;
    private final String nbt;
    private final int index;
    private final int iconIndex;
    private final boolean builtin;
    @Nullable
    private final EntityAttribute attribute;
    @Nullable
    private final DoubleSupplier base;
    @Nullable
    private final DoubleSupplier perLevel;
    private final boolean healsOnGain;
    @Nullable
    private final Consumer<PlayerStatsManager> levelChanged;

    private Skill(Builder builder, int index) {
        this.name = builder.name;
        this.nbt = builder.nbt;
        this.index = index;
        this.iconIndex = builder.iconIndex >= 0 ? builder.iconIndex : index;
        this.builtin = builder.builtin;
        this.attribute = builder.attribute;
        this.base = builder.base;
        this.perLevel = builder.perLevel;
        this.healsOnGain = builder.healsOnGain;
        this.levelChanged = builder.levelChanged;
    }

    public String name() {
        return this.name;
    }

    /** Kleingeschrieben - so stehen die Fähigkeiten in Datapacks und Übersetzungsschlüsseln. */
    public String key() {
        return this.name.toLowerCase(Locale.ROOT);
    }

    public String getNbt() {
        return this.nbt;
    }

    /** Fortlaufender Index ab 0. Speicher und Netzwerk arbeiten damit. */
    public int index() {
        return this.index;
    }

    /** Spalte in {@code textures/gui/icons.png} (Reihe v=16). */
    public int iconIndex() {
        return this.iconIndex;
    }

    /** Alte, 1-basierte Id des Enums. Nur noch für geerbten Code. */
    public int getId() {
        return this.index + 1;
    }

    public boolean isBuiltin() {
        return this.builtin;
    }

    @Override
    public String toString() {
        return this.name;
    }

    // --- Verhalten -------------------------------------------------------

    /**
     * Setzt den Grundwert des zugehörigen Attributs auf {@code base + level * proLevel}.
     * Ersetzt die vier {@code switch (skill)}-Blöcke aus LevelZ.
     */
    public void applyAttribute(LivingEntity entity, int level) {
        if (this.attribute == null || this.base == null || this.perLevel == null) {
            return;
        }
        EntityAttributeInstance instance = entity.getAttributeInstance(this.attribute);
        if (instance != null) {
            instance.setBaseValue(this.base.getAsDouble() + level * this.perLevel.getAsDouble());
        }
    }

    /** Aufstieg: Attribut neu setzen und - bei Leben - die dazugewonnenen Herzen auffüllen. */
    public void onGain(PlayerEntity player, int newLevel, int gained) {
        applyAttribute(player, newLevel);
        if (this.healsOnGain && gained > 0 && this.perLevel != null) {
            player.setHealth(player.getHealth() + (float) (this.perLevel.getAsDouble() * gained));
        }
    }

    /** Rechnet die Freischaltlisten neu, die von dieser Fähigkeit abhängen. */
    public void onLevelChanged(PlayerStatsManager stats) {
        if (this.levelChanged != null) {
            this.levelChanged.accept(stats);
        }
    }

    // --- Registry --------------------------------------------------------

    private static Builder builtin(String name, String nbt) {
        return new Builder(name, nbt, true);
    }

    /**
     * Meldet eine Fähigkeit aus einem Datapack an. Nur vor dem Einfrieren
     * möglich: Index und Netzwerkreihenfolge müssen für Server und Client
     * gleich bleiben, deshalb wird die Liste beim Weltstart festgezurrt.
     */
    public static Builder create(String name, String nbt) {
        return new Builder(name, nbt, false);
    }

    public static void freeze() {
        frozen = true;
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static int count() {
        return REGISTRY.size();
    }

    public static Skill[] values() {
        return cachedValues;
    }

    public static List<Skill> all() {
        return REGISTRY;
    }

    /** Wirft wie vorher {@code IllegalArgumentException}, wenn der Name unbekannt ist. */
    public static Skill valueOf(String name) {
        Skill skill = BY_NAME.get(name.toUpperCase(Locale.ROOT));
        if (skill == null) {
            throw new IllegalArgumentException("Unknown skill: " + name);
        }
        return skill;
    }

    @Nullable
    public static Skill find(String name) {
        return name == null ? null : BY_NAME.get(name.toUpperCase(Locale.ROOT));
    }

    @Nullable
    public static Skill fromNbt(String nbt) {
        return BY_NBT.get(nbt);
    }

    public static Iterable<Skill> listInRandomOrder(@Nullable Random random) {
        Random finalRandom = random == null ? Random.create() : random;
        return Arrays.stream(values()).sorted(Comparator.comparing(it -> finalRandom.nextInt())).collect(Collectors.toList());
    }

    public static final class Builder {
        private final String name;
        private final String nbt;
        private final boolean builtin;
        @Nullable
        private EntityAttribute attribute;
        @Nullable
        private DoubleSupplier base;
        @Nullable
        private DoubleSupplier perLevel;
        private boolean healsOnGain;
        private int iconIndex = -1;
        @Nullable
        private Consumer<PlayerStatsManager> levelChanged;

        private Builder(String name, String nbt, boolean builtin) {
            this.name = name.toUpperCase(Locale.ROOT);
            this.nbt = nbt;
            this.builtin = builtin;
        }

        public Builder attribute(EntityAttribute attribute, DoubleSupplier base, DoubleSupplier perLevel) {
            this.attribute = attribute;
            this.base = base;
            this.perLevel = perLevel;
            return this;
        }

        public Builder healsOnGain() {
            this.healsOnGain = true;
            return this;
        }

        public Builder icon(int iconIndex) {
            this.iconIndex = iconIndex;
            return this;
        }

        public Builder onLevelChanged(Consumer<PlayerStatsManager> action) {
            this.levelChanged = action;
            return this;
        }

        public Skill register() {
            if (frozen) {
                throw new IllegalStateException("Skill registry is frozen, cannot add " + this.name);
            }
            Skill existing = BY_NAME.get(this.name);
            if (existing != null) {
                return existing;
            }
            Skill skill = new Skill(this, REGISTRY.size());
            REGISTRY.add(skill);
            BY_NAME.put(skill.name, skill);
            BY_NBT.put(skill.nbt, skill);
            cachedValues = REGISTRY.toArray(new Skill[0]);
            return skill;
        }
    }
}
