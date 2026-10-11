# Zeichnet die Texturen der Dampfturbine und schreibt ihre Modelle und Blockstates.
# Eigene Pixel, nicht von Create abgeleitet. Aufruf aus "Create Industrial Pressure/":
#   python3 -I tools/turbine_assets.py src/main/resources
# Braucht ImageMagick (convert). Zufall mit festem Startwert, damit jeder Lauf dieselben Dateien ergibt.
import json, math, os, random, subprocess, sys

res = sys.argv[1]
TEX = os.path.join(res, "assets/createhp/textures/block/turbine")

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

# Je Werkstoff fuenf Toene von hell nach dunkel.
PAL = {
    "copper": [H("E3826C"), H("C26B4C"), H("A75A40"), H("904931"), H("793B2B")],
    "netherite": [H("6E696E"), H("57525A"), H("4A444C"), H("3B363D"), H("2A262B")],
}
BR = [H("FBDC7D"), H("E4B763"), H("D7AA5E"), H("BF8E55"), H("9E6947")]
DARK = [H("2A2A2E"), H("1A1A1D"), H("0E0E10")]
WATER = [H("6FA4F2"), H("3F76E4"), H("2F5CB8")]

def casing(p, seed):
    """Platte mit zweifachem Rahmen, Naht in der Mitte und vier Nieten."""
    r = random.Random(seed)
    px = [[p[2]] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if x == 0 or y == 0:
                c = p[0]
            elif x == 15 or y == 15:
                c = p[4]
            elif x == 1 or y == 1:
                c = p[1]
            elif x == 14 or y == 14:
                c = p[3]
            elif y == 7:
                c = p[3]
            elif y == 8:
                c = p[1]
            else:
                c = p[2] if r.random() > 0.18 else p[1 if r.random() > 0.5 else 3]
            px[y][x] = c
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[y][x] = p[0]
        px[y + 1][x] = p[4]
    return px

def port(p, seed, ring, inner):
    """Gehaeuse mit rundem Anschluss: Ring aus `ring`, Oeffnung aus `inner` (von aussen nach innen)."""
    px = casing(p, seed)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 2.2:
                px[y][x] = inner[2]
            elif d <= 3.6:
                px[y][x] = inner[1]
            elif d <= 4.4:
                px[y][x] = inner[0]
            elif d <= 6.0:
                px[y][x] = ring[1] if (x + y) % 5 else ring[0]
                if x + y > 17 and d > 5.2:
                    px[y][x] = ring[3]
    return px

def grate(p, seed):
    """Gehaeuse mit Ablaufgitter: drei Schlitze, unten im Schlitz Wasser."""
    px = casing(p, seed)
    for y in range(3, 13):
        for x in range(3, 13):
            px[y][x] = BR[3] if (x in (3, 12) or y in (3, 12)) else BR[2]
    for top in (4, 7, 10):
        for x in range(4, 12):
            px[top][x] = DARK[1]
            px[top + 1][x] = WATER[1] if x % 3 else WATER[0]
    return px

def rotor_side(p):
    """Schraeg gestellte Schaufeln um eine Welle; die Achse laeuft senkrecht durch die Textur."""
    px = [[DARK[1]] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if 6 <= x <= 9:
                px[y][x] = BR[3] if x in (6, 9) else BR[2]
            elif (x + y) % 4 < 2:
                px[y][x] = p[1] if (x + y) % 4 == 0 else p[3]
            else:
                px[y][x] = DARK[0] if x in (0, 15) else DARK[1]
    return px

def rotor_end(p):
    """Rotor von vorn: Nabe aus Messing, acht Schaufeln, dunkler Spalt zum Rand."""
    px = [[DARK[2]] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            a = (math.degrees(math.atan2(dy, dx)) + 360) % 45
            if d <= 2.3:
                px[y][x] = BR[1] if d < 1.2 else BR[3]
            elif d <= 7.2 and (a < 14 or a > 40):
                px[y][x] = p[1] if a < 7 else p[3]
            elif d > 7.2:
                px[y][x] = DARK[0]
    return px

for mat, p in PAL.items():
    save("%s_casing" % mat, casing(p, 31 if mat == "copper" else 37))
    save("%s_rotor_side" % mat, rotor_side(p))
    save("%s_rotor_end" % mat, rotor_end(p))
    save("%s_steam_inlet" % mat, port(p, 41 if mat == "copper" else 43, BR, DARK))
save("condensate_outlet", grate(PAL["copper"], 47))

def output_front():
    """Vorderplatte des Abtriebs: Messinglager um das Wellenloch; die Mitte bleibt im Modell offen."""
    px = casing(PAL["copper"], 53)
    for y in range(16):
        for x in range(16):
            if 3 <= x <= 12 and 3 <= y <= 12:
                px[y][x] = BR[0] if x == 3 or y == 3 else BR[3] if x == 12 or y == 12 else BR[2]
    return px

def output_recess():
    """Grund des Wellenlochs."""
    px = [[DARK[1]] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if x in (5, 10) or y in (5, 10):
                px[y][x] = DARK[0]
    return px

save("output_front", output_front())
save("output_recess", output_recess())

def write_json(path, data):
    path = os.path.join(res, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")

def tex(name):
    return "createhp:block/turbine/" + name

def simple(block, texture):
    write_json("assets/createhp/models/block/turbine/%s.json" % block,
               {"parent": "block/cube_all", "textures": {"all": tex(texture)}})
    write_json("assets/createhp/models/item/%s.json" % block, {"parent": "createhp:block/turbine/" + block})
    write_json("assets/createhp/blockstates/%s.json" % block,
               {"variants": {"": {"model": "createhp:block/turbine/" + block}}})

for mat, prefix in (("copper", ""), ("netherite", "netherite_")):
    simple(prefix + "turbine_casing", mat + "_casing")
    simple(prefix + "steam_inlet", mat + "_steam_inlet")
    rotor = prefix + "turbine_rotor"
    write_json("assets/createhp/models/block/turbine/%s.json" % rotor,
               {"parent": "block/cube_column", "textures": {"end": tex(mat + "_rotor_end"), "side": tex(mat + "_rotor_side")}})
    write_json("assets/createhp/models/item/%s.json" % rotor, {"parent": "createhp:block/turbine/" + rotor})
    model = "createhp:block/turbine/" + rotor
    write_json("assets/createhp/blockstates/%s.json" % rotor, {"variants": {
        "axis=x": {"model": model, "x": 90, "y": 90}, "axis=y": {"model": model}, "axis=z": {"model": model, "x": 90}}})
simple("condensate_outlet", "condensate_outlet")

def face(texture, uv, cull=None):
    f = {"texture": texture, "uv": uv}
    if cull:
        f["cullface"] = cull
    return f

# Abtrieb, Modell zeigt nach Norden (-Z): Vorderplatte 2 Pixel stark mit 6x6-Loch, dahinter der Koerper.
plate = []
for frm, to in (([0, 0, 0], [16, 5, 2]), ([0, 11, 0], [16, 16, 2]), ([0, 5, 0], [5, 11, 2]), ([11, 5, 0], [16, 11, 2])):
    x1, y1, _ = frm
    x2, y2, _ = to
    uv = [x1, 16 - y2, x2, 16 - y1]
    faces = {"north": face("#front", [16 - x2, 16 - y2, 16 - x1, 16 - y1], "north")}
    if y1 == 0:
        faces["down"] = face("#side", [x1, 14, x2, 16], "down")
    else:
        faces["down"] = face("#recess", [x1, 0, x2, 2])
    if y2 == 16:
        faces["up"] = face("#side", [x1, 0, x2, 2], "up")
    else:
        faces["up"] = face("#recess", [x1, 0, x2, 2])
    if x1 == 0:
        faces["west"] = face("#side", [0, 16 - y2, 2, 16 - y1], "west")
    else:
        faces["west"] = face("#recess", [0, 16 - y2, 2, 16 - y1])
    if x2 == 16:
        faces["east"] = face("#side", [14, 16 - y2, 16, 16 - y1], "east")
    else:
        faces["east"] = face("#recess", [14, 16 - y2, 16, 16 - y1])
    plate.append({"from": frm, "to": to, "faces": faces})
body = {"from": [0, 0, 2], "to": [16, 16, 16], "faces": {
    "north": face("#recess", [0, 0, 16, 16]),
    "south": face("#side", [0, 0, 16, 16], "south"),
    "east": face("#side", [0, 0, 14, 16], "east"), "west": face("#side", [2, 0, 16, 16], "west"),
    "up": face("#side", [0, 2, 16, 16], "up"), "down": face("#side", [0, 0, 16, 14], "down")}}
write_json("assets/createhp/models/block/turbine/turbine_output.json", {
    "parent": "block/block",
    "textures": {"front": tex("output_front"), "side": tex("copper_casing"), "recess": tex("output_recess"),
                 "particle": tex("copper_casing")},
    "elements": plate + [body]})
write_json("assets/createhp/models/item/turbine_output.json", {"parent": "createhp:block/turbine/turbine_output"})
rot = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
write_json("assets/createhp/blockstates/turbine_output.json",
           {"variants": {"facing=" + f: dict({"model": "createhp:block/turbine/turbine_output"}, **r) for f, r in rot.items()}})
print("ok")
