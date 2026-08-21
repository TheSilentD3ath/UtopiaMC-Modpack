<p align="center">
  <img src="logo_512.png" alt="Create: Industrial Pressure" width="260">
</p>

# Create: Industrial Pressure

A Create **Fabric 1.20.1** addon that adds a **High Pressure Pipe** — a reinforced fluid pipe meant to push more fluid, further, with a bigger internal buffer than vanilla copper pipes.

This repository is a **working scaffold**: it compiles, registers a craftable pipe that joins Create's fluid network, and ships a documented (opt-in) mixin for the actual throughput boost. Read "How the boost works" before expecting pipes to be faster than copper.

---

## Target / dependency versions

| Thing | Version |
|---|---|
| Minecraft | 1.20.1 |
| Loader | Fabric Loader 0.16.9 |
| Fabric API | 0.92.2+1.20.1 |
| Create (Fabric) | runs on **6.0.8.1**; compiled against 6.0.7.0 (see note) |
| Mappings | Mojmap + Parchment 2023.09.03 |
| Loom | 1.8.+ |

All of Create's own dependencies (Registrate, Porting Lib, Flywheel, Forge Config API Port) are pulled in transitively by the single `create-fabric` dependency.

## Building

```bash
# from the project root
gradle wrapper           # one-time: materialises the Gradle 8.10.2 wrapper jar
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # launch a dev client with Create + this mod
./gradlew genSources     # decompile Create — needed to finish the boost mixin (see below)
```

Drop `build/libs/create-high-pressure-pipes-*.jar` into a Fabric 1.20.1 server/client that already has Create Fabric.

## What works right now

- A **High Pressure Pipe** block + item, registered through Create's Registrate.
- It **reuses Create's `FluidPipeBlockEntity`**, so it attaches a `FluidTransportBehaviour` and is treated as a genuine part of any Create fluid network — it connects to pumps, copper pipes, tanks, basins, etc., and transports fluid.
- **It looks like a real Create pipe.** The blockstate + the 60 part models are Create **6.0.8.1**'s own fluid-pipe geometry, copied and re-namespaced, so it renders as a proper connecting pipe (straight runs, elbows, T-junctions) — not a cube. The skin is a recolored steel-teal "high pressure" variant of Create's pipe texture, so it reads as distinct from copper.
- **Longer reach (boost is ON):** `FluidPropagatorMixin` multiplies pump push/pull range ×4 by default (16 → 64 blocks). See the boost section for the important caveat that this is currently global.
- Crafting recipe (6 pipes from copper sheets + fluid pipes), block drop, and lang.

### Version note (compile vs run)

You run Create **6.0.8.1**. The dev maven (`mvn.devos.one`) lags Modrinth and currently tops out at **6.0.7.0**, so the project compiles against 6.0.7.0 and `fabric.mod.json` depends on `create >=6.0.0`. The Create 6.0.x API is stable across patch builds (verified directly against the 6.0.8.1 jar — every class/method this mod uses is identical), so the jar builds against 6.0.7.0 and loads/runs on 6.0.8.1.

## How the "high pressure" boost works (important — and corrected for Create 6)

I checked Create 6.0.8.1's actual code, and the mental model is different from older Create:

- **There is no pipe flow-rate config in Create 6.** The fluid config only exposes `mechanicalPumpRange`, `fluidTankCapacity`, etc. — *not* a pipe throughput value.
- **Pipes are pressure-based and are not the bottleneck.** A primed pipe network already moves fluid as fast as the pump/source allows. Making a pipe "flow more mB/tick" in vanilla Create means **spinning the pump faster (more RPM)**, not a pipe stat.
- **The one pipe-side limit that actually bites is pump REACH** — how many blocks a pump can push/pull. That's `FluidPropagator.getPumpRange()` (reads `mechanicalPumpRange`, default 16).

So the boost that's actually meaningful — and that's **wired up and ON by default** — is extending pump reach:

`mixin/FluidPropagatorMixin.java` injects on `FluidPropagator.getPumpRange()` and multiplies it by `CHPConfig.RANGE_MULTIPLIER` (default ×4 → 16 becomes 64 blocks). The method was verified to exist as `public static int` in 6.0.8.1. The inject is `@At("RETURN")` with `require = 0`, so if a future Create build renames it, the mod harmlessly falls back to vanilla range instead of crashing.

**Caveat — it's currently global.** Because `getPumpRange()` is a static utility with no per-network context, this lengthens reach for *every* pump, not only networks that use high pressure pipes. Set `RANGE_MULTIPLIER = 1.0` to disable it. Making it strictly per-high-pressure-pipe means hooking network construction (`FluidPropagator.propagateChangedPipe`) — a worthwhile follow-up, not done yet.

`mixin/FluidNetworkMixin.java` is an **optional, disabled** starting point if you want to also scale raw throughput (rarely needed — see above). It's empty and not listed in `createhp.mixins.json`.

## Known TODOs / polish

- **Per-pipe range gating**: the pump-reach boost works but is global (all pumps). Gating it to networks that contain a high pressure pipe needs a hook into `FluidPropagator.propagateChangedPipe`.
- **Window / glass variant**: like copper pipes, this pipe is opaque and doesn't show fluid moving inside. A see-through windowed variant (showing the fluid) would need the glass-pipe model + a BE renderer — not done.
- **Creative tab**: the item is reachable via creative *search* and crafting, but isn't pinned to a tab. Assign it to a Create or custom `CreativeModeTab` if you want it grouped.
- **Real config file**: `CHPConfig` is plain constants. Wire it to Forge Config API Port (already on the classpath) for in-game/file configuration.

## Layout

```
build.gradle, settings.gradle, gradle.properties   # build + pinned versions
gradle/wrapper/gradle-wrapper.properties           # Gradle 8.10.2 (run `gradle wrapper` to add the jar)
src/main/java/net/shiro/createhp/
  CreateHP.java                                     # entrypoint + Registrate
  content/HighPressurePipeBlock.java                # extends Create's FluidPipeBlock
  registry/CHPBlocks.java                           # block + item
  registry/CHPBlockEntities.java                    # BE type reusing FluidPipeBlockEntity
  config/CHPConfig.java                             # boost multipliers
  mixin/FluidPropagatorMixin.java                   # pump-reach boost (ACTIVE)
  mixin/FluidNetworkMixin.java                      # optional throughput hook (OFF, empty)
src/main/resources/
  fabric.mod.json, createhp.mixins.json
  assets/createhp/...                               # blockstate, models, lang, texture, icon
  data/createhp/...                                 # recipe, block loot table
```

License: MIT.
