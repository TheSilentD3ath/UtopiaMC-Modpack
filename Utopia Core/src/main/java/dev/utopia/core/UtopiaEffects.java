package dev.utopia.core;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Eigene Dauer-Effekte. Bewusst leer in der Logik: alle Werte kommen ueber
 * Attribut-Modifier aus den Trait-JSONs, der Effekt ist nur Marker + HUD-Icon.
 * Damit gibt es keinen applyUpdateEffect-Tick und keine Kosten pro Spieler-Tick.
 */
public class UtopiaEffects {

    public static final StatusEffect FEMININE = new MarkerEffect(StatusEffectCategory.BENEFICIAL, 0xF7A8C0);
    public static final StatusEffect MASCULINE = new MarkerEffect(StatusEffectCategory.BENEFICIAL, 0x55CDFC);

    public static void register() {
        Registry.register(Registries.STATUS_EFFECT, UtopiaCore.id("feminine"), FEMININE);
        Registry.register(Registries.STATUS_EFFECT, UtopiaCore.id("masculine"), MASCULINE);
    }

    private static class MarkerEffect extends StatusEffect {
        protected MarkerEffect(StatusEffectCategory category, int color) {
            super(category, color);
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return false;
        }
    }
}
