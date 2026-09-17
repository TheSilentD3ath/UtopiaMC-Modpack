package dev.utopia.core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.utopia.core.UtopiaCore;
import dev.utopia.core.unlock.UnlockTree;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Freier, zoombarer Baum-Canvas. Ein Raster existiert ausschliesslich im Editor. */
final class UnlockTreeCanvas {

    enum State { OWNED, BUYABLE, TOO_EXPENSIVE, BLOCKED }

    interface StateProvider {
        State state(String key, UnlockTree.Node node);
    }

    interface MoveListener {
        void move(String key, double x, double y);
    }

    private static final Identifier CANVAS_WOOD = UtopiaCore.id("textures/gui/unlock/canvas_wood.png");
    private static final Identifier NODE_CIRCLE = UtopiaCore.id("textures/gui/unlock/node_circle.png");
    private static final int TEXTURE_SIZE = 1254;
    private static final int CANVAS_TILE_SIZE = 260;
    private static final double UNIT = 64.0;
    private static final double MIN_ZOOM = 0.15;
    private static final double MAX_ZOOM = 1.75;
    private static final double LABEL_ZOOM = 0.30;
    private static final double DRAG_THRESHOLD = 4.0;
    private static final double DASH_LENGTH = 8.0;
    private static final double DASH_PERIOD = 13.0;

    private final TextRenderer textRenderer;
    private int left;
    private int top;
    private int right;
    private int bottom;
    private double panX;
    private double panY;
    private double zoom = 1.0;
    private boolean panning;
    private String draggedNode;
    private String pressedNode;
    private boolean gestureActive;
    private int pressedButton = -1;
    private double pressMouseX;
    private double pressMouseY;
    private double dragOffsetX;
    private double dragOffsetY;
    private String selected;

    UnlockTreeCanvas(TextRenderer textRenderer) {
        this.textRenderer = textRenderer;
    }

    void bounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    String selected() {
        return selected;
    }

    void select(String key) {
        selected = key;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    void render(DrawContext context, UnlockTree tree, StateProvider states, boolean editing,
            int mouseX, int mouseY) {
        context.enableScissor(left, top, right, bottom);
        drawCanvasBackground(context);
        context.fill(left, top, right, bottom, 0x10180E08);
        drawCanvasVignette(context);
        if (editing) {
            drawEditorGrid(context);
        }

        String hovered = nodeAt(tree, mouseX, mouseY);
        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<String, UnlockTree.Node> entry : tree.nodes().entrySet()) {
            String childKey = entry.getKey();
            UnlockTree.Node child = entry.getValue();
            for (int parentIndex = 0; parentIndex < child.parents().size(); parentIndex++) {
                String parentKey = child.parents().get(parentIndex);
                UnlockTree.Node parent = tree.nodes().get(parentKey);
                boolean primary = parentIndex == 0;
                boolean focused = childKey.equals(selected) || childKey.equals(hovered)
                        || parentKey.equals(selected) || parentKey.equals(hovered);
                // 68 Knoten besitzen 141 fachliche Abhaengigkeiten. Im
                // Ueberblick bildet nur die erste Elternkante die Baumform;
                // weitere Voraussetzungen erscheinen beim betroffenen Knoten
                // und vollstaendig im Inspektor.
                if (parent != null && (primary || focused)) {
                    int color = states.state(parentKey, parent) == State.OWNED ? 0xFF83B86F : 0xFFD7A65B;
                    edges.add(new Edge(screenX(parent), screenY(parent), screenX(child), screenY(child),
                            primary, primary ? color : 0xBF7FA8B0));
                }
            }
        }
        drawConnections(context, edges);

        List<Map.Entry<String, UnlockTree.Node>> visibleNodes = tree.nodes().entrySet().stream()
                .filter(entry -> nodeVisible(entry.getValue())).toList();
        drawNodeHalos(context, visibleNodes, states, hovered);
        for (Map.Entry<String, UnlockTree.Node> entry : visibleNodes) {
            UnlockTree.Node node = entry.getValue();
            drawNode(context, entry.getKey(), node, entry.getKey().equals(hovered));
        }
        context.disableScissor();
    }

    /** Die Holzplatte liegt im selben Koordinatenraum wie der Baum und bewegt sich bei Pan/Zoom mit. */
    private void drawCanvasBackground(DrawContext context) {
        int tileSize = MathHelper.clamp((int) Math.round(CANVAS_TILE_SIZE * zoom), 130, 455);
        int startX = left + Math.floorMod(renderedPanX(), tileSize) - tileSize;
        int startY = top + Math.floorMod(renderedPanY(), tileSize) - tileSize;
        for (int x = startX; x < right; x += tileSize) {
            for (int y = startY; y < bottom; y += tileSize) {
                context.drawTexture(CANVAS_WOOD, x, y, tileSize, tileSize,
                        0.0F, 0.0F, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
            }
        }
    }

    private void drawCanvasVignette(DrawContext context) {
        int depth = Math.min(20, Math.min((right - left) / 5, (bottom - top) / 5));
        for (int inset = 0; inset < depth; inset += 2) {
            int alpha = Math.max(0, 64 - inset * 3);
            int color = alpha << 24 | 0x00170B06;
            context.fill(left + inset, top + inset, right - inset, top + inset + 2, color);
            context.fill(left + inset, bottom - inset - 2, right - inset, bottom - inset, color);
            context.fill(left + inset, top + inset + 2, left + inset + 2, bottom - inset - 2, color);
            context.fill(right - inset - 2, top + inset + 2, right - inset, bottom - inset - 2, color);
        }
    }

    private void drawEditorGrid(DrawContext context) {
        int spacing = Math.max(12, (int) Math.round(24.0 * zoom));
        int firstX = left + Math.floorMod(renderedPanX(), spacing);
        int firstY = top + Math.floorMod(renderedPanY(), spacing);
        for (int x = firstX; x < right; x += spacing) {
            context.fill(x, top, x + 1, bottom, 0x263C281A);
        }
        for (int y = firstY; y < bottom; y += spacing) {
            context.fill(left, y, right, y + 1, 0x263C281A);
        }
    }

    /** Wenige gerade GUI-Quads sind robust sichtbar und vermeiden die fruehere Pixelabtastung. */
    private void drawConnections(DrawContext context, List<Edge> edges) {
        List<Edge> visible = edges.stream().filter(this::edgeVisible).toList();
        if (visible.isEmpty()) {
            return;
        }
        for (Edge edge : visible) {
            if (edge.primary()) {
                drawConnection(context, edge, 4, 0xB82C190E, false);
            }
        }
        for (Edge edge : visible) {
            drawConnection(context, edge, edge.primary() ? 2 : 1, edge.color(), true);
        }
    }

    /** Gerade Hauptaeste bleiben als Baum lesbar; Zusatzvoraussetzungen sind dezent gestrichelt. */
    private static void drawConnection(DrawContext context, Edge edge, int thickness,
            int color, boolean dashed) {
        if (!dashed) {
            drawSegment(context, edge.x1(), edge.y1(), edge.x2(), edge.y2(), thickness, color);
            return;
        }
        double deltaX = edge.x2() - edge.x1();
        double deltaY = edge.y2() - edge.y1();
        double length = Math.hypot(deltaX, deltaY);
        if (length < 0.001) {
            return;
        }
        double unitX = deltaX / length;
        double unitY = deltaY / length;
        for (double offset = 0.0; offset < length; offset += DASH_PERIOD) {
            double end = Math.min(length, offset + DASH_LENGTH);
            drawSegment(context, edge.x1() + unitX * offset, edge.y1() + unitY * offset,
                    edge.x1() + unitX * end, edge.y1() + unitY * end, thickness, color);
        }
    }

    private static void drawSegment(DrawContext context, double x1, double y1, double x2, double y2,
            int thickness, int color) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        if (length < 0.001) {
            return;
        }
        int segmentTop = -thickness / 2;
        context.getMatrices().push();
        context.getMatrices().translate(x1, y1, 0.0F);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(y2 - y1, x2 - x1)));
        context.fill(0, segmentTop, (int) Math.ceil(length), segmentTop + thickness, color);
        context.getMatrices().pop();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, double x, double y,
            int red, int green, int blue, int alpha) {
        buffer.vertex(matrix, (float) x, (float) y, 0.0F).color(red, green, blue, alpha).next();
    }

    private boolean edgeVisible(Edge edge) {
        int margin = 80;
        int minX = Math.min(edge.x1(), edge.x2()) - margin;
        int maxX = Math.max(edge.x1(), edge.x2()) + margin;
        int minY = Math.min(edge.y1(), edge.y2()) - margin;
        int maxY = Math.max(edge.y1(), edge.y2()) + margin;
        return maxX >= left && minX <= right && maxY >= top && minY <= bottom;
    }

    private boolean nodeVisible(UnlockTree.Node node) {
        int margin = nodeSize() + 90;
        int x = screenX(node);
        int y = screenY(node);
        return x >= left - margin && x <= right + margin && y >= top - margin && y <= bottom + margin;
    }

    private void drawNodeHalos(DrawContext context, List<Map.Entry<String, UnlockTree.Node>> nodes,
            StateProvider states, String hovered) {
        if (nodes.isEmpty()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        int size = nodeSize();
        for (Map.Entry<String, UnlockTree.Node> entry : nodes) {
            String key = entry.getKey();
            UnlockTree.Node node = entry.getValue();
            int centerX = screenX(node);
            int centerY = screenY(node);
            if (key.equals(selected) || key.equals(hovered)) {
                addCircle(buffer, matrix, centerX, centerY, size / 2.0F + 5.0F,
                        key.equals(selected) ? 0xFFFFE6A3 : 0xFFD8B36B);
            }
            int stateColor = switch (states.state(key, node)) {
                case OWNED -> 0xFF9BD17F;
                case BUYABLE -> 0xFFF2CF77;
                case TOO_EXPENSIVE -> 0xFF8D7458;
                case BLOCKED -> 0xFF5E4D3B;
            };
            addCircle(buffer, matrix, centerX, centerY, size / 2.0F + 2.5F, stateColor);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableDepthTest();
    }

    private static void addCircle(BufferBuilder buffer, Matrix4f matrix, float centerX, float centerY,
            float radius, int color) {
        int alpha = color >>> 24;
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            double first = Math.PI * 2.0 * i / segments;
            double second = Math.PI * 2.0 * (i + 1) / segments;
            vertex(buffer, matrix, centerX, centerY, red, green, blue, alpha);
            vertex(buffer, matrix, centerX + Math.cos(first) * radius,
                    centerY + Math.sin(first) * radius, red, green, blue, alpha);
            vertex(buffer, matrix, centerX + Math.cos(second) * radius,
                    centerY + Math.sin(second) * radius, red, green, blue, alpha);
        }
    }

    private void drawNode(DrawContext context, String key, UnlockTree.Node node, boolean hovered) {
        int size = nodeSize();
        int centerX = screenX(node);
        int centerY = screenY(node);
        int x = centerX - size / 2;
        int y = centerY - size / 2;
        context.drawTexture(NODE_CIRCLE, x, y, size, size, 0.0F, 0.0F,
                TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

        Item item = node.icon().map(Registries.ITEM::get).orElse(null);
        if (item != null && item != net.minecraft.item.Items.AIR) {
            float iconScale = MathHelper.clamp((size - 3) / 16.0F, 0.5F, 1.0F);
            context.getMatrices().push();
            context.getMatrices().translate(centerX - 8.0F * iconScale, centerY - 8.0F * iconScale, 0.0F);
            context.getMatrices().scale(iconScale, iconScale, 1.0F);
            context.drawItem(new ItemStack(item), 0, 0);
            context.getMatrices().pop();
        }

        if (zoom < LABEL_ZOOM && !hovered && !key.equals(selected)) {
            return;
        }
        Text label = UnlockScreen.displayName(node.name(), key);
        int labelWidth = Math.min(150, textRenderer.getWidth(label) + 8);
        int labelX = centerX - labelWidth / 2;
        int labelY = y + size + 3;
        context.fill(labelX - 1, labelY - 1, labelX + labelWidth + 1, labelY + 12, 0xE68E6037);
        context.fill(labelX, labelY, labelX + labelWidth, labelY + 11, 0xF22A160E);
        context.drawText(textRenderer, label, labelX + 4, labelY + 2, 0xFFF2DDB0, false);
    }

    String nodeAt(UnlockTree tree, double mouseX, double mouseY) {
        int size = nodeSize();
        for (Map.Entry<String, UnlockTree.Node> entry : tree.nodes().entrySet()) {
            int x = screenX(entry.getValue()) - size / 2;
            int y = screenY(entry.getValue()) - size / 2;
            if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) {
                return entry.getKey();
            }
        }
        return null;
    }

    boolean mouseClicked(UnlockTree tree, double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (button != 0 && button != 2) {
            return false;
        }
        pressedNode = nodeAt(tree, mouseX, mouseY);
        if (button == 0 && pressedNode != null) {
            selected = pressedNode;
            UnlockTree.Node node = tree.nodes().get(pressedNode);
            dragOffsetX = mouseX - screenX(node);
            dragOffsetY = mouseY - screenY(node);
        }
        gestureActive = true;
        pressedButton = button;
        pressMouseX = mouseX;
        pressMouseY = mouseY;
        return true;
    }

    boolean mouseDragged(double mouseX, double mouseY, double deltaX, double deltaY,
            boolean moveNodes, boolean snap, MoveListener listener) {
        if (!gestureActive) {
            return false;
        }
        if (!panning && draggedNode == null
                && Math.hypot(mouseX - pressMouseX, mouseY - pressMouseY) >= DRAG_THRESHOLD) {
            if (moveNodes && pressedButton == 0 && pressedNode != null) {
                draggedNode = pressedNode;
            } else {
                panning = true;
            }
        }
        if (draggedNode != null) {
            double modelX = (mouseX - dragOffsetX - left - renderedPanX()) / (UNIT * zoom);
            double modelY = (mouseY - dragOffsetY - top - renderedPanY()) / (UNIT * zoom);
            if (snap) {
                modelX = Math.round(modelX * 2.0) / 2.0;
                modelY = Math.round(modelY * 2.0) / 2.0;
            }
            listener.move(draggedNode, modelX, modelY);
            return true;
        }
        if (panning) {
            panX += deltaX;
            panY += deltaY;
            return true;
        }
        return false;
    }

    boolean mouseReleased() {
        boolean handled = gestureActive;
        panning = false;
        draggedNode = null;
        pressedNode = null;
        gestureActive = false;
        pressedButton = -1;
        return handled;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        double oldZoom = zoom;
        zoom = MathHelper.clamp(zoom * Math.pow(1.13, amount), MIN_ZOOM, MAX_ZOOM);
        double localX = mouseX - left;
        double localY = mouseY - top;
        panX = localX - (localX - panX) * (zoom / oldZoom);
        panY = localY - (localY - panY) * (zoom / oldZoom);
        return true;
    }

    void center(UnlockTree tree) {
        if (tree == null || tree.nodes().isEmpty()) {
            panX = 0;
            panY = 0;
            zoom = 1.0;
            return;
        }
        double minX = tree.nodes().values().stream().mapToDouble(UnlockTree.Node::x).min().orElse(0.0);
        double maxX = tree.nodes().values().stream().mapToDouble(UnlockTree.Node::x).max().orElse(0.0);
        double minY = tree.nodes().values().stream().mapToDouble(UnlockTree.Node::y).min().orElse(0.0);
        double maxY = tree.nodes().values().stream().mapToDouble(UnlockTree.Node::y).max().orElse(0.0);
        double modelWidth = Math.max(UNIT, (maxX - minX + 2.0) * UNIT);
        double modelHeight = Math.max(UNIT, (maxY - minY + 2.0) * UNIT);
        double fitX = Math.max(1.0, right - left - 24.0) / modelWidth;
        double fitY = Math.max(1.0, bottom - top - 24.0) / modelHeight;
        zoom = MathHelper.clamp(Math.min(1.0, Math.min(fitX, fitY)), MIN_ZOOM, MAX_ZOOM);
        panX = (right - left) / 2.0 - (minX + maxX) * 0.5 * UNIT * zoom;
        panY = (bottom - top) / 2.0 - (minY + maxY) * 0.5 * UNIT * zoom;
    }

    void zoomBy(double amount) {
        double centerX = (left + right) / 2.0;
        double centerY = (top + bottom) / 2.0;
        mouseScrolled(centerX, centerY, amount);
    }

    int zoomPercent() {
        return (int) Math.round(zoom * 100.0);
    }

    Position modelAt(double mouseX, double mouseY, boolean snap) {
        double modelX = (mouseX - left - renderedPanX()) / (UNIT * zoom);
        double modelY = (mouseY - top - renderedPanY()) / (UNIT * zoom);
        if (snap) {
            modelX = Math.round(modelX * 2.0) / 2.0;
            modelY = Math.round(modelY * 2.0) / 2.0;
        }
        return new Position(modelX, modelY);
    }

    int left() {
        return left;
    }

    int top() {
        return top;
    }

    int right() {
        return right;
    }

    int bottom() {
        return bottom;
    }

    private int screenX(UnlockTree.Node node) {
        return left + renderedPanX() + (int) Math.round(node.x() * UNIT * zoom);
    }

    private int screenY(UnlockTree.Node node) {
        return top + renderedPanY() + (int) Math.round(node.y() * UNIT * zoom);
    }

    private int renderedPanX() {
        return (int) Math.round(panX);
    }

    private int renderedPanY() {
        return (int) Math.round(panY);
    }

    private int nodeSize() {
        return MathHelper.clamp((int) Math.round(42 * zoom), 12, 58);
    }

    private record Edge(int x1, int y1, int x2, int y2, boolean primary, int color) {
    }

    record Position(double x, double y) {
    }
}
