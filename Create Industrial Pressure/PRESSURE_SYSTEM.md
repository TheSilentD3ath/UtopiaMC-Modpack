# Pressure system

Plan and state of the pressure system of Create: Industrial Pressure. Pipes, valves and tanks
hold a pressure limit; the player has to watch the pressure in a pipe run and keep it under
the limit with regulators and relief valves. Steam becomes a fluid that boilers push through
pipes to engines elsewhere.

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
| copper pressure pipes | 256 bar | bursts and drops |
| netherite pressure pipes | 1,024 bar | explodes, no drop |

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

## Phase 4: steam (planned, decisions 2026-10-11)

Steam is generated in boilers, moved by pumps through pipes and turned into rotation in a large
steam turbine.

- **Steam** (`createhp:steam`) is a fluid that only exists in pipes and tanks: no bucket, no
  world block.
- **Steam outlet** (Dampfauslass): a block attached to a boiler like a steam engine. It makes the tank
  a boiler and turns its boiler levels into steam, 10 mB per tick per level; levels used by steam
  engines on the same boiler are not available to it. Pipes take the steam from the outlet. Works on
  Create's tanks and on pressure tanks.
- **Pressure tank boilers:** up to 5 × 5 wide (Create's tank: 3 × 3), level cap 50 for copper and
  150 for netherite. Size: 1 level per block for copper, 2 for netherite (Create: 1 per 4 blocks).
  Netherite triples the heat of each burner, so a 5 × 5 base of superheated blaze burners gives
  50 levels in copper and 150 in netherite. Water supply still limits as in Create.
- **Transport:** steam moves with pumps like any fluid; pump pressure counts against the limits.
- **Steam turbine:** a multiblock with a 5 × 5 casing around a 3 × 3 rotor, length chosen by the
  player, in copper and netherite. Steam enters through an inlet in the casing; an output shaft at the
  front delivers rotation. Per 10 mB of steam per tick it delivers 1.5 times the output of a Create
  steam engine at full power (copper) or 2.5 times (netherite). Half of the steam leaves as water
  through a condensate outlet, so a boiler can be fed in a loop.
- **Turbine pressure:** the turbine holds its material's limit (256 / 1,024 bar); above it, the inlet
  bursts (copper) or the turbine explodes (netherite), like pipes.
- Not part of it (maintainer): steam fed into a pressure tank with Create's steam engines.

## Textures

New blocks need textures in Create's layout. Pipes, valves and tanks are recoloured from Create
and listed in `LICENSE`; valves and tanks, with their models and blockstates, come from
`tools/valve_assets.py` and `tools/tank_assets.py`. The pressure gauge on regulator and relief valve
is drawn by that script, not taken from Create.
