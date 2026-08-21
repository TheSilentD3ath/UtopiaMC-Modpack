package net.shiro.createhp.mixin;

import com.simibubi.create.content.fluids.FluidNetwork;

import org.spongepowered.asm.mixin.Mixin;

/**
 * ====================================================================================
 *  OPTIONAL / DISABLED — not listed in createhp.mixins.json. Most users won't need it.
 * ====================================================================================
 *
 * The real, working boost is {@link FluidPropagatorMixin} (pump reach). This class is only a
 * starting point if you want to also mess with raw throughput.
 *
 * Reality check on Create 6's fluid model: pipes are PRESSURE-based, and a primed network already
 * transfers as fast as the pump/source allows — the pipes are not the bottleneck. There is no
 * "fluidPipeFlowRate" config in Create 6 (the fluid config only exposes mechanicalPumpRange,
 * fluidTankCapacity, etc.). So "make the pipe push more mB/tick" usually means "spin the pump
 * faster" (more RPM), not a pipe stat. That's why this is optional and off by default.
 *
 * If you still want to experiment, {@code FluidNetwork#tick()V} exists in 6.0.8.1 and is where a
 * network advances its flows each tick. To hook it meaningfully you'd:
 *   1. run `./gradlew genSources`, open FluidNetwork#tick, and find the FluidStack amount being
 *      moved (look at PipeConnection#manageFlows / provideOutboundFlow);
 *   2. add a @ModifyVariable / @Redirect that scales that amount by CHPConfig.FLOW_RATE_MULTIPLIER;
 *   3. ideally gate it on the network actually containing a high pressure pipe;
 *   4. add "FluidNetworkMixin" to createhp.mixins.json.
 *
 * Left intentionally empty so it compiles and applies nothing even if someone lists it by mistake.
 */
@Mixin(value = FluidNetwork.class, remap = false)
public abstract class FluidNetworkMixin {
	// intentionally empty — see class javadoc
}
