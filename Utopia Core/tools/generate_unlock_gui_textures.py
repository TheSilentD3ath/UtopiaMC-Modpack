#!/usr/bin/env python3
"""Erzeugt die GUI-Texturen des Freischaltbaums.

Warum generiert und nicht gemalt: Die Knotenformen und Zustandsabzeichen sind reine
Geometrie. Generiert bleiben sie exakt zentriert, exakt gleich gross und lassen sich
aendern, ohne ein Bildbearbeitungsprogramm zu oeffnen. Alle Formen sind WEISS mit
Alphakanal und werden im Spiel ueber setShaderColor eingefaerbt -- eine Textur je
Form statt einer je Form-und-Zustand-Kombination.

Aufruf aus dem Ordner "Utopia Core":

    python3 tools/generate_unlock_gui_textures.py

Schreibt nach src/main/resources/assets/utopiacore/textures/gui/unlock/.
Nur die Standardbibliothek, kein Pillow noetig.
"""

import math
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, os.pardir, "src", "main", "resources",
                   "assets", "utopiacore", "textures", "gui", "unlock")

SHAPE_SIZE = 64          # Formen: 64 px Quelle fuer 16-48 px Darstellung
BADGE_SIZE = 32          # Abzeichen: klein, sie werden bei ~10 px gezeichnet
SUPERSAMPLE = 4          # Kantenglaettung durch Ueberabtastung
OUTLINE_THICKNESS = 0.17  # Rahmenstaerke, relativ zum Formradius
WOOD_TARGET = 512        # Zielkantenlaenge der verkleinerten Holztexturen


# --------------------------------------------------------------------------
# PNG schreiben und lesen
# --------------------------------------------------------------------------

def write_png(path, width, height, pixels, colour_type=6):
    """pixels: bytes, RGBA (colour_type 6) oder RGB (colour_type 2), zeilenweise."""
    channels = 4 if colour_type == 6 else 3
    stride = width * channels
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # Filter 0 (None); die Bilder sind klein genug
        raw += pixels[y * stride:(y + 1) * stride]

    def chunk(kind, payload):
        return (struct.pack(">I", len(payload)) + kind + payload
                + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF))

    data = b"\x89PNG\r\n\x1a\n"
    data += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, colour_type, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    data += chunk(b"IEND", b"")
    with open(path, "wb") as handle:
        handle.write(data)
    return len(data)


def read_png(path):
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    pos, idat, width, height, depth, colour = 8, b"", None, None, None, None
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", payload[:10])
        elif kind == b"IDAT":
            idat += payload
        elif kind == b"IEND":
            break
        pos += 12 + length
    if depth != 8 or colour not in (2, 6):
        raise SystemExit("Nur 8-Bit RGB/RGBA wird unterstuetzt: %s" % path)

    raw = zlib.decompress(idat)
    channels = 3 if colour == 2 else 4
    stride = width * channels
    out = bytearray(height * stride)
    previous = bytearray(stride)
    pos = 0
    for y in range(height):
        method = raw[pos]
        pos += 1
        line = bytearray(raw[pos:pos + stride])
        pos += stride
        if method == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 255
        elif method == 2:
            for i in range(stride):
                line[i] = (line[i] + previous[i]) & 255
        elif method == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + previous[i]) >> 1)) & 255
        elif method == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = previous[i]
                c = previous[i - channels] if i >= channels else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pred) & 255
        out[y * stride:(y + 1) * stride] = line
        previous = line
    return width, height, channels, bytes(out)


# --------------------------------------------------------------------------
# Formen, in normierten Koordinaten mit Rand bei Radius 1
# --------------------------------------------------------------------------

def _hexagon(x, y):
    # Regelmaessiges Sechseck, Spitze oben: Schnitt von sechs Halbebenen.
    # Inkreisradius eines Sechsecks mit Umkreisradius 1 ist cos(30 Grad).
    apothem = math.cos(math.pi / 6.0)
    for k in range(6):
        angle = k * math.pi / 3.0
        if x * math.cos(angle) + y * math.sin(angle) > apothem:
            return False
    return True


def _gear(x, y):
    radius = math.hypot(x, y)
    if radius > 1.0:
        return False
    teeth = 8
    angle = math.atan2(y, x)
    # Zahnflanken leicht verschliffen statt hart gestuft: scharf genug, dass das
    # Zahnrad bei 16 px als Zahnrad lesbar bleibt, weich genug gegen Treppenkanten.
    wave = math.cos(teeth * angle)
    profile = 0.74 + 0.26 * (0.5 + 0.5 * math.tanh(7.0 * wave))
    return radius <= profile


SHAPES = {
    "circle": lambda x, y: x * x + y * y <= 1.0,
    "square": lambda x, y: max(abs(x), abs(y)) <= 0.90,
    "rsquare": lambda x, y: (abs(x) / 0.92) ** 4 + (abs(y) / 0.92) ** 4 <= 1.0,
    "diamond": lambda x, y: abs(x) + abs(y) <= 1.0,
    "hexagon": _hexagon,
    "gear": _gear,
}


def render_mask(size, predicate, margin=0.06):
    """Graustufen-Deckung 0..255 je Pixel, ueberabgetastet."""
    mask = bytearray(size * size)
    span = 1.0 - margin
    step = 1.0 / (SUPERSAMPLE + 1)
    samples = SUPERSAMPLE * SUPERSAMPLE
    for py in range(size):
        for px in range(size):
            hits = 0
            for sy in range(SUPERSAMPLE):
                for sx in range(SUPERSAMPLE):
                    fx = (px + (sx + 1) * step) / size * 2.0 - 1.0
                    fy = (py + (sy + 1) * step) / size * 2.0 - 1.0
                    if predicate(fx / span, fy / span):
                        hits += 1
            mask[py * size + px] = (hits * 255) // samples
    return mask


def mask_to_rgba(mask):
    out = bytearray(len(mask) * 4)
    for i, alpha in enumerate(mask):
        out[i * 4 + 0] = 255
        out[i * 4 + 1] = 255
        out[i * 4 + 2] = 255
        out[i * 4 + 3] = alpha
    return bytes(out)


def ring_mask(size, predicate):
    """Rahmen: Aussenform minus derselben Form, um die Rahmenstaerke geschrumpft."""
    outer = render_mask(size, predicate)
    inner_scale = 1.0 - OUTLINE_THICKNESS
    inner = render_mask(size, lambda x, y: predicate(x / inner_scale, y / inner_scale))
    return bytearray(max(0, outer[i] - inner[i]) for i in range(len(outer)))


# --------------------------------------------------------------------------
# Zustandsabzeichen
# --------------------------------------------------------------------------

def _segment_distance(x, y, x1, y1, x2, y2):
    dx, dy = x2 - x1, y2 - y1
    length = dx * dx + dy * dy
    if length <= 1e-9:
        return math.hypot(x - x1, y - y1)
    t = max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / length))
    return math.hypot(x - (x1 + t * dx), y - (y1 + t * dy))


def _check(x, y):
    half = 0.16
    return (_segment_distance(x, y, -0.58, -0.02, -0.18, 0.42) <= half
            or _segment_distance(x, y, -0.18, 0.42, 0.60, -0.46) <= half)


def _plus(x, y):
    return (abs(x) <= 0.62 and abs(y) <= 0.18) or (abs(y) <= 0.62 and abs(x) <= 0.18)


def _coin(x, y):
    radius = math.hypot(x, y)
    return 0.34 <= radius <= 0.80


def _lock(x, y):
    # Buegel: obere Haelfte eines Rings
    if y <= -0.02:
        ring = abs(math.hypot(x, y + 0.02) - 0.36)
        if ring <= 0.13 and abs(x) <= 0.50:
            return True
    # Koerper
    return abs(x) <= 0.56 and -0.02 <= y <= 0.70


BADGES = {
    "badge_check": _check,
    "badge_plus": _plus,
    "badge_coin": _coin,
    "badge_lock": _lock,
}


# --------------------------------------------------------------------------
# Holztexturen verkleinern
# --------------------------------------------------------------------------

def downscale(path, target):
    width, height, channels, pixels = read_png(path)
    if width <= target and height <= target:
        return None
    out = bytearray(target * target * 3)
    for oy in range(target):
        y0 = oy * height // target
        y1 = max(y0 + 1, (oy + 1) * height // target)
        for ox in range(target):
            x0 = ox * width // target
            x1 = max(x0 + 1, (ox + 1) * width // target)
            r = g = b = count = 0
            for sy in range(y0, y1):
                base = sy * width * channels
                for sx in range(x0, x1):
                    i = base + sx * channels
                    r += pixels[i]
                    g += pixels[i + 1]
                    b += pixels[i + 2]
                    count += 1
            i = (oy * target + ox) * 3
            out[i] = r // count
            out[i + 1] = g // count
            out[i + 2] = b // count
    return bytes(out)


# --------------------------------------------------------------------------

def main():
    os.makedirs(OUT, exist_ok=True)
    total = 0

    for name, predicate in SHAPES.items():
        fill = mask_to_rgba(render_mask(SHAPE_SIZE, predicate))
        total += write_png(os.path.join(OUT, name + ".png"), SHAPE_SIZE, SHAPE_SIZE, fill)
        outline = mask_to_rgba(ring_mask(SHAPE_SIZE, predicate))
        total += write_png(os.path.join(OUT, name + "_outline.png"), SHAPE_SIZE, SHAPE_SIZE, outline)
        print("Form   %-18s %d x %d" % (name, SHAPE_SIZE, SHAPE_SIZE))

    for name, predicate in BADGES.items():
        mask = render_mask(BADGE_SIZE, predicate, margin=0.10)
        total += write_png(os.path.join(OUT, name + ".png"), BADGE_SIZE, BADGE_SIZE, mask_to_rgba(mask))
        print("Abzeichen %-15s %d x %d" % (name, BADGE_SIZE, BADGE_SIZE))

    for name in ("light_wood", "dark_wood", "canvas_wood"):
        path = os.path.join(OUT, name + ".png")
        if not os.path.exists(path):
            continue
        before = os.path.getsize(path)
        scaled = downscale(path, WOOD_TARGET)
        if scaled is None:
            print("Holz   %-18s bereits klein genug" % name)
            continue
        after = write_png(path, WOOD_TARGET, WOOD_TARGET, scaled, colour_type=2)
        total += after
        print("Holz   %-18s %d x %d, %d KB -> %d KB"
              % (name, WOOD_TARGET, WOOD_TARGET, before // 1024, after // 1024))

    print("\nGesamt geschrieben: %d KB" % (total // 1024))
    return 0


if __name__ == "__main__":
    sys.exit(main())
