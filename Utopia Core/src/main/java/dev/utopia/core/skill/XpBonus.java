package dev.utopia.core.skill;

import dev.utopia.core.character.CharacterAccess;
import net.levelz.stats.Skill;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Klassen- und Herkunftsbonus auf gewonnene Erfahrung.
 *
 * LevelZ kennt keine Erfahrung pro Fähigkeit - es gibt einen Topf fürs
 * Gesamtlevel, aus dem freie Punkte kommen. "Bergbau XP +30%" kann also nicht
 * heißen, dass ein Bergbau-Balken schneller füllt. Es heißt: **Erfahrung aus
 * bergmännischer Arbeit zählt für dich mehr.** Der Bergmann steigt beim Graben
 * schneller, der Händler beim Handeln.
 *
 * Angewendet wird an den Stellen, an denen LevelZ Erfahrung vergibt und die
 * Tätigkeit noch bekannt ist:
 *
 *   Erz abbauen   -> mining      Mob töten     -> strength / archery
 *   Schmelzen     -> smithing    Angeln        -> luck
 *   Tiere züchten -> farming     Handeln       -> trade
 *
 * Fähigkeiten ohne eigene Erfahrungsquelle (Alchemie, Technik) bekommen hier
 * nichts - dafür müsste es die Quelle erst geben.
 */
public final class XpBonus {

    private XpBonus() {
    }

    /**
     * Technik kommt aus einer JSON-Datei, es gibt also keine Java-Konstante
     * dafuer. Einmal nachschlagen und merken - der Aufruf liegt in Bau- und
     * Schmelzpfaden.
     */
    @Nullable
    private static Skill tech;
    private static boolean techLookedUp;

    @Nullable
    public static Skill tech() {
        if (!techLookedUp) {
            tech = Skill.find("TECH");
            techLookedUp = true;
        }
        return tech;
    }

    /**
     * Nimmt den hoechsten passenden Faktor, nicht das Produkt. Wer Schmied und
     * Techniker zugleich waere, soll seine Boni nicht multiplizieren.
     */
    public static int applyBest(@Nullable PlayerEntity player, int amount, Skill... sources) {
        if (player == null || amount <= 0) {
            return amount;
        }
        int best = amount;
        for (Skill source : sources) {
            best = Math.max(best, apply(player, source, amount));
        }
        return best;
    }

    public static int apply(@Nullable PlayerEntity player, @Nullable Skill source, int amount) {
        if (player == null || amount <= 0 || source == null) {
            return amount;
        }
        float factor = ((CharacterAccess) player).utopia$getCharacter().bundle().xpMultiplier(source.key());
        if (factor == 1.0F) {
            return amount;
        }
        return Math.max(1, Math.round(amount * factor));
    }

    /**
     * Nahkampf oder Fernkampf? Entschieden wird an der Waffe in der Hand -
     * die Todesursache liegt an dieser Stelle nicht mehr vor. Wer mit dem Bogen
     * schießt, hält ihn beim Tod des Ziels praktisch immer noch.
     */
    public static Skill killSource(@Nullable PlayerEntity player) {
        if (player != null && player.getMainHandStack().getItem() instanceof net.minecraft.item.RangedWeaponItem) {
            return Skill.ARCHERY;
        }
        return Skill.STRENGTH;
    }
}
