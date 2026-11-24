package com.craisinlord.antos.content.client.studio;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;

final class StudioMenu extends StudioPopup {
    private record Item(String label, StudioIcon icon, String shortcut, boolean enabled, boolean checked, boolean danger, boolean separator, boolean heading, Runnable action) { }

    private final AntStudioScreen screen;
    private final int anchorX;
    private final int anchorY;
    private final int anchorH;
    private final List<Item> items = new ArrayList<>();
    private int selected = -1;

    StudioMenu(AntStudioScreen screen, int anchorX, int anchorY, int anchorH) {
        this.screen = screen;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorH = anchorH;
    }

    StudioMenu item(String label, Runnable action) { return item(label, null, "", true, false, action); }
    StudioMenu item(String label, StudioIcon icon, Runnable action) { return item(label, icon, "", true, false, action); }
    StudioMenu check(String label, boolean checked, Runnable action) { return item(label, null, "", true, checked, action); }

    StudioMenu item(String label, StudioIcon icon, String shortcut, boolean enabled, boolean checked, Runnable action) {
        items.add(new Item(label, icon, shortcut, enabled, checked, false, false, false, action));
        return this;
    }

    StudioMenu danger(String label, boolean enabled, Runnable action) {
        items.add(new Item(label, null, "", enabled, false, true, false, false, action));
        return this;
    }

    StudioMenu separator() {
        if (!items.isEmpty() && !items.get(items.size() - 1).separator()) items.add(new Item("", null, "", false, false, false, true, false, null));
        return this;
    }

    StudioMenu heading(String label) {
        items.add(new Item(label, null, "", false, false, false, false, true, null));
        return this;
    }

    boolean empty() { return items.isEmpty(); }
    List<String> labels() { return items.stream().map(Item::label).toList(); }

    void choose(String label) {
        for (Item item : items) if (item.label().equals(label) && item.enabled()) { run(item); return; }
        throw new IllegalArgumentException("No menu item " + label + " in " + labels());
    }

    @Override
    void layout(StudioKit kit) {
        int width = 70;
        int height = 4;
        for (Item item : items) {
            width = Math.max(width, kit.width(item.label()) + 22 + (item.icon() == null ? 0 : 12) + (item.shortcut().isEmpty() ? 0 : kit.width(item.shortcut()) + 12));
            height += item.separator() ? 5 : 14;
        }
        near(kit, anchorX, anchorY, anchorH, Math.min(230, width), height);
    }

    @Override
    void render(StudioKit kit) {
        kit.box(x, y, w, h, StudioTheme.window, StudioTheme.accent);
        int total = 0;
        for (Item item : items) total += item.separator() ? 5 : 14;
        String id = "menu" + System.identityHashCode(this);
        int offset = kit.scroll(id, x + 1, y + 2, w - 2, h - 4, total);
        kit.clip(x + 1, y + 2, w - 2, h - 4);
        int top = y + 2 - offset;
        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);
            if (item.separator()) {
                kit.fill(x + 6, top + 2, w - 12, 1, StudioTheme.line);
                top += 5;
                continue;
            }
            if (item.heading()) {
                kit.text(item.label(), x + 8, top + 3, w - 16, StudioTheme.soft);
                top += 14;
                continue;
            }
            boolean hover = item.enabled() && kit.hover(x + 1, top, w - 2, 14);
            if (item.enabled() && kit.pointed(x + 1, top, w - 2, 14)) selected = index;
            if (item.enabled() && (hover || selected == index)) kit.fill(x + 1, top, w - 2, 14, StudioTheme.selected);
            if (item.checked()) kit.fill(x + 5, top + 5, 4, 4, StudioTheme.accent);
            int left = x + 13;
            if (item.icon() != null) {
                item.icon().draw(kit, left - 2, top + 2, 10);
                left += 12;
            }
            int color = !item.enabled() ? StudioTheme.strongLine : item.danger() ? StudioTheme.error : StudioTheme.text;
            int reserved = item.shortcut().isEmpty() ? 6 : kit.width(item.shortcut()) + 12;
            kit.text(item.label(), left, top + 3, x + w - reserved - left, color);
            if (!item.shortcut().isEmpty()) kit.right(item.shortcut(), x + w - 6, top + 3, StudioTheme.soft);
            if (item.enabled()) kit.press(x + 1, top, w - 2, 14, () -> run(item));
            top += 14;
        }
        kit.unclip();
    }

    private void run(Item item) {
        screen.closePopup(this);
        item.action().run();
    }

    @Override
    boolean key(int key, int modifiers) {
        if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
            int step = key == InputConstants.KEY_DOWN ? 1 : -1;
            for (int tries = 0; tries < items.size(); tries++) {
                selected = Math.floorMod(selected < 0 && step < 0 ? items.size() - 1 : selected + step, items.size());
                if (items.get(selected).enabled()) return true;
            }
            return true;
        }
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && selected >= 0 && items.get(selected).enabled()) {
            run(items.get(selected));
            return true;
        }
        return false;
    }
}
