# Erzeugt Texturen, Modelle und Blockstates der Drucktanks aus Creates Fluessigkeitstank.
#
# Aufruf aus "Create Industrial Pressure/":
#   python3 -I tools/tank_assets.py <create-assets> tools/map_copper.json tools/map_netherite.json src/main/resources
# <create-assets> ist assets/create aus dem ausgepackten Create-JAR (6.0.8.1). Braucht ImageMagick (convert).
# Die Kupfertoene werden mit derselben Tabelle wie Rohre und Ventile umgefaerbt; das Glas der Fenster bleibt.
import json, os, re, subprocess, sys

create_assets, map_copper, map_netherite, res = sys.argv[1:5]
TEX = os.path.join(create_assets, "textures/block")
OUT_TEX = os.path.join(res, "assets/createhp/textures/block/pressure_tank")
os.makedirs(OUT_TEX, exist_ok=True)

def load(path):
    out = subprocess.run(["convert", path, "-depth", "8", "txt:-"], capture_output=True, text=True, check=True).stdout
    lines = out.splitlines()
    w, h = map(int, re.match(r"# ImageMagick pixel enumeration: (\d+),(\d+)", lines[0]).groups())
    px = {}
    for line in lines[1:]:
        m = re.match(r"\s*(\d+),(\d+):\s*\(([^)]*)\)", line)
        if m:
            x, y = int(m.group(1)), int(m.group(2))
            v = [int(round(float(c))) for c in m.group(3).split(",")]
            if len(v) == 3:
                v.append(255)
            px[(x, y)] = tuple(v)
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

# Create-Textur -> unser Name (pressure_tank/<material>_<name>.png)
TEXTURES = {
    "fluid_tank": "tank", "fluid_tank_connected": "tank_connected",
    "fluid_tank_top": "top", "fluid_tank_top_connected": "top_connected",
    "fluid_tank_inner": "inner", "fluid_tank_inner_connected": "inner_connected",
    "fluid_tank_window": "window", "fluid_tank_window_single": "window_single",
}

written = []
for material, mp in MATERIAL.items():
    for src, name in TEXTURES.items():
        w, h, px = load(os.path.join(TEX, src + ".png"))
        out = {}
        for k, (r, g, b, a) in px.items():
            c = mp.get((r, g, b), (r, g, b)) if a else (r, g, b)
            out[k] = (c[0], c[1], c[2], a)
        save(os.path.join(OUT_TEX, "%s_%s.png" % (material, name)), w, h, out)
        written.append("%s_%s.png" % (material, name))

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")

BLOCKS = {"pressure_tank": "copper", "netherite_pressure_tank": "netherite"}
MODELS = os.path.join(res, "assets/createhp/models")
cvariants = json.load(open(os.path.join(create_assets, "blockstates/fluid_tank.json")))["variants"]
shapes = sorted({v["model"].split("/")[-1] for v in cvariants.values()})

for block, material in BLOCKS.items():
    t = lambda n: "createhp:block/pressure_tank/%s_%s" % (material, n)
    # Creates Tankmodelle: 0 Deckel, 1 Seite, 3 Fenster, 4 Innenseite, 5 Fenster eines Einzeltanks
    for shape in shapes:
        write_json(os.path.join(MODELS, "block/%s/%s.json" % (block, shape)), {
            "parent": "create:block/fluid_tank/" + shape,
            "textures": {"0": t("top"), "1": t("tank"), "3": t("window"), "4": t("inner"),
                         "5": t("window_single"), "particle": t("tank")},
        })
    write_json(os.path.join(MODELS, "item/%s.json" % block), {"parent": "createhp:block/%s/block_single_window" % block})
    variants = {k: dict(v, model=v["model"].replace("create:block/fluid_tank/", "createhp:block/%s/" % block))
                for k, v in cvariants.items()}
    write_json(os.path.join(res, "assets/createhp/blockstates/%s.json" % block), {"variants": dict(sorted(variants.items()))})

print(len(written), "textures,", len(shapes), "shapes")
