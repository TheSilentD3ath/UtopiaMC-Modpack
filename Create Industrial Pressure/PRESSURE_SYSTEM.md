# Pressure system

Plan and state of the pressure system of Create: Industrial Pressure. Pipes, valves and tanks
hold a pressure limit; the player has to watch the pressure in a pipe run and keep it under
the limit with regulators and relief valves. Steam becomes a fluid that pumps carry from boilers
through pipes to a large steam turbine.

## Decisions (maintainer, 2026-10-11)

- **Pressure model:** the pressure the pumps put into the network, plus a limit per part.
  There is no build-up when an outlet is blocked.
- **Netherite overload:** a real, TNT-like explosion. Block damage can be switched off in the
  server config; it is on by default.
- **Steam:** a fluid carried in pipes, from a boiler to engines elsewhere.
- **Old burst rule:** replaced. Pipes burst only above their limit; there is no longer a rule
  that every Create pipe in a high pressure network bursts.

Further decisions (maintainer, 2026-10-11):

1. A valve becomes smart by right-clicking it with an Electron Tube.
2. A relief valve vents into the world as a cloud. The fluid is lost; nothing is placed.
3. Pressure tank capacity per block: 32 buckets for copper, 128 for netherite.
4. Boiler level cap of pressure tanks: 50 for copper, 150 for netherite; one steam boiler level is
   worth 10 mB of steam per tick.
5. Textures: recoloured from Create, like the pipes.
6. Create's own fluid tanks hold 16 bar like Create's pipes, with a server config switch.
7. An overloaded pressure tank behaves like a pipe: copper bursts, netherite explodes.
8. Steam: steam outlet block on boilers, pumped like any fluid, large steam turbine as the consumer;
   details under Phase 4.

## Units and limits

The pressure is Create's own: every pipe connection holds an inbound and an outward value
that the pumps set when they build their network, and Create derives the flow rate from
it. The mod only reads these values. 16 Create pressure units are 1 bar.

| Source at 256 RPM | Create units | bar |
| --- | --- | --- |
| Create mechanical pump | 256 | 16 |
| tier 1 / 2 / 3 | 1,024 / 2,048 / 4,096 | 64 / 128 / 256 |
| tier 4 / 5 (netherite) | 8,192 / 16,384 | 512 / 1,024 |

| Part | Limit | On overload |
| --- | --- | --- |
| Create pipes, glass, encased and smart pipes, fluid valve | 16 bar | bursts and drops |
| copper pressure pipes, valves, gauges, tanks and steam inlets | 256 bar | bursts and drops |
| netherite pressure pipes, valves, gauges, tanks and steam inlets | 1,024 bar | explodes, no drop |

A part's pressure is the highest value on any of its connections. Pumps feeding the same pipe
add up. Each limit equals the matching pump at full speed, so a network of matching parts
never bursts; overload comes from a stronger pump on weaker parts or from several pumps on
one pipe.

## Phase 1: measure and limit (done)

- `Pressure` reads a pipe's pressure in bar and its limit; `PipeMaterial` holds the limits of
  the mod's pipes.
- Every running high pressure pump checks its network every 30 ticks (bounded by its reach).
  An overloaded part cracks for four checks, then bursts or explodes. Networks of Create pumps
  are never checked, so they behave exactly as in Create.
- Engineer's Goggles show the pressure and limit on the mod's pipes ("32 of 256 bar", gold
  from 75 percent, red above the limit with a warning) and the pump pressure on its pumps.
- Display Link source "Pipe Pressure" on the mod's pipes and pumps and on Create's pipes,
  valves and pump. Modes: load as a progress bar, load in percent, pressure in bar, limit.
- Server config `config/createhp-server.toml`: explosion block damage, explosion power,
  check interval, checks until a part bursts.

## Phase 2: valves (done)

Three valves, each in copper (256 bar) and netherite (1,024 bar). All are Create's fluid valve
underneath: same shape, orientation and pipe connections, Create's models as parents.

| Valve | Does | Smart variant (Electron Tube) |
| --- | --- | --- |
| shut-off valve | closes completely: no fluid and no pressure past it; opens by rotation like Create's valve | opens while powered by redstone; no shaft |
| pressure regulator | caps the pressure behind it at its set value and so lowers the flow | redstone strength 0 to 15 sets the value (15 = the valve's limit, 0 = closed) |
| relief valve | keeps its whole pipe run at or below its set value; the surplus is vented as a cloud and the fluid is lost | emits redstone 15 while it vents |

- **Where it acts.** Create sets pressure only in `PumpBlockEntity.distributePressureTo`.
  `PressureShaping` walks the pipes from the pump first and gives every pipe a cap; the pump's
  `addPressure` calls are then limited so that the sum at a connection stays under the cap. Create
  keeps computing with its own values. Behind a closed shut-off valve the cap is 0 (Create's own
  valve lets pressure through, so pipes behind a closed Create valve would still burst). Behind a
  regulator, seen from the pump, the cap is its set value. A relief valve caps its zone: everything
  reachable from it without passing a regulator or a closed shut-off valve.
- **Several pumps.** Each pump side enters its contribution at every valve it reaches; a pump side
  that no longer reaches a valve removes it, and contributions of pumps that are gone or stopped
  are dropped every second. A relief valve vents when the sum is above its set value, so two pumps
  of 64 bar each open a relief valve set to 100 bar.
- **Flow.** Create derives a network's flow once from the pressure at its source (pressure / 2 mB
  per tick). A regulator behind the pump would not change that, so `FluidNetworkMixin` caps the
  flow of every network that passes a regulator or a venting relief zone. Each network gets the
  share of its pump's contribution: two pumps through one regulator set to 16 bar deliver 128 mB/t
  together, not each. Venting draws the surplus (excess pressure / 2 mB per tick, shared the same
  way) from the network's source on top.
- **Set value.** Create's value panel on the two sides where the shut-off valve has its shaft;
  regulator and relief valve show a pressure gauge there. 256 steps: 1 bar per step on copper,
  4 bar on netherite. Defaults: regulator 16 bar (Create's pipe limit), relief valve its own limit.
- **Relief valve at a pipe end** (the usual place, at the end of a T-piece): not an open pipe end,
  so nothing spills. Create gives dead-end branches no pressure; the relief valve therefore shows
  the pressure of its run on the goggles and on Display Links.
- **Indicator.** The valve's indicator shows the state from the block's ENABLED property: shut-off
  open green / closed red, regulator idle green / regulating yellow, relief valve idle green /
  venting orange. The handle colour tells the kinds apart (dark, brass, red); smart valves have a
  brass indicator.
- **Goggles:** pressure and limit like a pipe, plus open/closed, set value and outlet, "venting
  N bar", and the smart behaviour. **Display Link:** the pipe pressure source works on all valves.
- **Overload:** valves count against their material's limit like pipes. A copper valve bursts and
  drops, a netherite valve explodes.
- **Recipes** (shapeless): shut-off valve = Create fluid valve + pressure pipe of the material;
  regulator = shut-off valve + precision mechanism; relief valve = shut-off valve + piston. A smart
  valve drops its Electron Tube when broken.

Limits of the model: Create computes one flow rate per network, so a regulator in one branch also
throttles the other branches of the same network. The pump contributions are not saved; after
loading, every regulator and relief valve makes its network redistribute once.

## Phase 3: pressure tanks (done)

- **High Pressure Tank** (copper): 32 buckets per block, limit 256 bar. **Netherite Pressure Tank**:
  128 buckets per block, limit 1,024 bar. Both are Create's fluid tank underneath: multiblocks up to
  3 × 3 and Create's height limit, windows by wrench, comparator output, boiler for steam engines,
  Create's tank item (places whole layers on wide tanks). Each material has its own block entity
  type, so copper and netherite tanks never merge into one tank.
- **Capacity.** Create reads it from a static method. `TankCapacityMixin` redirects its five calls
  in the tank block entity (creation, loading, resizing) for pressure tanks only; without it Create
  would cut every pressure tank back to 8 buckets per block when the world loads.
- **Pressure.** A tank has no pressure of its own in Create; the pipe connections feeding it carry
  it. A tank's pressure is the highest of those, over the whole multiblock. The high pressure pumps'
  check now also looks at tanks at the ends of their pipes: the tank block at an overloaded
  connection cracks and bursts (copper, drops itself, its share of the content is lost) or explodes
  (netherite), like pipes.
- **Create's tanks** now hold 16 bar, like Create's pipes (maintainer). The server config switch
  `createTanksLimited` turns this off for worlds with existing setups that fill Create tanks from high
  pressure pumps. The creative tank is never limited.
- **Goggles:** Create's tank info plus "Tank Pressure: N of M bar". **Display Link:** the pipe
  pressure source works on all tanks, Create's included; a new "Tank Fill Level" source shows fill as
  a bar, in percent, content or capacity in buckets, right at the tank (Create needs a Smart Observer
  for that). Pressure tanks also offer Create's boiler status source.
- **Recipes** (shaped): copper = four Create fluid tanks, four copper sheets, one sturdy sheet;
  netherite = four copper pressure tanks, four netherite scrap, one netherite ingot.

## Phase 4: steam (done)

Steam is generated in boilers, moved by pumps through pipes and turned into rotation in a large
steam turbine.

- **Steam** (`createhp:steam`) is a fluid that only exists in pipes and tanks: no bucket, no
  world block, lighter than air. It renders white in glass pipes and tank windows.
- **Steam outlet** (Dampfauslass): a block attached to a boiler like a steam engine. It makes the tank
  a boiler and turns its boiler levels into steam, 10 mB per tick per level; levels used by steam
  engines on the same boiler are not available to it, several outlets share the rest. Pumps take the
  steam from its front; it holds 8 buckets, enough for a netherite boiler's 1,500 mB/t. Works on
  Create's tanks and on pressure tanks.
- **Pressure tank boilers:** up to 5 × 5 wide (Create's tank: 3 × 3), level cap 50 for copper and
  150 for netherite. Size: 1 level per block for copper, 2 for netherite (Create: 1 per 4 blocks).
  Netherite triples the heat of each burner, so a 5 × 5 base of superheated blaze burners gives
  50 levels in copper and 150 in netherite. Water supply still limits as in Create. Above 18 levels
  the goggles' boiler bars are scaled to Create's 18. Create's own boilers keep Create's rules.
- **Transport:** steam moves with pumps like any fluid; pump pressure counts against the limits.
- **Steam turbine** (`content/turbine/`): a multiblock in copper or netherite.
  - Shape: the turbine output sits in the middle of a 5 × 5 front wall, its shaft facing out. Behind it
    come 1 to 16 rotor layers (3 × 3 rotor blocks inside a ring of 16 casing blocks), closed by a full
    5 × 5 back wall. Steam inlets and condensate outlets replace any casing block of the ring or the
    walls, as many as wanted. Casing, rotor and inlet exist per material and must all match; output
    and condensate outlet fit both.
  - The output checks the shape every 20 ticks. An incomplete turbine stops at once; its goggles say
    "incomplete" and name the first block that is missing or wrong, or that it is longer than 16.
  - Each rotor layer takes up to 100 mB of steam per tick (copper) or 200 (netherite). The output
    draws that much from the inlets each tick, at most.
  - Power: per 10 mB of steam per tick 1.5 times (copper) or 2.5 times (netherite) a Create steam
    engine at full load, which is 16,384 SU, whatever the speed; the speed is set by redstone (see
    Phase 5). The rate is the average over 20 ticks, so uneven pump delivery does not make the shaft
    network recompute every tick.
  - Condensate: half of the steam used comes out as water, shared evenly by the condensate outlets
    (8 buckets each), for pumps to draw and feed back into a boiler. Water that finds no room is
    lost; the turbine keeps running.
  - Pressure limit: the steam inlet holds its material's limit (256 / 1,024 bar). The high pressure
    pumps check it like a tank at the end of their pipe: copper cracks and bursts, netherite explodes.
  - Goggles: on the output material, rotor layers, speed, pressure needed, efficiency, steam used of
    the maximum, condensate, and Create's generator stats; on an inlet its pressure, content and whether it belongs to a complete
    turbine; on a condensate outlet its water.
  - Look: casing, inlets, condensate outlets and output have connected textures in the layout of
    Create's fluid tank (RECTANGLE, 4 × 4 tiles). A wall reads as one seamless plate: the only pattern
    inside is a sheen stripe that runs on across block edges, frame and rivets sit only at the outer
    edge; parts of the other material do not join. Output and condensate outlet fit both
    materials; the output switches them to the turbine's material (block state `netherite`) when it
    finds the turbine complete.
  - Recipes: casing (4) = copper casing + 4 copper sheets; netherite casing (8) = 8 casings +
    netherite ingot; rotor (2) = shaft + 4 copper sheets; netherite rotor (4) = 4 rotors + netherite
    ingot; inlet = casing + pressure pipe of the material; condensate outlet = casing + fluid pipe;
    output = casing + shaft + precision mechanism.
- Not part of it (maintainer): steam fed into a pressure tank with Create's steam engines.

Defaults chosen during the build, not decided by the maintainer: at most 16 rotor layers, water lost
when the condensate outlets are full, and the recipes above.

## Phase 5: control (done, decisions 2026-10-12)

The turbine is run by the player's own redstone control: they measure the pressure, regulate it up
and down and keep it below the limit.

- **Speed by redstone.** The signal at the turbine output (directly, or through an adjacent casing
  block powered by a lever, repeater or comparator) sets the speed: no signal = off, signal n =
  16 × n RPM, 15 = 256 RPM. An unpowered turbine draws no steam. Existing turbines without a signal
  stop. Speed changes act in the same tick; switching between off and on waits at least 10 ticks
  after the last switch, because Create destroys a block whose rotation starts and stops too often
  (5 points per switch, 1 point back per tick, broken above 128). Fast on/off control would otherwise
  break the output within seconds.
- **Pressure needed.** The turbine needs pressure at its inlets in proportion to its speed: three
  quarters of its limit at 256 RPM, so 0.75 bar per RPM in copper (192 bar at 256 RPM) and 3 bar per
  RPM in netherite (768 bar). The quarter above it is the margin before the inlet bursts.
- **Too little pressure.** Each inlet's steam counts with its own efficiency, its pressure divided by
  the need, at most 100 %. At half the need it gives half the power. An inlet with no pressure at all
  is not drawn from, so its steam is not wasted. The power stays that of the steam; the speed only
  divides it (capacity = SU / RPM).
- **Create divides pressure at branches.** A pump's pressure is split among parallel branches, so a
  T-piece in front of two inlets gives each less. Measured: 64 bar from the pump, 32 bar behind the
  T-piece on one branch.
- **Comparator on the steam inlet:** its pressure against the turbine's need, 15 = enough, any lower
  pressure at least 1, 0 without pressure or without a running turbine.
- **Pressure Gauge / Netherite Pressure Gauge:** a straight pipe piece (Create's straight pipe
  underneath) with a dial. It carries fluid like a pipe and holds its material's limit (bursts or
  explodes like a pipe). A comparator reads its pressure against its limit, 15 = limit reached, any
  pressure at least 1. Goggles show pressure, limit and the comparator value; the pipe pressure
  Display Link source works on it. Recipe: pressure pipe of the material + comparator.
- **One-time warning** (CIP, client): before the title screen on the first start, a screen explains
  the limits, bursting, netherite explosions, adding pumps and the tools to watch and regulate
  pressure. "Understood" stores `pressureWarningSeen = true` in `config/createhp-client.toml`; Escape
  does not close it. With Quick Play the game goes straight to a server; the warning then waits for
  the next time the title screen opens.

## Textures

New blocks need textures in Create's layout. Pipes, valves and tanks are recoloured from Create
and listed in `LICENSE`; valves and tanks, with their models and blockstates, come from
`tools/valve_assets.py` and `tools/tank_assets.py`. The pressure gauge on regulator and relief valve
is drawn by that script, not taken from Create. Steam, the steam outlet, the turbine parts and the
pressure gauges are drawn pixel by pixel in `tools/steam_assets.py`, `tools/turbine_assets.py` and
`tools/gauge_assets.py`; they are not derived from Create and are not listed in `LICENSE`. The gauges
take the colour values of the mod's pressure pipes so they match their line.
