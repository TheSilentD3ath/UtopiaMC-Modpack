package dev.utopia.core.mixin;

import dev.utopia.core.UtopiaAttributes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Wendet fire_resistance und fall_resistance an. Ein einziger Eingriff am
 * Schadenseingang, kein Tick-Aufwand: der Zweig wird nur betreten, wenn der
 * Schaden ueberhaupt aus Feuer oder Fall kommt.
 */
@Mixin(LivingEntity.class)
public abstract class LivingDamageMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float utopia$reduceDamage(float amount, DamageSource source) {
        if (amount <= 0.0F) {
            return amount;
        }
        EntityAttribute attribute = null;
        if (source.isIn(DamageTypeTags.IS_FIRE)) {
            attribute = UtopiaAttributes.FIRE_RESISTANCE;
        } else if (source.isIn(DamageTypeTags.IS_FALL)) {
            attribute = UtopiaAttributes.FALL_RESISTANCE;
        }
        if (attribute == null) {
            return amount;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        EntityAttributeInstance instance = self.getAttributeInstance(attribute);
        if (instance == null) {
            return amount;
        }
        double reduction = instance.getValue();
        return reduction <= 0.0D ? amount : (float) (amount * (1.0D - Math.min(reduction, 1.0D)));
    }
}
