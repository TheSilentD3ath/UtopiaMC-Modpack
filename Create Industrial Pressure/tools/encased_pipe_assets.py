# Erzeugt Textur, Modell und Blockstate der ummantelten Druckrohre aus Creates ummanteltem Fluessigkeitsrohr.
#
# Aufruf aus "Create Industrial Pressure/":
#   python3 -I tools/encased_pipe_assets.py <create-assets> src/main/resources
# <create-assets> ist assets/create aus dem ausgepackten Create-JAR (6.0.8.1). Braucht ImageMagick (convert).
#
# Die geschlossenen Seiten nehmen Creates eigenes Modell mit Kupfergehaeuse (create:block/encased_fluid_pipe/block_flat),
# damit sie sich mit Gehaeusen daneben verbinden. Nur die offene Seite ist eigen: Creates Rahmen um die Oeffnung bleibt,
# in der Oeffnung sitzt die Rohrwand im Ton des Druckrohrs, damit man ein Hochdruck- oder Netherit-Rohr von Creates
# Kupferrohr (16 bar) unterscheiden kann, ohne die Brille aufzusetzen.
import json, os, re, subprocess, sys

create_assets, res = sys.argv[1:3]
SRC = os.path.join(create_assets, "textures/block/encased_pipe.png")
ASSETS = os.path.join(res, "assets/createhp")

def load(path):
    out = subprocess.run(["convert", path, "-depth", "8", "txt:-"], capture_output=True, text=True, check=True).stdout
    px = {}
    for line in out.splitlines()[1:]:
        m = re.match(r"(\d+),(\d+):\((\d+),(\d+),(\d+),(\d+)\)", line.replace(" ", ""))
        if m:
            x, y, r, g, b, a = map(int, m.groups())
            px[(x, y)] = (r, g, b, a)
    return px

def save(path, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    body = ["# ImageMagick pixel enumeration: 16,16,255,srgba"]
    for y in range(16):
        for x in range(16):
            body.append("%d,%d: (%d,%d,%d,%d)" % (x, y, *px[(x, y)]))
    subprocess.run(["convert", "txt:-", "PNG32:" + path], input="\n".join(body), text=True, check=True)

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")

H = lambda s: (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)

# Die Toene der Druckrohre wie in tools/gauge_assets.py, von hell nach dunkel; das Kupfer-Hochdruckrohr ist tuerkis.
PAL = {
    "high_pressure": [H("66BDE3"), H("60B2D6"), H("57A1C2"), H("4B8BA7"), H("407890")],
    "netherite": [H("685F64"), H("62585E"), H("574E53"), H("484045"), H("413A3F")],
}
HOLE = H("1C1418")

def open_face(src, p):
    """
    Creates Oeffnung ist 8 x 8 Pixel gross (4..11), so breit wie ein Rohr. Darin: die Rohrwand als Ring, oben und links
    im Licht, unten und rechts im Schatten, innen ein dunkles Loch.
    """
    px = dict(src)
    for y in range(4, 12):
        for x in range(4, 12):
            ring = x in (4, 11) or y in (4, 11)
            inner = x in (5, 10) or y in (5, 10)
            if ring:
                lit = x == 4 or y == 4
                shade = x == 11 or y == 11
                px[(x, y)] = p[2] if lit and shade else p[0] if lit else p[3]
            elif inner:
                px[(x, y)] = p[4]
            else:
                px[(x, y)] = HOLE
    return px

# Creates BlockStateGen.encasedPipe: je Richtung ein Teil, flach wenn zu, offen wenn auf.
ROTATION = {"down": {"x": -90}, "up": {"x": 90}, "north": {"y": 180}, "south": {}, "west": {"y": 90}, "east": {"y": 270}}

src = load(SRC)
for name, block in (("high_pressure", "encased_high_pressure_pipe"), ("netherite", "encased_netherite_pressure_pipe")):
    save(os.path.join(ASSETS, "textures/block/encased_pipe", name + ".png"), open_face(src, PAL[name]))
    texture = "createhp:block/encased_pipe/" + name
    write_json(os.path.join(ASSETS, "models/block", block, "block_open.json"), {
        "textures": {"0": texture, "particle": texture},
        "elements": [{"from": [0, 0, 15], "to": [16, 16, 16],
                      "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#0"}}}],
    })
    parts = []
    for opened in (False, True):
        model = "createhp:block/%s/block_open" % block if opened else "create:block/encased_fluid_pipe/block_flat"
        for d, rot in ROTATION.items():
            parts.append({"when": {d: "true" if opened else "false"}, "apply": dict({"model": model}, **rot)})
    write_json(os.path.join(ASSETS, "blockstates", block + ".json"), {"multipart": parts})
print("ok")
