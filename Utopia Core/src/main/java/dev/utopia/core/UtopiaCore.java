package dev.utopia.core;

import dev.utopia.core.character.CharacterAccess;
import dev.utopia.core.character.CharacterData;
import dev.utopia.core.character.CharacterService;
import dev.utopia.core.character.CharacterTraits;
import dev.utopia.core.command.UtopiaCommand;
import dev.utopia.core.criteria.CharacterCriterion;
import net.minecraft.advancement.criterion.Criteria;
import dev.utopia.core.integration.EstrogenBridge;
import dev.utopia.core.network.UtopiaNetworking;
import dev.utopia.core.skill.SkillLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utopia Core - RPG-Kern des Utopia-Modpacks.
 *
 * Fork von LevelZ (Globox_Z), GPL-3.0. Der geerbte LevelZ-Code liegt weiterhin
 * unter {@code net.levelz} und wird schrittweise in dieses Paket migriert.
 * Alles Neue (Origins, Gender, Klassen, Unlocks) lebt unter {@code dev.utopia.core}.
 */
public class UtopiaCore implements ModInitializer {

    public static final String MOD_ID = "utopiacore";
    public static final Logger LOGGER = LoggerFactory.getLogger("UtopiaCore");

    /** Advancement-Ausloeser fuer Quest- und Datapack-Anbindung. */
    public static final CharacterCriterion CHARACTER_CRITERION = Criteria.register(new CharacterCriterion());

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    /** Namespace fuer Datapack-Inhalte und Uebersetzungsschluessel. */
    public static Identifier data(String path) {
        return new Identifier("utopia", path);
    }

    @Override
    public void onInitialize() {
        // Zuerst: die Faehigkeiten-Registry steht, bevor irgendetwas sie liest.
        SkillLoader.load();

        UtopiaAttributes.register();
        UtopiaEffects.register();
        UtopiaNetworking.registerServerReceivers();
        UtopiaCommand.register();
        EstrogenBridge.init();
        dev.utopia.core.unlock.UnlockEnforcement.register();

        // Datapack-Registries: eine Instanz pro Trait-Typ, alle server-autoritativ.
        for (CharacterTraits loader : CharacterTraits.all()) {
            ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(loader);
        }
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new dev.utopia.core.unlock.UnlockTrees());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
            // Volle Neuberechnung genau einmal pro Join, danach nur noch ereignisgesteuert.
            CharacterService.applyAll(player, data, true);
            UtopiaNetworking.syncTraits(player);
            UtopiaNetworking.syncTrees(player);
            UtopiaNetworking.syncCharacter(player, data);
            if (!data.isComplete()) {
                CharacterService.setSelectionLock(player, true);
                UtopiaNetworking.openCreation(player);
            } else {
                // Auch bestehende Charaktere sollen ihre Advancements bekommen.
                CHARACTER_CRITERION.trigger(player, data);
            }
        });

        // Einziger Tick-Hook des Frameworks: haelt Spieler ohne Charakter fest
        // und erneuert permanente Effekte. Laeuft nur ueber Spieler, nicht ueber Entities.
        ServerTickEvents.END_SERVER_TICK.register(CharacterService::serverTick);

        LOGGER.info("Utopia Core initialisiert");
    }
}
