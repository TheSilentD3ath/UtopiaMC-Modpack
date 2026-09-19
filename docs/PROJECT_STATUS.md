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

- Utopia Core `0.10.11` builds successfully from this repository. Its declared
  toolchain has been moved up to the one the pack actually ships — Fabric Loader
  0.18.4 and Fabric API 0.92.2 instead of the 2023 versions it still named, plus
  Loom 1.7 and Gradle 8.8 to carry them. The old declaration described a
  configuration nobody runs and kept Create out of the development client.
  The byte-identity of the repository-built JAR against the development artifact
  therefore has to be re-established on the same toolchain. Pack-level runtime
  verification and final public release integration remain separate gates.
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

That pass has since been repeated with Create actually installed in the
development runtime, so node icons resolve to real items. Three further defects
turned up only once real icons were on screen; they are fixed:

- `DrawContext.drawTexture` does not enable blending in 1.20.1, so the alpha in
  `setShaderColor` was discarded and every transparency in the canvas was drawn
  at full strength. Search dimming, the darkening of locked nodes and the shadow
  under the state badges all looked like colour choices and were a render state
  bug. `tinted` now enables blending around its own draw.
- Item icons sat at 62 percent of the node, which at working zoom meant an 8 px
  icon inside a 20 px node. They now take 80 percent and snap to whole scaling
  steps.
- Nodes without a resolvable icon were drawn dark on a dark canvas and read as
  holes in the board rather than as nodes.

`requires_mods` still filters the addon nodes that are not installed here — 39 of
68 are active in this runtime. That is the intended behaviour, not a defect.

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
  it, and a selection can be cleared again — previously it could not, which left
  part of the canvas permanently covered and unclickable.
- The tree list is always present. It used to disappear whenever a world had
  only one tree, which hid the only control for switching trees and gave no hint
  that more were coming. It now works like a browser sidebar: pinned by default,
  or collapsed to a 26 px rail that shows each tree's item icon and opens on
  hover. Collapsed, it opens as an overlay rather than pushing the map aside, so
  passing the pointer over it does not reflow what you were looking at, and it
  stays shut while the map is being dragged. The choice is remembered per client
  in `config/utopiacore-client.json`. Editing forces it open — tools that vanish
  when the pointer leaves are not tools.
- Added: node search (by name, description, entry id and item name), a state
  legend, per-frame state caching, editor undo, and a layered auto-layout
  action that rearranges a tree by progression tier.
- GUI textures dropped from 4.5 MB to 884 KB. The wood textures were 1254 px
  sources drawn at 130–455 px, which aliased visibly while zooming.
- The map itself is dark now, not just its frame. It was the brightest surface on
  screen, which put the weight on the background instead of on the nodes; the
  canvas texture is drawn at 45 percent, taking it from relative luminance 0.176
  to 0.032. The state outlines were rebalanced against that into a brightness
  ladder — locked 3.2:1, too expensive 5.7:1, buyable 9.3:1, unlocked 8.9:1 — so
  the state survives without colour vision. The two top steps are deliberately
  equal and are told apart by hue and badge.
- Nodes are a third larger relative to their spacing (54 px to 72 px at full
  zoom), because the icon, not the plate, is what has to be readable.
- The shadow under every edge is gone. It existed to lift bright lines off bright
  wood and was a second line per edge on a dark canvas.

The auto layout is no longer proposed for `create.json`, and the reason is worth
recording because it was measured, not guessed. The map draws only the first
prerequisite of each node as a permanent line — 67 of the 141 edges; the rest
appear on hover. Counting crossings among the lines that are actually drawn:

| layout | crossings | extent |
| --- | --- | --- |
| authored by hand | 0 | 30 x 43, portrait |
| auto layout, barycentre over all edges | 65 | 26 x 17 |
| auto layout, ordered along the drawn chain | 24 | 29 x 20 |

The authored tree is already optimal for what is on screen. The barycentre pass —
the usual second Sugiyama step — was optimising the 74 edges nobody sees and took
apart the 67 everybody does. It has been replaced by a depth-first walk along the
drawn parent chain, which keeps sibling branches together and cuts crossings from
65 to 24. Zero is not reachable that way: a drawn edge skips columns whenever its
node also has a deeper second prerequisite, and it cannot move further left
without violating that one.

So the button is a starting point for a new or unpositioned tree, not an
improvement on a considered arrangement, and it says so in its own documentation.

The editing mode is no longer reachable from the screen itself. A player never
changes a tree, so the two buttons that led there sat in the bottom bar for an
audience that has no use for them; removing them is also what finally made room
for the whole state legend, which had been truncated to two of its four entries.
The mode now opens with a rebindable key, `key.utopia.edit_trees`, default F8,
listed under the Utopia category in Controls. The key is checked inside the
screen because key presses go to an open screen rather than to the client tick,
and it is checked after the search field so that typing never triggers it. The
server-side `canEditTrees` permission still gates everything; without it the key
does nothing and the screen says nothing about it. Leaving is still by Save or
Discard, which only exist while editing. "New tree" moved into the editor
sidebar.

Open items: purchase feedback is the status line only, with no sound. The
overview of `create.json` sits at 10 percent zoom on a 507 x 212 logical canvas
and is a navigation aid rather than a reading mode; the default view opens on the
progress frontier at working zoom instead.

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
