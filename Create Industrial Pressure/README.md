# Create: Industrial Pressure

Fabric addon for Create 6 that adds pressure-rated pipe materials, pressure valves, pressure tanks,
steam carried in pipes, a large steam turbine and five mechanical pump tiers.

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
- shut-off valves, pressure regulators and relief valves in copper and netherite; an Electron
  Tube makes them smart (redstone control, or a redstone signal while venting)
- pressure tanks in copper (32 buckets per block, 256 bar) and netherite (128 buckets, 1024 bar),
  built on Create's tank: multiblocks, windows, boiler; Create's own tanks now hold 16 bar (server
  config switch `createTanksLimited`)
- steam as a fluid: a Steam Outlet on a boiler turns its spare levels into steam (10 mB per tick per
  level) for pumps to carry; pressure tank boilers may be 5 × 5 wide and reach 50 (copper) or 150
  (netherite) levels
- a steam turbine multiblock in copper and netherite: 5 × 5 casing around a 3 × 3 rotor, 1 to 16
  rotor layers, 1.5 or 2.5 times a steam engine's output per 10 mB of steam per tick at 64 RPM, half
  the steam back as water through condensate outlets; its steam inlets burst or explode above their
  limit
- pipe pressure on Engineer's Goggles and as a Display Link source, for the mod's pipes, valves,
  tanks and pumps and for Create's pipes, valves, tanks and pump; a tank fill-level source
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
| Create fluid tank (unless `createTanksLimited = false`) | 16 bar |
| High Pressure Tank / Netherite Pressure Tank | 256 / 1024 bar |
| Steam Inlet / Netherite Steam Inlet | 256 / 1024 bar |

Valves hold the limit of their material (256 or 1024 bar):

| Valve | Does | Smart (right-click with Electron Tube) |
|---|---|---|
| High Pressure Valve / Netherite Pressure Valve | closes fully, fluid and pressure; opens by rotation | opens while powered |
| High Pressure Regulator / Netherite Pressure Regulator | caps the pressure behind it at the set value, which lowers the flow | redstone 0-15 sets the value |
| High Pressure Relief Valve / Netherite Relief Valve | keeps its pipe run at or below the set value and vents the surplus, which is lost | emits redstone while venting |

The set value uses Create's value panel on the valve's gauge side. How the valves shape Create's
pressure and flow is described in `PRESSURE_SYSTEM.md`.

Each limit is the maximum of the matching pump at full speed, so a network built from matching
parts never bursts. Overload comes from mixing (a strong pump on weaker pipes) or from several
pumps feeding the same pipe, whose pressures add up. Only high pressure pumps check their network,
so a network of Create pumps behaves exactly as in Create. The full design, including valves, tanks,
steam and the turbine, is in `PRESSURE_SYSTEM.md`.

The numeric tier values live in `PumpTier`, the pipe limits in `PipeMaterial` and `Pressure`.
Explosion, check interval and time to burst are in the server config `config/createhp-server.toml`;
edit it while the server is stopped, because the server writes its loaded values back on shutdown.

## Architecture

```text
CreateHP
  registry/       block, block-entity and creative-tab registration
  content/        pressure pipes, pump tiers and the pump block entity
  content/pressure/  pressure in bar, limits, goggles and the Display Link source
  content/valve/     the three valves and how they shape the pumps' pressure and flow
  content/tank/      pressure tanks, tank pressure and the fill-level Display Link source
  content/steam/     steam fluid, steam outlet and the boiler rules of pressure tanks
  content/turbine/   the steam turbine: parts, shape check, steam use and rotation
  client/         render layers, Create-compatible attachment models and renderers
  config/         server config
  mixin/          narrow integration points for range, throughput, wrench, valves, flow, tanks and boilers
tools/            generators for the valve, tank, steam and turbine textures, models and blockstates
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
