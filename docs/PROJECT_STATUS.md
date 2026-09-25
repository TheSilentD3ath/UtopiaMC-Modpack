# Utopia 3.0 project status

Status date: 2026-09-25

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
- Create Industrial Pressure builds successfully from this repository and has
  now been smoke-tested in a client and on a dedicated server (see the section
  below). The repository-built JAR is no longer byte-identical to the
  development artifact: the smoke-test fixes changed its declared dependencies,
  added a German translation and a block tag, and fixed pick-block on glass
  pipes. Before a release, the open defects listed there need a decision.
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

## Create Industrial Pressure smoke test (2026-09-25)

The first in-game test of the mod, run against the exact Create it builds
against: Create Fabric 6.0.8.1 build 1744 (the Modrinth JAR and the Maven
artifact are the same file, SHA-1 `f750d019…`).

Environments:

- Development client (`./gradlew runClient`, Fabric API 0.92.6), on Loader
  0.18.4 for the main pass and on 0.17.2 for the re-check after the fixes.
  Software OpenGL on a virtual display.
- Dedicated Fabric server, Loader 0.18.4, with Fabric API 0.92.6, the Create JAR
  and the release JAR from `./gradlew build`, so the mixins ran on production
  mappings through the refmap.
- A production client with the same three mods, connected to that server.

### Tested and working

- All seven items can be placed by hand. The creative tab holds them in order;
  names are correct in English and German.
- Connection: a hand-placed row alternating Create pipe, high pressure pipe and
  netherite pipe joins into one straight pipe. Create's own mechanical pump
  moves water through a high pressure pipe, both glass variants, a netherite
  pipe, a Create glass pipe and a Create pipe into a tank.
- Every pump tier moves fluid; water is visible in the glass pipes, and lava was
  moved by the tier 5 pump on the dedicated server.
- Throughput and stress, measured on the server as tank content over game time,
  at 64 RPM with pipes on both sides of the pump:

  | pump | mB per tick | stress |
  | --- | --- | --- |
  | Create mechanical pump | 32 | 256 SU |
  | tier 1 | 128 | 1,024 SU |
  | tier 2 | 256 | 2,048 SU |
  | tier 3 | 512 | 4,096 SU |
  | tier 4 (netherite) | 1,024 | 8,192 SU |
  | tier 5 (advanced netherite) | 2,048 | 16,384 SU |

  Exactly the documented factors of 4 to 64, for both throughput and stress.
- Reach scales with speed as documented: tier 1 at 64 RPM does not fill a tank
  30 pipes away, at 128 RPM it does; at 256 RPM it reaches 60 but not 70. Tier 2
  at 256 RPM reaches 70. Create's pump reaches 14 but not 30.
- The wrench turns both solid pipes into their glass variant and back, in
  singleplayer and on the dedicated server.
- Breaking any of the nine blocks with `/setblock … destroy` drops the right
  item; glass pipes drop their solid pipe. (Mining by hand: see the fixes.)
- Bursting: in a high pressure pump's network, Create fluid pipes and Create glass
  pipes crack and break after a few seconds and drop their item. The mod's own
  pipes survive, and a Create pipe on Create's pump survives.
- A tank added to a running line 10 pipes from a high pressure pump starts
  filling without restarting the pump.
- Mixins: 11 of the 12 injectors are woven, identically in the development
  client and on production mappings, checked in the classes exported
  with `-Dmixin.debug.export=true`.
- Multiplayer: the production client joins, registries sync, and placing,
  wrenching and rendering work.
- All seven recipes load without errors. None was crafted.

### Fixed in this pass

- The build declared Loader 0.16.9 and Fabric API 0.92.2 and forced that loader,
  but Create 6.0.8.1 requires at least 0.17.2 and 0.92.6. `runClient` therefore
  stopped at mod resolution; the development client cannot have run in this
  configuration. The build now uses those minimums, and `fabric.mod.json`
  declares them, which Create enforces anyway.
- The mod shipped no `mineable/pickaxe` tag. In survival, no tool counted as the
  right one: a high pressure pipe still stood after 6 seconds with a diamond
  pickaxe, broke within 22, and dropped nothing. Create tags its own pipes and
  pumps the same way. All nine blocks are now tagged, without a tool tier.
  Checked on the dedicated server with the production client: all nine break
  with a diamond pickaxe and drop the right item.
- Pick-block (middle click) on a glass pipe gave Create's copper fluid pipe. It
  now gives the solid pipe of the same material.
- `de_de.json` with all 10 keys, using Create's own German terms: Rohr, Glasrohr,
  Netherit- with a hyphen, Erweitert for advanced.

### Open defects, not changed

These need a decision before a release.

1. **Pumps with a tank directly on a face.** Measured at 64 RPM, in mB per tick:

   | setup | Create pump | tier 3 now | tier 3 with proposed fix |
   | --- | --- | --- | --- |
   | pipes on both sides | 32 | 512 | 512 |
   | source directly on the intake | 32 | 32 | 512 |
   | tank directly on the output | 32 | **0** | 512 |
   | both directly attached | 32 | not measured | 512 |

   With a tank on its output, a high pressure pump moves nothing at all. With a
   source on its intake, it moves no more than Create's pump while costing 16
   times the stress. Two mixin bugs in `PumpSourcePressureMixin` cause this. The
   `getSpeed()` redirect names `KineticBlockEntity` as owner, but the bytecode
   calls `PumpBlockEntity.getSpeed()`, so it never applies; `require = 0` hides
   that. The `ordinal = 1` redirect raises the slot that Create zeroes on *both*
   pump faces, not only on the output. That reverses the pressure on both faces,
   the flows turn around (visible in the pump's NBT), and pump and neighbour push
   against each other. Proposed fix: retarget the `getSpeed()` redirect to
   `PumpBlockEntity`, and remove the `ordinal = 1` redirect together with
   `CHPPumpContext`. It was built and measured outside the repository (third
   column); piped throughput for tiers 3 and 5 and bursting are unchanged. It is
   not committed because it removes a documented feature. The feature's premise,
   that Create's pump fills a directly attached tank only at a floor rate, does
   not hold on 6.0.8.1: Create's pump moves 32 mB per tick into it, the same as
   through pipes.
2. **Network changes far from the pump.** `FluidPropagatorPumpMixin` makes high
   pressure pumps hear about network changes, but Create's search for pumps stops
   at its own 16-block range along pipes that carry no pressure. A tank placed at
   the end of a 25-pipe dead end, on a tier 1 pump at 128 RPM (reach 32), stays
   empty until the pump is restarted. Options: widen that search while it runs,
   which costs a longer search on every pipe change, or accept and document it.
3. **Which pipes burst.** Encased fluid pipes, smart fluid pipes and fluid valves
   in a high pressure network do not burst, while plain and glass Create pipes do.
   Encasing a pipe in copper is therefore a free way around the mechanic. The
   README says regular Create-compatible pipes burst.
4. **Silent mixins.** Every behaviour mixin uses `require = 0`, which is how
   defect 1 went unnoticed; `fabric.mod.json` also accepts Create 6.0.0 and later
   while only 6.0.8.1 was tested. Options: `require = 1` for the injection points
   confirmed on 6.0.8.1, or a check at startup.
5. **Design points.** Netherite pipes copy the netherite block's hardness 50
   (about 9.4 seconds per pipe with a diamond pickaxe by the formula) and blast
   resistance 1200.
   No block needs a tool tier; vanilla wants diamond for netherite and stone for
   copper, while Create's pipes accept any pickaxe. Tiers 1 to 3 look identical,
   as do tiers 4 and 5, and no tooltip explains tier, reach or stress.

### Environment notes

- On software OpenGL, Flywheel's indirect backend does not draw pump cogs,
  Create's own pump included. `/flywheel backend flywheel:instancing` fixes it.
  Not a defect of the mod.
- The development client cannot join a production server: Loom resolves Porting
  Lib from Maven with a different set of modules than the one nested in Create's
  JAR, and the client lacks `porting_lib:area_selector`. Multiplayer tests need a
  production client.
- In one development session, after a language switch and `/reload`, high
  pressure pumps could not be mined at all (one swing, no progress) while
  Create's pump broke normally. It did not happen again in a fresh client, after
  F3+T, after `/reload`, or with either Flywheel backend. Unexplained; worth
  watching in the playtest.
- Create 6.0.8.1 requires Fabric API 0.92.6 or later, while this file and the
  root `CLAUDE.md` name 0.92.2. If the pack really ships 0.92.2 next to Create
  6.0.8.1, it cannot start. Check the canonical pack configuration.

### Not tested

- Crafting the recipes in game, and how recipe viewers show them.
- Picking blocks up by sneaking with the wrench: the simulated sneak key did not
  work in this environment, not even on Create's own pump.
- Survival progression and balance, stress in real networks with other
  consumers, the cost of the burst scan on large networks, chunk unloading with
  running pumps, pipes on contraptions, schematics and the Schematicannon, and
  fluids other than water and lava.
- Any Create version other than 6.0.8.1, and the mod inside the full pack.
- The names of the two glass pipes: they have no item and appear nowhere without
  an info mod. Only the presence of their keys was checked.

Screenshots of every block and of the setups were handed over in the session;
they are not in the repository.

## Open items from the 2026-09-24 review

- Translations. `de_de.json` of Utopia Core lacks 130 of 232 keys: the 69 tree
  and node names, but also the classes (20), origins (10) and genders (6) of the
  character creation screen, the first screen a player sees. The LevelZ
  namespace has no German file (286 keys, 95 of them the configuration screen).
  Create Industrial Pressure now has one (all 10 keys, see the smoke test). If
  the pack ships translations through a resource pack that is not in this
  repository, part of this may be covered there.
- There is no CI: nothing builds the two mods on push.
- The Trinkets compatibility mixin warns about missing obfuscation mappings for
  two `@Shadow` fields. It is harmless — they are Trinkets fields, not Minecraft
  fields — and the fix is `@Shadow(remap = false)` on those two fields. Not on the
  class: `canInsert` overrides a Minecraft method and needs the mapping.
- Unlocking still gives no sound.
- The Utopia Core development client runs Create Fabric 0.5.1, while Create
  Industrial Pressure builds against Create 6.0.8.1. Tree nodes for Create 6
  features (packaging, stock network and others) therefore show initials instead
  of item icons in that client. In-game checks of the tree are only
  representative once the development runtime uses Create 6 as well.

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
