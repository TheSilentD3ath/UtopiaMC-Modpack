package dev.utopia.core.skill;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.utopia.core.UtopiaCore;
import net.fabricmc.loader.api.FabricLoader;
import net.levelz.network.PlayerStatsServerPacket;
import net.levelz.stats.Skill;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Lädt zusätzliche Fähigkeiten aus {@code data/utopia/skills/*.json} im Jar.
 *
 * Bewusst NICHT über das Datapack-System:
 *
 * - Reihenfolge und Index einer Fähigkeit müssen auf Server und Client gleich
 *   sein, sonst landen Level beim Synchronisieren in der falschen Fähigkeit.
 *   Aus dem Jar geladen ist das automatisch der Fall - gleiche Jar-Datei,
 *   gleiche Liste, kein Sync-Paket nötig.
 * - Ein Datapack darf jederzeit neu geladen werden. Eine Fähigkeit, die
 *   mittendrin dazukommt, verschiebt alle folgenden Indizes. Das wäre eine
 *   Fehlerquelle für einen Gewinn, den hier niemand braucht.
 *
 * Eine neue Fähigkeit ist also eine JSON-Datei im Mod, kein Datapack-Eintrag.
 *
 * <pre>
 * { "name": "TECH", "nbt": "TechLevel", "order": 100, "icon_index": 12 }
 * </pre>
 */
public final class SkillLoader {

    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "data/utopia/skills";

    private SkillLoader() {
    }

    public static void load() {
        Path root = FabricLoader.getInstance().getModContainer(UtopiaCore.MOD_ID)
                .flatMap(container -> container.findPath(DIRECTORY))
                .orElse(null);
        if (root == null || !Files.isDirectory(root)) {
            UtopiaCore.LOGGER.info("Keine zusätzlichen Fähigkeiten gefunden");
            Skill.freeze();
            return;
        }

        List<JsonObject> definitions = new ArrayList<>();
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    definitions.add(GSON.fromJson(reader, JsonObject.class));
                } catch (Exception exception) {
                    UtopiaCore.LOGGER.error("Fähigkeit {} konnte nicht gelesen werden", file.getFileName(), exception);
                }
            }
        } catch (Exception exception) {
            UtopiaCore.LOGGER.error("Fähigkeiten konnten nicht geladen werden", exception);
        }

        // Feste Reihenfolge, damit der Index nicht von der Dateisystem-Sortierung abhängt.
        definitions.sort(Comparator
                .comparingInt((JsonObject json) -> json.has("order") ? json.get("order").getAsInt() : 0)
                .thenComparing(json -> json.get("name").getAsString()));

        int added = 0;
        for (JsonObject json : definitions) {
            String name = json.get("name").getAsString();
            String nbt = json.has("nbt") ? json.get("nbt").getAsString() : defaultNbt(name);
            Skill.Builder builder = Skill.create(name, nbt);
            if (json.has("icon_index")) {
                builder.icon(json.get("icon_index").getAsInt());
            }
            // Eine neue Fähigkeit kann in den Datapack-Sperrlisten auf Blöcke,
            // Braumittel oder Schmiedegut zeigen. Welche davon, weiß hier
            // niemand - also nach einem Aufstieg alle drei neu berechnen. Das
            // passiert pro Klick auf den Levelknopf, nicht pro Tick.
            builder.onLevelChanged(stats -> {
                PlayerStatsServerPacket.syncLockedBlockList(stats);
                PlayerStatsServerPacket.syncLockedBrewingItemList(stats);
                PlayerStatsServerPacket.syncLockedSmithingItemList(stats);
            });
            builder.register();
            added++;
        }

        Skill.freeze();
        UtopiaCore.LOGGER.info("{} Fähigkeiten insgesamt ({} zusätzlich)", Skill.count(), added);
    }

    /** TECH -> TechLevel, passend zum Schema der geerbten Fähigkeiten. */
    private static String defaultNbt(String name) {
        String lower = name.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1) + "Level";
    }
}
