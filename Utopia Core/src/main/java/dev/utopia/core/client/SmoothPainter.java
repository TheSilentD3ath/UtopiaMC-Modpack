package dev.utopia.core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/**
 * Kantengeglaettete Vektorformen fuer die GUI: Flaechen, Ringe und Linien.
 *
 * <p>Minecraft zeichnet GUI-Grafik ohne Kantenglaettung. Texturen, die vergroessert
 * werden, bekommen Treppenkanten, und {@code fill} an einer gedrehten Matrix ebenso. Hier
 * wird deshalb jede Kante mit einem Saum von genau einem Bildschirmpixel gezeichnet, der
 * von voller Deckkraft auf null auslaeuft. Das ist dieselbe Idee wie die Kantenglaettung
 * eines Browsers bei SVG, nur von Hand: Die Grafikkarte blendet den Saum mit dem
 * Untergrund, und die Kante wirkt glatt, egal wie gross die Form gerade ist.
 *
 * <p>Alle Koordinaten sind GUI-Einheiten als Kommazahl. Die Saumbreite rechnet sich aus
 * dem GUI-Massstab, damit sie auf jedem Bildschirm einem echten Pixel entspricht.
 *
 * <p>Polygone muessen sternfoermig um ihren Mittelpunkt sein und ihre Ecken mit
 * steigendem Winkel aufzaehlen (auf dem Bildschirm also im Uhrzeigersinn). Alle Formen der
 * Karte erfuellen das, auch das Zahnrad.
 */
final class SmoothPainter {

    /** Obergrenze fuer Gehrungen an spitzen Ecken, damit keine Stacheln entstehen. */
    private static final float MITER_LIMIT = 4.0F;

    private final BufferBuilder buffer;
    private final Matrix4f matrix;
    private final float pixel;
    private final float z;

    private SmoothPainter(DrawContext context, float z) {
        this.buffer = Tessellator.getInstance().getBuffer();
        this.matrix = context.getMatrices().peek().getPositionMatrix();
        this.pixel = pixel();
        this.z = z;
    }

    /**
     * Neuen Zeichenstapel beginnen. Alles bis {@link #end()} geht in einem Aufruf an die
     * Grafikkarte.
     *
     * @param z Tiefe in der GUI. Gegenstaende liegen bei etwa 150; was darueber stehen
     *          soll, braucht mehr.
     */
    static SmoothPainter begin(DrawContext context, float z) {
        SmoothPainter painter = new SmoothPainter(context, z);
        painter.buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        return painter;
    }

    void end() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Die Dreiecke haben gemischten Umlaufsinn (Faecher und Saeume). Minecraft laesst
        // Rueckseiten sonst weg, und dann fehlten Teile jeder Form.
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Breite eines Bildschirmpixels in GUI-Einheiten. */
    static float pixel() {
        return (float) (1.0 / guiScale());
    }

    static double guiScale() {
        return Math.max(1.0, MinecraftClient.getInstance().getWindow().getScaleFactor());
    }

    /** Auf das Raster der Bildschirmpixel runden — scharf, aber dreimal feiner als GUI-Pixel. */
    static double snap(double value) {
        double scale = guiScale();
        return Math.round(value * scale) / scale;
    }

    // --- Flaechen ---------------------------------------------------------

    /** Gefuelltes Polygon mit weicher Aussenkante. */
    void fill(float[] xs, float[] ys, float cx, float cy, int argb) {
        int n = xs.length;
        float[] mx = new float[n];
        float[] my = new float[n];
        miters(xs, ys, mx, my);
        float h = pixel * 0.5F;
        int a = argb >>> 24;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            triangle(cx, cy, a, xs[i] - mx[i] * h, ys[i] - my[i] * h, a,
                    xs[j] - mx[j] * h, ys[j] - my[j] * h, a, argb);
        }
        band(xs, ys, mx, my, -h, a, h, 0, argb);
    }

    /**
     * Ring entlang der Kante, {@code width} nach innen, mit weicher Innen- und Aussenkante.
     *
     * <p>Gedacht, um <em>ueber</em> eine vollstaendige {@link #fill} gelegt zu werden. Zwei
     * aneinanderstossende weiche Saeume ergaeben eine feine dunkle Naht, weil sich zwei
     * halbe Deckkraefte beim Ueberblenden nicht zu einer ganzen addieren. Liegt der Ring
     * dagegen auf der Flaeche, blendet sein Innensaum direkt in deren Farbe.
     */
    void ring(float[] xs, float[] ys, float width, int argb) {
        int n = xs.length;
        float[] mx = new float[n];
        float[] my = new float[n];
        miters(xs, ys, mx, my);
        float h = pixel * 0.5F;
        float w = Math.max(width, pixel * 2.0F);
        int a = argb >>> 24;
        band(xs, ys, mx, my, -w - h, 0, -w + h, a, argb);
        band(xs, ys, mx, my, -w + h, a, -h, a, argb);
        band(xs, ys, mx, my, -h, a, h, 0, argb);
    }

    // --- Linien -----------------------------------------------------------

    /** Linie mit weichen Laengskanten und stumpfen Enden. */
    void line(double x1, double y1, double x2, double y2, float width, int argb) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-4F) {
            return;
        }
        float nx = -dy / length;
        float ny = dx / length;
        float h = pixel * 0.5F;
        float half = width * 0.5F;
        int a = argb >>> 24;
        float ax = (float) x1;
        float ay = (float) y1;
        float bx = (float) x2;
        float by = (float) y2;
        if (half <= h) {
            // Haarlinie, duenner als ein Pixel: Deckkraft statt Breite, sonst flimmert sie.
            int core = Math.round(a * Math.min(1.0F, width / pixel));
            float o = half + h;
            strip(ax, ay, bx, by, nx, ny, -o, 0, 0.0F, core, argb);
            strip(ax, ay, bx, by, nx, ny, 0.0F, core, o, 0, argb);
            return;
        }
        strip(ax, ay, bx, by, nx, ny, -half - h, 0, -half + h, a, argb);
        strip(ax, ay, bx, by, nx, ny, -half + h, a, half - h, a, argb);
        strip(ax, ay, bx, by, nx, ny, half - h, a, half + h, 0, argb);
    }

    /** Gestrichelte Linie; {@code phase} verschiebt das Muster entlang der Linie. */
    void dashed(double x1, double y1, double x2, double y2, float width, float dash, float period,
            float phase, int argb) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        if (length < 1.0E-4 || period <= 0.0F) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        double start = -(((phase % period) + period) % period);
        for (double offset = start; offset < length; offset += period) {
            double from = Math.max(0.0, offset);
            double to = Math.min(length, offset + dash);
            if (to - from > 1.0E-3) {
                line(x1 + ux * from, y1 + uy * from, x1 + ux * to, y1 + uy * to, width, argb);
            }
        }
    }

    /** Zusammenhaengender Linienzug, Ecken ueberlappt statt offen. */
    void polyline(float[] xs, float[] ys, float width, int argb) {
        float extend = width * 0.5F;
        for (int i = 0; i + 1 < xs.length; i++) {
            float dx = xs[i + 1] - xs[i];
            float dy = ys[i + 1] - ys[i];
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 1.0E-4F) {
                continue;
            }
            float ux = dx / length * extend;
            float uy = dy / length * extend;
            float sx = i == 0 ? xs[i] : xs[i] - ux;
            float sy = i == 0 ? ys[i] : ys[i] - uy;
            float ex = i + 2 == xs.length ? xs[i + 1] : xs[i + 1] + ux;
            float ey = i + 2 == ys.length ? ys[i + 1] : ys[i + 1] + uy;
            line(sx, sy, ex, ey, width, argb);
        }
    }

    // --- Grundformen ------------------------------------------------------

    /** Regelmaessiges Vieleck um (cx, cy), erste Ecke bei {@code startAngle}. */
    static float[][] regular(double cx, double cy, double radius, int sides, double startAngle) {
        float[] xs = new float[sides];
        float[] ys = new float[sides];
        for (int i = 0; i < sides; i++) {
            double angle = startAngle + i * Math.PI * 2.0 / sides;
            xs[i] = (float) (cx + radius * Math.cos(angle));
            ys[i] = (float) (cy + radius * Math.sin(angle));
        }
        return new float[][] {xs, ys};
    }

    /** Kreis mit so vielen Ecken, dass die Sehne unter einem Zehntelpixel bleibt. */
    static float[][] circle(double cx, double cy, double radius) {
        double radiusPixels = radius * guiScale();
        int sides = (int) Math.max(16, Math.min(96, Math.round(radiusPixels * 0.8)));
        return regular(cx, cy, radius, sides, 0.0);
    }

    /** Rechteck mit gerundeten Ecken; {@code corner} = 0 ergibt ein scharfes Rechteck. */
    static float[][] roundedRect(double cx, double cy, double halfWidth, double halfHeight, double corner) {
        double c = Math.max(0.0, Math.min(corner, Math.min(halfWidth, halfHeight)));
        // Eckmittelpunkte nach steigendem Winkel: unten rechts, unten links, oben links, oben rechts.
        double[][] centres = {
                {cx + halfWidth - c, cy + halfHeight - c},
                {cx - halfWidth + c, cy + halfHeight - c},
                {cx - halfWidth + c, cy - halfHeight + c},
                {cx + halfWidth - c, cy - halfHeight + c}};
        if (c <= 0.0) {
            // Ohne Rundung sind die Eckmittelpunkte die Ecken selbst.
            float[] xs = new float[4];
            float[] ys = new float[4];
            for (int i = 0; i < 4; i++) {
                xs[i] = (float) centres[i][0];
                ys[i] = (float) centres[i][1];
            }
            return new float[][] {xs, ys};
        }
        int steps = (int) Math.max(3, Math.min(12, Math.round(c * guiScale() * 0.5)));
        float[] xs = new float[(steps + 1) * 4];
        float[] ys = new float[(steps + 1) * 4];
        int k = 0;
        for (int quarter = 0; quarter < 4; quarter++) {
            double base = quarter * Math.PI / 2.0;
            for (int s = 0; s <= steps; s++) {
                double angle = base + s * (Math.PI / 2.0) / steps;
                xs[k] = (float) (centres[quarter][0] + c * Math.cos(angle));
                ys[k] = (float) (centres[quarter][1] + c * Math.sin(angle));
                k++;
            }
        }
        return new float[][] {xs, ys};
    }

    // --- Innereien --------------------------------------------------------

    /**
     * Gehrungsvektor je Ecke: die Richtung, in die sich die Ecke verschiebt, wenn beide
     * anliegenden Kanten um genau eine Einheit nach aussen wandern.
     */
    private static void miters(float[] xs, float[] ys, float[] mx, float[] my) {
        int n = xs.length;
        for (int i = 0; i < n; i++) {
            int p = (i + n - 1) % n;
            int q = (i + 1) % n;
            float[] a = normal(xs[p], ys[p], xs[i], ys[i]);
            float[] b = normal(xs[i], ys[i], xs[q], ys[q]);
            float sx = a[0] + b[0];
            float sy = a[1] + b[1];
            float len = (float) Math.sqrt(sx * sx + sy * sy);
            if (len < 1.0E-5F) {
                mx[i] = b[0];
                my[i] = b[1];
                continue;
            }
            sx /= len;
            sy /= len;
            float cos = sx * b[0] + sy * b[1];
            float scale = Math.min(MITER_LIMIT, 1.0F / Math.max(1.0E-3F, cos));
            mx[i] = sx * scale;
            my[i] = sy * scale;
        }
    }

    /** Aussennormale der Kante a→b bei steigendem Winkel (Bildschirm: y nach unten). */
    private static float[] normal(float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-6F) {
            return new float[] {0.0F, 0.0F};
        }
        return new float[] {dy / len, -dx / len};
    }

    private void band(float[] xs, float[] ys, float[] mx, float[] my, float d0, int a0, float d1, int a1,
            int argb) {
        int n = xs.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            quad(xs[i] + mx[i] * d0, ys[i] + my[i] * d0, a0,
                    xs[j] + mx[j] * d0, ys[j] + my[j] * d0, a0,
                    xs[j] + mx[j] * d1, ys[j] + my[j] * d1, a1,
                    xs[i] + mx[i] * d1, ys[i] + my[i] * d1, a1, argb);
        }
    }

    private void strip(float ax, float ay, float bx, float by, float nx, float ny, float d0, int a0,
            float d1, int a1, int argb) {
        quad(ax + nx * d0, ay + ny * d0, a0, bx + nx * d0, by + ny * d0, a0,
                bx + nx * d1, by + ny * d1, a1, ax + nx * d1, ay + ny * d1, a1, argb);
    }

    private void quad(float x0, float y0, int a0, float x1, float y1, int a1, float x2, float y2, int a2,
            float x3, float y3, int a3, int argb) {
        triangle(x0, y0, a0, x1, y1, a1, x2, y2, a2, argb);
        triangle(x0, y0, a0, x2, y2, a2, x3, y3, a3, argb);
    }

    private void triangle(float x0, float y0, int a0, float x1, float y1, int a1, float x2, float y2, int a2,
            int argb) {
        int r = argb >> 16 & 255;
        int g = argb >> 8 & 255;
        int b = argb & 255;
        buffer.vertex(matrix, x0, y0, z).color(r, g, b, a0).next();
        buffer.vertex(matrix, x1, y1, z).color(r, g, b, a1).next();
        buffer.vertex(matrix, x2, y2, z).color(r, g, b, a2).next();
    }
}
