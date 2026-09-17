# Utopia 3.0 project status

Status date: 2026-09-17

The current verified release baseline is Utopia 3.0.0 for Minecraft 1.20.1
with Fabric Loader 0.18.4. Development continues in a separate working copy
while this public repository is migrated to the current structure.

## Repository migration

- Utopia Core `1.4.13+utopia.0.10.11` source, resources, Gradle wrapper and GPL
  attribution are synchronized with the authoritative development workspace.
- Create Industrial Pressure `0.1.0+1.20.1` source, resources and Gradle wrapper
  are synchronized with the authoritative post-refactoring workspace.
- Build outputs, Gradle caches, runtime profiles and release binaries remain
  excluded.
- Guidebook, Heracles and canonical pack configuration are not imported yet.

## Current work

- Utopia Core `0.10.11` builds successfully from this repository. Its
  repository-built JAR is byte-identical to the verified development artifact.
  Pack-level runtime verification and final public release integration remain
  separate gates.
- Create Industrial Pressure builds successfully from this repository. Its
  repository-built JAR is byte-identical to the current development artifact;
  the remaining gate is its full in-game client/server smoke test.
- Guidebook generation works, but final in-game validation remains pending.
- Heracles quest data has passed its current static validation.
- The approved client performance baseline for the next build includes
  Distant Horizons on Medium plus ImmediatelyFast 1.5.5, More Culling 0.24.6
  and Enhanced Block Entities 0.9. These changes still require integration
  into the canonical release artifact.
- A nametag-shadow compatibility fix is awaiting release integration.

There is no final next-release artifact yet. Items listed here are development
state, not a promise that they are already present in the downloadable pack.

## Release validation

The previously planned 22/23 August release window has passed. The next public
release still requires a newly scheduled live playtest using a fresh survival
server and fresh clients running one combined Utopia 3.0 release candidate. A
small group of players should test normal early-game progression and multiplayer
interaction to catch the last integration bugs before release.

The release remains conditional on the combined candidate passing installation,
server start, world join, character creation, quests, Guidebook, Create machinery,
multiplayer lifecycle and stability checks without a release-blocking regression.
Issues found during the playtest will be triaged before the final artifact is
published.

## Public version distinction

The public Modrinth build named **Utopia (Femboy Edition) 1.0.0** is the older
public release for Minecraft 1.20.1 with Fabric Loader 0.15.11. The active
Utopia 3 development line is substantially newer. Documentation or source files
for Utopia 3 must therefore not be read as a claim that those changes are
already included in the Modrinth 1.0.0 download.
