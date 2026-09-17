package dev.utopia.core.character;

import dev.utopia.core.UtopiaCore;
import dev.utopia.core.integration.EstrogenBridge;
import dev.utopia.core.network.UtopiaNetworking;
import net.levelz.stats.Skill;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Zentrale Anwendungslogik: aus Charakterdaten wird Spielzustand.
 *
 * Regeln:
 * - Neuberechnung nur bei Aenderung ({@code dirty}) oder beim Join.
 * - Attribut-Modifier bekommen deterministische UUIDs, damit sie sich beim
 *   erneuten Anwenden sauber selbst ersetzen und nichts doppelt stapelt.
 * - Kein Scan pro Tick; der Tick-Hook laeuft nur ueber Spieler ohne Charakter.
 */
public final class CharacterService {

    /** Praktisch unendlich in 1.20.1 (echte INFINITE-Dauer gibt es erst ab 1.20.2). */
    public static final int PERMANENT = Integer.MAX_VALUE;
    private static int tickCounter;

    private CharacterService() {
    }

    // --- Anwenden --------------------------------------------------------

    public static void applyAll(ServerPlayerEntity player, CharacterData data, boolean force) {
        if (!data.isDirty() && !force) {
            return;
        }
        TraitBundle bundle = TraitBundle.build(data);
        data.setBundle(bundle);

        CharacterStateApplier.applyAttributes(player, data, bundle);
        CharacterStateApplier.applyEffects(player, data, bundle);
        EstrogenBridge.apply(player, data, bundle);
        CharacterStateApplier.syncGrantedUnlocks(data, bundle);

        data.clearDirty();
    }

    /**
     * Einmalige Startwerte: Skill-Level, Bonuspunkte, Ausruestung.
     * Wird ausschliesslich beim Abschluss der Charaktererstellung aufgerufen.
     */
    public static void applyStart(ServerPlayerEntity player, CharacterData data) {
        CharacterStarter.apply(player, data.bundle());
    }

    // --- Faehigkeiten ----------------------------------------------------

    /**
     * Schaltet alle abschaltbaren Faehigkeiten des Charakters um. Sind welche
     * an, gehen alle aus; sind alle aus, gehen alle wieder an.
     *
     * Bewusst ein Schalter fuer alles statt einer Auswahl: aktuell hat kein
     * Charakter mehr als eine abschaltbare Faehigkeit. Kommt eine zweite dazu,
     * ist hier die Stelle fuer eine kleine Liste.
     *
     * @return die betroffenen Effekte, oder leer wenn es nichts zu schalten gibt.
     */
    public static List<StatusEffect> toggleAbilities(ServerPlayerEntity player, CharacterData data) {
        List<Identifier> toggleable = new ArrayList<>();
        data.bundle().effects.forEach((id, spec) -> {
            if (spec.toggleable()) {
                toggleable.add(id);
            }
        });
        if (toggleable.isEmpty()) {
            return List.of();
        }

        boolean anyActive = toggleable.stream().anyMatch(id -> !data.disabledAbilities().contains(id));
        if (anyActive) {
            data.disabledAbilities().addAll(toggleable);
        } else {
            data.disabledAbilities().removeAll(toggleable);
        }
        data.markDirty();
        applyAll(player, data, true);

        List<StatusEffect> affected = new ArrayList<>();
        for (Identifier id : toggleable) {
            StatusEffect effect = Registries.STATUS_EFFECT.get(id);
            if (effect != null) {
                affected.add(effect);
            }
        }
        return affected;
    }

    public static boolean abilitiesEnabled(CharacterData data) {
        return data.bundle().effects.entrySet().stream()
                .anyMatch(entry -> entry.getValue().toggleable() && !data.disabledAbilities().contains(entry.getKey()));
    }

    // --- Auswahl ---------------------------------------------------------

    /** Prueft die Kombination gegen die Requirements aller drei Traits. */
    public static boolean isValidSelection(Identifier origin, Identifier gender, Identifier clazz) {
        CharacterTrait o = CharacterTraits.get(TraitType.ORIGIN, origin);
        CharacterTrait g = CharacterTraits.get(TraitType.GENDER, gender);
        CharacterTrait c = CharacterTraits.get(TraitType.CLASS, clazz);
        if (o == null || g == null || c == null) {
            return false;
        }
        if (!o.selectable() || !g.selectable() || !c.selectable()) {
            return false;
        }
        return o.requires().allows(origin, gender, clazz)
                && g.requires().allows(origin, gender, clazz)
                && c.requires().allows(origin, gender, clazz);
    }

    public static void completeSelection(ServerPlayerEntity player, CharacterData data,
            Identifier origin, Identifier gender, Identifier clazz) {
        completeSelection(player, data, origin, gender, clazz, true);
    }

    /**
     * @param giveStart Startlevel, Bonuspunkte und Ausruestung mitgeben. Beim
     *                  Wechsel per Befehl bewusst {@code false} - sonst liegt
     *                  nach dem dritten Ausprobieren dreimal Ausruestung im
     *                  Inventar. Wer den vollen Start will, nimmt
     *                  {@code /utopia character reset}.
     */
    public static void completeSelection(ServerPlayerEntity player, CharacterData data,
            Identifier origin, Identifier gender, Identifier clazz, boolean giveStart) {
        data.select(origin, gender, clazz);
        applyAll(player, data, true);
        if (giveStart) {
            applyStart(player, data);
        }
        setSelectionLock(player, false);
        UtopiaCore.CHARACTER_CRITERION.trigger(player, data);
        UtopiaNetworking.syncCharacter(player, data);
        UtopiaCore.LOGGER.info("{} startet als {} / {} / {}", player.getName().getString(), origin, gender, clazz);
    }

    /**
     * Solange die Auswahl laeuft, ist der Spieler unverwundbar und satt - er
     * steht ja handlungsunfaehig im Screen. Wird beim Abschluss zurueckgesetzt.
     *
     * Hinweis: setzt {@code invulnerable} hart. Wer vorher per /effect oder
     * Gamemode unverwundbar war, ist es nach der Auswahl nicht mehr.
     */
    public static void setSelectionLock(ServerPlayerEntity player, boolean locked) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (player.isInvulnerable() != locked) {
            player.setInvulnerable(locked);
        }
        if (locked) {
            player.getHungerManager().setFoodLevel(20);
            player.setVelocity(0.0D, 0.0D, 0.0D);
            player.fallDistance = 0.0F;
        }
    }

    // --- Tick ------------------------------------------------------------

    /**
     * Einziger wiederkehrende Hook. Laeuft alle 40 Ticks und beruehrt nur
     * Spieler, deren Charakter noch nicht fertig ist oder deren Daten dirty sind.
     */
    public static void serverTick(MinecraftServer server) {
        if (++tickCounter < 40) {
            return;
        }
        tickCounter = 0;
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity player = players.get(i);
            CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
            if (data.isDirty()) {
                applyAll(player, data, false);
                UtopiaNetworking.syncCharacter(player, data);
            }
            if (!data.isComplete()) {
                setSelectionLock(player, true);
                UtopiaNetworking.openCreation(player);
            } else {
                EstrogenBridge.checkProgress(player, data);
            }
        }
    }

    // --- Helfer ----------------------------------------------------------

    public static Skill resolveSkill(String name) {
        return CharacterStarter.resolveSkill(name);
    }

    /** Deterministische UUID pro Attribut+Operation, damit Modifier ersetzbar bleiben. */
    public static UUID modifierUuid(Identifier attribute, EntityAttributeModifier.Operation operation) {
        return CharacterStateApplier.modifierUuid(attribute, operation);
    }

    public static List<Identifier> selectable(TraitType type, Identifier origin, Identifier gender, Identifier clazz) {
        List<Identifier> result = new ArrayList<>();
        for (Identifier id : CharacterTraits.ordered(type)) {
            CharacterTrait trait = CharacterTraits.get(type, id);
            if (trait != null && trait.selectable() && trait.requires().allows(origin, gender, clazz)) {
                result.add(id);
            }
        }
        return result;
    }
}
