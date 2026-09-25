# UtopiaMC-Modpack

Minecraft 1.20.1 Fabric modpack "Utopia". Two mod sources live here:
`Utopia Core/` (RPG core, hard fork of LevelZ) and `Create Industrial Pressure/`
(Create addon). Each has its own `CLAUDE.md`. Current state and open items:
`docs/PROJECT_STATUS.md`.

## Working with the maintainer

- Answer in German.
- Work autonomously; ask only for design decisions, which are theirs.
- Verify instead of assuming: read the code, measure, test in the client. The numbers
  in the docs were measured; keep it that way and say when something is untested.

## Conventions

- Java comments: German, umlauts written as ae/oe/ue/ss, explaining *why*.
- `docs/PROJECT_STATUS.md` and the READMEs are English; `Utopia Core/ARCHITEKTUR.md`
  is German.
- Commit messages: English, conventional style (`feat(core): …`, `fix(cip): …`).
- Work on a branch from `main`; changes reach `main` through a pull request.

## Toolchain

JDK 17, Fabric Loader 0.18.4, Fabric API 0.92.2+1.20.1, Loom 1.7, Gradle 8.8.
Build each mod from its own folder with `./gradlew build`. The built Utopia Core
requires Loader 0.18.4 and does not start on the old public pack (Loader 0.15.11).

## Licences — do not change casually

- Utopia Core: GPL-3.0, because it is a fork of LevelZ. It cannot be relicensed.
- Create Industrial Pressure: all rights reserved, source available, since commit
  e39f440. Versions up to 0285f9b were MIT and stay MIT. Material based on Create is
  listed in its `LICENSE` with Create's MIT notice; keep that list complete and never
  copy a Create texture verbatim.
- FTB Quests is "All Rights Reserved, visible source": take ideas only, never code or
  textures.

## Testing in the game (cloud container)

`run/` is not versioned, so a fresh container has no test runtime. Utopia Core needs:

- in `Utopia Core/run/mods/`: create-fabric 0.5.1-j build 1631, porting_lib 2.3.15,
  ForgeConfigAPIPort 8.0.3 and milk-lib 1.2.60 (Modrinth), plus a singleplayer world.
  This is older than the Create 6.0.8.1 the pack uses; tree nodes for Create 6
  features (packaging, stock network, …) show initials instead of item icons there.
- Xvfb on `:99` at 1600x900x24 (software OpenGL is enough), then from `Utopia Core/`:
  `DISPLAY=:99 ./gradlew runClient --args="--quickPlaySingleplayer 'New World'"`.
- Drive it with `xdotool`, capture with `import -window root`. GUI scale is 3 at that
  size. In the world, `U` opens the unlock trees and `F8` the tree editor. Key presses
  sent while the world is still loading are lost.
- Stop the client by killing only the java process whose command line contains
  `KnotClient`. `pkill -f` with a pattern like `Xvfb` also matches your own shell.
- The client log always has about twelve harmless error lines (no audio device, no
  narrator library, duplicate classes in the dev mods). Compare against that baseline.
- Installing third-party packages or plugins (npm, plugin marketplaces) is blocked in
  this environment by policy. Do not work around it.

## Not in this repository yet

Heracles quests, the Guidebook and the canonical pack configuration are still in the
maintainer's local workspace. Work on them needs that data imported first, or a
session on the maintainer's machine through Remote Control.
