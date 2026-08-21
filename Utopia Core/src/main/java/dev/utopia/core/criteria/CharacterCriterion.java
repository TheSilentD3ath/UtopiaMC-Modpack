package dev.utopia.core.criteria;

import com.google.gson.JsonObject;
import dev.utopia.core.character.CharacterData;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import org.jetbrains.annotations.Nullable;

/**
 * Advancement-Ausloeser {@code utopia:character}. Damit koennen Quests
 * (Heracles), Datapacks und Loot-Tabellen an Origin, Gender oder Klasse haengen.
 *
 * Bewusst ein eigener Ausloeser statt eines NBT-Predicates auf {@code minecraft:tick}:
 * letzteres wuerde den Spieler jeden Tick komplett nach NBT serialisieren, nur um
 * eine Zeichenkette zu vergleichen. Hier feuert es genau zweimal - bei der Auswahl
 * und beim Betreten der Welt.
 *
 * Bedingungen (alle optional, leer = egal):
 * <pre>
 * { "trigger": "utopia:character", "conditions": { "class": "utopia:tank" } }
 * </pre>
 */
public class CharacterCriterion extends AbstractCriterion<CharacterCriterion.Conditions> {

    static final Identifier ID = new Identifier("utopia", "character");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    protected Conditions conditionsFromJson(JsonObject json, LootContextPredicate playerPredicate,
            AdvancementEntityPredicateDeserializer deserializer) {
        return new Conditions(playerPredicate, read(json, "origin"), read(json, "gender"), read(json, "class"));
    }

    @Nullable
    private static Identifier read(JsonObject json, String key) {
        return json.has(key) ? Identifier.tryParse(json.get(key).getAsString()) : null;
    }

    public void trigger(ServerPlayerEntity player, CharacterData data) {
        this.trigger(player, conditions -> conditions.matches(data));
    }

    public static class Conditions extends AbstractCriterionConditions {

        @Nullable
        private final Identifier origin;
        @Nullable
        private final Identifier gender;
        @Nullable
        private final Identifier clazz;

        public Conditions(LootContextPredicate playerPredicate, @Nullable Identifier origin,
                @Nullable Identifier gender, @Nullable Identifier clazz) {
            super(ID, playerPredicate);
            this.origin = origin;
            this.gender = gender;
            this.clazz = clazz;
        }

        public boolean matches(CharacterData data) {
            return matches(this.origin, data.origin())
                    && matches(this.gender, data.gender())
                    && matches(this.clazz, data.clazz());
        }

        private static boolean matches(@Nullable Identifier expected, @Nullable Identifier actual) {
            return expected == null || expected.equals(actual);
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer serializer) {
            JsonObject json = super.toJson(serializer);
            write(json, "origin", this.origin);
            write(json, "gender", this.gender);
            write(json, "class", this.clazz);
            return json;
        }

        private static void write(JsonObject json, String key, @Nullable Identifier value) {
            if (value != null) {
                json.addProperty(key, value.toString());
            }
        }
    }
}
