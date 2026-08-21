package dev.utopia.core.mixin;

import dev.utopia.core.skill.XpBonus;
import net.levelz.entity.LevelExperienceOrbEntity;
import net.levelz.stats.Skill;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Erfahrung fuers Brauen - zaehlt als Alchemie.
 *
 * LevelZ vergab dafuer bisher nichts, weshalb der Alchemie-Bonus des Magiers
 * ins Leere lief.
 *
 * Der Braustand weiss nicht, wer ihn bestueckt hat. Den Faktor bekommt deshalb
 * der naechststehende Spieler - derselbe, zu dem die Erfahrungskugel ohnehin
 * fliegt. Damit stimmen Bonus und Empfaenger immer ueberein.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingXpMixin {

    private static final int BREW_XP = 5;
    private static final double REACH = 8.0D;

    @Inject(method = "craft", at = @At("TAIL"))
    private static void utopia$brewExperience(World world, BlockPos pos, net.minecraft.util.collection.DefaultedList<net.minecraft.item.ItemStack> slots,
            CallbackInfo info) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        PlayerEntity player = world.getClosestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, REACH, false);
        LevelExperienceOrbEntity.spawn(serverWorld, Vec3d.ofCenter(pos).add(0.0D, 0.5D, 0.0D),
                XpBonus.apply(player, Skill.ALCHEMY, BREW_XP));
    }
}
