package dev.utopia.core.mixin;

import dev.utopia.core.UtopiaAttributes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Streckt den Luftvorrat um {@code utopiacore:breath_capacity}.
 *
 * Bewusst der Luftvorrat und keine Schwimmgeschwindigkeit: Bewegung wird vom
 * Client vorhergesagt und vom Server nachgerechnet. Wer daran dreht, bekommt
 * Ruckeln und Zurückgezogen-Werden. Der Luftvorrat ist eine schlichte Zahl, die
 * niemand vorhersagt - das kann nicht auseinanderlaufen.
 */
@Mixin(Entity.class)
public abstract class EntityBreathMixin {

    @Inject(method = "getMaxAir", at = @At("RETURN"), cancellable = true)
    private void utopia$extendBreath(CallbackInfoReturnable<Integer> info) {
        // Zwei Fallstricke an dieser Stelle:
        //
        // 1. getMaxAir sitzt an Entity, das Attribut gibt es aber nur am
        //    Spieler. Alles andere wird sofort wieder verlassen - die Methode
        //    laeuft fuer jede Kreatur in jedem Tick.
        // 2. Entity ruft im Konstruktor setAir(getMaxAir()) auf. Da ist der
        //    Attribut-Behaelter von LivingEntity noch nicht angelegt und
        //    getAttributes() liefert null. Ohne die Pruefung kracht es beim
        //    Erzeugen jedes Spielers.
        if (!((Object) this instanceof PlayerEntity player) || player.getAttributes() == null) {
            return;
        }
        EntityAttributeInstance instance = player.getAttributeInstance(UtopiaAttributes.BREATH_CAPACITY);
        if (instance == null) {
            return;
        }
        double factor = instance.getValue();
        if (factor != 1.0D) {
            info.setReturnValue((int) Math.max(1, info.getReturnValueI() * factor));
        }
    }
}
