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

## Phase 2: valves

Each valve comes in copper (256 bar) and netherite (1,024 bar), and each has a smart variant.

| Valve | Does | Smart variant |
| --- | --- | --- |
| shut-off valve | closes a pipe completely, like Create's fluid valve | opens and closes by redstone instead of rotation |
| pressure regulator | caps the pressure behind it at a set value and so lowers the flow | redstone strength 0 to 15 sets the value |
| relief valve | opens above a set pressure and lets fluid out until it is below | emits redstone while it vents |

- The set value is chosen like Create's value boxes (scroll on the valve).
- A regulator splits a run in two, like a pump does in Create: downstream it re-emits the
  lower of the upstream pressure and its set value. This keeps Create's flow model intact.
- Every valve is a Display Link source through the existing pressure source.

Open for the maintainer: how a valve becomes smart (proposal: right-click with an Electron
Tube, which Create uses for its "smart" parts), and where a relief valve's fluid goes (into the
world as fluid, or vented as particles and lost).

## Phase 3: pressure tanks

- Copper and netherite pressure tanks as multiblocks like Create's fluid tank, with a pressure
  limit and more capacity per block than Create's 8 buckets.
- Display Link: fill level, and pressure where a pump feeds them.

Open for the maintainer: capacity per block for each tier.

## Phase 4: steam

- Steam (`createhp:steam`) is a fluid. A pressure tank heated like a Create boiler and fed with
  water produces steam instead of driving engines directly; steam leaves through the tank's
  outlets into pipes.
- Pressure tanks can hold more boiler levels than Create's cap of 18, by tier.
- At the far end, steam fed into a tank that carries Create steam engines counts as boiler
  levels for those engines.
- The pressure of a steam line is that of its boiler, so it counts against pipe limits like
  pump pressure does.

Open for the maintainer: the boiler level cap per tier, and how many mB of steam one boiler
level is worth (Create uses 10 mB of water per tick per level).

## Textures

New blocks need textures in Create's layout. The pipes so far were recoloured from Create and
are listed in `LICENSE`; the same approach works for valves and tanks, unless they are to be
drawn as commissioned art.
