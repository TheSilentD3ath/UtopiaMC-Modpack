# Utopia 3.0 project status

Status date: 2026-09-24

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

- Node names sit under the nodes, but only where there is room (see the
  smoothness pass below). 68 labelled nodes need roughly twice the available
  canvas area, so names fade out below a node size of 24 GUI units and a name
  that would cover another node or name is left out. "Fit to screen" therefore
  still works; it simply shows no names.
- Node state is carried by a badge (check, plus, coin, lock) plus outline, not
  by colour alone. The previous state colours sat at 1.18:1 against each other,
  so "already unlocked" and "unlockable now" were effectively indistinguishable
  and invisible to red-green colour blindness.
- Nodes carry an optional `shape` (circle, square, rsquare, diamond, hexagon,
  gear) describing the *kind* of node. `create.json` now assigns these by graph
  role: gear for the root, diamond for whole-addon gates, hexagon for branch
  points, square for dead ends. Node positions were not touched.
- Only the first prerequisite of each node is drawn as a permanent line. The
  others appear, dashed, as soon as the node or one of its dependants is hovered
  or selected, and the inspector lists every prerequisite by name. Drawn
  permanently, they were a web of dashed lines across the whole map that hid the
  shape of the tree. (An earlier revision of this file said all edges were
  drawn; that stage was superseded.)
- The inspector lies over the right edge of the map and only while something
  is selected, and a selection can be cleared again — previously it could not,
  which left part of the canvas permanently covered and unclickable. (An earlier
  revision of this file described it as a permanent column; that cost a third of
  the map while standing empty most of the time and was dropped.)
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
- GUI textures dropped from 4.5 MB to 884 KB, and with the smoothness pass to
  284 KB: only the frame wood is left. The wood textures were 1254 px sources
  drawn at 130–455 px, which aliased visibly while zooming.
- The map itself is dark and flat, not just its frame. It was the brightest
  surface on screen, which put the weight on the background instead of on the
  nodes. It is now a flat fill in the hue of the former darkened wood, relative
  luminance 0.022 (the tinted wood averaged 0.035, the untinted texture 0.176).
  The state outlines form a brightness ladder against it — locked 3.7:1, too
  expensive 6.5:1, unlocked 10.1:1, buyable 10.6:1 — so the state survives
  without colour vision. The two top steps are deliberately close and are told
  apart by hue and badge.
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

## Unlock tree smoothness pass

The layout was accepted, but the map felt less smooth and less clean in the game
than in the web mockup. Four causes were found in the code, none of them a matter
of taste:

1. Positions were rounded to whole GUI pixels. At GUI scale 3 that is three screen
   pixels, so the map followed the mouse in steps of three, and lines, nodes and
   background rounded at different moments and slid against each other in between.
   Positions now round to screen pixels.
2. Node size snapped to multiples of four GUI units (twelve screen pixels) and item
   icons to five fixed scales, so both popped while zooming. Both now grow
   continuously.
3. The camera eased per frame, not per unit of time: four times faster at 144 fps
   than at 30, and uneven when the frame rate varied. It is now time-based. Zoom
   eases on a logarithmic scale, and the point under the cursor stays fixed for the
   whole animation instead of drifting.
4. Shapes, outlines and badges were 32 to 64 px textures scaled up to nearly three
   times their size, and lines were hard rotated rectangles; both showed staircase
   edges. Everything on the map is now vector geometry (`SmoothPainter`) with a
   one-screen-pixel anti-aliased edge, at any zoom.

Measured in the development client (1600 x 900, GUI scale 3), dragging the map in
single-pixel mouse steps and comparing each frame with the first:

| | before | after |
| --- | --- | --- |
| map movement for 9 px of mouse movement | 9 px, in steps of 0, 0, 3 | 9 px, 1 px per step |
| frames that are a rigid shift of the first | 5 of 9 (residual 0); the other 4 are not (residual 40 to 73) | 9 of 9 (residual 0) |

The look was chosen by the maintainer from the mockup: smooth lines, a flat map,
and names under the nodes. Details:

- Names use a pixel-exact font scale (two thirds at GUI scale 3, so one font pixel
  is exactly two screen pixels), wrap to at most two lines, and sit on a plate in
  the map colour so that a line running underneath recedes instead of crossing the
  text. Priority: selection, hover, search matches, then buyable, too expensive,
  unlocked, locked. A name is only shown while its node's centre is on screen.
- Badges are a dark disc with a coloured rim and the state glyph. The legend in
  the footer draws the same badge, so it shows exactly what the map shows.
- One pattern is taken from FTB Quests, as a pattern only — FTB Quests is "All
  Rights Reserved, visible source", and no code or textures were used: a flow
  along the highlighted chain from prerequisite to dependant. Unlike FTB Quests it
  runs only on the hovered or selected chain, so the map is still at rest.
  `FLOW_SPEED = 0` turns it off.
- A pre-existing bug is fixed on the way: item icons (depth 150) showed through
  the collapsed tree list when it opened over the map (depth 0). The map now
  leaves out the strip under the open list.
- The zoom step per wheel notch went from 1.25 to 1.2.
- The texture generator is reduced to what is still needed and renamed
  `tools/downscale_unlock_wood.py`.

Checked in the development client with Create installed: default view, a hovered
node with two prerequisites, 84 percent, the 10 percent overview, and the tree
list opened over the map. The client log has the same twelve environment lines as
before the change (no audio device, no narrator library, duplicate classes in the
development mods) and nothing from the changed classes.

## Open items from the 2026-09-24 review

- Translations. `de_de.json` of Utopia Core lacks 130 of 232 keys: the 69 tree
  and node names, but also the classes (20), origins (10) and genders (6) of the
  character creation screen, the first screen a player sees. Create Industrial
  Pressure has no German file (10 keys), and neither has the LevelZ namespace
  (286 keys, 95 of them the configuration screen). If the pack ships translations
  through a resource pack that is not in this repository, part of this may be
  covered there.
- There is no CI: nothing builds the two mods on push.
- The Trinkets compatibility mixin warns about missing obfuscation mappings for
  two `@Shadow` fields. It is harmless — they are Trinkets fields, not Minecraft
  fields — and the fix is `@Shadow(remap = false)` on those two fields. Not on the
  class: `canInsert` overrides a Minecraft method and needs the mapping.
- Unlocking still gives no sound.

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
