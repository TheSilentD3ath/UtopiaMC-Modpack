package dev.utopia.core.character;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.utopia.core.UtopiaCore;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datapack-Loader fuer Origins, Gender und Klassen.
 *
 * Server-autoritativ: geladen wird nur serverseitig, der Client bekommt die
 * Definitionen beim Join als ein Paket und haelt sie in derselben Map.
 * Die Traits liegen in unveraenderlichen, nach {@code order} sortierten Listen -
 * kein Sortieren zur Laufzeit, kein Streaming im Hot Path.
 */
public class CharacterTraits extends JsonDataLoader implements IdentifiableResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final Map<TraitType, Map<Identifier, CharacterTrait>> REGISTRY = new EnumMap<>(TraitType.class);
    private static final Map<TraitType, List<Identifier>> ORDERED = new EnumMap<>(TraitType.class);

    private final TraitType type;

    private CharacterTraits(TraitType type) {
        super(GSON, type.directory());
        this.type = type;
        REGISTRY.put(type, Map.of());
        ORDERED.put(type, List.of());
    }

    public static List<CharacterTraits> all() {
        List<CharacterTraits> list = new ArrayList<>();
        for (TraitType type : TraitType.values()) {
            list.add(new CharacterTraits(type));
        }
        return list;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
        Map<Identifier, CharacterTrait> parsed = new LinkedHashMap<>();
        prepared.forEach((id, json) -> CharacterTrait.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> UtopiaCore.LOGGER.error("Trait {} konnte nicht geladen werden: {}", id, error))
                .ifPresent(trait -> parsed.put(id, trait)));

        List<Identifier> ordered = new ArrayList<>(parsed.keySet());
        ordered.sort(Comparator
                .comparingInt((Identifier id) -> parsed.get(id).order())
                .thenComparing(Identifier::toString));

        REGISTRY.put(type, Collections.unmodifiableMap(parsed));
        ORDERED.put(type, List.copyOf(ordered));
        UtopiaCore.LOGGER.info("{} {} geladen", parsed.size(), type.key());
    }

    @Override
    public Identifier getFabricId() {
        return UtopiaCore.id(type.key() + "_traits");
    }

    // --- Zugriff ---------------------------------------------------------

    public static CharacterTrait get(TraitType type, Identifier id) {
        if (id == null) {
            return null;
        }
        return REGISTRY.getOrDefault(type, Map.of()).get(id);
    }

    public static Map<Identifier, CharacterTrait> map(TraitType type) {
        return REGISTRY.getOrDefault(type, Map.of());
    }

    /** Nach {@code order} sortierte Ids - genau die Reihenfolge im Auswahl-Screen. */
    public static List<Identifier> ordered(TraitType type) {
        return ORDERED.getOrDefault(type, List.of());
    }

    /** Wird clientseitig nach dem Sync-Paket aufgerufen. */
    public static void acceptSynced(TraitType type, Map<Identifier, CharacterTrait> traits, List<Identifier> ordered) {
        REGISTRY.put(type, Collections.unmodifiableMap(traits));
        ORDERED.put(type, List.copyOf(ordered));
    }
}
