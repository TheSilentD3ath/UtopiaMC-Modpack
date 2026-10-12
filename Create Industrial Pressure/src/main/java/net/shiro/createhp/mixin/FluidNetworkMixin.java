package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.FluidNetwork;

import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;

import net.createmod.catnip.math.BlockFace;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.shiro.createhp.content.valve.ValveFlow;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Regler und Ueberdruckventile im Durchsatz eines Fluessigkeitsnetzes (siehe {@link ValveFlow}).
 *
 * Create liest den Durchsatz in tick() genau einmal aus dem Feld transferSpeed ({@code long flowSpeed =
 * transferSpeed}); dieses Lesen kappen wir. Das Ablassen haengt sich an den Aufruf von
 * keepPortableFluidInterfaceEngaged(), der erst kommt, wenn die Quelle feststeht.
 */
@Mixin(value = FluidNetwork.class, remap = false)
public abstract class FluidNetworkMixin {

	@Shadow
	Level world;
	@Shadow
	Set<BlockPos> visited;
	@Shadow
	Storage<FluidVariant> source;
	@Shadow
	FluidStack fluid;
	@Shadow
	int transferSpeed;
	@Shadow
	BlockFace start;

	@Unique
	private long createhp$nextCheck = Long.MIN_VALUE;
	@Unique
	private ValveFlow.Effect createhp$effect = ValveFlow.Effect.NONE;

	/** Die Ventilsuche laeuft ueber alle Rohre des Netzes; einmal pro Sekunde reicht. */
	@Unique
	private ValveFlow.Effect createhp$effect() {
		long now = world.getGameTime();
		if (now >= createhp$nextCheck) {
			createhp$nextCheck = now + 20;
			createhp$effect = ValveFlow.evaluate(world, start.getPos(), visited);
		}
		return createhp$effect;
	}

	@Redirect(method = "tick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lcom/simibubi/create/content/fluids/FluidNetwork;transferSpeed:I"))
	private int createhp$capTransfer(FluidNetwork network) {
		return (int) Math.min(transferSpeed, createhp$effect().cap());
	}

	@Inject(method = "tick", at = @At(value = "INVOKE",
			target = "Lcom/simibubi/create/content/fluids/FluidNetwork;keepPortableFluidInterfaceEngaged()V"))
	private void createhp$vent(CallbackInfo ci) {
		ValveFlow.vent(world, source, fluid, createhp$effect());
	}
}
