package dev.utopia.core.character;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Applies the persistent, derived parts of a trait bundle to a player. */
final class CharacterStateApplier {

    private static final String MODIFIER_NAME = "utopia:character";

    private CharacterStateApplier() {
    }

    static void applyAttributes(ServerPlayerEntity player, CharacterData data, TraitBundle bundle) {
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
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    static void applyEffects(ServerPlayerEntity player, CharacterData data, TraitBundle bundle) {
        Map<Identifier, CharacterTrait.EffectSpec> active = new LinkedHashMap<>();
        bundle.effects.forEach((id, spec) -> {
            if (!spec.toggleable() || !data.disabledAbilities().contains(id)) {
                active.put(id, spec);
            }
        });

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
            if (current != null && current.getAmplifier() == spec.amplifier()
                    && current.getDuration() > 20 * 60 * 60) {
                return;
            }
            player.addStatusEffect(new StatusEffectInstance(effect, CharacterService.PERMANENT, spec.amplifier(), true,
                    spec.showParticles(), spec.showIcon()));
        });
    }

    static void syncGrantedUnlocks(CharacterData data, TraitBundle bundle) {
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

    static UUID modifierUuid(Identifier attribute, EntityAttributeModifier.Operation operation) {
        return UUID.nameUUIDFromBytes(
                ("utopia:" + attribute + ":" + operation.getId()).getBytes(StandardCharsets.UTF_8));
    }

    private static String attributeKey(Identifier attribute, EntityAttributeModifier.Operation operation) {
        return attribute + "|" + operation.getId();
    }

    private static void removeAttribute(ServerPlayerEntity player, String key) {
        int split = key.lastIndexOf('|');
        if (split < 1 || split == key.length() - 1) {
            return;
        }
        Identifier id = Identifier.tryParse(key.substring(0, split));
        if (id == null) {
            return;
        }
        EntityAttributeModifier.Operation operation;
        try {
            operation = EntityAttributeModifier.Operation.fromId(Integer.parseInt(key.substring(split + 1)));
        } catch (IllegalArgumentException exception) {
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
}
