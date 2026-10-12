# Zeichnet die Texturen der Dampfturbine und schreibt ihre Modelle und Blockstates.
# Eigene Pixel, nicht von Create abgeleitet. Aufruf aus "Create Industrial Pressure/":
#   python3 -I tools/turbine_assets.py src/main/resources
# Braucht ImageMagick (convert). Jeder Lauf ergibt dieselben Dateien.
import json, math, os, subprocess, sys

res = sys.argv[1]
TEX = os.path.join(res, "assets/createhp/textures/block/turbine")

def save(name, px):
    h, w = len(px), len(px[0])
    path = os.path.join(TEX, name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    body = ["# ImageMagick pixel enumeration: %d,%d,255,srgba" % (w, h)]
    for y in range(h):
        for x in range(w):
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

def tile(p, left=False, right=False, up=False, down=False):
    """
    Eine Kachel der Gehaeusewand: genietete Platte mit feiner Naht zu verbundenen Nachbarn, kraeftiger Rahmen nur
    an Seiten ohne Nachbarn. So wirkt eine Turbinenwand wie eine Flaeche aus Platten mit Rahmen am Aussenrand.
    Jede Kachel zeichnet nur die Naht oben und links; die rechte und untere kommt vom Nachbarn.
    """
    # Glatte Platte mit einem schwachen Glanzstreifen; in jeder Kachel gleich, damit die Wand ruhig wirkt.
    px = [[p[1] if (x + y) % 16 in (5, 6) else p[2] for x in range(16)] for y in range(16)]
    # Nahte zu Nachbarn: dunkle Fuge, darunter bzw. rechts davon eine helle Kante.
    if up:
        for i in range(16):
            px[0][i], px[1][i] = p[3], p[1]
    if left:
        for i in range(16):
            px[i][0], px[i][1] = p[3], p[1]
    # Rahmen ohne Nachbarn: drei Pixel, aussen hell (oben, links) bzw. dunkel (unten, rechts), innen Schatten.
    for i in range(16):
        if not up:
            px[0][i], px[1][i], px[2][i] = p[0], p[1], p[3]
        if not left:
            px[i][0], px[i][1], px[i][2] = p[0], p[1], p[3]
    for i in range(16):
        if not down:
            px[15][i], px[14][i], px[13][i] = p[4], p[3], p[1]
        if not right:
            px[i][15], px[i][14], px[i][13] = p[4], p[3], p[1]
    # Vier Nieten je Platte, knapp innerhalb von Rahmen oder Naht: hell oben links, Schatten unten rechts.
    for (x, y) in ((3, 3), (11, 3), (3, 11), (11, 11)):
        px[y][x], px[y][x + 1] = p[0], p[1]
        px[y + 1][x], px[y + 1][x + 1] = p[1], p[4]
    return px

def with_port(px):
    """Runder Anschluss in der Mitte: Messingring, dunkle Oeffnung."""
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 2.2:
                px[y][x] = DARK[2]
            elif d <= 3.6:
                px[y][x] = DARK[1]
            elif d <= 4.4:
                px[y][x] = DARK[0]
            elif d <= 6.0:
                px[y][x] = BR[3] if x + y > 17 and d > 5.2 else BR[1] if (x + y) % 5 else BR[0]
    return px

def with_grate(px):
    """Ablaufgitter: Messingrahmen, drei Schlitze, unten im Schlitz Wasser."""
    for y in range(3, 13):
        for x in range(3, 13):
            px[y][x] = BR[3] if (x in (3, 12) or y in (3, 12)) else BR[2]
    for top in (4, 7, 10):
        for x in range(4, 12):
            px[top][x] = DARK[1]
            px[top + 1][x] = WATER[1] if x % 3 else WATER[0]
    return px

def with_bearing(px):
    """Messinglager um das Wellenloch des Abtriebs; die Mitte bleibt im Modell offen."""
    for y in range(3, 13):
        for x in range(3, 13):
            px[y][x] = BR[0] if x == 3 or y == 3 else BR[3] if x == 12 or y == 12 else BR[2]
    return px

# Creates Verbindungsart RECTANGLE (wie beim Fluessigkeitstank): 4x4 Kacheln. Spalte: links/rechts verbunden,
# Zeile: oben/unten verbunden, in der Reihenfolge von AllCTTypes.RECTANGLE.getTextureIndex.
RECT_X = [(False, False), (False, True), (True, True), (True, False)]
RECT_Y = [(False, True), (True, True), (True, False), (False, False)]

def connected(make):
    px = [[None] * 64 for _ in range(64)]
    for ty, (up, down) in enumerate(RECT_Y):
        for tx, (left, right) in enumerate(RECT_X):
            t = make(left, right, up, down)
            for y in range(16):
                for x in range(16):
                    px[ty * 16 + y][tx * 16 + x] = t[y][x]
    return px

def ct(name, make):
    """Einzelner Block und Kachelbogen fuer verbundene Texturen; Create sucht den Bogen unter name_connected."""
    save(name, make(False, False, False, False))
    save(name + "_connected", connected(make))

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
    ct("%s_casing" % mat, lambda l, r, u, d, p=p: tile(p, l, r, u, d))
    ct("%s_steam_inlet" % mat, lambda l, r, u, d, p=p: with_port(tile(p, l, r, u, d)))
    save("%s_rotor_side" % mat, rotor_side(p))
    save("%s_rotor_end" % mat, rotor_end(p))
# Kondensatauslass und Abtrieb passen in beide Turbinen; sie bekommen beide Ausfuehrungen, der Abtrieb waehlt.
for mat, prefix in (("copper", ""), ("netherite", "netherite_")):
    p = PAL[mat]
    ct(prefix + "condensate_outlet", lambda l, r, u, d, p=p: with_grate(tile(p, l, r, u, d)))
    ct(prefix + "output_front", lambda l, r, u, d, p=p: with_bearing(tile(p, l, r, u, d)))

def output_recess():
    """Grund des Wellenlochs."""
    px = [[DARK[1]] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if x in (5, 10) or y in (5, 10):
                px[y][x] = DARK[0]
    return px

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
for prefix in ("", "netherite_"):
    write_json("assets/createhp/models/block/turbine/%scondensate_outlet.json" % prefix,
               {"parent": "block/cube_all", "textures": {"all": tex(prefix + "condensate_outlet")}})
write_json("assets/createhp/models/item/condensate_outlet.json", {"parent": "createhp:block/turbine/condensate_outlet"})
write_json("assets/createhp/blockstates/condensate_outlet.json", {"variants": {
    "netherite=false": {"model": "createhp:block/turbine/condensate_outlet"},
    "netherite=true": {"model": "createhp:block/turbine/netherite_condensate_outlet"}}})

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
for mat, prefix in (("copper", ""), ("netherite", "netherite_")):
    write_json("assets/createhp/models/block/turbine/%sturbine_output.json" % prefix, {
        "parent": "block/block",
        "textures": {"front": tex(prefix + "output_front"), "side": tex(mat + "_casing"), "recess": tex("output_recess"),
                     "particle": tex(mat + "_casing")},
        "elements": plate + [body]})
write_json("assets/createhp/models/item/turbine_output.json", {"parent": "createhp:block/turbine/turbine_output"})
rot = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
variants = {}
for f, r in rot.items():
    for netherite, prefix in (("false", ""), ("true", "netherite_")):
        variants["facing=%s,netherite=%s" % (f, netherite)] = dict({"model": "createhp:block/turbine/%sturbine_output" % prefix}, **r)
write_json("assets/createhp/blockstates/turbine_output.json", {"variants": variants})
print("ok")
