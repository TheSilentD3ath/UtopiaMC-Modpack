#!/usr/bin/env python3
"""Verkleinert die Holztextur des Freischalt-Bildschirms auf ihre Darstellungsgroesse.

Die Holztextur des Rahmens kam als 1254-px-Quelle und wurde bei 130 bis 455 px
gezeichnet, was beim Zoomen sichtbar flimmerte. Dieses Skript mittelt sie auf 512 px
herunter. Es ist nur noch fuer den Fall da, dass jemand eine groessere Quelle einsetzt.

Frueher erzeugte dieses Skript auch die Knotenformen und Zustandsabzeichen als
64-px- und 32-px-Texturen. Die werden inzwischen zur Laufzeit als kantengeglaettete
Vektorformen gezeichnet (SmoothPainter), weil vergroesserte Texturen beim Hineinzoomen
Treppenkanten bekamen. Die Karte selbst ist flach und braucht kein Holz mehr.

Aufruf aus dem Ordner "Utopia Core":

    python3 tools/downscale_unlock_wood.py

Nur die Standardbibliothek, kein Pillow noetig.
"""

import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, os.pardir, "src", "main", "resources",
                   "assets", "utopiacore", "textures", "gui", "unlock")

WOOD_TARGET = 512        # Zielkantenlaenge der verkleinerten Holztextur
WOOD_TEXTURES = ("dark_wood",)


def write_png(path, width, height, pixels, colour_type=6):
    """pixels: bytes, RGBA (colour_type 6) oder RGB (colour_type 2), zeilenweise."""
    channels = 4 if colour_type == 6 else 3
    stride = width * channels
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # Filter 0 (None)
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


def main():
    for name in WOOD_TEXTURES:
        path = os.path.join(OUT, name + ".png")
        if not os.path.exists(path):
            print("Holz   %-12s fehlt" % name)
            continue
        before = os.path.getsize(path)
        scaled = downscale(path, WOOD_TARGET)
        if scaled is None:
            print("Holz   %-12s bereits klein genug" % name)
            continue
        after = write_png(path, WOOD_TARGET, WOOD_TARGET, scaled, colour_type=2)
        print("Holz   %-12s %d x %d, %d KB -> %d KB"
              % (name, WOOD_TARGET, WOOD_TARGET, before // 1024, after // 1024))
    return 0


if __name__ == "__main__":
    sys.exit(main())
