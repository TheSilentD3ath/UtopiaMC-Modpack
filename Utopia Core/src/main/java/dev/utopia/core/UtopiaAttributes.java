package dev.utopia.core;

import net.minecraft.entity.attribute.ClampedEntityAttribute;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Eigene Attribute fuer Wirkungen, fuer die es in Vanilla (und in den Mods des
 * Packs) kein Gegenstueck gibt. Bewusst wenige und generisch gehalten, damit
 * Origins und Klassen sie ueber ihre JSONs frei nutzen koennen.
 *
 * Angewendet werden sie in:
 *   fire_resistance / fall_resistance -> dev.utopia.core.mixin.LivingDamageMixin
 *   regeneration_speed               -> net.levelz.mixin.player.HungerManagerMixin
 */
public class UtopiaAttributes {

    /** Anteil, um den Feuerschaden reduziert wird. 0.15 = 15% weniger. */
    public static final EntityAttribute FIRE_RESISTANCE = new ClampedEntityAttribute(
            "attribute.name.utopiacore.fire_resistance", 0.0D, 0.0D, 1.0D).setTracked(true);

    /** Anteil, um den Fallschaden reduziert wird. */
    public static final EntityAttribute FALL_RESISTANCE = new ClampedEntityAttribute(
            "attribute.name.utopiacore.fall_resistance", 0.0D, 0.0D, 1.0D).setTracked(true);

    /** Faktor auf die natuerliche Regeneration. 1.1 = 10% schneller. */
    public static final EntityAttribute REGENERATION_SPEED = new ClampedEntityAttribute(
            "attribute.name.utopiacore.regeneration_speed", 1.0D, 0.0D, 8.0D).setTracked(true);

    /** Faktor auf den Luftvorrat. 2.0 = doppelt so lange tauchen. */
    public static final EntityAttribute BREATH_CAPACITY = new ClampedEntityAttribute(
            "attribute.name.utopiacore.breath_capacity", 1.0D, 0.1D, 16.0D).setTracked(true);

    public static void register() {
        Registry.register(Registries.ATTRIBUTE, UtopiaCore.id("fire_resistance"), FIRE_RESISTANCE);
        Registry.register(Registries.ATTRIBUTE, UtopiaCore.id("fall_resistance"), FALL_RESISTANCE);
        Registry.register(Registries.ATTRIBUTE, UtopiaCore.id("regeneration_speed"), REGENERATION_SPEED);
        Registry.register(Registries.ATTRIBUTE, UtopiaCore.id("breath_capacity"), BREATH_CAPACITY);
    }
}
