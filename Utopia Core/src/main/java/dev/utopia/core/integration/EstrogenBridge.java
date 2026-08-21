package dev.utopia.core.integration;

import dev.utopia.core.UtopiaCore;
import dev.utopia.core.character.CharacterData;
import dev.utopia.core.character.CharacterService;
import dev.utopia.core.character.TraitBundle;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Integration mit dem Estrogen-Mod - bewusst ohne Compile-Abhaengigkeit.
 *
 * Estrogen ist Kotlin und zieht cynosure/kittyconfig/FLK nach. Statt dagegen zu
 * linken, greifen wir ueber die Vanilla-Registries auf genau das zu, was wir
 * brauchen:
 *
 *   Effekt:    estrogen:estrogen        ("Girl Power": Dash + Koerper-Feature)
 *   Attribute: estrogen:show_boobs      (steuert das Chest-Rendering)
 *              estrogen:boob_initial_size
 *              estrogen:boob_growing_start_time
 *              estrogen:dash_level
 *
 * Dadurch muss NICHTS aus Estrogen kopiert werden: das Chest-Feature haengt am
 * Attribut, nicht am Effekt, und laesst sich unabhaengig vom Dash setzen.
 *
 * Rollen:
 * - Cis-Frau  -> bekommt den Effekt sofort dauerhaft (inkl. Dash + Chest).
 * - Transfem  -> startet ohne Effekt und geht den Estrogen-Weg im Spiel
 *                (Liquid Estrogen / Patches). Sobald der Effekt das erste Mal
 *                anliegt, wird das Trait-Buendel {@code utopia:female} dauerhaft
 *                gewaehrt - ab da bleibt es auch ohne Patches bestehen.
 * - Mann      -> eigener Marker-Effekt utopia:masculine, kein Dash.
 */
public final class EstrogenBridge {

    public static final Identifier ESTROGEN_EFFECT = new Identifier("estrogen", "estrogen");
    public static final Identifier SHOW_BOOBS = new Identifier("estrogen", "show_boobs");
    public static final Identifier BOOB_SIZE = new Identifier("estrogen", "boob_initial_size");
    public static final Identifier BOOB_START = new Identifier("estrogen", "boob_growing_start_time");
    public static final Identifier DASH_LEVEL = new Identifier("estrogen", "dash_level");

    /** Trait-Buendel, das nach abgeschlossener Transition gewaehrt wird. */
    public static final Identifier FEMALE_TRAIT = new Identifier("utopia", "female");
    /** Gender-Id, die als "in Transition" gilt. */
    public static final Identifier TRANSFEM = new Identifier("utopia", "transfem");

    private static boolean loaded;

    private EstrogenBridge() {
    }

    public static void init() {
        loaded = FabricLoader.getInstance().isModLoaded("estrogen");
        UtopiaCore.LOGGER.info("Estrogen-Integration: {}", loaded ? "aktiv" : "inaktiv");
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Wird nach jedem Neuberechnen des Buendels aufgerufen. Setzt nur das, was
     * die Trait-JSONs nicht selbst ueber generische Attribute abdecken koennen.
     */
    public static void apply(ServerPlayerEntity player, CharacterData data, TraitBundle bundle) {
        if (!loaded) {
            return;
        }
        boolean feminineBody = bundle.effects.containsKey(ESTROGEN_EFFECT)
                || data.grantedTraits().contains(FEMALE_TRAIT);
        setAttribute(player, SHOW_BOOBS, feminineBody ? 1.0D : 0.0D);
    }

    /**
     * Transfem-Fortschritt: sobald der Estrogen-Effekt anliegt, wird der
     * weibliche Trait dauerhaft gewaehrt. Laeuft im 40-Tick-Rhythmus und nur
     * fuer Spieler, die tatsaechlich transfem sind - kein globaler Scan.
     */
    public static void checkProgress(ServerPlayerEntity player, CharacterData data) {
        if (!loaded || !TRANSFEM.equals(data.gender()) || data.grantedTraits().contains(FEMALE_TRAIT)) {
            return;
        }
        StatusEffect effect = Registries.STATUS_EFFECT.get(ESTROGEN_EFFECT);
        if (effect == null || !player.hasStatusEffect(effect)) {
            return;
        }
        data.grantTrait(FEMALE_TRAIT);
        player.addStatusEffect(new StatusEffectInstance(effect, CharacterService.PERMANENT, 0, true, false, true));
        UtopiaCore.LOGGER.info("{} hat die Transition abgeschlossen", player.getName().getString());
    }

    private static void setAttribute(ServerPlayerEntity player, Identifier id, double value) {
        EntityAttribute attribute = Registries.ATTRIBUTE.get(id);
        if (attribute == null) {
            return;
        }
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance == null) {
            return;
        }
        java.util.UUID uuid = CharacterService.modifierUuid(id, EntityAttributeModifier.Operation.ADDITION);
        instance.removeModifier(uuid);
        if (value != 0.0D) {
            instance.addPersistentModifier(
                    new EntityAttributeModifier(uuid, "utopia:gender", value, EntityAttributeModifier.Operation.ADDITION));
        }
    }
}
