# Zeichnet die Texturen von Dampf und Dampfauslass und schreibt Modell und Blockstate des Auslasses.
# Eigene Pixel, nicht von Create abgeleitet. Aufruf aus "Create Industrial Pressure/":
#   python3 -I tools/steam_assets.py src/main/resources
# Braucht ImageMagick (convert). Zufall mit festem Startwert, damit jeder Lauf dieselben Dateien ergibt.
import json, os, random, subprocess, sys

res = sys.argv[1]
TEX = os.path.join(res, "assets/createhp/textures/block")

def save(path, w, h, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    body = ["# ImageMagick pixel enumeration: %d,%d,255,srgba" % (w, h)]
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[y][x]
            body.append("%d,%d: (%d,%d,%d,%d)" % (x, y, r, g, b, a))
    subprocess.run(["convert", "txt:-", "PNG32:" + path], input="\n".join(body), text=True, check=True)

H = lambda s, a=255: (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)

# Dampf: helle, leicht wolkige Flaeche, halb durchsichtig.
rnd = random.Random(1726)
def steam(w, h, seed):
    r = random.Random(seed)
    base = [["" for _ in range(w)] for _ in range(h)]
    out = []
    for y in range(h):
        row = []
        for x in range(w):
            v = 222 + r.randint(-14, 14) + (8 if (x + y // 2) % 7 == 0 else 0)
            v = max(190, min(250, v))
            row.append((v, v + 3 if v < 252 else v, min(255, v + 6), 170 + r.randint(-20, 20)))
        out.append(row)
    return out
save(os.path.join(TEX, "fluid/steam_still.png"), 16, 16, steam(16, 16, 11))
save(os.path.join(TEX, "fluid/steam_flow.png"), 16, 16, steam(16, 16, 12))

# Dampfauslass: Messingplatte mit Nieten, Kupferhals, Stutzen mit dunkler Oeffnung.
BR = [H("FBDC7D"), H("E4B763"), H("D7AA5E"), H("BF8E55"), H("9E6947"), H("724731")]
CU = [H("E3826C"), H("C26B4C"), H("A75A40"), H("904931"), H("793B2B")]
DARK = [H("2A2A2E"), H("1A1A1D"), H("0E0E10")]

def plate():
    px = [[BR[2]] * 16 for _ in range(16)]
    for i in range(16):
        px[0][i] = px[i][0] = BR[1]
        px[15][i] = px[i][15] = BR[4]
    for y in range(1, 15):
        for x in range(1, 15):
            px[y][x] = BR[2] if (x + y) % 5 else BR[3]
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[y][x] = BR[0]
        px[y + 1][x + 1 if x < 13 else x - 1] = BR[5]
    return px

def side():
    px = [[CU[1]] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            px[y][x] = CU[0] if y in (0, 1) else CU[3] if y in (14, 15) else CU[1] if (x // 2) % 2 else CU[2]
    return px

def front():
    px = [[CU[1]] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 6.5:
                px[y][x] = CU[0] if x + y < 15 else CU[3]
            elif d > 4.5:
                px[y][x] = CU[1] if (x + y) % 4 else CU[2]
            elif d > 3.5:
                px[y][x] = DARK[0]
            elif d > 2:
                px[y][x] = DARK[1]
            else:
                px[y][x] = DARK[2]
    return px

save(os.path.join(TEX, "steam_outlet/plate.png"), 16, 16, plate())
save(os.path.join(TEX, "steam_outlet/side.png"), 16, 16, side())
save(os.path.join(TEX, "steam_outlet/front.png"), 16, 16, front())

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")

def cube(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}

def face(tex, uv, cull=None):
    f = {"texture": tex, "uv": uv}
    if cull:
        f["cullface"] = cull
    return f

# Modell zeigt nach Norden (-Z); die Platte liegt an der Suedseite am Tank.
model = {
    "parent": "block/block",
    "textures": {"plate": "createhp:block/steam_outlet/plate", "side": "createhp:block/steam_outlet/side",
                 "front": "createhp:block/steam_outlet/front", "particle": "createhp:block/steam_outlet/plate"},
    "elements": [
        cube([2, 2, 14], [14, 14, 16], {
            "north": face("#plate", [2, 2, 14, 14]), "south": face("#plate", [2, 2, 14, 14], "south"),
            "east": face("#side", [0, 2, 2, 14]), "west": face("#side", [0, 2, 2, 14]),
            "up": face("#side", [2, 0, 14, 2]), "down": face("#side", [2, 0, 14, 2])}),
        cube([4, 4, 8], [12, 12, 14], {
            "east": face("#side", [2, 4, 8, 12]), "west": face("#side", [2, 4, 8, 12]),
            "up": face("#side", [4, 2, 12, 8]), "down": face("#side", [4, 2, 12, 8])}),
        cube([3, 3, 5], [13, 13, 8], {
            "north": face("#front", [3, 3, 13, 13]), "south": face("#side", [3, 3, 13, 13]),
            "east": face("#side", [8, 3, 11, 13]), "west": face("#side", [8, 3, 11, 13]),
            "up": face("#side", [3, 8, 13, 11]), "down": face("#side", [3, 8, 13, 11])}),
    ],
}
write_json(os.path.join(res, "assets/createhp/models/block/steam_outlet.json"), model)
write_json(os.path.join(res, "assets/createhp/models/item/steam_outlet.json"), {"parent": "createhp:block/steam_outlet"})
rot = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
write_json(os.path.join(res, "assets/createhp/blockstates/steam_outlet.json"),
           {"variants": {"facing=" + f: dict({"model": "createhp:block/steam_outlet"}, **r) for f, r in rot.items()}})
print("ok")
