# Erzeugt Texturen, Modelle und Blockstates der Druckventile aus Creates Fluessigkeitsventil.
#
# Aufruf aus "Create Industrial Pressure/":
#   python3 -I tools/valve_assets.py <create-assets> tools/map_copper.json tools/map_netherite.json src/main/resources
# <create-assets> ist assets/create aus dem ausgepackten Create-JAR (6.0.8.1). Braucht ImageMagick (convert).
# Die Farbtabellen hat tools/palette.py aus Creates Rohrtexturen und unseren umgefaerbten abgeleitet:
#   python3 -I tools/palette.py <create pipes.png> <unsere pipes.png> <create pipes_connected.png> <unsere ...>
import json, os, re, subprocess, sys

create_assets, map_copper, map_netherite, res = sys.argv[1:5]
TEX = os.path.join(create_assets, "textures/block")
OUT_TEX = os.path.join(res, "assets/createhp/textures/block/pressure_valve")
os.makedirs(OUT_TEX, exist_ok=True)

def load(path):
    out = subprocess.run(["convert", path, "-depth", "8", "txt:-"], capture_output=True, text=True, check=True).stdout
    lines = out.splitlines()
    w, h = map(int, re.match(r"# ImageMagick pixel enumeration: (\d+),(\d+)", lines[0]).groups())
    px = {}
    for line in lines[1:]:
        m = re.match(r"(\d+),(\d+):\((\d+),(\d+),(\d+),(\d+)\)", line.replace(" ", ""))
        if m:
            x, y, r, g, b, a = map(int, m.groups())
            px[(x, y)] = (r, g, b, a)
    return w, h, px

def save(path, w, h, px):
    body = ["# ImageMagick pixel enumeration: %d,%d,255,srgba" % (w, h)]
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[(x, y)]
            body.append("%d,%d: (%d,%d,%d,%d)" % (x, y, r, g, b, a))
    subprocess.run(["convert", "txt:-", "PNG32:" + path], input="\n".join(body), text=True, check=True)

def hexmap(d):
    return {tuple(int(k[i:i + 2], 16) for i in (0, 2, 4)): tuple(int(v[i:i + 2], 16) for i in (0, 2, 4)) for k, v in d.items()}

MATERIAL = {
    "copper": hexmap(json.load(open(map_copper))["map"]),
    "netherite": hexmap(json.load(open(map_netherite))["map"]),
}

def recolor(px, *maps):
    out = {}
    for k, (r, g, b, a) in px.items():
        c = (r, g, b)
        if a:
            for m in maps:
                if c in m:
                    c = m[c]
                    break
        out[k] = (c[0], c[1], c[2], a)
    return out

H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))

# Schlau: der cremefarbene Grund der Anzeige wird Messing, hell auf hell (Creates Messingblock).
SMART = {H(a): H(b) for a, b in [("F9F2DE", "FFEB8C"), ("F6EDD8", "FBDC7D"), ("F2E9D3", "F7CB6C"),
                                   ("EEE4CE", "EEC168"), ("E0D2AE", "E4B763"), ("D4C2A4", "D7AA5E")]}
# Regler regelt: die gruenen Balken werden gelb.
GREEN_TO_YELLOW = {H(a): H(b) for a, b in [("43A550", "D9A400"), ("43C652", "F5C91B"), ("42874B", "A87F06")]}
# Ueberdruckventil laesst ab: die roten Balken werden orange.
RED_TO_ORANGE = {H(a): H(b) for a, b in [("B70000", "E0650A"), ("CD0000", "FF8A1C"), ("A50000", "B84E06")]}

# Handrad je Ventilart, damit man die drei auch im Ruhezustand unterscheidet.
HANDLE = {"shutoff": None, "regulator": H("D9A62E"), "relief": H("B3261E")}
HANDLE_DARK = H("222227")

KINDS = {
    # Art: (Zustand bei ENABLED=false, Zustand bei ENABLED=true, Quelle und Umfaerbung je Zustand)
    "shutoff": ("closed", "open", {"closed": ("valve_closed", {}), "open": ("valve_open", {})}),
    "regulator": ("idle", "active", {"idle": ("valve_open", {}), "active": ("valve_open", GREEN_TO_YELLOW)}),
    "relief": ("idle", "venting", {"idle": ("valve_open", {}), "venting": ("valve_closed", RED_TO_ORANGE)}),
}

BLOCKS = {
    "pressure_valve": ("copper", "shutoff"),
    "netherite_pressure_valve": ("netherite", "shutoff"),
    "pressure_regulator": ("copper", "regulator"),
    "netherite_pressure_regulator": ("netherite", "regulator"),
    "relief_valve": ("copper", "relief"),
    "netherite_relief_valve": ("netherite", "relief"),
}

written = []

# Regler und Ueberdruckventil haben keine Welle. Statt Creates Wellenloch bekommen ihre Seiten ein Manometer,
# dort sitzt auch das Wertefeld. Eigene Pixel: heller Zifferblattgrund, Skalenstriche, roter Zeiger.
DIAL_INNER = {H("030303"), H("1A1919"), H("3C3B3B")}
GAUGE = {"W": H("EEE6CF"), "S": H("CFC3A2"), "T": H("5A5A5A"), "N": H("B3261E"), "H": H("2E2E2E")}
# Innenflaeche des Lochs (lokal 3..8), Zeile fuer Zeile; "." laesst den Grund wie er ist.
GAUGE_ROWS = [
    "STWWTS",
    "TWWWNW",
    "WWWNWW",
    "WWHWWW",
    "TWWWWT",
    "SWWWWS",
]

def draw_gauge(px, ox=18, oy=2):
    for y in range(2, 10):
        for x in range(2, 10):
            if px[(ox + x, oy + y)][:3] in DIAL_INNER and not (3 <= x <= 8 and 3 <= y <= 8):
                px[(ox + x, oy + y)] = GAUGE["S"] + (255,)
    for j, row in enumerate(GAUGE_ROWS):
        for i, ch in enumerate(row):
            if ch != ".":
                px[(ox + 3 + i, oy + 3 + j)] = GAUGE[ch] + (255,)

# Koerper
w, h, body = load(os.path.join(TEX, "fluid_valve.png"))
for material, mp in MATERIAL.items():
    for kind, handle in HANDLE.items():
        px = recolor(body, mp)
        if handle:
            for x in range(4, 11):
                if body[(x, 15)][:3] == HANDLE_DARK:
                    px[(x, 15)] = handle + (255,)
        if kind != "shutoff":
            draw_gauge(px)
        name = "%s_%s_body.png" % (material, kind)
        save(os.path.join(OUT_TEX, name), w, h, px)
        written.append(name)

# Anzeigen
for material, mp in MATERIAL.items():
    for kind, (off, on, states) in KINDS.items():
        for state, (src, extra) in states.items():
            w, h, ind = load(os.path.join(TEX, src + ".png"))
            for smart in (False, True):
                maps = [extra] + ([SMART] if smart else []) + [mp]
                px = recolor(ind, *maps)
                name = "%s_%s_%s%s.png" % (material, kind, state, "_smart" if smart else "")
                save(os.path.join(OUT_TEX, name), w, h, px)
                written.append(name)

# Leere Textur: blendet im Item-Modell die Welle aus, die Regler und Ueberdruckventil nicht haben.
save(os.path.join(OUT_TEX, "blank.png"), 16, 16, {(x, y): (0, 0, 0, 0) for x in range(16) for y in range(16)})

# Modelle
MODELS = os.path.join(res, "assets/createhp/models")
def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")

def tex(name):
    return "createhp:block/pressure_valve/" + name

for material in MATERIAL:
    for kind in KINDS:
        write_json(os.path.join(MODELS, "block/pressure_valve/%s_%s_pointer.json" % (material, kind)), {
            "parent": "create:block/fluid_valve/pointer",
            "textures": {"2": tex("%s_%s_body" % (material, kind)), "particle": tex("%s_%s_body" % (material, kind))},
        })

# Creates Blockstate-Tabelle: Modellrichtung und Drehung je Zustand
cvariants = json.load(open(os.path.join(create_assets, "blockstates/fluid_valve.json")))["variants"]
for block, (material, kind) in BLOCKS.items():
    off, on, states = KINDS[kind]
    bodytex = tex("%s_%s_body" % (material, kind))
    for orient in ("horizontal", "vertical"):
        for enabled in (False, True):
            state = on if enabled else off
            for smart in (False, True):
                write_json(os.path.join(MODELS, "block/%s/block_%s_%s%s.json" % (block, orient, state, "_smart" if smart else "")), {
                    "parent": "create:block/fluid_valve/block_%s_%s" % (orient, "open" if enabled else "closed"),
                    "textures": {"2": bodytex, "3": tex("%s_%s_%s%s" % (material, kind, state, "_smart" if smart else "")),
                                 "particle": bodytex},
                })
    item_tex = {"2": bodytex, "4": tex("%s_%s_%s" % (material, kind, off if kind == "shutoff" else off)), "particle": bodytex}
    if kind == "shutoff":
        item_tex["4"] = tex("%s_%s_open" % (material, kind))
    else:
        item_tex["1_0"] = tex("blank")
        item_tex["1_1"] = tex("blank")
    write_json(os.path.join(MODELS, "item/%s.json" % block), {"parent": "create:block/fluid_valve/item", "textures": item_tex})

    variants = {}
    for key, v in cvariants.items():
        props = dict(p.split("=") for p in key.split(","))
        if props["waterlogged"] != "false":
            continue
        orient = "horizontal" if "horizontal" in v["model"] else "vertical"
        enabled = props["enabled"] == "true"
        state = on if enabled else off
        for smart in (False, True):
            nk = "axis_along_first=%s,enabled=%s,facing=%s,smart=%s" % (props["axis_along_first"], props["enabled"], props["facing"], str(smart).lower())
            nv = {"model": "createhp:block/%s/block_%s_%s%s" % (block, orient, state, "_smart" if smart else "")}
            for r in ("x", "y"):
                if r in v:
                    nv[r] = v[r]
            variants[nk] = nv
    write_json(os.path.join(res, "assets/createhp/blockstates/%s.json" % block), {"variants": dict(sorted(variants.items()))})

print(len(written), "textures")
print("\n".join(sorted(written)))
