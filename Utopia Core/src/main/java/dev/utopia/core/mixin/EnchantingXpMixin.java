package dev.utopia.core.mixin;

import dev.utopia.core.skill.XpBonus;
import net.levelz.entity.LevelExperienceOrbEntity;
import net.levelz.stats.Skill;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Erfahrung fuers Verzaubern - zaehlt als Alchemie.
 *
 * Passt zu LevelZ, wo Alchemie ohnehin die Verzauberungschance beeinflusst.
 * Der Betrag richtet sich nach der gewaehlten Reihe: die teure Verzauberung
 * bringt mehr zurueck als die billige.
 */
@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantingXpMixin {

    private static final int XP_PER_TIER = 4;

    @Inject(method = "onButtonClick", at = @At("RETURN"))
    private void utopia$enchantExperience(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> info) {
        if (!info.getReturnValueZ() || player.getWorld().isClient()) {
            return;
        }
        int amount = XpBonus.apply(player, Skill.ALCHEMY, XP_PER_TIER * (id + 1));
        LevelExperienceOrbEntity.spawn((ServerWorld) player.getWorld(), player.getPos().add(0.0D, 0.5D, 0.0D), amount);
    }
}
