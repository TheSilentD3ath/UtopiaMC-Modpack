package dev.utopia.core.character;

import dev.utopia.core.UtopiaCore;
import dev.utopia.core.integration.EstrogenBridge;
import dev.utopia.core.network.UtopiaNetworking;
import net.levelz.access.PlayerStatsManagerAccess;
import net.levelz.network.PlayerStatsServerPacket;
import net.levelz.stats.PlayerStatsManager;
import net.levelz.stats.Skill;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final String MODIFIER_NAME = "utopia:character";
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

        applyAttributes(player, data, bundle);
        applyEffects(player, data, bundle);
        EstrogenBridge.apply(player, data, bundle);
        syncGrantedUnlocks(data, bundle);

        data.clearDirty();
    }

    private static void applyAttributes(ServerPlayerEntity player, CharacterData data, TraitBundle bundle) {
        // Was beim letzten Mal gesetzt wurde und jetzt nicht mehr dazugehoert,
        // muss weg - sonst behaelt ein Mann, der zur Frau wird, seine zwei
        // Extraherzen.
        Set<String> nowApplied = new LinkedHashSet<>();
        for (CharacterTrait.AttributeMod mod : bundle.attributes) {
            nowApplied.add(attributeKey(mod.attribute(), mod.operation()));
        }
        for (String stale : data.appliedAttributes()) {
            if (!nowApplied.contains(stale)) {
                removeAttribute(player, stale);
            }
        }
        data.appliedAttributes().clear();
        data.appliedAttributes().addAll(nowApplied);

        // Die neuen setzen. Deterministische UUIDs halten das idempotent.
        for (CharacterTrait.AttributeMod mod : bundle.attributes) {
            EntityAttribute attribute = Registries.ATTRIBUTE.get(mod.attribute());
            if (attribute == null) {
                continue;
            }
            EntityAttributeInstance instance = player.getAttributeInstance(attribute);
            if (instance == null) {
                continue;
            }
            UUID uuid = modifierUuid(mod.attribute(), mod.operation());
            instance.removeModifier(uuid);
            instance.addPersistentModifier(new EntityAttributeModifier(uuid, MODIFIER_NAME, mod.value(), mod.operation()));
        }
        // Health-Aenderungen duerfen den Spieler nicht mit 0 HP zuruecklassen.
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * Startknoten der aktuellen Wahl gewaehren und die der alten wieder abnehmen.
     * Gekaufte Knoten bleiben unangetastet - nur Geschenke sind an die Klasse
     * gebunden.
     */
    private static void syncGrantedUnlocks(CharacterData data, TraitBundle bundle) {
        for (Identifier stale : Set.copyOf(data.grantedUnlocks())) {
            if (!bundle.unlocks.contains(stale)) {
                data.grantedUnlocks().remove(stale);
                data.unlocks().remove(stale);
            }
        }
        for (Identifier granted : bundle.unlocks) {
            data.grantedUnlocks().add(granted);
            data.unlocks().add(granted);
        }
    }

    /** "minecraft:generic.max_health|0" - Attribut plus Operation, so wie die UUID abgeleitet wird. */
    private static String attributeKey(Identifier attribute, EntityAttributeModifier.Operation operation) {
        return attribute + "|" + operation.getId();
    }

    private static void removeAttribute(ServerPlayerEntity player, String key) {
        int split = key.lastIndexOf('|');
        Identifier id = Identifier.tryParse(key.substring(0, split));
        EntityAttributeModifier.Operation operation = EntityAttributeModifier.Operation.fromId(
                Integer.parseInt(key.substring(split + 1)));
        if (id == null) {
            return;
        }
        EntityAttribute attribute = Registries.ATTRIBUTE.get(id);
        if (attribute == null) {
            return;
        }
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance != null) {
            instance.removeModifier(modifierUuid(id, operation));
        }
    }

    private static void applyEffects(ServerPlayerEntity player, CharacterData data, TraitBundle bundle) {
        // Abschaltbare Faehigkeiten, die der Spieler ausgeschaltet hat, zaehlen
        // hier wie nicht vorhanden - sie werden also abgenommen und nicht neu
        // gesetzt.
        Map<Identifier, CharacterTrait.EffectSpec> active = new LinkedHashMap<>();
        bundle.effects.forEach((id, spec) -> {
            if (!spec.toggleable() || !data.disabledAbilities().contains(id)) {
                active.put(id, spec);
            }
        });

        // Effekte der vorherigen Wahl abnehmen - sonst traegt man nach dem
        // Wechsel beide Geschlechter-Effekte gleichzeitig.
        for (Identifier stale : data.appliedEffects()) {
            if (!active.containsKey(stale)) {
                StatusEffect effect = Registries.STATUS_EFFECT.get(stale);
                if (effect != null) {
                    player.removeStatusEffect(effect);
                }
            }
        }
        data.appliedEffects().clear();
        data.appliedEffects().addAll(active.keySet());

        active.forEach((id, spec) -> {
            StatusEffect effect = Registries.STATUS_EFFECT.get(id);
            if (effect == null) {
                return;
            }
            StatusEffectInstance current = player.getStatusEffect(effect);
            if (current != null && current.getAmplifier() == spec.amplifier() && current.getDuration() > 20 * 60 * 60) {
                return; // schon dauerhaft aktiv, nichts tun
            }
            player.addStatusEffect(new StatusEffectInstance(effect, PERMANENT, spec.amplifier(), true,
                    spec.showParticles(), spec.showIcon()));
        });
    }

    /**
     * Einmalige Startwerte: Skill-Level, Bonuspunkte, Ausruestung.
     * Wird ausschliesslich beim Abschluss der Charaktererstellung aufgerufen.
     */
    public static void applyStart(ServerPlayerEntity player, CharacterData data) {
        TraitBundle bundle = data.bundle();
        PlayerStatsManager stats = ((PlayerStatsManagerAccess) player).getPlayerStatsManager();

        bundle.startLevels.forEach((name, level) -> {
            Skill skill = resolveSkill(name);
            if (skill != null && level > 0) {
                stats.setSkillLevel(skill, Math.max(stats.getSkillLevel(skill), level));
            } else if (skill == null) {
                UtopiaCore.LOGGER.warn("Unbekannter Skill in Trait-Daten: {}", name);
            }
        });
        if (bundle.bonusPoints > 0) {
            stats.setSkillPoints(stats.getSkillPoints() + bundle.bonusPoints);
        }
        PlayerStatsServerPacket.writeS2CSkillPacket(stats, player);

        if (bundle.clearInventory) {
            player.getInventory().clear();
        }
        StringBuilder handedOut = new StringBuilder();
        for (CharacterTrait.LoadoutEntry entry : bundle.loadout) {
            String result = giveLoadout(player, entry);
            if (result != null) {
                handedOut.append(handedOut.length() == 0 ? "" : ", ").append(result);
            }
        }
        // Eine Zeile pro Charaktererstellung. Ohne sie ist "die Ausruestung
        // fehlt" nicht von "die Ausruestung kam nie an" zu unterscheiden.
        UtopiaCore.LOGGER.info("Startausruestung fuer {}: {}", player.getName().getString(),
                handedOut.length() == 0 ? "nichts" : handedOut);
    }

    /** @return was tatsaechlich ausgeteilt wurde, oder null wenn nichts. */
    private static String giveLoadout(ServerPlayerEntity player, CharacterTrait.LoadoutEntry entry) {
        net.minecraft.item.Item item = Registries.ITEM.getOrEmpty(entry.item().id()).orElse(null);
        if (item == null) {
            UtopiaCore.LOGGER.warn("Loadout-Item {} existiert nicht - uebersprungen", entry.item().id());
            return null;
        }
        ItemStack stack = new ItemStack(item, entry.item().count());
        entry.item().tag().ifPresent(tag -> stack.setNbt(tag.copy()));
        entry.enchantments().forEach((id, level) -> {
            net.minecraft.enchantment.Enchantment enchantment = Registries.ENCHANTMENT.get(Identifier.tryParse(id));
            if (enchantment != null && level > 0) {
                stack.addEnchantment(enchantment, level);
            } else {
                UtopiaCore.LOGGER.warn("Unbekannte Verzauberung im Loadout: {}", id);
            }
        });
        // equipStack statt direkt in inventory.armor zu schreiben: nur so wird
        // der Ausruestungswechsel mitgeschrieben, den Client und andere Mods
        // (Trinkets, Backslot, Rüstungs-Renderer) auswerten.
        String slot = entry.slot().toLowerCase(Locale.ROOT);
        EquipmentSlot equipment = switch (slot) {
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
        if (equipment != null) {
            ItemStack occupied = player.getEquippedStack(equipment);
            if (!occupied.isEmpty() && !player.getInventory().insertStack(occupied)) {
                player.dropItem(occupied, false);
            }
            player.equipStack(equipment, stack);
        } else if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
        return slot + "=" + entry.item().id() + (entry.item().count() > 1 ? " x" + entry.item().count() : "");
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
        String normalized = TraitBundle.normalize(name).toUpperCase(Locale.ROOT);
        for (Skill skill : Skill.values()) {
            if (skill.name().equals(normalized)) {
                return skill;
            }
        }
        return null;
    }

    /** Deterministische UUID pro Attribut+Operation, damit Modifier ersetzbar bleiben. */
    public static UUID modifierUuid(Identifier attribute, EntityAttributeModifier.Operation operation) {
        return UUID.nameUUIDFromBytes(("utopia:" + attribute + ":" + operation.getId()).getBytes(StandardCharsets.UTF_8));
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
