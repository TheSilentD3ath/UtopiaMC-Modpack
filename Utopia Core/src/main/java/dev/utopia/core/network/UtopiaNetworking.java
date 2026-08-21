package dev.utopia.core.network;

import dev.utopia.core.UtopiaCore;
import dev.utopia.core.character.CharacterAccess;
import dev.utopia.core.character.CharacterData;
import dev.utopia.core.character.CharacterService;
import dev.utopia.core.character.CharacterTrait;
import dev.utopia.core.character.CharacterTraits;
import dev.utopia.core.character.TraitType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Netzwerkschicht. Bewusst wenige, grobe Pakete:
 * - Trait-Definitionen einmal beim Join / bei /reload
 * - Charakterzustand nur bei Aenderung
 * - eine einzige Auswahl-Nachricht vom Client (nicht drei)
 */
public final class UtopiaNetworking {

    public static final Identifier OPEN_CREATION = UtopiaCore.id("open_creation");
    public static final Identifier SYNC_TRAITS = UtopiaCore.id("sync_traits");
    public static final Identifier SYNC_CHARACTER = UtopiaCore.id("sync_character");
    public static final Identifier SELECT = UtopiaCore.id("select");
    public static final Identifier TOGGLE_ABILITIES = UtopiaCore.id("toggle_abilities");
    public static final Identifier SYNC_TREES = UtopiaCore.id("sync_trees");
    public static final Identifier BUY_NODE = UtopiaCore.id("buy_node");

    private UtopiaNetworking() {
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(SELECT, (server, player, handler, buf, sender) -> {
            Identifier origin = buf.readIdentifier();
            Identifier gender = buf.readIdentifier();
            Identifier clazz = buf.readIdentifier();
            server.execute(() -> {
                CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
                if (data.isComplete()) {
                    return; // Umwahl laeuft ausschliesslich ueber /utopia character
                }
                if (!CharacterService.isValidSelection(origin, gender, clazz)) {
                    UtopiaCore.LOGGER.warn("Ungueltige Charakterwahl von {}: {}/{}/{}",
                            player.getName().getString(), origin, gender, clazz);
                    openCreation(player);
                    return;
                }
                CharacterService.completeSelection(player, data, origin, gender, clazz);
            });
        });

        registerAbilityToggle();
        registerNodePurchase();
    }

    private static void registerAbilityToggle() {
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_ABILITIES, (server, player, handler, buf, sender) -> server.execute(() -> {
            CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
            if (!data.isComplete()) {
                return;
            }
            java.util.List<net.minecraft.entity.effect.StatusEffect> affected = CharacterService.toggleAbilities(player, data);
            if (affected.isEmpty()) {
                player.sendMessage(net.minecraft.text.Text.translatable("message.utopia.ability.none")
                        .formatted(net.minecraft.util.Formatting.GRAY), true);
                return;
            }
            net.minecraft.text.MutableText names = net.minecraft.text.Text.empty();
            for (int i = 0; i < affected.size(); i++) {
                if (i > 0) {
                    names.append(", ");
                }
                names.append(affected.get(i).getName());
            }
            boolean on = CharacterService.abilitiesEnabled(data);
            player.sendMessage(net.minecraft.text.Text
                    .translatable(on ? "message.utopia.ability.on" : "message.utopia.ability.off", names)
                    .formatted(on ? net.minecraft.util.Formatting.GREEN : net.minecraft.util.Formatting.GRAY), true);
        }));
    }

    /** Die Baeume gehen einmal pro Join raus, wie die Traits. */
    public static void syncTrees(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        java.util.List<Identifier> order = dev.utopia.core.unlock.UnlockTrees.ordered();
        buf.writeVarInt(order.size());
        for (Identifier id : order) {
            buf.writeIdentifier(id);
            buf.encode(net.minecraft.nbt.NbtOps.INSTANCE, dev.utopia.core.unlock.UnlockTree.CODEC,
                    dev.utopia.core.unlock.UnlockTrees.tree(id));
        }
        ServerPlayNetworking.send(player, SYNC_TREES, buf);
    }

    private static void registerNodePurchase() {
        ServerPlayNetworking.registerGlobalReceiver(BUY_NODE, (server, player, handler, buf, sender) -> {
            String nodeId = buf.readString(128);
            server.execute(() -> {
                CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
                int overallLevel = ((net.levelz.access.PlayerStatsManagerAccess) player).getPlayerStatsManager().getOverallLevel();
                String problem = dev.utopia.core.unlock.UnlockService.whyNotBuyable(player, data, nodeId, overallLevel);
                if (problem != null) {
                    player.sendMessage(net.minecraft.text.Text.translatable(problem)
                            .formatted(net.minecraft.util.Formatting.RED), true);
                    return;
                }
                dev.utopia.core.unlock.UnlockService.buy(player, data, nodeId, overallLevel);
                CharacterService.applyAll(player, data, true);
                // Die Sperrlisten des Spielers neu rechnen und mitschicken.
                net.levelz.network.PlayerStatsServerPacket.writeS2CSkillPacket(
                        ((net.levelz.access.PlayerStatsManagerAccess) player).getPlayerStatsManager(), player);
                syncCharacter(player, data);
                player.sendMessage(net.minecraft.text.Text.translatable("message.utopia.unlock.bought", nodeId)
                        .formatted(net.minecraft.util.Formatting.GREEN), true);
            });
        });
    }

    public static void syncTraits(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        for (TraitType type : TraitType.values()) {
            Map<Identifier, CharacterTrait> traits = CharacterTraits.map(type);
            List<Identifier> ordered = CharacterTraits.ordered(type);
            buf.writeVarInt(ordered.size());
            for (Identifier id : ordered) {
                buf.writeIdentifier(id);
                buf.encode(net.minecraft.nbt.NbtOps.INSTANCE, CharacterTrait.CODEC, traits.get(id));
            }
        }
        ServerPlayNetworking.send(player, SYNC_TRAITS, buf);
    }

    /**
     * Nur das Oeffnen-Signal. Die Trait-Definitionen gehen einmal beim Join raus
     * ({@link #syncTraits}), nicht bei jedem Wiederholungsversuch.
     */
    public static void openCreation(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, OPEN_CREATION, PacketByteBufs.create());
    }

    public static void syncCharacter(ServerPlayerEntity player, CharacterData data) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(data.isComplete());
        buf.writeVarInt(data.unlockPoints());
        writeNullable(buf, data.origin());
        writeNullable(buf, data.gender());
        writeNullable(buf, data.clazz());
        buf.writeVarInt(data.unlocks().size());
        for (Identifier unlock : data.unlocks()) {
            buf.writeIdentifier(unlock);
        }
        ServerPlayNetworking.send(player, SYNC_CHARACTER, buf);
    }

    private static void writeNullable(PacketByteBuf buf, Identifier id) {
        buf.writeBoolean(id != null);
        if (id != null) {
            buf.writeIdentifier(id);
        }
    }
}
