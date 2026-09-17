package dev.utopia.core.character;

import dev.utopia.core.UtopiaCore;
import net.levelz.access.PlayerStatsManagerAccess;
import net.levelz.network.PlayerStatsServerPacket;
import net.levelz.stats.PlayerStatsManager;
import net.levelz.stats.Skill;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Applies one-time skill values and equipment after character creation. */
final class CharacterStarter {

    private static final Identifier COLD_SPAWN_COMFORT = new Identifier("environmentz", "comfort");
    private static final int COLD_SPAWN_COMFORT_TICKS = 20 * 60 * 8;
    private static final List<CharacterTrait.LoadoutEntry> COLD_SPAWN_ARMOR = List.of(
            inventoryItem("environmentz", "wanderer_helmet"),
            inventoryItem("environmentz", "wanderer_chestplate"),
            inventoryItem("environmentz", "wanderer_leggings"),
            inventoryItem("environmentz", "wanderer_boots"));

    private CharacterStarter() {
    }

    static void apply(ServerPlayerEntity player, TraitBundle bundle) {
        applySkills(player, bundle);
        applyLoadout(player, bundle);
        applyColdSpawnProtection(player);
    }

    static Skill resolveSkill(String name) {
        String normalized = TraitBundle.normalize(name).toUpperCase(Locale.ROOT);
        for (Skill skill : Skill.values()) {
            if (skill.name().equals(normalized)) {
                return skill;
            }
        }
        return null;
    }

    private static void applySkills(ServerPlayerEntity player, TraitBundle bundle) {
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
    }

    private static void applyLoadout(ServerPlayerEntity player, TraitBundle bundle) {
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
        UtopiaCore.LOGGER.info("Startausruestung fuer {}: {}", player.getName().getString(),
                handedOut.length() == 0 ? "nichts" : handedOut);
    }

    private static void applyColdSpawnProtection(ServerPlayerEntity player) {
        BlockPos position = player.getBlockPos();
        if (!player.getWorld().getBiome(position).value().isCold(position)) {
            return;
        }

        StringBuilder handedOut = new StringBuilder();
        for (CharacterTrait.LoadoutEntry entry : COLD_SPAWN_ARMOR) {
            String result = giveLoadout(player, entry);
            if (result != null) {
                handedOut.append(handedOut.length() == 0 ? "" : ", ").append(result);
            }
        }

        StatusEffect comfort = Registries.STATUS_EFFECT.getOrEmpty(COLD_SPAWN_COMFORT).orElse(null);
        boolean comfortApplied = comfort != null;
        if (comfortApplied) {
            player.addStatusEffect(new StatusEffectInstance(
                    comfort, COLD_SPAWN_COMFORT_TICKS, 0, false, false, true));
        }
        UtopiaCore.LOGGER.info("Kaeltestart fuer {}: Kleidung={}, Komforteffekt={}",
                player.getName().getString(),
                handedOut.length() == 0 ? "nicht verfuegbar" : handedOut,
                comfortApplied ? "aktiv" : "nicht verfuegbar");
    }

    private static CharacterTrait.LoadoutEntry inventoryItem(String namespace, String path) {
        CharacterTrait.ItemSpec item = new CharacterTrait.ItemSpec(
                new Identifier(namespace, path), 1, Optional.empty());
        return new CharacterTrait.LoadoutEntry(item, "inventory", Map.of());
    }

    private static String giveLoadout(ServerPlayerEntity player, CharacterTrait.LoadoutEntry entry) {
        Item item = Registries.ITEM.getOrEmpty(entry.item().id()).orElse(null);
        if (item == null) {
            UtopiaCore.LOGGER.warn("Loadout-Item {} existiert nicht - uebersprungen", entry.item().id());
            return null;
        }
        ItemStack stack = new ItemStack(item, entry.item().count());
        entry.item().tag().ifPresent(tag -> stack.setNbt(tag.copy()));
        entry.enchantments().forEach((id, level) -> {
            Identifier enchantmentId = Identifier.tryParse(id);
            Enchantment enchantment = enchantmentId == null ? null : Registries.ENCHANTMENT.get(enchantmentId);
            if (enchantment != null && level > 0) {
                stack.addEnchantment(enchantment, level);
            } else {
                UtopiaCore.LOGGER.warn("Unbekannte Verzauberung im Loadout: {}", id);
            }
        });

        String slot = entry.slot().toLowerCase(Locale.ROOT);
        EquipmentSlot equipment = equipmentSlot(slot);
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

    private static EquipmentSlot equipmentSlot(String slot) {
        return switch (slot) {
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }
}
