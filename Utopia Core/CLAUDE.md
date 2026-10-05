# Utopia Core

GPL-3.0 hard fork of LevelZ that presents itself as `levelz`. Architecture:
`ARCHITEKTUR.md` (German). Test plan: `TESTPLAN.md`.

## Unlock tree screen (`dev.utopia.core.client`)

- `UnlockScreen`: layout, header, search, inspector, tree list, footer legend.
- `UnlockTreeCanvas`: the map — camera, edges, nodes, names, input.
- `SmoothPainter`: anti-aliased vector fills, rings and lines for the GUI.
- `UnlockLayout`: auto layout, meant for new or unpositioned trees only.
- `UtopiaClientSettings`: per-client settings in `config/utopiacore-client.json`.

Decisions the maintainer made; keep them unless they say otherwise:

- Dark, calm look close to FTB Quests. Flat map (`#342716`), vector shapes, names
  under the nodes where there is room.
- The editor opens only with a rebindable key (default F8); no buttons for it.
- The tree list is always present: pinned, or a 26 px rail that opens on hover.
- Only a node's first prerequisite is drawn permanently; the rest appear on hover.
- Positions in `data/utopia/utopia/trees/create.json` are hand-authored and have zero
  crossings among the drawn edges. Never run the auto layout over that file.

Rendering rules learned the hard way:

- `DrawContext.drawTexture` does not enable blending in 1.20.1; alpha from
  `setShaderColor` is silently lost without `RenderSystem.enableBlend()`.
- Depth order: item icons render at z≈150, map overlays (outlines, badges, names) at
  200, the inspector at 300, the context menu at 600. Whatever covers the map must lie
  higher than what it covers, or items show through it.
- Round screen positions with `SmoothPainter.snap` (physical pixels), not to whole GUI
  pixels, or the map moves in 3-pixel jumps at GUI scale 3.
- Key presses go to the open screen, not to client tick events. Check key bindings
  with `matchesKey` inside the screen.
- The state outlines form a brightness ladder with measured contrast against the map
  (locked 3.7:1 up to buyable 10.6:1). Recompute it when the ground or a colour changes.

## Known gaps

- `de_de.json` is complete (232 of 232 keys). The `levelz` namespace has no German file.
  German node names are cut at the default map zoom (44 of 68, English 10), because
  the label wrap breaks only at spaces; `tools/LabelFit.java` measures it.
- Character creation takes skill names and the summary of earlier choices from ids,
  so they stay English, and shows fractional attributes as rounded fractions
  ("+0.2" for 15 percent).
- `SurvivalTrinketSlotMixin` warns about two `@Shadow` fields. Fix it with
  `@Shadow(remap = false)` on those fields, not on the class: `canInsert` overrides a
  Minecraft method and needs the mapping.
- Unlocking a node plays no sound.
