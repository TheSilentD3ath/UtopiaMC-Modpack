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

## Unlock tree interface rework

The Utopia Core unlock screen (`dev.utopia.core.client`) has been reworked. Both
source projects build with JDK 17, and the screen has been exercised in a real
client on a virtual display with software rendering: character creation, opening
the tree on the progress frontier, the hover tooltip, the inspector, the editor
with auto layout and undo, and discarding a draft. Four defects found that way
are fixed.

Still open: a pass on a real installation with Create and the addon mods present.
In the development runtime those mods are absent, so 29 of the 68 nodes are
filtered out by `requires_mods` and every node icon resolves to air, which makes
the nodes render as empty shapes there. That will not happen in the pack itself.

What changed:

- Map nodes no longer carry a permanent text label. 68 labelled nodes need
  roughly twice the available canvas area, which forced every "fit to screen"
  to the minimum zoom. Names now appear on hover and in the inspector.
- Node state is carried by a badge (check, plus, coin, lock) plus outline, not
  by colour alone. The previous state colours sat at 1.18:1 against each other,
  so "already unlocked" and "unlockable now" were effectively indistinguishable
  and invisible to red-green colour blindness.
- Nodes carry an optional `shape` (circle, square, rsquare, diamond, hexagon,
  gear) describing the *kind* of node. `create.json` now assigns these by graph
  role: gear for the root, diamond for whole-addon gates, hexagon for branch
  points, square for dead ends. Node positions were not touched.
- All prerequisite edges are drawn. Previously only the first parent edge was
  shown, so for 45 of 68 nodes the drawn structure was not the real one.
- The inspector is a column beside the canvas instead of a panel floating over
  it, the sidebar collapses when there is only one tree, and a selection can be
  cleared again — previously it could not, which left part of the canvas
  permanently covered and unclickable.
- Added: node search (by name, description, entry id and item name), a state
  legend, per-frame state caching, editor undo, and a layered auto-layout
  action that rearranges a tree by progression tier.
- GUI textures dropped from 4.5 MB to 884 KB. The wood textures were 1254 px
  sources drawn at 130–455 px, which aliased visibly while zooming.

Open items: purchase feedback is the status line only, with no sound. The layered
auto-layout is available as an editor button but has deliberately not been
applied to `create.json`. That tree is still authored with every node 3.2 units
from its neighbours across a 30 x 43 portrait span, which measured in-game means
the map has to sit near 23 percent zoom before a node's own neighbours fit on
screen, and a full overview does not fit at all. Running the auto layout turns it
into a left-to-right flow of roughly 26 x 17 that fits at once. Whether to adopt
that is a content decision for the pack author, not a code change.

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
