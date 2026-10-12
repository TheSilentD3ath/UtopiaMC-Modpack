# Create: Industrial Pressure

Create addon: high-pressure pipes, tiered pumps, larger fluid networks and a Netherite
tier. Details and known risks: `README.md`. Store page text: `MODRINTH.md`.

- Licence: all rights reserved, source available (`LICENSE`). Material based on Create
  is listed there under THIRD-PARTY MATERIAL with Create's MIT notice. A new texture
  derived from Create must be added to that list.
- Pipe textures have to follow Create's texture layout, because Create's pipe models
  expect it. Keep the layout; draw your own pixels.
- Builds against Create Fabric 6.0.8.1 (`gradle.properties`); the fabric.mod.json asks
  for Create >= 6.0.0. The Utopia Core dev client runs an older Create and is no test
  bed for this mod.
- Smoke-tested on 2026-09-25 against Create 6.0.8.1: dev client, dedicated server and a
  production client in multiplayer. Results and the open defects that still need a
  decision are in `docs/PROJECT_STATUS.md`.
- `de_de.json` follows Create's own German terms (Rohr, Glasrohr, Netherit-, Erweitert).
  Keep both lang files on the same keys.
- Pressure system: decisions, units, limits and the phases (valves, pressure tanks, steam,
  turbine, control) are in `PRESSURE_SYSTEM.md`. All five phases are built. Pressure is
  Create's own pipe pressure read in bar (16 units = 1 bar); do not add a second pressure model. Valves
  change it only while a pump distributes (`PressureShaping`) and cap the flow of Create's
  fluid networks (`FluidNetworkMixin`, `ValveFlow`).
- Valve and tank textures, models and blockstates come from `tools/valve_assets.py` and
  `tools/tank_assets.py` (they read Create's assets from the Create JAR; need ImageMagick).
  Steam, steam outlet, turbine and gauge assets come from `tools/steam_assets.py`,
  `tools/turbine_assets.py` and `tools/gauge_assets.py` (own pixels, nothing from Create).
  Regenerate instead of editing the generated files by hand; they reproduce the committed files
  byte for byte.
- Pressure tanks are Create's fluid tank with a per-material block entity type; their capacity
  comes from `TankCapacityMixin`, because Create reads it from a static method.
- The steam turbine is checked from its output block (`TurbineStructure`); the output pulls steam
  from the inlets' buffers and pushes water into the condensate outlets' buffers each tick, so no
  part holds another's storage. Steam inlets join the pumps' overload check like tanks
  (`HighPressurePumpBlockEntity.addOverloadedTanks`).
- Turbine walls use connected textures: `client/TurbineCTBehaviour` joins casing, inlet, condensate
  outlet and output of the same material, `client/CHPTurbineModels` holds the sheets. Register them
  with a lambda in `CHPBlocks` (a method reference would load the client class on the server).
- Goggle lines need a new `LangBuilder` each (`PressureTooltip.lang()`); a shared one repeats every
  earlier line.
- The client config `config/createhp-client.toml` only holds `pressureWarningSeen`; set it to false
  to see the one-time warning screen again.
- The server config is `config/createhp-server.toml` (global on Fabric). `createTanksLimited`
  decides whether Create's own tanks burst above 16 bar. Edit it with the
  server stopped; on shutdown the server writes its loaded values back.

## Testing

- `./gradlew runClient` works as is (Loader 0.17.2). Add `-Pfabric_loader_version=0.18.4`
  to test on the loader the pack ships.
- On the container's software OpenGL, Flywheel's indirect backend does not draw pump
  cogs, Create's own pump included. Run `/flywheel backend flywheel:instancing` in the
  world first.
- The dev client cannot join a production server: Loom pulls Porting Lib from Maven with
  a different module set than the one nested in Create's JAR (registry mismatch on
  `porting_lib:area_selector`). For multiplayer, run the release JAR on both sides with
  Fabric API 0.92.6 and Create's Modrinth JAR.
- The new valve mixins (`FluidNetworkMixin`, `ReliefOpenEndMixin`) use the default
  `require = 1` and stop the game if they no longer apply. The pressure redirect in
  `PumpThroughputMixin` also carries the valves; it is `require = 0` like the other pump mixins.
- A creative fluid tank with `{TankContent:{Variant:{fluid:"createhp:steam"},...}}` is an endless
  steam source for turbine tests; `data get block <output>` shows `Formed`, `Length`, `SteamRate`
  and the kinetic network's capacity.
- A turbine's redstone signal for tests: `setblock <in front of a casing next to the output>
  create:analog_lever[face=wall,facing=<away from the casing>]{State:N}`. Comparators read the block
  their `facing` points at; their value is in `data get block <comparator> OutputSignal`, the inlet's
  and gauge's own value in their `Comparator` tag.
- A burst part spills its fluid: after an overload test the block is water, not air.
- On the dedicated server, `data merge block <valve> {ScrollValue:N}` sets a valve's value
  without the panel's callback; break and re-place a neighbouring pipe so the pumps redistribute.
- The pump mixins use `require = 0` and fail silently. After any Create update, start with
  `-Dmixin.debug.export=true` and check with `javap` that every handler is called from
  the exported Create classes in `.mixin.out/`.
- A new pixel-art logo is commissioned. When it replaces `logo_512.png`, check that it
  stays sharp when Mod Menu scales it.
