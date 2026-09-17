package dev.utopia.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.utopia.core.UtopiaCore;
import dev.utopia.core.character.CharacterAccess;
import dev.utopia.core.character.CharacterData;
import dev.utopia.core.character.CharacterService;
import dev.utopia.core.network.UtopiaNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.levelz.access.PlayerStatsManagerAccess;
import net.levelz.network.PlayerStatsServerPacket;
import net.levelz.stats.PlayerStatsManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * /utopia character get [spieler]
 * /utopia character set <spieler> <origin> <gender> <klasse>     (OP)
 * /utopia character reset [spieler]                              (OP)
 * /utopia points add <spieler> <anzahl>                          (OP)
 * /utopia handbuch                                               (alle)
 * /utopia handbook                                               (alle)
 *
 * Die Rechte haengen bewusst an den Unterbefehlen, nicht an der Wurzel: haengt
 * das Recht an "/utopia", blendet Brigadier den ganzen Befehl fuer normale
 * Spieler aus - auch die harmlose Abfrage. In einer Einzelspielerwelt ohne
 * Cheats hat selbst der Host nur Rechtestufe 0.
 */
public final class UtopiaCommand {

    private static final int OP_LEVEL = 2;

    private UtopiaCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("utopia");

            // Oeffentliche, optionale Bruecke zum getrennt ausgelieferten
            // Handbuch-Datapack. Keine Lavender-Klassen referenzieren: Fehlt
            // der Mod, muss Utopia Core weiterhin normal laden koennen.
            root.then(CommandManager.literal("handbuch")
                    .executes(context -> requestHandbook(context, "handbuch", true)));
            root.then(CommandManager.literal("handbook")
                    .executes(context -> requestHandbook(context, "handbook", false)));

            root.then(CommandManager.literal("character")
                    // get: fuer jeden, aber ohne Argument nur ueber sich selbst
                    .then(CommandManager.literal("get")
                            .executes(context -> get(context, context.getSource().getPlayerOrThrow()))
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .requires(source -> source.hasPermissionLevel(OP_LEVEL))
                                    .executes(context -> get(context, EntityArgumentType.getPlayer(context, "target")))))

                    .then(CommandManager.literal("set")
                            .requires(source -> source.hasPermissionLevel(OP_LEVEL))
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .then(CommandManager.argument("origin", IdentifierArgumentType.identifier())
                                            .then(CommandManager.argument("gender", IdentifierArgumentType.identifier())
                                                    .then(CommandManager.argument("class", IdentifierArgumentType.identifier())
                                                            .executes(UtopiaCommand::set))))))

                    .then(CommandManager.literal("reset")
                            .requires(source -> source.hasPermissionLevel(OP_LEVEL))
                            .executes(context -> reset(context, context.getSource().getPlayerOrThrow()))
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .executes(context -> reset(context, EntityArgumentType.getPlayer(context, "target"))))));

            // Freischalt-Baeume: kaufen darf jeder fuer sich, Punkte verteilen nur OP.
            root.then(CommandManager.literal("unlock")
                    .then(CommandManager.literal("list")
                            .executes(context -> listNodes(context)))
                    .then(CommandManager.literal("buy")
                            .then(CommandManager.argument("node", com.mojang.brigadier.arguments.StringArgumentType.string())
                                    .executes(context -> buyNode(context))))
                    .then(CommandManager.literal("points")
                            .requires(source -> source.hasPermissionLevel(OP_LEVEL))
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .then(CommandManager.argument("amount", IntegerArgumentType.integer())
                                            .executes(context -> addUnlockPoints(context))))));

            root.then(CommandManager.literal("points")
                    .requires(source -> source.hasPermissionLevel(OP_LEVEL))
                    .then(CommandManager.literal("add")
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                            .executes(UtopiaCommand::addPoints)))));

            dispatcher.register(root);
        });
    }

    private static int requestHandbook(CommandContext<ServerCommandSource> context, String objectiveName,
            boolean german) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (!FabricLoader.getInstance().isModLoaded("lavender")) {
            source.sendError(Text.literal(german
                    ? "Lavender ist nicht installiert; das Utopia-Handbuch ist deshalb nicht verfügbar."
                    : "Lavender is not installed, so the Utopia handbook is unavailable."));
            return 0;
        }

        Scoreboard scoreboard = source.getServer().getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective(objectiveName);
        Identifier giveFunction = UtopiaCore.data("handbook/give_" + (german ? "de" : "en"));
        boolean datapackLoaded = source.getServer().getCommandFunctionManager().getFunction(giveFunction).isPresent();
        if (!datapackLoaded || objective == null || objective.getCriterion() != ScoreboardCriterion.TRIGGER) {
            source.sendError(Text.literal(german
                    ? "Das Utopia-Handbuch-Datapack ist nicht installiert oder nicht geladen."
                    : "The Utopia handbook datapack is not installed or not loaded."));
            return 0;
        }

        // Entspricht /trigger <sprache> set 1, umgeht aber nur dessen
        // kurzlebigen Enable-Zustand. Das Datapack verarbeitet die Anfrage im
        // naechsten Tick und bleibt allein fuer das Buch-Item verantwortlich.
        scoreboard.getPlayerScore(player.getEntityName(), objective).setScore(1);
        return 1;
    }

    private static int get(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) {
        CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
        String summary = player.getName().getString() + ": " + data.origin() + " / " + data.gender() + " / "
                + data.clazz() + (data.grantedTraits().isEmpty() ? "" : " + " + data.grantedTraits());
        context.getSource().sendFeedback(() -> Text.literal(summary), false);
        return 1;
    }

    private static int set(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "target");
        Identifier origin = IdentifierArgumentType.getIdentifier(context, "origin");
        Identifier gender = IdentifierArgumentType.getIdentifier(context, "gender");
        Identifier clazz = IdentifierArgumentType.getIdentifier(context, "class");
        if (!CharacterService.isValidSelection(origin, gender, clazz)) {
            context.getSource().sendError(Text.literal("Ungueltige Kombination"));
            return 0;
        }
        CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
        // Ohne Startausruestung - beim Ausprobieren soll sich nichts anhaeufen.
        CharacterService.completeSelection(player, data, origin, gender, clazz, false);
        context.getSource().sendFeedback(
                () -> Text.literal(player.getName().getString() + " ist jetzt " + origin + " / " + gender + " / " + clazz),
                true);
        return 1;
    }

    private static int reset(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) {
        CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
        data.reset();
        CharacterService.applyAll(player, data, true);
        CharacterService.setSelectionLock(player, true);
        UtopiaNetworking.syncTraits(player);
        UtopiaNetworking.syncCharacter(player, data);
        UtopiaNetworking.openCreation(player);
        context.getSource().sendFeedback(
                () -> Text.literal(player.getName().getString() + " waehlt neu"), true);
        return 1;
    }

    private static int listNodes(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
        context.getSource().sendFeedback(() -> Text.literal("Freischaltpunkte: " + data.unlockPoints()), false);
        for (Identifier treeId : dev.utopia.core.unlock.UnlockTrees.ordered()) {
            dev.utopia.core.unlock.UnlockTree tree = dev.utopia.core.unlock.UnlockTrees.tree(treeId);
            StringBuilder line = new StringBuilder(treeId.getPath() + ": ");
            tree.nodes().forEach((key, node) -> {
                String nodeId = treeId.getPath() + "/" + key;
                boolean owned = dev.utopia.core.unlock.UnlockService.owns(data, nodeId);
                line.append(owned ? "[x] " : "[ ] ").append(key)
                        .append("(").append(dev.utopia.core.unlock.UnlockService.cost(data, nodeId, node)).append(") ");
            });
            context.getSource().sendFeedback(() -> Text.literal(line.toString()), false);
        }
        return 1;
    }

    private static int buyNode(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String nodeId = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "node");
        CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
        int level = ((PlayerStatsManagerAccess) player).getPlayerStatsManager().getOverallLevel();
        String problem = dev.utopia.core.unlock.UnlockService.whyNotBuyable(data, nodeId, level);
        if (problem != null) {
            context.getSource().sendError(Text.translatable(problem));
            return 0;
        }
        dev.utopia.core.unlock.UnlockService.buy(player, data, nodeId, level);
        CharacterService.applyAll(player, data, true);
        PlayerStatsServerPacket.writeS2CSkillPacket(((PlayerStatsManagerAccess) player).getPlayerStatsManager(), player);
        UtopiaNetworking.syncCharacter(player, data);
        context.getSource().sendFeedback(() -> Text.translatable("message.utopia.unlock.bought", nodeId), false);
        return 1;
    }

    private static int addUnlockPoints(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "target");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        CharacterData data = ((CharacterAccess) player).utopia$getCharacter();
        data.addUnlockPoints(amount);
        UtopiaNetworking.syncCharacter(player, data);
        context.getSource().sendFeedback(
                () -> Text.literal(player.getName().getString() + " hat jetzt " + data.unlockPoints() + " Freischaltpunkte"), true);
        return 1;
    }

    private static int addPoints(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "target");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        PlayerStatsManager stats = ((PlayerStatsManagerAccess) player).getPlayerStatsManager();
        stats.setSkillPoints(stats.getSkillPoints() + amount);
        PlayerStatsServerPacket.writeS2CSkillPacket(stats, player);
        context.getSource().sendFeedback(
                () -> Text.literal("+" + amount + " Skillpunkte fuer " + player.getName().getString()), true);
        return 1;
    }
}
