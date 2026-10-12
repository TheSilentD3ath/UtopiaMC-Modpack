# Utopia 3.0 project status

Status date: 2026-10-11

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
  Loom 1.7 and Gradle 8.8 to carry them. On 2026-09-25 the Fabric API was raised
  to 0.92.6, the minimum Create 6.0.8.1 accepts (see the smoke test below). The
  old declaration described a configuration nobody runs and kept Create out of
  the development client.
  The byte-identity of the repository-built JAR against the development artifact
  therefore has to be re-established on the same toolchain. Pack-level runtime
  verification and final public release integration remain separate gates.
- Create Industrial Pressure builds successfully from this repository and has
  now been smoke-tested in a client and on a dedicated server (see the section
  below). The repository-built JAR is no longer byte-identical to the
  development artifact: the smoke-test fixes changed its declared dependencies,
  added a German translation and a block tag, and fixed pick-block on glass
  pipes; a later fix made pumps work with a tank directly on a face. Since then
  all four phases of the pressure system were added: pressure limits with
  bursting and netherite explosions; shut-off, regulator and relief valves;
  pressure tanks; and steam with a steam outlet for boilers and a large steam
  turbine.
  Before a release, the open defects listed there need a decision.
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
- Mixins: 11 of the 12 injectors were woven, identically in the development
  client and on production mappings, checked in the classes exported
  with `-Dmixin.debug.export=true`. The twelfth was the dead redirect behind the
  pump defect; after the pump fix all 11 remaining injectors are woven.
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

### Pump fix (2026-10-05)

A high pressure pump with a tank directly on one of its faces was broken: with
the tank on the output it moved nothing, with the source on the intake it moved
no more than Create's pump while costing up to 64 times the stress. Two mixin
bugs in `PumpSourcePressureMixin` caused it:

- The `getSpeed()` redirect named `KineticBlockEntity` as owner, but the bytecode
  calls `PumpBlockEntity.getSpeed()`, so it never applied; `require = 0` hid
  that.
- The `ordinal = 1` redirect raised the slot that Create zeroes on *both* pump
  faces, not only on the output. That reversed the pressure on both faces, the
  flows turned around (visible in the pump's NBT), and pump and neighbour pushed
  against each other.

The redirect now targets `PumpBlockEntity`, and the `ordinal = 1` redirect is
gone together with `CHPPumpContext`. Its premise, that Create's pump fills a
directly attached tank only at a floor rate, does not hold on 6.0.8.1: Create's
pump moves 32 mB per tick into it, the same as through pipes.

Measured on the dedicated server with the release JAR, Utopia Core and Create
6.0.8.1, at 64 RPM, in mB per tick:

| setup | Create pump | tier 3 before | tier 3 after | tier 5 after |
| --- | --- | --- | --- | --- |
| pipes on both sides | 32 | 512 | 512 | 2,048 |
| source directly on the intake | 32 | 32 | 512 | 2,048 |
| tank directly on the output | 32 | **0** | 512 | 2,048 |
| both directly attached | 32 | not measured | 512 | not measured |

Some readings came out at 77/78 of these values (31.2, 505.4) because one probe
fell one tick apart from the game-time query. Tiers 1 to 5 through pipes still
match the factors 4 to 64. The pump's NBT now shows the same pattern as Create's
pump, only scaled: outward pressure and outward flow on the output, inbound on
the intake. Bursting and reach are unchanged (Create pipe and Create glass pipe
burst, a Create pipe on Create's pump does not; tier 1 at 256 RPM reaches 60
pipes but not 70, at 64 RPM not 30). In the production client the tank on the
tier 5 pump's output visibly fills, and the client log has only the usual
environment entries.

### Open defects, not changed

These need a decision before a release.

1. **Network changes far from the pump.** `FluidPropagatorPumpMixin` makes high
   pressure pumps hear about network changes, but Create's search for pumps stops
   at its own 16-block range along pipes that carry no pressure. A tank placed at
   the end of a 25-pipe dead end, on a tier 1 pump at 128 RPM (reach 32), stays
   empty until the pump is restarted. Options: widen that search while it runs,
   which costs a longer search on every pipe change, or accept and document it.
2. **Silent mixins.** Every behaviour mixin uses `require = 0`, which is how the
   pump defect went unnoticed; `fabric.mod.json` also accepts Create 6.0.0 and
   later while only 6.0.8.1 was tested. Options: `require = 1` for the injection
   points confirmed on 6.0.8.1, or a check at startup.
3. **Design points.** Netherite pipes copy the netherite block's hardness 50
   (about 9.4 seconds per pipe with a diamond pickaxe by the formula) and blast
   resistance 1200. No block needs a tool tier; vanilla wants diamond for
   netherite and stone for copper, while Create's pipes accept any pickaxe.
   Tiers 1 to 3 look identical, as do tiers 4 and 5, and no tooltip explains
   tier, reach or stress.

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
- Create 6.0.8.1 requires Fabric API 0.92.6 or later. Utopia Core and the root
  `CLAUDE.md` named 0.92.2; both now say 0.92.6, and Utopia Core declares it as
  its minimum. Checked on the dedicated server: with Fabric API 0.92.6, Utopia
  Core, Create Industrial Pressure and Create 6.0.8.1 start together (plus Cloth
  Config 11.1.136 and LibZ 1.0.3), and both Create's pump and a tier 3 pump
  still move 32 and 512 mB per tick. With 0.92.2 the server refuses to start:
  Create, Ponder and Create Industrial Pressure all ask for 0.92.6 or later.
  The canonical pack configuration is not in this repository; it has to ship
  0.92.6 or later as well.

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

## Create Industrial Pressure pressure system (2026-10-11)

The maintainer asked for a pressure system: pipe runs whose pressure has to be
watched, valves that shut off, regulate and relieve, smart variants by redstone,
tiers like the pipes, pressure tanks, explosions for netherite, Display Link
support and steam carried in pipes. Four decisions were taken: pressure is what the
pumps put in, plus a limit per part (no build-up when an outlet is blocked);
netherite explodes for real, with a server-config switch for block damage; steam
is a fluid in pipes; the old rule that every Create pipe in a high pressure network
bursts is replaced by limits. The plan for all four phases, with units, limits and
the open points, is in `Create Industrial Pressure/PRESSURE_SYSTEM.md`.

Phase 1 is built: pressure in bar from Create's own pipe pressure (16 units are
1 bar), limits per pipe (Create parts 16 bar, copper pressure pipes 256, netherite
1,024), bursting and the netherite explosion, the server config, Engineer's
Goggles readouts and a Display Link source. This also closes the encased-pipe gap
listed under the smoke test's open defects: encased and smart pipes now burst
like any other Create pipe.

Tested on the dedicated server with the release JAR, Utopia Core and Create
6.0.8.1, with a production client:

| setup | pressure | part in the middle | result |
| --- | --- | --- | --- |
| tier 1, 64 RPM | 16 bar | Create pipe (16) | holds |
| tier 1, 128 RPM | 32 bar | Create pipe (16) | bursts, drops |
| tier 1, 128 RPM | 32 bar | encased Create pipe (16) | bursts |
| tier 1, 128 RPM | 32 bar | smart pipe (16) | bursts, drops |
| tier 2, 256 RPM | 128 bar | Create glass pipe (16) | bursts, drops a fluid pipe |
| tier 3, 256 RPM | 256 bar | copper pressure pipe (256) | holds |
| tier 5, 256 RPM | 1,024 bar | copper pressure pipe (256) | bursts, drops |
| tier 5, 256 RPM | 1,024 bar | netherite pipe (1,024) | holds |
| Create pump, 256 RPM | 16 bar | Create pipe | holds |

The pressures were read from the pipes' NBT and match the table in
`PRESSURE_SYSTEM.md` exactly. Two tier 5 pumps feeding one netherite pipe put
2,048 bar on the shared part (1,024 on each feed): both shared pipes exploded and
left a crater. With `explosionBreaksBlocks = false` the same setup removed the two
pipes, left ground and tank intact and still killed a pig standing next to it.

In the production client, in German: the goggles show "Rohrdruck: 32 von 256 bar"
on a pipe and "Pumpendruck: 32 bar" under the kinetic stats of a pump, and an
overloaded pipe reads "1024 von 256 bar, Überlastet, platzt gleich" in red while it
cracks. A Display Link on a pressure pipe writes "Rohr 32 bar" onto a sign, and
onto a three-wide Create display board "Last" with a progress bar and "Druck 32
bar". The Display Link screen lists the source as "Rohrdruck" with all four modes.
Both logs have only the usual environment entries.

Found on the way: the server writes its loaded config values back when it stops,
so `config/createhp-server.toml` has to be edited while the server is down. An edit
on a running server is lost. Forge Config API Port keeps server configs in the
global `config/` folder on Fabric, not per world.

Not tested: Create's fluid valve as an overloaded part (its limit is set, but it
needs its own rotation in the test rig), the explosion power setting, a network
that several high pressure pumps check at the same time (each keeps its own crack
count, so a pipe may crack faster), and the goggles on Create's own pipes, which
show nothing because Create gives them no goggle readout.

### Phase 2: valves (2026-10-11)

Built: shut-off valve, pressure regulator and relief valve, each in copper (256 bar)
and netherite (1,024 bar), each upgradeable to a smart variant with an Electron Tube.
How they act on Create's pressure and flow is in `PRESSURE_SYSTEM.md`, together
with the maintainer's decisions on the open points (Electron Tube, venting as a
cloud, tank and boiler sizes, recoloured textures).

Tested on the dedicated server (Fabric Loader 0.18.4, release JAR, Create 6.0.8.1,
Utopia Core), pressures read from NBT, flow from the target tank over about 100 ticks.
Pump tier 3 at 256 RPM (256 bar) unless noted:

| setup | measured | expected |
| --- | --- | --- |
| no valve | 2,048 mB/t | 256 bar / 2 × 16 |
| shut-off valve closed | 256 bar before, 0 behind, no flow | behind 0 (Create's own valve passes the pressure) |
| shut-off valve opened by a motor | 256 bar behind, 2,048 mB/t | full flow |
| regulator, default 16 bar | 256 before, 16 behind, 128 mB/t | 16 × 8 |
| regulator at 64 bar | 64 behind, 512 mB/t | 64 × 8 |
| smart regulator, redstone 8 | 136.5 bar behind, 1,092 mB/t | 256 × 8/15 |
| smart shut-off, redstone on / off | open, 2,048 mB/t / closed, no flow | |
| relief 128 bar at the end of a T | whole run 128 bar, 1,024 mB/t, nothing spilled | |
| relief 2 bar, tier 1 at 16 RPM, finite source | source −32 mB/t, target +16 mB/t | half vented |
| two tier 1 pumps (64 bar each) into one relief at 100 bar | 100 bar, venting, 800 mB/t | 100 × 8 |
| smart relief at 250 / 256 / 100 bar | lamp on / off / on | vents above its value only |
| Create pipe behind regulator 16, relief 16, closed shut-off | all three hold; without a valve it bursts | |
| netherite regulator at 1,024 bar inlet | holds, 16 bar behind (default value 4 steps) | |
| copper regulator at 1,024 bar inlet | bursts and drops | |
| netherite regulator between two tier 5 pumps (2,048 bar) | explodes, crater, no drop | |
| smart valve broken | drops valve and Electron Tube | |

After a restart the regulators and relief valves set their networks up again by
themselves (all rates above re-measured). `javap` on the exported Create classes
shows every new handler applied: pressure shaping and flow cap in `PumpBlockEntity`
and `FluidNetwork`, the relief-end check in `FluidPropagator`, the pointer accessor
on `FluidValveBlockEntity`.

Production client, German, joined to that server: all six valves render with their
indicator, coloured handle and, on regulator and relief valve, a pressure gauge on
the value-panel side; the shaft shows only on the normal shut-off valve. A running
setup shows the regulator yellow ("Regelt") and the relief valve venting clouds and
lighting a redstone lamp. Goggles on the regulator: "256 von 256 bar, Eingestellt auf
64 bar, Regelt"; on the relief valve: "Öffnet bei 32 bar, Lässt 32 bar ab, Schlau: gibt
beim Ablassen Redstone aus". Right-click with an Electron Tube made a shut-off valve
smart (shaft gone, brass indicator, tube kept in creative). Holding right-click on
the value panel opens Create's board titled "Sollwert" in bar; a new value reached
the network at once (regulator 27 bar: 27 bar behind it, the relief valve set to 32
stopped venting). The item tooltips with Shift explain each valve. A Display Link on
the relief valve writes "32 bar" onto a sign. Both logs: only the usual entries.

Fixed during the test: with two pumps, each of Create's fluid networks was capped on
its own, so the relief case delivered 1,024 instead of 800 mB/t; the cap is now
shared by the pumps' contributions. A relief valve at the end of a T showed "0,0 von
256 bar", because Create gives dead-end branches no pressure; it now shows the
pressure of its run. The shaft hole on regulator and relief valve became a gauge.

Not tested: rotating or picking up the new valves with the wrench, valves on
contraptions, the schematicannon item list, consuming the Electron Tube in survival
(only creative, where it is kept), netherite shut-off and relief valves exploding
(only the regulator; the code path is shared), a regulator in one branch of a
network with several branches (Create computes one flow per network, so the other
branches are throttled too; see `PRESSURE_SYSTEM.md`), the dev client.

### Phase 3: pressure tanks (2026-10-11)

Built: High Pressure Tank (32 buckets per block, 256 bar) and Netherite Pressure Tank
(128 buckets, 1,024 bar), both Create's fluid tank underneath. The maintainer also
decided that Create's own tanks hold 16 bar like Create's pipes, with a server config
switch (`createTanksLimited`), and that an overloaded tank behaves like a pipe.

Tested on the dedicated server (release JAR, Create 6.0.8.1, Utopia Core):

| setup | result |
| --- | --- |
| copper tank, single, tier 3 at 256 bar | fills to 2,592,000 droplets (32 buckets), holds |
| netherite tank, single, tier 5 at 1,024 bar | fills to 10,368,000 droplets (128 buckets), holds |
| copper tank 2 × 2 × 3, tier 3 | one multiblock, 31,104,000 droplets (384 buckets) |
| netherite tank next to that copper tank | stays a separate tank |
| restart | all three keep their full content |
| copper tank at 1,024 bar (tier 5) | bursts |
| netherite tank at 2,048 bar (two tier 5 pumps) | explodes, crater, no drop |
| Create tanks filled by high pressure pumps in the old test rigs | burst; the one behind a closed smart valve holds |
| `createTanksLimited = false`, Create tank fed at 256 bar | holds |

`javap` on the exported Create class shows the capacity redirect in all five calls
(`createInventory`, twice in `read`, `applyFluidTankSize`, `getTankSize`), and the
model invoker on `FluidTankModel` in the client.

Production client, German: copper and netherite tanks render with connected textures
and windows, also as a 2 × 2 × 3 multiblock; the wrench toggles the windows. Goggles
on a fed tank: Create's tank info and "Tankdruck: 256 von 256 bar". A Display Link on
the tank writes "100%" (fill level) and "256 bar" (pressure) onto a sign. A netherite
tank with a steam engine is recognised as a boiler. The Shift tooltip explains the tank.

Not tested: pressure tanks on contraptions (Create's tank has its own mounted storage
and movement behaviour; ours are not registered for that), the schematicannon, placing
whole layers with the item on a wide tank, the creative mode exchange with buckets,
the dev client.

### Phase 4: steam and the steam turbine (2026-10-11)

Built: steam as a fluid, a Steam Outlet for boilers, pressure tank boilers up to
5 × 5 wide with 50 (copper) or 150 (netherite) levels, and a steam turbine multiblock
in copper and netherite with steam inlets and condensate outlets. The maintainer
chose a steam outlet block, transport by pumps, the boiler sizes, a large 5 × 5
turbine in the style of Create: New Age and Create: Power Grid (ideas only, no
code or textures), clearly more power than Create, half the steam back as water, and
a pressure limit in the turbine. Details in `PRESSURE_SYSTEM.md`.

Tested on the dedicated server (release JAR, Create 6.0.8.1, Utopia Core); steam
rates read from NBT, flow from tank contents over about 200 ticks:

| setup | measured | expected |
| --- | --- | --- |
| Create boiler 2 × 2 × 2 with a steam outlet | 20 mB/t steam | 2 levels |
| netherite pressure tank 5 × 5 × 3 on 25 superheated burners | ActiveHeat 150, 1,500 mB/t | 150 levels |
| copper pressure tank 5 × 5 × 2 on 25 superheated burners | 500 mB/t | 50 levels |
| Create boiler 3 × 3 × 8, 6 steam engines and an outlet | 60 mB/t | 12 levels by water, 6 for the engines |
| copper turbine, 2 rotor layers, endless steam, tier 1 pump | 200 mB/t, 64 RPM, 491,520 SU | 20 × 16,384 × 1.5 |
| same, condensate pumped into a tank | 100 mB/t water | half the steam |
| same, fed by Create's pump at 256 RPM | 128 mB/t, 314,573 SU | 12.8 × 16,384 × 1.5 |
| rotor removed, or a netherite rotor in the copper turbine | stops at once, speed 0 | |
| copper inlet at 512 bar (tier 4) | bursts, turbine incomplete | |
| netherite turbine, 1 layer, tier 5 at 1,024 bar | holds, 200 mB/t, 819,200 SU | 20 × 16,384 × 2.5 |
| netherite inlet at 2,048 bar (two tier 5 pumps) | explodes | |
| Create boiler 3 × 3 × 3 on 9 superheated burners → outlet → tier 1 → copper turbine, 1 layer | 60 mB/t, 147,456 SU | 6 levels; 6 steam engines give 98,304 |
| that turbine with a second condensate outlet, the first full | 30 mB/t into the second | all 30 mB/t condensate |
| 16 rotor layers / 17 | forms / "too long" | at most 16 |
| restart | turbines run on with their saved rate in the same kinetic network | |

`javap` on the exported `BoilerData` shows all nine new handlers called (size and
water limits, heat factor, outlet count, active with outlets, scaled bars, clear,
write, read).

Production client, German: the outlet, steam (white in a glass pipe and in a pressure
tank window) and all turbine parts render with their textures; the output shows its
half shaft in the front opening. Goggles: steam outlet "Dampfauslass: 60 mB/t Dampf,
60 von 8000 mB im Auslass"; netherite boiler "Kesselzustand: Lvl 150" with the bars
scaled to 18; turbine output "Dampfturbine: Kupfer, Rotorlagen: 1, 60 von 100 mB/t
Dampf, 30 mB/t Kondensat" and Create's generator stats with 147,456 SU; an
incomplete turbine "Unvollständig, Prüfe den Block bei 203 -58 0", and "Zu lang:
höchstens 16 Rotorlagen"; inlet "Einlassdruck: 0,0 von 256 bar, 0 von 8000 mB Dampf
im Einlass, Teil einer vollständigen Turbine"; condensate outlet "8000 von 8000 mB
Wasser". The Shift tooltips explain output and inlet. Both logs: only the usual
entries; the new recipes load without errors.

Fixed during the test: goggle lines repeated all earlier lines (one shared
`LangBuilder`), on the steam outlet from phase 4a and on the turbine parts. With two
condensate outlets, half the water was lost when the one listed second was full; a
second pass now gives it to any outlet with room.

Defaults chosen during the build, for the maintainer to confirm or change: at most
16 rotor layers, a fixed 64 RPM like the steam engine, condensate lost when the
outlets are full, and the recipes listed in `PRESSURE_SYSTEM.md`.

Not tested: crafting the new recipes in survival, the turbine under real load or
overstressed (only its capacity was read), its shaft turning seen over time (stills
only), turbine parts on contraptions or rotated with the wrench, several inlets fed by
different pumps, the condensate fed back into the boiler that makes the steam,
steam outlets on a netherite pressure tank together with steam engines, the dev
client.

## German translation of Utopia Core

Utopia Core's `de_de.json` is complete: 232 of 232 keys. A script check shows it
has the same keys as `en_us.json`, in the same order, with the same placeholders
and no duplicates. Six values are the same as in English on purpose: Utopia,
Create, Big Cannons, Neko, Tank, and the editor field "Icon (namespace:item)".

Terms for machines, items and mechanics come from Create's own German file
(Create 6.0.8.1) and from the German files of the addons (Connected, Crafts &
Additions, Power Grid, Steam 'n' Rails, Railways Navigator, Big Cannons), so the
tree reads like the rest of the game: Vorrichtung for contraption, Lohe for blaze,
"Das Messingzeitalter" as in Create's advancement. Create: New Age has no German
file; its node names are ours. The maintainer decided four points: class names in
the generic masculine, "Trans Mädchen", "Fertigkeitspunkte" for skill points, and
mod names left untranslated.

Checked in the development client in German (1600 x 900, GUI scale 3): all five
origins, three genders and ten classes, the buttons, and the unlock screen's
inspector. Nothing is cut off. The fullest page is the Survivor: ten effect lines
under a two-line description, with one line to spare. The first wording took three
lines and left no spare line, so a word that is not in the English text was
dropped. Two counts that were wrong in the singular ("1 Gegenstände", "+1
Fertigkeitspunkte") were reworded. The client log has three entries at ERROR level:
no audio device, no narrator, and an empty Porting Lib registry. None of them comes
from Utopia Core.

A bug turned up in the process and is fixed. The effect lines built an attribute's
translation key from the id's path, so the four Utopia attributes showed as raw
keys (`attribute.name.fire_resistance`) in every language. Attributes from mods
that are not installed are now left out, because `CharacterStateApplier` skips
them as well.

Some text on the German screen is still English, because it comes from ids, not
from a language file:

- skill names in the effect lines ("Mining beginnt auf Level 8"), from the LevelZ
  skill id;
- the summary of earlier choices under the buttons ("Villager · Transfem"), from
  the trait id.

The numbers in the same lines have two formatting problems, in English as well:

- An attribute that is a fraction shows as that fraction, rounded to one decimal.
  The Melon Citizen's 15 percent less fire damage reads "+0.2 Feuerresistenz".
- Some experience bonuses read "+10.0%" where others read "+25%", because 1.1 − 1
  leaves a floating-point remainder.

These are code issues, not translation issues, and they are not changed here.

Node names on the map were measured with the glyph widths of the Minecraft font and
a copy of the canvas's wrap (`tools/LabelFit.java`). Its prediction matches the
client: "Ingenieurs…" and "Drucklufta…" at the default view.

| line width | English cut | German cut |
| --- | --- | --- |
| 66 font px (default view, 34 percent) | 10 of 68 | 44 of 68 |
| 102 font px | 0 | 14 |
| 165 font px (full zoom) | 0 | 0 |

The wrap breaks lines only at spaces. At the default view, single German compounds
such as "Flüssigkeitstechnik" or "Präzisionsgetriebe" are wider than a line.
Rewording cannot fix that without dropping Create's terms. The inspector and search
still show and find the full name.

Testing note: this container had neither xdotool nor ImageMagick, and installing
them was refused. `tools/Drive.java` drives Xvfb with `java.awt.Robot` from the JDK
instead (keys, clicks, PNG screenshots). Without a window manager, the client
window stays at 854 x 480 unless it is started with `--width 1600 --height 900`.

## Open items from the 2026-09-24 review

- Translations. Utopia Core is done (see above), and so is Create Industrial
  Pressure (all 10 keys, see the smoke test). The LevelZ namespace has no German
  file (286 keys, 95 of them the configuration screen). If the pack ships
  translations through a resource pack that is not in this repository, part of
  this may be covered there.
- German node names are cut at the default map zoom: 44 of 68, against 10 in
  English. Possible remedies: break long words at hyphens or with a hyphen at the
  line end, a wider minimum label width, or a third line. This is a design
  decision for the maintainer.
- Character creation still shows skill names and earlier choices from ids (English
  in every language), shows fractional attributes as rounded fractions instead of
  percentages, and prints some bonuses as "+10.0%" (see the translation section).
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
