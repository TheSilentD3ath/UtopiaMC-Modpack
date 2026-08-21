package dev.utopia.core.mixin;

import dev.utopia.core.UtopiaAttributes;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Haengt die Utopia-Attribute an den Spieler. Ohne diesen Schritt liefert
 * getAttributeInstance(...) null und die Modifier verpuffen wirkungslos.
 */
@Mixin(PlayerEntity.class)
public class PlayerAttributesMixin {

    @Inject(method = "createPlayerAttributes", at = @At("RETURN"))
    private static void utopia$addAttributes(CallbackInfoReturnable<DefaultAttributeContainer.Builder> info) {
        info.getReturnValue()
                .add(UtopiaAttributes.FIRE_RESISTANCE)
                .add(UtopiaAttributes.FALL_RESISTANCE)
                .add(UtopiaAttributes.REGENERATION_SPEED)
                .add(UtopiaAttributes.BREATH_CAPACITY);
    }
}
