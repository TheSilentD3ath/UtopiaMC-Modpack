# Erzeugt die Fenstertexturen der Glas-Druckrohre aus Creates Glasrohr (glass_fluid_pipe.png).
#
# Aufruf aus "Create Industrial Pressure/":
#   python3 -I tools/glass_pipe_assets.py <create-assets> tools/map_copper.json tools/map_netherite.json src/main/resources
# <create-assets> ist assets/create aus dem ausgepackten Create-JAR (6.0.8.1). Braucht ImageMagick (convert).
#
# Creates Fenster wirkt nur durch die hellen Glastoene an seinen Kanten wie Glas; das Fenster selbst ist leer, damit man
# die Fluessigkeit sieht. Die erste Fassung unserer Texturen hatte diese Glastoene mit umgefaerbt, ihr Fenster sah wie
# ein Loch aus. Hier bekommt das Kupfer die Rohrfarbe aus den Farbtabellen (wie die Ventile), das Glas aber eigene
# Glastoene, die sich von der Rohrfarbe abheben.
import json, os, re, subprocess, sys

create_assets, map_copper, map_netherite, res = sys.argv[1:5]
SRC = os.path.join(create_assets, "textures/block/glass_fluid_pipe.png")
OUT = os.path.join(res, "assets/createhp/textures/block")

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
    body = ["# ImageMagick pixel enumeration: 16,16,255,srgba"]
    for y in range(16):
        for x in range(16):
            body.append("%d,%d: (%d,%d,%d,%d)" % (x, y, *px[(x, y)]))
    subprocess.run(["convert", "txt:-", "PNG32:" + path], input="\n".join(body), text=True, check=True)

H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))
hexmap = lambda d: {H(k): H(v) for k, v in d.items()}

# Creates Glastoene, von hell nach dunkel.
CREATE_GLASS = [H("D0EAE9"), H("A8D0D9"), H("8BC1CD"), H("7BAEB7")]
# Auf dem tuerkisen Hochdruckrohr waeren Creates Glastoene kaum heller als das Rohr; dort ist das Glas weisser.
# Auf dem dunklen Netherit heben sich Creates Toene gut ab.
GLASS = {
    "copper": [H("EEF9FA"), H("CFEAEF"), H("B4DBE3"), H("A2CCD5")],
    "netherite": CREATE_GLASS,
}
TARGETS = {"copper": "glass_fluid_pipe.png", "netherite": "netherite_glass_fluid_pipe.png"}
MAPS = {"copper": map_copper, "netherite": map_netherite}

src = load(SRC)
for material, name in TARGETS.items():
    body = hexmap(json.load(open(MAPS[material]))["map"])
    glass = dict(zip(CREATE_GLASS, GLASS[material]))
    px = {}
    for k, (r, g, b, a) in src.items():
        c = (r, g, b)
        if a:
            if c in glass:
                c = glass[c]
            elif c in body:
                c = body[c]
            else:
                sys.exit("Unbekannter Ton %02X%02X%02X bei %s" % (r, g, b, k))
        else:
            c = (0, 0, 0)
        px[k] = (c[0], c[1], c[2], a)
    save(os.path.join(OUT, name), px)
print("ok")
