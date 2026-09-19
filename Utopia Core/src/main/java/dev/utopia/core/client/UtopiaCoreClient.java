package dev.utopia.core.client;

import dev.utopia.core.character.CharacterTrait;
import dev.utopia.core.character.CharacterTraits;
import dev.utopia.core.character.TraitType;
import dev.utopia.core.network.UtopiaNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UtopiaCoreClient implements ClientModInitializer {

    /** Gespiegelter Charakterzustand fuer HUD und Screens. */
    public static Identifier origin;
    public static Identifier gender;
    public static Identifier clazz;
    public static boolean complete;
    /** Offene Freischaltpunkte, gespiegelt fuer die Oberflaeche. */
    public static int unlockPoints;
    public static final java.util.Set<Identifier> unlocks = new java.util.HashSet<>();
    /** Nur der Server entscheidet, ob der lokale Spieler Baeume bearbeiten darf. */
    public static boolean canEditTrees;

    /**
     * Ob dieser Tastendruck den Bearbeitungsmodus oeffnen soll.
     *
     * <p>Nicht ueber {@code wasPressed} abgefragt: Solange ein Fenster offen ist, laufen
     * Tastendruecke dorthin und nicht in den Client-Tick. Das Baumfenster fragt deshalb
     * selbst nach.
     */
    public static boolean isEditorKey(int keyCode, int scanCode) {
        return editorKey != null && editorKey.matchesKey(keyCode, scanCode);
    }

    /** Der Server will die Auswahl sehen - geoeffnet wird erst, wenn die Welt steht. */
    private static boolean pendingOpen;
    private static int openDelay;
    private static final int SPAWN_DELAY = 20;

    /** Standardmaessig Y - im Pack ist praktisch jeder andere Buchstabe belegt. */
    private static net.minecraft.client.option.KeyBinding abilityKey;
    /** Standardmaessig U - oeffnet die Freischalt-Baeume. */
    private static net.minecraft.client.option.KeyBinding treeKey;
    /**
     * Standardmaessig F8 - schaltet im Baumfenster den Bearbeitungsmodus frei.
     *
     * <p>Bewusst kein Knopf im Fenster: Wer spielt, veraendert die Baeume nicht. Der
     * Bearbeitungsmodus richtet sich an den Packautor und an alle, die sich aus dem Mod
     * etwas eigenes bauen, und die finden die Taste in den Steuerungseinstellungen.
     *
     * <p>Eine Funktionstaste, weil die Taste im Baumfenster ausgewertet wird, wo das
     * Suchfeld jeden Buchstaben fuer sich beansprucht. Umbelegen laesst sie sich trotzdem
     * frei; liegt sie dann auf einem Buchstaben, greift sie nur ausserhalb des Suchfelds.
     */
    private static net.minecraft.client.option.KeyBinding editorKey;

    @Override
    public void onInitializeClient() {
        UnlockTooltip.register();
        abilityKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
                new net.minecraft.client.option.KeyBinding("key.utopia.toggle_abilities",
                        org.lwjgl.glfw.GLFW.GLFW_KEY_Y, "key.categories.utopia"));

        treeKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
                new net.minecraft.client.option.KeyBinding("key.utopia.open_trees",
                        org.lwjgl.glfw.GLFW.GLFW_KEY_U, "key.categories.utopia"));

        editorKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
                new net.minecraft.client.option.KeyBinding("key.utopia.edit_trees",
                        org.lwjgl.glfw.GLFW.GLFW_KEY_F8, "key.categories.utopia"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (treeKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new UnlockScreen());
                }
            }
            while (abilityKey.wasPressed()) {
                if (client.player != null) {
                    ClientPlayNetworking.send(UtopiaNetworking.TOGGLE_ABILITIES,
                            net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(UtopiaNetworking.SYNC_TRAITS, (client, handler, buf, sender) -> {
            Map<TraitType, Map<Identifier, CharacterTrait>> parsed = new LinkedHashMap<>();
            Map<TraitType, List<Identifier>> ordered = new LinkedHashMap<>();
            for (TraitType type : TraitType.values()) {
                int size = buf.readVarInt();
                Map<Identifier, CharacterTrait> traits = new LinkedHashMap<>();
                List<Identifier> order = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    Identifier id = buf.readIdentifier();
                    traits.put(id, buf.decode(net.minecraft.nbt.NbtOps.INSTANCE, CharacterTrait.CODEC));
                    order.add(id);
                }
                parsed.put(type, traits);
                ordered.put(type, order);
            }
            client.execute(() -> parsed.forEach((type, traits) -> CharacterTraits.acceptSynced(type, traits, ordered.get(type))));
        });

        ClientPlayNetworking.registerGlobalReceiver(UtopiaNetworking.SYNC_CHARACTER, (client, handler, buf, sender) -> {
            boolean isComplete = buf.readBoolean();
            int points = buf.readVarInt();
            Identifier newOrigin = buf.readBoolean() ? buf.readIdentifier() : null;
            Identifier newGender = buf.readBoolean() ? buf.readIdentifier() : null;
            Identifier newClass = buf.readBoolean() ? buf.readIdentifier() : null;
            int unlockCount = buf.readVarInt();
            List<Identifier> newUnlocks = new ArrayList<>(unlockCount);
            for (int i = 0; i < unlockCount; i++) {
                newUnlocks.add(buf.readIdentifier());
            }
            client.execute(() -> {
                complete = isComplete;
                unlockPoints = points;
                origin = newOrigin;
                gender = newGender;
                clazz = newClass;
                unlocks.clear();
                unlocks.addAll(newUnlocks);
                // Denselben Zustand in die Spielerdaten schreiben und das Buendel
                // bauen: dadurch rechnet der Freischalt-Screen Kosten und
                // Levelanforderungen mit demselben Code wie der Server.
                if (client.player != null) {
                    dev.utopia.core.character.CharacterData data =
                            ((dev.utopia.core.character.CharacterAccess) client.player).utopia$getCharacter();
                    data.select(newOrigin, newGender, newClass);
                    data.setUnlockPoints(points);
                    data.unlocks().clear();
                    data.unlocks().addAll(newUnlocks);
                    data.setBundle(dev.utopia.core.character.TraitBundle.build(data));
                }
                if (complete) {
                    pendingOpen = false;
                    if (client.currentScreen instanceof CharacterCreationScreen) {
                        client.setScreen(null);
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(UtopiaNetworking.SYNC_TREES, (client, handler, buf, sender) -> {
            boolean canEdit = buf.readBoolean();
            int size = buf.readVarInt();
            java.util.Map<Identifier, dev.utopia.core.unlock.UnlockTree> trees = new LinkedHashMap<>();
            List<Identifier> order = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                Identifier id = buf.readIdentifier();
                trees.put(id, buf.decode(net.minecraft.nbt.NbtOps.INSTANCE, dev.utopia.core.unlock.UnlockTree.CODEC));
                order.add(id);
            }
            client.execute(() -> {
                canEditTrees = canEdit;
                dev.utopia.core.unlock.UnlockTrees.acceptSynced(trees, order);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(UtopiaNetworking.OPEN_CREATION,
                (client, handler, buf, sender) -> client.execute(() -> pendingOpen = true));

        // Der Screen wird erst geoeffnet, wenn die Welt wirklich da ist. Sonst
        // erscheint er ueber dem Ladebildschirm und wird davon wieder verdraengt.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!pendingOpen) {
                return;
            }
            if (client.player == null || client.world == null) {
                return;
            }
            if (client.currentScreen instanceof CharacterCreationScreen) {
                pendingOpen = false;
                return;
            }
            // currentScreen != null bedeutet hier: Ladebildschirm (DownloadingTerrain/Progress).
            if (client.currentScreen != null) {
                openDelay = SPAWN_DELAY;
                return;
            }
            if (openDelay > 0) {
                openDelay--;
                return;
            }
            pendingOpen = false;
            client.setScreen(new CharacterCreationScreen());
        });
    }

    public static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }
}
