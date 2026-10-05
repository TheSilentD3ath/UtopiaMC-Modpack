import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Misst Knotennamen mit den echten Glyphenbreiten der Minecraft-Schrift und
 * bildet UnlockTreeCanvas.wrap nach (Umbruch nur an Leerzeichen, hoechstens
 * zwei Zeilen, Rest mit Auslassung). So laesst sich fuer jede Sprache zaehlen,
 * wie viele Namen auf der Karte gekuerzt werden, ohne jede Zoomstufe im Client
 * abzufahren.
 *
 * Aufruf: java tools/LabelFit.java <minecraft-client.jar> <en_us.json> <andere.json> <breite>...
 * Das Jar liegt nach einem Build unter ~/.gradle/caches/fabric-loom/1.20.1/.
 * Breite in Schriftpixeln: 66 bei der Standardansicht (Mindestbreite 44 GUI-Pixel
 * bei Textskala 2/3), 165 bei vollem Zoom (110 GUI-Pixel).
 */
public class LabelFit {
    static final Map<Integer, Integer> ADV = new HashMap<>();

    public static void main(String[] a) throws Exception {
        // Umlaute sonst als '?', wenn die Shell kein UTF-8 meldet.
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        try (ZipFile jar = new ZipFile(a[0])) {
            String json = new String(jar.getInputStream(jar.getEntry("assets/minecraft/font/include/default.json")).readAllBytes(), StandardCharsets.UTF_8);
            // Provider in Dateireihenfolge; der erste, der ein Zeichen kennt, gewinnt.
            Matcher prov = Pattern.compile("\\{[^{}]*\"type\"\\s*:\\s*\"bitmap\"[^{}]*\\}", Pattern.DOTALL).matcher(json);
            while (prov.find()) {
                String p = prov.group();
                String file = find(p, "\"file\"\\s*:\\s*\"minecraft:([^\"]+)\"");
                int height = p.contains("\"height\"") ? Integer.parseInt(find(p, "\"height\"\\s*:\\s*(\\d+)")) : 8;
                List<String> rows = new ArrayList<>();
                Matcher r = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(p.substring(p.indexOf("\"chars\"") + 7));
                while (r.find()) rows.add(unescape(r.group(1)));
                BufferedImage img;
                try (InputStream in = jar.getInputStream(jar.getEntry("assets/minecraft/textures/" + file))) { img = ImageIO.read(in); }
                int cols = rows.get(0).codePointCount(0, rows.get(0).length());
                int cw = img.getWidth() / cols, ch = img.getHeight() / rows.size();
                float scale = (float) height / ch;
                for (int y = 0; y < rows.size(); y++) {
                    int[] cps = rows.get(y).codePoints().toArray();
                    for (int x = 0; x < cps.length; x++) {
                        if (cps[x] == 0 || ADV.containsKey(cps[x])) continue;
                        int w = 0;
                        for (int c = cw - 1; c >= 0 && w == 0; c--)
                            for (int yy = 0; yy < ch; yy++)
                                if ((img.getRGB(x * cw + c, y * ch + yy) >>> 24) != 0) { w = c + 1; break; }
                        ADV.put(cps[x], (int) (0.5F + w * scale) + 1);
                    }
                }
            }
        }
        ADV.put((int) ' ', 4);
        Map<String, String> en = lang(a[1]), de = lang(a[2]);
        int[] widths = Arrays.stream(a).skip(3).mapToInt(Integer::parseInt).toArray();
        System.out.printf("%-28s", "breite (Schriftpixel)");
        for (int w : widths) System.out.printf(" | %4d en  de", w);
        System.out.println();
        int[][] cut = new int[widths.length][2];
        List<String> keys = new ArrayList<>(en.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            if (!k.startsWith("tree.utopia.create.")) continue;
            String id = k.substring("tree.utopia.create.".length());
            StringBuilder line = new StringBuilder(String.format("%-28s", id));
            for (int i = 0; i < widths.length; i++) {
                boolean ce = cut(en.get(k), widths[i]), cd = cut(de.get(k), widths[i]);
                cut[i][0] += ce ? 1 : 0; cut[i][1] += cd ? 1 : 0;
                line.append(String.format(" |       %s   %s", ce ? "X" : ".", cd ? "X" : "."));
            }
            line.append("   ").append(de.get(k)).append(" -> ").append(String.join(" / ", wrap(de.get(k), widths[0])));
            System.out.println(line);
        }
        System.out.printf("%-28s", "gekuerzt");
        for (int[] c : cut) System.out.printf(" | %8d %3d", c[0], c[1]);
        System.out.println();
    }

    static boolean cut(String text, int width) {
        return String.join(" ", wrap(text, width)).contains("\u2026");
    }

    /** Nachbau von UnlockTreeCanvas.wrap. */
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = text.trim().split("\\s+");
        int index = 0;
        while (index < words.length && lines.size() < 2) {
            String candidate = line.length() == 0 ? words[index] : line + " " + words[index];
            if (w(candidate) <= width || line.length() == 0) { line.setLength(0); line.append(candidate); index++; continue; }
            lines.add(line.toString());
            line.setLength(0);
        }
        if (line.length() > 0 && lines.size() < 2) lines.add(line.toString());
        if (index < words.length || (!lines.isEmpty() && w(lines.get(lines.size() - 1)) > width)) {
            StringBuilder rest = new StringBuilder(lines.isEmpty() ? "" : lines.remove(lines.size() - 1));
            for (int i = index; i < words.length; i++) rest.append(' ').append(words[i]);
            lines.add(trim(rest.toString().trim(), width - w("\u2026")).trim() + "\u2026");
        }
        return lines;
    }

    static String trim(String s, int width) {
        int sum = 0, i = 0;
        for (; i < s.length(); i++) { sum += adv(s.charAt(i)); if (sum > width) break; }
        return s.substring(0, i);
    }

    static int w(String s) { return s.chars().map(LabelFit::adv).sum(); }

    static int adv(int cp) {
        Integer v = ADV.get(cp);
        if (v == null) throw new IllegalStateException("keine Glyphe: " + (char) cp);
        return v;
    }

    static String find(String s, String re) { Matcher m = Pattern.compile(re).matcher(s); return m.find() ? m.group(1) : null; }

    static String unescape(String s) {
        Matcher m = Pattern.compile("\\\\u([0-9a-fA-F]{4})|\\\\(.)").matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) != null ? String.valueOf((char) Integer.parseInt(m.group(1), 16)) : m.group(2)));
        m.appendTail(out);
        return out.toString();
    }

    static Map<String, String> lang(String file) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(Files.readString(Path.of(file)));
        while (m.find()) map.put(m.group(1), unescape(m.group(2)));
        return map;
    }
}
