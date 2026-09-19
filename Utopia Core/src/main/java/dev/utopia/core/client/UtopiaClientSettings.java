package dev.utopia.core.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.utopia.core.UtopiaCore;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Einstellungen, die nur diesen Client betreffen.
 *
 * <p>Bewusst eine eigene kleine Datei und keine Konfigurationsbibliothek: Es geht um
 * Darstellungsgewohnheiten, die der Spieler im Fenster selbst umschaltet, nicht um etwas,
 * das in einen Einstellungsbildschirm gehoert. Fehlt oder bricht die Datei, gelten die
 * Voreinstellungen — eine kaputte Einstellungsdatei darf niemanden aus seinem Spiel werfen.
 */
public final class UtopiaClientSettings {

    private static final String FILE = "utopiacore-client.json";
    private static final String SIDEBAR_PINNED = "unlock_sidebar_pinned";

    /**
     * Ob die Baumliste dauerhaft ausgeklappt bleibt.
     *
     * <p>Voreinstellung angeheftet: Der Spieler muss sehen koennen, zwischen welchen
     * Baeumen er waehlen kann. Wer die Flaeche lieber der Karte gibt, heftet sie ab, dann
     * bleibt eine schmale Leiste, die beim Darueberfahren aufklappt.
     */
    private static boolean sidebarPinned = true;
    private static boolean loaded;

    private UtopiaClientSettings() {
    }

    public static boolean sidebarPinned() {
        load();
        return sidebarPinned;
    }

    public static void sidebarPinned(boolean value) {
        load();
        if (sidebarPinned == value) {
            return;
        }
        sidebarPinned = value;
        save();
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = path();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (json.has(SIDEBAR_PINNED)) {
                sidebarPinned = json.get(SIDEBAR_PINNED).getAsBoolean();
            }
        } catch (Exception exception) {
            // Auch ein JsonSyntaxException oder eine Zahl statt eines Wahrheitswerts landet
            // hier. Voreinstellungen behalten und weitermachen.
            UtopiaCore.LOGGER.warn("Client-Einstellungen nicht lesbar, es gelten die Voreinstellungen", exception);
        }
    }

    private static void save() {
        JsonObject json = new JsonObject();
        json.addProperty(SIDEBAR_PINNED, sidebarPinned);
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            Files.writeString(path, json.toString());
        } catch (IOException exception) {
            UtopiaCore.LOGGER.warn("Client-Einstellungen nicht schreibbar", exception);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE);
    }
}
