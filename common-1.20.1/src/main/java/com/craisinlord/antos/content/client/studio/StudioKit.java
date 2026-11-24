package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.client.AntOSUiSounds;
import com.craisinlord.antos.content.client.screen.StudioAssets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class StudioKit {
    static final int NORMAL = 0;
    static final int ON = 1;
    static final int PRIMARY = 2;
    static final int GHOST = 3;
    static final int DANGER = 4;
    static final int ADD = 5;

    interface Action { void run(Click click); }
    interface Capture { void move(int x, int y); default void release(int x, int y) { } }
    record Click(int button, boolean twice, boolean shift, boolean control, int x, int y) { }
    private record Hit(int layer, String id, int x, int y, int w, int h, Action action, Payload payload, boolean focusable) { }
    private record Zone(int layer, int x, int y, int w, int h, String id, int maximum) { }
    private record Wheel(int layer, int x, int y, int w, int h, DoubleConsumer action) { }
    private record Text(int layer, int x, int y, int w, int h, String id, AbstractWidget widget) { }
    record Payload(Object value, String label) { }

    final StudioAssets assets = new StudioAssets();
    private final Font font = Minecraft.getInstance().font;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final List<Wheel> wheels = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private final Deque<int[]> clips = new ArrayDeque<>();
    private final Map<String, AbstractWidget> widgets = new HashMap<>();
    private final Map<String, Integer> seen = new HashMap<>();
    private final Map<String, Integer> scrolls = new HashMap<>();
    private final Map<String, Runnable> enters = new HashMap<>();
    private final Map<String, String> numberText = new HashMap<>();
    private final Map<String, Integer> autoIds = new HashMap<>();
    private final Set<String> syncing = new HashSet<>();
    private List<Hit> liveHits = List.of();
    private List<Zone> liveZones = List.of();
    private List<Wheel> liveWheels = List.of();
    private List<Text> liveTexts = List.of();
    private GuiGraphics g;
    private int frame;
    private int layer;
    private int topLayer;
    private int nextTop;
    private float pose = 1.0F;
    private String focus = "";
    private boolean keyboard;
    private String tooltip = "";
    private Capture capture;
    private Payload dragging;
    private int dragX;
    private int dragY;
    private Hit pressed;
    private int pressX;
    private int pressY;
    private boolean moved;
    private boolean dragActive;
    private Runnable drop;
    private long lastClick;
    private String lastClickId = "";
    private Click lastPress = new Click(0, false, false, false, 0, 0);
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;
    private boolean pointed;
    int mx;
    int my;
    int width;
    int height;

    Font font() { return font; }
    GuiGraphics g() { return g; }
    float pose() { return pose; }

    void begin(GuiGraphics graphics, int mouseX, int mouseY, int width, int height, float pose) {
        g = graphics;
        pointed = mouseX != lastX || mouseY != lastY;
        lastX = mouseX;
        lastY = mouseY;
        mx = mouseX;
        my = mouseY;
        this.width = width;
        this.height = height;
        this.pose = pose;
        frame++;
        hits.clear();
        zones.clear();
        wheels.clear();
        texts.clear();
        clips.clear();
        autoIds.clear();
        enters.clear();
        tooltip = "";
        layer = 0;
        nextTop = 0;
        drop = null;
    }

    void end() {
        g.flush();
        topLayer = nextTop;
        liveHits = List.copyOf(hits);
        liveZones = List.copyOf(zones);
        liveWheels = List.copyOf(wheels);
        liveTexts = List.copyOf(texts);
        widgets.keySet().removeIf(id -> frame - seen.getOrDefault(id, 0) > 60 && !id.equals(focus));
        if (!focus.isEmpty() && keyboard && liveHits.stream().noneMatch(hit -> hit.id().equals(focus)) && !widgets.containsKey(focus)) focus = "";
        if (keyboard && !focus.isEmpty()) for (Hit hit : liveHits) if (hit.id().equals(focus) && hit.layer() == topLayer) {
            push(Math.min(1500, overlayZ()));
            outline(hit.x() - 1, hit.y() - 1, hit.w() + 2, hit.h() + 2, StudioTheme.strong);
            pop();
            break;
        }
        if (dragActive && dragging != null) {
            push(overlayZ());
            int w = Math.min(140, font.width(dragging.label()) + 10);
            g.fill(mx + 6, my + 4, mx + 6 + w, my + 18, StudioTheme.window);
            outline(mx + 6, my + 4, w, 14, drop == null ? StudioTheme.strongLine : StudioTheme.accent);
            text(dragging.label(), mx + 11, my + 7, w - 8, StudioTheme.text);
            pop();
        } else if (!tooltip.isEmpty() && capture == null) drawTooltip();
    }

    void layer(int value) {
        layer = value;
        nextTop = Math.max(nextTop, value);
        g.flush();
    }

    int layer() { return layer; }

    private int overlayZ() { return Math.min(2000, (int) (9500.0F / pose) - 200); }

    void push(int z) {
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, z);
    }

    void pop() {
        g.flush();
        g.pose().popPose();
    }

    private boolean live() { return layer >= topLayer; }

    void clip(int x, int y, int w, int h) {
        int left = x;
        int top = y;
        int right = x + Math.max(0, w);
        int bottom = y + Math.max(0, h);
        int[] outer = clips.peek();
        if (outer != null) {
            left = Math.max(left, outer[0]);
            top = Math.max(top, outer[1]);
            right = Math.max(left, Math.min(right, outer[2]));
            bottom = Math.max(top, Math.min(bottom, outer[3]));
        }
        clips.push(new int[]{left, top, right, bottom});
        g.flush();
        g.enableScissor((int) Math.floor(left * pose), (int) Math.floor(top * pose), (int) Math.ceil(right * pose), (int) Math.ceil(bottom * pose));
    }

    void unclip() {
        g.flush();
        clips.pop();
        g.disableScissor();
    }

    int depth() { return clips.size(); }

    void unwind(int depth) {
        while (clips.size() > depth) unclip();
    }

    boolean visible(int y, int h) {
        int[] clip = clips.peek();
        return clip == null || y + h > clip[1] && y < clip[3];
    }

    private boolean clipped(int x, int y) {
        int[] clip = clips.peek();
        return clip != null && (x < clip[0] || x >= clip[2] || y < clip[1] || y >= clip[3]);
    }

    boolean hover(int x, int y, int w, int h) {
        return live() && capture == null && inside(x, y, w, h, mx, my) && !clipped(mx, my);
    }

    boolean pointed(int x, int y, int w, int h) { return pointed && hover(x, y, w, h); }

    boolean over(int x, int y, int w, int h) { return live() && inside(x, y, w, h, mx, my) && !clipped(mx, my); }

    static boolean inside(int x, int y, int w, int h, double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }

    private String autoId(String base) {
        int count = autoIds.merge(base, 1, Integer::sum);
        return layer + ":" + base + "#" + count;
    }

    void hit(int x, int y, int w, int h, Action action) { hit(autoId("hit"), x, y, w, h, action, null, false); }

    void press(int x, int y, int w, int h, Runnable action) {
        hit(autoId("press"), x, y, w, h, click -> { if (click.button() == 0) AntOSUiSounds.action(action); }, null, true);
    }

    void grab(int x, int y, int w, int h, Payload payload, Action action) { hit(autoId("grab"), x, y, w, h, action, payload, true); }

    void block(int x, int y, int w, int h) { hit(autoId("block"), x, y, w, h, click -> { }, null, false); }

    private void hit(String id, int x, int y, int w, int h, Action action, Payload payload, boolean focusable) {
        int[] clip = clips.peek();
        int left = x;
        int top = y;
        int right = x + w;
        int bottom = y + h;
        if (clip != null) {
            left = Math.max(left, clip[0]);
            top = Math.max(top, clip[1]);
            right = Math.min(right, clip[2]);
            bottom = Math.min(bottom, clip[3]);
        }
        if (right <= left || bottom <= top) return;
        hits.add(new Hit(layer, id, left, top, right - left, bottom - top, action, payload, focusable));
    }

    boolean accepting(Predicate<Object> accept) { return dragActive && dragging != null && accept.test(dragging.value()); }

    boolean dropZone(int x, int y, int w, int h, Predicate<Object> accept, Consumer<Object> action) {
        if (!accepting(accept)) return false;
        boolean over = live() && inside(x, y, w, h, mx, my) && !clipped(mx, my);
        outline(x, y, w, h, over ? StudioTheme.accent : StudioTheme.strongLine);
        if (over) {
            Object value = dragging.value();
            drop = () -> action.accept(value);
        }
        return over;
    }

    boolean draggingSomething() { return dragActive; }

    void capture(Capture value) { capture = value; }
    boolean captured() { return capture != null; }

    void fill(int x, int y, int w, int h, int color) { g.fill(x, y, x + w, y + h, color); }

    void outline(int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    void dashed(int x, int y, int w, int h, int color) {
        for (int at = 0; at < w; at += 4) {
            g.fill(x + at, y, Math.min(x + w, x + at + 2), y + 1, color);
            g.fill(x + at, y + h - 1, Math.min(x + w, x + at + 2), y + h, color);
        }
        for (int at = 0; at < h; at += 4) {
            g.fill(x, y + at, x + 1, Math.min(y + h, y + at + 2), color);
            g.fill(x + w - 1, y + at, x + w, Math.min(y + h, y + at + 2), color);
        }
    }

    void box(int x, int y, int w, int h, int fill, int border) {
        fill(x, y, w, h, fill);
        outline(x, y, w, h, border);
    }

    String trim(String value, int width) {
        if (width <= 0) return "";
        if (font.width(value) <= width) return value;
        int dots = font.width("...");
        return width <= dots ? font.plainSubstrByWidth(value, width) : font.plainSubstrByWidth(value, width - dots) + "...";
    }

    int text(String value, int x, int y, int width, int color) {
        String shown = trim(value, width);
        g.drawString(font, shown, x, y, color, false);
        if (!shown.equals(value) && hover(x, y - 2, width, 12)) tooltip(value);
        return font.width(shown);
    }

    int right(String value, int x, int y, int color) {
        int w = font.width(value);
        g.drawString(font, value, x - w, y, color, false);
        return w;
    }

    int width(String value) { return font.width(value); }

    int wrap(String value, int x, int y, int width, int color) { return wrap(Component.literal(value), x, y, width, color, 11); }

    int wrap(Component value, int x, int y, int width, int color, int lineHeight) {
        for (FormattedCharSequence line : font.split(value, Math.max(10, width))) {
            if (visible(y, lineHeight)) g.drawString(font, line, x, y, color, false);
            y += lineHeight;
        }
        return y;
    }

    int wrapHeight(Component value, int width, int lineHeight) { return font.split(value, Math.max(10, width)).size() * lineHeight; }

    void tooltip(String value) { tooltip = value; }

    private void drawTooltip() {
        List<FormattedCharSequence> lines = font.split(Component.literal(tooltip), Math.min(220, Math.max(60, width - 20)));
        int w = lines.stream().mapToInt(font::width).max().orElse(0) + 8;
        int h = lines.size() * 10 + 5;
        int x = Math.max(2, Math.min(width - w - 2, mx + 8));
        int y = my + 12 + h > height ? my - h - 4 : my + 12;
        push(overlayZ());
        box(x, Math.max(2, y), w, h, StudioTheme.panel, StudioTheme.strongLine);
        for (int index = 0; index < lines.size(); index++) g.drawString(font, lines.get(index), x + 4, Math.max(2, y) + 3 + index * 10, StudioTheme.text, false);
        pop();
    }

    int buttonWidth(String label) { return font.width(label) + 12; }

    int button(int x, int y, String label, int style, Runnable action) { return button(x, y, buttonWidth(label), 16, label, style, true, "", action); }

    int button(int x, int y, int w, int h, String label, int style, boolean enabled, String tip, Runnable action) {
        return button(x, y, w, h, label, style, enabled, tip, click -> { if (click.button() == 0) AntOSUiSounds.action(action); });
    }

    int button(int x, int y, int w, int h, String label, int style, boolean enabled, String tip, Action action) {
        boolean hover = enabled && hover(x, y, w, h);
        int fill = style == PRIMARY ? hover ? StudioTheme.strong : StudioTheme.accent : style == ON ? StudioTheme.hover : hover ? StudioTheme.hover : StudioTheme.button;
        int border = style == ON || style == PRIMARY || hover ? StudioTheme.accent : StudioTheme.line;
        int color = style == PRIMARY ? StudioTheme.ink : style == ON || hover ? StudioTheme.accent : style == DANGER ? StudioTheme.error
                : style == GHOST ? StudioTheme.muted : style == ADD ? StudioTheme.accent : StudioTheme.text;
        if (!enabled) {
            fill = StudioTheme.section;
            border = StudioTheme.line;
            color = StudioTheme.strongLine;
        }
        if (style != GHOST && style != ADD) box(x, y, w, h, fill, border);
        else if (hover) fill(x, y, w, h, StudioTheme.hover);
        String shown = trim(label, w - 4);
        g.drawString(font, shown, x + (w - font.width(shown) + 1) / 2, y + (h - 8) / 2, color, false);
        if (hover(x, y, w, h)) {
            if (!tip.isEmpty()) tooltip = tip;
            else if (!shown.equals(label)) tooltip = label;
        }
        if (enabled) hit(autoId("button:" + label), x, y, w, h, action, null, true);
        return w;
    }

    int iconButton(int x, int y, int size, ResourceLocation icon, boolean enabled, String tip, Runnable action) {
        boolean hover = enabled && hover(x, y, size, size);
        box(x, y, size, size, hover ? StudioTheme.hover : StudioTheme.button, hover ? StudioTheme.accent : StudioTheme.line);
        if (!enabled) g.setColor(0.4F, 0.4F, 0.4F, 1.0F);
        StudioAssets.icon(g, icon, x + (size - 10) / 2, y + (size - 10) / 2, 10);
        if (!enabled) {
            g.flush();
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (hover(x, y, size, size) && !tip.isEmpty()) tooltip = tip;
        if (enabled) hit(autoId("icon:" + icon), x, y, size, size, click -> { if (click.button() == 0) AntOSUiSounds.action(action); }, null, true);
        return size;
    }

    int chipWidth(String label, boolean icon, boolean on) { return font.width(label) + 10 + (icon ? 12 : 0) + (on ? 7 : 0); }

    int chip(int x, int y, int maximum, String label, StudioIcon icon, boolean on, String tip, Runnable action) {
        return chip(x, y, maximum, label, icon, on, tip, click -> { if (click.button() == 0) AntOSUiSounds.action(action); });
    }

    int chip(int x, int y, int maximum, String label, StudioIcon icon, boolean on, String tip, Action action) {
        int w = Math.max(16, Math.min(maximum, chipWidth(label, icon != null, on)));
        boolean hover = hover(x, y, w, 15);
        box(x, y, w, 15, on || hover ? StudioTheme.hover : StudioTheme.button, on || hover ? StudioTheme.accent : StudioTheme.line);
        int left = x + 5;
        if (on) {
            fill(left, y + 6, 4, 4, StudioTheme.accent);
            left += 7;
        }
        if (icon != null) {
            icon.draw(this, left - 1, y + 3, 9);
            left += 11;
        }
        text(label, left, y + 4, x + w - 4 - left, on ? StudioTheme.strong : hover ? StudioTheme.accent : StudioTheme.muted);
        if (hover && !tip.isEmpty()) tooltip = tip;
        hit(autoId("chip:" + label), x, y, w, 15, action, null, true);
        return w;
    }

    int drop(int x, int y, int w, String label, StudioIcon icon, Action action) {
        boolean hover = hover(x, y, w, 16);
        box(x, y, w, 16, hover ? StudioTheme.hover : StudioTheme.button, hover ? StudioTheme.accent : StudioTheme.line);
        int left = x + 5;
        if (icon != null) {
            icon.draw(this, left - 1, y + 3, 10);
            left += 12;
        }
        text(label, left, y + 4, x + w - 13 - left, hover ? StudioTheme.accent : StudioTheme.text);
        arrow(x + w - 10, y + 6, true, StudioTheme.soft);
        hit(autoId("drop:" + label), x, y, w, 16, action, null, true);
        return w;
    }

    void arrow(int x, int y, boolean down, int color) {
        if (down) for (int row = 0; row < 3; row++) fill(x + row, y + row, 5 - row * 2, 1, color);
        else for (int column = 0; column < 3; column++) fill(x + column + 1, y - 1 + column, 1, 5 - column * 2, color);
    }

    int check(int x, int y, String label, boolean on, Consumer<Boolean> action) {
        int w = 14 + font.width(label);
        boolean hover = hover(x, y, w, 13);
        box(x, y + 2, 9, 9, on ? StudioTheme.accent : StudioTheme.desk, hover ? StudioTheme.accent : StudioTheme.strongLine);
        if (on) {
            fill(x + 2, y + 6, 2, 2, StudioTheme.ink);
            fill(x + 4, y + 7, 1, 2, StudioTheme.ink);
            fill(x + 5, y + 5, 1, 2, StudioTheme.ink);
            fill(x + 6, y + 4, 1, 2, StudioTheme.ink);
        }
        g.drawString(font, label, x + 13, y + 3, hover ? StudioTheme.accent : StudioTheme.text, false);
        hit(autoId("check:" + label), x, y, w, 13, click -> { if (click.button() == 0) AntOSUiSounds.action(() -> action.accept(!on)); }, null, true);
        return w;
    }

    int segments(int x, int y, List<String> labels, int selected, IntConsumer action) {
        int total = 2;
        for (String label : labels) total += font.width(label) + 10;
        outline(x, y, total, 16, StudioTheme.line);
        int left = x + 1;
        for (int index = 0; index < labels.size(); index++) {
            int choice = index;
            String label = labels.get(index);
            int w = font.width(label) + 10;
            boolean hover = hover(left, y + 1, w, 14);
            fill(left, y + 1, w, 14, index == selected ? StudioTheme.hover : StudioTheme.button);
            g.drawString(font, label, left + 5, y + 4, index == selected || hover ? StudioTheme.accent : StudioTheme.muted, false);
            press(left, y + 1, w, 14, () -> action.accept(choice));
            left += w;
        }
        return total;
    }

    void slot(int x, int y, int size, StudioIcon icon, boolean focused, String tip, Action action) {
        boolean hover = hover(x, y, size, size);
        box(x, y, size, size, StudioTheme.desk, hover || focused ? StudioTheme.accent : StudioTheme.strongLine);
        if (icon != null) icon.draw(this, x + 2, y + 2, size - 4);
        if (hover && !tip.isEmpty()) tooltip = tip;
        if (action != null) hit(autoId("slot"), x, y, size, size, action, null, true);
    }

    void dot(int x, int y, boolean problem, boolean warning) {
        if (!problem) {
            fill(x, y, 4, 4, StudioTheme.accent);
            return;
        }
        int color = warning ? StudioTheme.warning : StudioTheme.error;
        fill(x + 2, y - 1, 1, 1, color);
        fill(x + 1, y, 3, 2, color);
        fill(x, y + 2, 5, 2, color);
    }

    void scrollbar(int x, int y, int h, int total, int offset, int maximum, IntConsumer setter) {
        if (maximum <= 0 || h < 8) return;
        int thumb = Math.max(10, h * h / Math.max(h, total));
        int top = y + (h - thumb) * offset / Math.max(1, maximum);
        boolean hover = hover(x - 1, y, 5, h);
        fill(x, y, 3, h, StudioTheme.section);
        fill(x, top, 3, thumb, hover || captured() ? StudioTheme.muted : StudioTheme.strongLine);
        hit(x - 2, y, 7, h, click -> {
            if (click.button() != 0) return;
            int grab = click.y() >= top && click.y() < top + thumb ? click.y() - top : thumb / 2;
            Capture drag = (px, py) -> setter.accept(Math.max(0, Math.min(maximum, Math.round((py - grab - y) * maximum / (float) Math.max(1, h - thumb)))));
            drag.move(click.x(), click.y());
            capture(drag);
        });
    }

    int scroll(String id, int x, int y, int w, int h, int content) {
        int maximum = Math.max(0, content - h);
        int offset = Math.max(0, Math.min(maximum, scrolls.getOrDefault(id, 0)));
        scrolls.put(id, offset);
        zones.add(new Zone(layer, x, y, w, h, id, maximum));
        scrollbar(x + w - 3, y, h, content, offset, maximum, value -> scrolls.put(id, value));
        return offset;
    }

    void scrollTo(String id, int value) { scrolls.put(id, Math.max(0, value)); }
    int scrollOf(String id) { return scrolls.getOrDefault(id, 0); }
    void wheel(int x, int y, int w, int h, DoubleConsumer action) { wheels.add(new Wheel(layer, x, y, w, h, action)); }

    static final class Field {
        String hint = "";
        int maximum = 512;
        boolean bad;
        boolean borderless;
        boolean right;
        int color;
        Runnable enter;

        Field hint(String value) { hint = value; return this; }
        Field max(int value) { maximum = value; return this; }
        Field bad(boolean value) { bad = value; return this; }
        Field plain() { borderless = true; return this; }
        Field color(int value) { color = value; return this; }
        Field enter(Runnable value) { enter = value; return this; }
    }

    void field(String id, int x, int y, int w, String value, Field options, Consumer<String> change) {
        seen.put(id, frame);
        EditBox box = widgets.get(id) instanceof EditBox existing ? existing : null;
        if (box == null) {
            box = new EditBox(font, 0, 0, 100, 12, Component.empty());
            box.setBordered(false);
            widgets.put(id, box);
        }
        EditBox current = box;
        box.setMaxLength(Math.max(options.maximum, value.length()));
        box.setResponder(text -> { if (!syncing.contains(id)) change.accept(text); });
        if (!box.getValue().equals(value)) {
            syncing.add(id);
            try {
                int cursor = box.getCursorPosition();
                box.setValue(value);
                caret(box, id.equals(focus) ? cursor : 0);
            } finally { syncing.remove(id); }
        }
        box.setFocused(id.equals(focus));
        box.setTextColor(options.color == 0 ? StudioTheme.text : options.color);
        boolean hover = hover(x, y, w, 16);
        if (!options.borderless || hover || id.equals(focus))
            box(x, y, w, 16, StudioTheme.desk, options.bad ? StudioTheme.error : id.equals(focus) ? StudioTheme.accent : hover ? StudioTheme.strongLine : StudioTheme.line);
        box.setX(x + 4);
        box.setY(y + 4);
        box.setWidth(Math.max(8, w - 8));
        box.setHeight(10);
        if (value.isEmpty() && !id.equals(focus) && !options.hint.isEmpty()) text(options.hint, x + 4, y + 4, w - 8, StudioTheme.soft);
        else {
            if (value.isEmpty() && !options.hint.isEmpty()) text(options.hint, x + 6, y + 4, w - 10, StudioTheme.strongLine);
            current.render(g, mx, my, 0);
        }
        if (options.enter != null) enters.put(id, options.enter);
        texts.add(new Text(layer, x, y, w, 16, id, box));
        hit(id, x, y, w, 16, click -> focusText(id, click), null, true);
    }

    void area(String id, int x, int y, int w, int h, String value, int limit, String hint, boolean bad, Consumer<String> change) {
        seen.put(id, frame);
        StudioArea area = widgets.get(id) instanceof StudioArea existing ? existing : null;
        if (area == null) {
            area = new StudioArea(this, limit);
            area.setWidth(Math.max(10, w - 12));
            widgets.put(id, area);
        }
        area.listener(change);
        area.setX(x + 4);
        area.setY(y + 4);
        area.setWidth(Math.max(10, w - 12));
        area.setHeight(Math.max(10, h - 7));
        area.sync(value);
        area.setFocused(id.equals(focus) && area.isFocused() || id.equals(focus));
        boolean hover = hover(x, y, w, h);
        box(x, y, w, h, StudioTheme.desk, bad ? StudioTheme.error : id.equals(focus) ? StudioTheme.accent : hover ? StudioTheme.strongLine : StudioTheme.line);
        if (value.isEmpty() && !id.equals(focus)) text(hint, x + 4, y + 4, w - 8, StudioTheme.soft);
        area.render(g, mx, my, 0);
        StudioArea scrolled = area;
        texts.add(new Text(layer, x, y, w, h, id, scrolled));
        hit(id, x, y, w, h, click -> focusText(id, click), null, true);
    }

    int areaHeight(String id, int minimum, int maximum) {
        return widgets.get(id) instanceof StudioArea area ? Math.max(minimum, Math.min(maximum, area.contentHeight() + 9)) : minimum;
    }

    void insert(String id, String text) {
        if (widgets.get(id) instanceof StudioArea area) {
            focus(id);
            area.insert(text);
        }
    }

    void number(String id, int x, int y, int w, double value, double minimum, double maximum, boolean whole, DoubleConsumer change) {
        String formatted = format(value, whole);
        String shown = id.equals(focus) && numberText.containsKey(id) ? numberText.get(id) : formatted;
        boolean bad = parse(shown, minimum, maximum, whole) == null;
        field(id, x, y, w, shown, new Field().max(16).bad(bad), text -> {
            numberText.put(id, text);
            Double parsed = parse(text, minimum, maximum, whole);
            if (parsed != null) change.accept(parsed);
        });
        if (!id.equals(focus)) numberText.remove(id);
        if (hover(x, y, w, 16)) {
            if (bad) tooltip = StudioText.t("number.range", format(minimum, whole), format(maximum, whole));
            wheel(x, y, w, 16, amount -> {
                if (!id.equals(focus)) return;
                double next = Math.max(minimum, Math.min(maximum, value + Math.signum(amount) * (whole ? Screen.hasShiftDown() ? 10 : 1 : Screen.hasShiftDown() ? 0.1 : 0.01)));
                numberText.remove(id);
                change.accept(whole ? Math.rint(next) : Math.round(next * 1000) / 1000.0);
            });
        }
    }

    private static Double parse(String text, double minimum, double maximum, boolean whole) {
        try {
            double value = whole ? Long.parseLong(text.trim()) : Double.parseDouble(text.trim());
            return Double.isFinite(value) && value >= minimum && value <= maximum ? value : null;
        } catch (NumberFormatException exception) { return null; }
    }

    static String format(double value, boolean whole) {
        if (whole || value == Math.rint(value)) return Long.toString((long) value);
        return Double.toString(value);
    }

    private void focusText(String id, Click click) {
        AbstractWidget widget = widgets.get(id);
        if (widget == null) return;
        boolean already = id.equals(focus);
        focus(id);
        keyboard = false;
        widget.setFocused(true);
        if (widget instanceof EditBox box) {
            if (click.button() == 1) { box.setValue(""); return; }
            box.mouseClicked(Math.max(box.getX(), Math.min(box.getX() + box.getWidth() - 1, click.x())), box.getY() + 2, 0);
            if (!click.shift()) box.setHighlightPos(box.getCursorPosition());
            if (click.twice() || !already && numberText(id)) selectAll(box);
            int origin = box.getCursorPosition();
            capture((px, py) -> {
                int at = Math.max(0, px - box.getX());
                box.setHighlightPos(origin);
                box.mouseClicked(Math.max(box.getX(), Math.min(box.getX() + box.getWidth() - 1, px)), box.getY() + 2, 0);
                box.setHighlightPos(origin);
            });
        } else if (widget instanceof StudioArea area) {
            area.mouseClicked(click.x(), click.y(), click.button());
            capture((px, py) -> area.mouseDragged(px, py, 0, 0, 0));
        }
    }

    private boolean numberText(String id) { return id.endsWith("!number"); }

    void focus(String id) {
        if (focus.equals(id)) return;
        AbstractWidget previous = widgets.get(focus);
        if (previous != null) previous.setFocused(false);
        numberText.remove(focus);
        focus = id;
        AbstractWidget next = widgets.get(id);
        if (next != null) next.setFocused(true);
    }

    void focusAll(String id) {
        focus(id);
        if (widgets.get(id) instanceof EditBox box) selectAll(box);
    }

    private static void caret(EditBox box, int position) {
        box.moveCursorTo(position);
        box.setHighlightPos(box.getCursorPosition());
    }

    private static void selectAll(EditBox box) {
        box.moveCursorToEnd();
        box.setHighlightPos(0);
    }

    void blur() { focus(""); keyboard = false; }
    String focused() { return focus; }
    boolean typing() { return widgets.containsKey(focus); }
    boolean typingArea() { return widgets.get(focus) instanceof StudioArea; }

    boolean mouseDown(int x, int y, int button) {
        long now = System.currentTimeMillis();
        for (int index = liveHits.size() - 1; index >= 0; index--) {
            Hit hit = liveHits.get(index);
            if (hit.layer() < topLayer || !inside(hit.x(), hit.y(), hit.w(), hit.h(), x, y)) continue;
            boolean twice = now - lastClick < 300 && lastClickId.equals(hit.id()) && Math.abs(lastPress.x() - x) < 4 && Math.abs(lastPress.y() - y) < 4;
            lastClick = twice ? 0 : now;
            lastClickId = hit.id();
            Click click = new Click(button, twice, Screen.hasShiftDown(), Screen.hasControlDown(), x, y);
            lastPress = click;
            if (!widgets.containsKey(hit.id()) && button != 2) blur();
            if (hit.payload() != null && button == 0) {
                pressed = hit;
                pressX = x;
                pressY = y;
                moved = false;
                return true;
            }
            hit.action().run(click);
            return true;
        }
        blur();
        return false;
    }

    void mouseMove(int x, int y) {
        mx = x;
        my = y;
        if (capture != null) { capture.move(x, y); return; }
        if (pressed != null && !moved && Math.abs(x - pressX) + Math.abs(y - pressY) > 4) {
            moved = true;
            dragActive = true;
            dragging = pressed.payload();
        }
    }

    void mouseUp(int x, int y, int button) {
        if (capture != null) {
            Capture ended = capture;
            capture = null;
            ended.release(x, y);
            return;
        }
        Hit hit = pressed;
        pressed = null;
        if (hit == null) return;
        if (dragActive) {
            Runnable action = drop;
            dragActive = false;
            dragging = null;
            if (action != null) AntOSUiSounds.action(action);
        } else hit.action().run(new Click(0, lastPress.twice(), lastPress.shift(), lastPress.control(), x, y));
    }

    void cancelDrag() {
        capture = null;
        pressed = null;
        dragActive = false;
        dragging = null;
    }

    boolean mouseWheel(int x, int y, double amount) {
        for (int index = liveWheels.size() - 1; index >= 0; index--) {
            Wheel wheel = liveWheels.get(index);
            if (wheel.layer() >= topLayer && inside(wheel.x(), wheel.y(), wheel.w(), wheel.h(), x, y)) {
                wheel.action().accept(amount);
                return true;
            }
        }
        for (int index = liveTexts.size() - 1; index >= 0; index--) {
            Text text = liveTexts.get(index);
            if (text.layer() >= topLayer && text.widget() instanceof StudioArea area && inside(text.x(), text.y(), text.w(), text.h(), x, y) && area.wheel(amount)) return true;
        }
        for (int index = liveZones.size() - 1; index >= 0; index--) {
            Zone zone = liveZones.get(index);
            if (zone.layer() < topLayer || !inside(zone.x(), zone.y(), zone.w(), zone.h(), x, y) || zone.maximum() <= 0) continue;
            scrolls.put(zone.id(), Math.max(0, Math.min(zone.maximum(), scrolls.getOrDefault(zone.id(), 0) - (int) Math.round(amount * 20))));
            return true;
        }
        return false;
    }

    boolean type(char character, int modifiers) {
        AbstractWidget widget = widgets.get(focus);
        return widget != null && widget.charTyped(character, modifiers);
    }

    boolean key(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB) {
            List<Hit> order = liveHits.stream().filter(hit -> hit.focusable() && hit.layer() >= topLayer).toList();
            if (order.isEmpty()) return true;
            int index = -1;
            for (int at = 0; at < order.size(); at++) if (order.get(at).id().equals(focus)) index = at;
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            Hit next = order.get(Math.floorMod(index < 0 ? step > 0 ? 0 : order.size() - 1 : index + step, order.size()));
            keyboard = true;
            if (widgets.containsKey(next.id())) focusAll(next.id());
            else focus(next.id());
            return true;
        }
        AbstractWidget widget = widgets.get(focus);
        if (widget != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { blur(); return true; }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && widget instanceof EditBox) {
                Runnable enter = enters.get(focus);
                if (enter != null) enter.run();
                else blur();
                return true;
            }
            if (widget.keyPressed(key, scan, modifiers)) return true;
            boolean command = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0;
            boolean special = key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25;
            boolean vertical = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN;
            return !command && !special && !vertical;
        }
        if (!focus.isEmpty() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)) {
            for (Hit hit : liveHits) if (hit.id().equals(focus) && hit.layer() >= topLayer) {
                hit.action().run(new Click(0, false, false, false, hit.x() + hit.w() / 2, hit.y() + hit.h() / 2));
                return true;
            }
        }
        if (!focus.isEmpty() && key == GLFW.GLFW_KEY_ESCAPE && keyboard) { blur(); return true; }
        return false;
    }

    boolean anyAt(int x, int y) {
        return liveHits.stream().anyMatch(hit -> hit.layer() >= topLayer && inside(hit.x(), hit.y(), hit.w(), hit.h(), x, y));
    }

    List<String> hitIds() { return liveHits.stream().map(Hit::id).toList(); }

    int[] hitBounds(String prefix) {
        for (Hit hit : liveHits) if (hit.layer() >= topLayer && hit.id().contains(prefix)) return new int[]{hit.x(), hit.y(), hit.w(), hit.h()};
        return null;
    }
}
