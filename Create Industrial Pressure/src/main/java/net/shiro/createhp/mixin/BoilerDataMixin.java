package net.shiro.createhp.mixin;

import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.createmod.catnip.data.Iterate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.steam.BoilerRules;
import net.shiro.createhp.content.steam.SteamOutletBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Groessere Kessel und Dampfauslaesse.
 *
 * - Drucktanks setzen eigene Grenzen (BoilerRules): mehr Stufen je Block, hoehere Hoechststufe, mehr Hitze je
 *   Brenner bei Netherit. Ohne gesetzte Regeln bleibt alles wie in Create.
 * - Ein Dampfauslass am Tank macht ihn zum Kessel wie eine Dampfmaschine (isActive). Create zaehlt nur seine
 *   eigenen Maschinen und Pfeifen; die Auslaesse zaehlt dieser Mixin dazu.
 * - Creates Balkenanzeige zeichnet einen Strich je Stufe; bei 150 Stufen waere die Brille breiter als der
 *   Bildschirm. Ueber 18 Stufen steht deshalb ein Strich fuer mehrere Stufen.
 */
@Mixin(value = BoilerData.class, remap = false)
public abstract class BoilerDataMixin implements BoilerRules {

	/** Creates Wasserbedarf je Stufe: 10 mB pro Tick, in Tropfen. */
	@Unique
	private static final int createhp$WATER_PER_LEVEL = 10 * 81;
	@Unique
	private static final int createhp$CREATE_CAP = 18;

	@Shadow
	public double waterSupply;
	@Shadow
	private int minValue;
	@Shadow
	private int maxValue;

	@Unique
	private int createhp$levelCap = createhp$CREATE_CAP;
	@Unique
	private int createhp$levelsPerBlock;
	@Unique
	private float createhp$heatFactor = 1f;
	@Unique
	private int createhp$outlets;

	@Override
	public void createhp$setRules(int levelCap, int levelsPerBlock, float heatFactor) {
		createhp$levelCap = levelCap;
		createhp$levelsPerBlock = levelsPerBlock;
		createhp$heatFactor = heatFactor;
	}

	@Override
	public int createhp$getLevelCap() {
		return createhp$levelCap;
	}

	@Override
	public int createhp$getOutlets() {
		return createhp$outlets;
	}

	@Inject(method = "getMaxHeatLevelForBoilerSize", at = @At("HEAD"), cancellable = true)
	private void createhp$sizeLimit(int boilerSize, CallbackInfoReturnable<Integer> cir) {
		if (createhp$levelsPerBlock > 0)
			cir.setReturnValue(Math.min(createhp$levelCap, boilerSize * createhp$levelsPerBlock));
	}

	@Inject(method = "getMaxHeatLevelForWaterSupply", at = @At("HEAD"), cancellable = true)
	private void createhp$waterLimit(CallbackInfoReturnable<Integer> cir) {
		if (createhp$levelCap != createhp$CREATE_CAP)
			cir.setReturnValue(Math.min(createhp$levelCap, Mth.ceil(waterSupply) / createhp$WATER_PER_LEVEL));
	}

	// Netherit: mehr Hitze je Brenner. Nur echte Hitze (> 0); "passiv" (0) und "keine" (-1) bleiben, wie sie sind.
	@Redirect(method = "updateTemperature", at = @At(value = "INVOKE", remap = true,
			target = "Lcom/simibubi/create/api/boiler/BoilerHeater;findHeat(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)F"))
	private float createhp$heat(Level level, BlockPos pos, BlockState state) {
		float heat = BoilerHeater.findHeat(level, pos, state);
		return heat > 0 ? heat * createhp$heatFactor : heat;
	}

	@Inject(method = "evaluate", at = @At("RETURN"), cancellable = true)
	private void createhp$countOutlets(FluidTankBlockEntity controller, CallbackInfoReturnable<Boolean> cir) {
		Level level = controller.getLevel();
		if (level == null)
			return;
		BlockPos origin = controller.getBlockPos();
		int outlets = 0;
		for (int y = 0; y < controller.getHeight(); y++)
			for (int x = 0; x < controller.getWidth(); x++)
				for (int z = 0; z < controller.getWidth(); z++) {
					BlockPos pos = origin.offset(x, y, z);
					if (!FluidTankBlock.isTank(level.getBlockState(pos)))
						continue;
					for (Direction d : Iterate.directions) {
						BlockState attached = level.getBlockState(pos.relative(d));
						if (attached.getBlock() instanceof SteamOutletBlock && SteamOutletBlock.getFacing(attached) == d)
							outlets++;
					}
				}
		if (outlets != createhp$outlets) {
			createhp$outlets = outlets;
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
	private void createhp$activeWithOutlets(CallbackInfoReturnable<Boolean> cir) {
		if (createhp$outlets > 0)
			cir.setReturnValue(true);
	}

	@Inject(method = "clear", at = @At("RETURN"))
	private void createhp$clear(CallbackInfo ci) {
		createhp$outlets = 0;
	}

	@Inject(method = "write", at = @At("RETURN"))
	private void createhp$write(CallbackInfoReturnable<CompoundTag> cir) {
		cir.getReturnValue().putInt("CreatehpOutlets", createhp$outlets);
	}

	@Inject(method = "read", at = @At("HEAD"))
	private void createhp$read(CompoundTag nbt, int boilerSize, CallbackInfo ci) {
		createhp$outlets = nbt.getInt("CreatehpOutlets");
	}

	@Inject(method = "barComponent", at = @At("HEAD"), cancellable = true)
	private void createhp$scaledBars(int level, CallbackInfoReturnable<MutableComponent> cir) {
		if (createhp$levelCap <= createhp$CREATE_CAP)
			return;
		// Gleiche Farben wie bei Create, aber auf hoechstens 18 Striche verkleinert.
		int scale = Mth.ceil(createhp$levelCap / (float) createhp$CREATE_CAP);
		int min = Mth.ceil(minValue / (float) scale);
		int lvl = Mth.ceil(level / (float) scale);
		int max = Mth.ceil(maxValue / (float) scale);
		cir.setReturnValue(Component.empty()
				.append(createhp$bars(Math.max(0, min - 1), ChatFormatting.DARK_GREEN))
				.append(createhp$bars(min > 0 ? 1 : 0, ChatFormatting.GREEN))
				.append(createhp$bars(Math.max(0, lvl - min), ChatFormatting.DARK_GREEN))
				.append(createhp$bars(Math.max(0, max - lvl), ChatFormatting.DARK_RED))
				.append(createhp$bars(Math.max(0, createhp$CREATE_CAP - Math.max(max, lvl)), ChatFormatting.DARK_GRAY)));
	}

	@Unique
	private static MutableComponent createhp$bars(int count, ChatFormatting format) {
		return Component.literal("|".repeat(Math.max(0, count))).withStyle(format);
	}
}
