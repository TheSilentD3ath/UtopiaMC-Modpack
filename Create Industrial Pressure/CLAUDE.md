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
- The pump mixins use `require = 0` and fail silently. After any Create update, start with
  `-Dmixin.debug.export=true` and check with `javap` that every handler is called from
  the exported Create classes in `.mixin.out/`.
- A new pixel-art logo is commissioned. When it replaces `logo_512.png`, check that it
  stays sharp when Mod Menu scales it.
