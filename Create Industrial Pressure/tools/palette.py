# Leitet die Farbzuordnung Create-Kupfer -> eigene Farbe aus zwei gleich aufgebauten Texturen ab.
import subprocess, sys, json, re
def pixels(path):
    out = subprocess.run(["convert", path, "-depth", "8", "txt:-"], capture_output=True, text=True, check=True).stdout
    px = {}
    for line in out.splitlines()[1:]:
        m = re.match(r"(\d+),(\d+):\((\d+),(\d+),(\d+),(\d+)\)", line.replace(" ", ""))
        if m:
            x, y, r, g, b, a = map(int, m.groups())
            px[(x, y)] = (r, g, b, a)
    return px
pairs = sys.argv[1:]
mapping = {}
conflicts = 0
for i in range(0, len(pairs), 2):
    src, dst = pixels(pairs[i]), pixels(pairs[i + 1])
    for k, c in src.items():
        d = dst.get(k)
        if c[3] == 0 or d is None or d[3] == 0:
            continue
        key = c[:3]
        if key in mapping and mapping[key] != d[:3]:
            conflicts += 1
        mapping.setdefault(key, d[:3])
print(json.dumps({"colors": len(mapping), "conflicts": conflicts, "map": {"%02x%02x%02x" % k: "%02x%02x%02x" % v for k, v in mapping.items()}}))
