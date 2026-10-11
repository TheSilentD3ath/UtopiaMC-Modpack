# Create: Industrial Pressure

Fabric addon for Create 6 that adds pressure-rated pipe materials and five mechanical pump tiers.

## Target versions

| Component | Version |
|---|---|
| Minecraft | 1.20.1 |
| Java | 17 |
| Fabric Loader | 0.17.2 (minimum required by Create 6.0.8.1) |
| Fabric API | 0.92.6+1.20.1 (minimum required by Create 6.0.8.1) |
| Create Fabric | 6.0.8.1 build 1744 |
| Fabric Loom | 1.8.13 |
| Gradle | 8.10.2 |

## Build

The repository contains a complete, checksum-pinned Gradle wrapper. No system Gradle installation
or local dependency folder is required.

```powershell
./gradlew.bat clean build --no-daemon
```

The release JAR is written to
`build/libs/create-high-pressure-pipes-0.1.0+1.20.1.jar`.

Useful development tasks:

```powershell
./gradlew.bat runClient
./gradlew.bat genSources
```

## Features

- copper and netherite pressure pipes, each with a wrench-created glass variant
- five pump tiers with speed-scaled reach from 64 to 1024 blocks
- pressure multipliers from 4× to 64×, coupled to matching Stress Unit costs
- contextual range and throughput boosts that leave vanilla pumps unchanged
- pressure ratings: every pipe holds a limit in bar; above it, it cracks and bursts, and an
  overloaded netherite pipe explodes (block damage can be switched off in the server config)
- pipe pressure on Engineer's Goggles and as a Display Link source, for the mod's pipes and pumps
  and for Create's pipes, valves and pump
- Create-style attachment models, transparent fluid rendering and a dedicated creative tab

Glass variants follow Create's own lifecycle: wrench a solid pressure pipe to expose its window,
and wrench the window again to restore the matching solid material. They deliberately have no
standalone inventory item and drop/require their matching solid pressure pipe.

Pressure is Create's own pipe pressure, shown in bar: 16 Create pressure units are 1 bar, so
Create's pump at 256 RPM gives 16 bar and the pump tiers at 256 RPM give 64 to 1024 bar. Limits:

| Pipe | Limit |
|---|---|
| Create fluid, glass, encased and smart pipe, fluid valve | 16 bar |
| high pressure pipe and its glass variant | 256 bar |
| netherite pressure pipe and its glass variant | 1024 bar |

Each limit is the maximum of the matching pump at full speed, so a network built from matching
parts never bursts. Overload comes from mixing (a strong pump on weaker pipes) or from several
pumps feeding the same pipe, whose pressures add up. Only high pressure pumps check their network,
so a network of Create pumps behaves exactly as in Create. The full plan, including valves, tanks
and steam, is in `PRESSURE_SYSTEM.md`.

The numeric tier values live in `PumpTier`, the pipe limits in `PipeMaterial` and `Pressure`.
Explosion, check interval and time to burst are in the server config `config/createhp-server.toml`;
edit it while the server is stopped, because the server writes its loaded values back on shutdown.

## Architecture

```text
CreateHP
  registry/       block, block-entity and creative-tab registration
  content/        pressure pipes, pump tiers and the pump block entity
  client/         render layers and Create-compatible attachment models
  mixin/          narrow integration points for range, throughput and wrench behavior
  config/         static gameplay tuning values
```

`PressurePipeBlock` and `PressureGlassPipeBlock` are material-neutral implementations configured by
the registry. This keeps copper and netherite variants on one behavior path while preserving their
existing registry IDs, models and block-entity types.

The pump block entity owns the pressure-crack lifecycle because crack progress must live and tick
with a specific pump. Short-lived `ThreadLocal` contexts only bridge Create methods whose signatures
do not expose the active custom pump.

## Known risks

- no automated or GameTest coverage exists yet; a manual client and server smoke test against Create
  6.0.8.1 was run on 2026-09-25, results and open defects in `../docs/PROJECT_STATUS.md`
- mixins use `require = 0` where Create patch-version drift should degrade gracefully; runtime smoke
  tests are still required after dependency upgrades
- the Create dependency graph currently emits duplicate Flywheel-class warnings during Loom's first
  remap, although the clean build succeeds
- the custom attachment model uses an API deprecated by the current dependency set

License: All rights reserved, source available. Playing it and including the unmodified
mod in modpacks is allowed; redistributing it or publishing modified versions is not. See
[LICENSE](LICENSE). Versions up to commit `0285f9b` were released under MIT and stay MIT.
