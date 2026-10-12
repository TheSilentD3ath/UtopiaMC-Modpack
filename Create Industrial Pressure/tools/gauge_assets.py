# Zeichnet die Texturen der Druckmesser und schreibt ihre Modelle und Blockstates.
# Eigene Pixel, nicht von Create abgeleitet. Aufruf aus "Create Industrial Pressure/":
#   python3 -I tools/gauge_assets.py src/main/resources
# Braucht ImageMagick (convert).
import json, math, os, subprocess, sys

res = sys.argv[1]
TEX = os.path.join(res, "assets/createhp/textures/block/pressure_gauge")

def save(name, px):
    path = os.path.join(TEX, name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    body = ["# ImageMagick pixel enumeration: 16,16,255,srgba"]
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[y][x]
            body.append("%d,%d: (%d,%d,%d,%d)" % (x, y, r, g, b, a))
    subprocess.run(["convert", "txt:-", "PNG32:" + path], input="\n".join(body), text=True, check=True)

H = lambda s, a=255: (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)

# Die Toene der Druckrohre (pipes.png, netherite_pipes.png), damit der Druckmesser zu seiner Leitung passt; das
# Kupfer-Hochdruckrohr ist tuerkis.
PAL = {
    "copper": [H("66BDE3"), H("60B2D6"), H("57A1C2"), H("4B8BA7"), H("407890")],
    "netherite": [H("685F64"), H("62585E"), H("574E53"), H("484045"), H("413A3F")],
}
FACE = [H("F4EEDC"), H("E2D9C0"), H("C9BEA0")]
DARK = [H("2A2A2E"), H("1A1A1D")]
RED = H("C8322D")
CLEAR = (0, 0, 0, 0)

def dial(p):
    """Zifferblatt im Bereich 3..12: Metallrand, helles Blatt, Teilstriche, rotes Feld oben rechts, Zeiger."""
    px = [[CLEAR] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if not (3 <= x <= 12 and 3 <= y <= 12):
                continue
            edge = x in (3, 12) or y in (3, 12)
            if edge:
                px[y][x] = p[0] if x + y < 15 else p[3]
            elif x in (4, 11) or y in (4, 11):
                px[y][x] = p[2]
            else:
                px[y][x] = FACE[0] if (x + y) % 5 else FACE[1]
    # Zwei Teilstriche links und oben, das rote Feld der Grenze oben rechts, Zeiger von der Nabe nach oben links.
    for (x, y) in ((5, 7), (7, 5)):
        px[y][x] = DARK[0]
    for (x, y) in ((9, 5), (10, 5), (10, 6)):
        px[y][x] = RED
    for (x, y) in ((7, 7), (6, 6)):
        px[y][x] = DARK[1]
    px[8][8] = DARK[0]
    px[8][7] = DARK[0]
    return px

def collar(p):
    """Ober- und Unterseite des Gehaeuses: Metallring um das Rohr."""
    px = [[CLEAR] * 16 for _ in range(16)]
    for y in range(3, 13):
        for x in range(3, 13):
            ring = max(abs(x - 7.5), abs(y - 7.5))
            px[y][x] = p[1] if ring > 3.5 else p[3] if ring > 2.5 else DARK[0]
    return px

def pipe(p):
    """Rohr: helle Kante, dunkle Kante, Mittelstreifen. Die Spalten 4..11 werden verwendet."""
    px = [[CLEAR] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(4, 12):
            px[y][x] = p[0] if x == 4 else p[4] if x == 11 else p[1] if (y // 2) % 2 else p[2]
    return px

def pipe_end(p):
    px = [[CLEAR] * 16 for _ in range(16)]
    for y in range(4, 12):
        for x in range(4, 12):
            px[y][x] = p[1] if x in (4, 11) or y in (4, 11) else DARK[1]
    return px

for mat, p in PAL.items():
    save("%s_dial" % mat, dial(p))
    save("%s_collar" % mat, collar(p))
    save("%s_pipe" % mat, pipe(p))
    save("%s_pipe_end" % mat, pipe_end(p))

def write_json(path, data):
    path = os.path.join(res, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")

def face(texture, uv, cull=None):
    f = {"texture": texture, "uv": uv}
    if cull:
        f["cullface"] = cull
    return f

# Modell entlang Y: Rohr von unten nach oben, in der Mitte das Gehaeuse mit vier Zifferblaettern.
for mat, block in (("copper", "pressure_gauge"), ("netherite", "netherite_pressure_gauge")):
    t = lambda n: "createhp:block/pressure_gauge/%s_%s" % (mat, n)
    model = {
        "parent": "block/block",
        "textures": {"pipe": t("pipe"), "end": t("pipe_end"), "dial": t("dial"), "collar": t("collar"),
                     "particle": t("collar")},
        "elements": [
            {"from": [4, 0, 4], "to": [12, 16, 12], "faces": {
                "north": face("#pipe", [4, 0, 12, 16]), "south": face("#pipe", [4, 0, 12, 16]),
                "east": face("#pipe", [4, 0, 12, 16]), "west": face("#pipe", [4, 0, 12, 16]),
                "up": face("#end", [4, 4, 12, 12], "up"), "down": face("#end", [4, 4, 12, 12], "down")}},
            {"from": [3, 3, 3], "to": [13, 13, 13], "faces": {
                "north": face("#dial", [3, 3, 13, 13]), "south": face("#dial", [3, 3, 13, 13]),
                "east": face("#dial", [3, 3, 13, 13]), "west": face("#dial", [3, 3, 13, 13]),
                "up": face("#collar", [3, 3, 13, 13]), "down": face("#collar", [3, 3, 13, 13])}},
        ],
    }
    write_json("assets/createhp/models/block/pressure_gauge/%s.json" % block, model)
    write_json("assets/createhp/models/item/%s.json" % block, {"parent": "createhp:block/pressure_gauge/" + block})
    m = "createhp:block/pressure_gauge/" + block
    write_json("assets/createhp/blockstates/%s.json" % block, {"variants": {
        "axis=x": {"model": m, "x": 90, "y": 90}, "axis=y": {"model": m}, "axis=z": {"model": m, "x": 90}}})
print("ok")
