package com.craisinlord.antos.content.client.studio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

final class StudioPicker extends StudioPopup {
    enum Type { ITEM, TAG, ENTITY, CHOICE }
    record Picked(Type type, String id, ItemStack stack) { }
    private record Entry(Type type, String id, String name, String search, StudioIcon icon, ItemStack stack) { }

    private static List<Entry> items;
    private static Object language;
    private static List<Entry> entities;
    private static Object entityLanguage;

    private final AntStudioScreen screen;
    private final int anchorX;
    private final int anchorY;
    private final int anchorH;
    private final Consumer<Picked> result;
    private final List<String> tabs = new ArrayList<>();
    private String choices = "";
    private String title = "";
    private String query = "";
    private String tab = "";
    private String createLabel = "";
    private Consumer<String> create;
    private boolean custom;
    private boolean clearable;
    private boolean keepOpen;
    private int selected;
    private int columns = 1;
    private int cellHeight = 14;
    private List<Entry> shown = List.of();
    private String shownFor = "";
    private final String id = "picker" + System.identityHashCode(this);

    StudioPicker(AntStudioScreen screen, int anchorX, int anchorY, int anchorH, Consumer<Picked> result) {
        this.screen = screen;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorH = anchorH;
        this.result = result;
    }

    StudioPicker items(boolean tags, boolean mobs) {
        tabs.add("all");
        tabs.add("recent");
        tabs.add("held");
        if (tags) tabs.add("tags");
        if (mobs) tabs.add("mobs");
        tab = screen.prefs.recentItems().isEmpty() ? "all" : "recent";
        custom = true;
        return this;
    }

    StudioPicker entities() {
        tabs.add("mobs");
        tab = "mobs";
        custom = true;
        return this;
    }

    StudioPicker choices(String type, String title) {
        choices = type;
        this.title = title;
        tab = "choices";
        tabs.add("choices");
        return this;
    }

    StudioPicker custom() { custom = true; return this; }
    StudioPicker clearable() { clearable = true; return this; }
    StudioPicker keepOpen() { keepOpen = true; return this; }

    StudioPicker create(String label, Consumer<String> action) {
        createLabel = label;
        create = action;
        return this;
    }

    String field() { return id + "search"; }

    @Override
    void layout(StudioKit kit) {
        boolean grid = !tab.equals("choices");
        near(kit, anchorX, anchorY, anchorH, grid ? 236 : 200, grid ? 152 : 168);
    }

    private static List<Entry> allItems() {
        Object current = net.minecraft.locale.Language.getInstance();
        if (items == null || language != current) {
            List<Entry> list = new ArrayList<>();
            for (var item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                ItemStack stack = StudioIcon.stack(id);
                String name = stack.getHoverName().getString();
                list.add(new Entry(Type.ITEM, id, name, (name + " " + id).toLowerCase(Locale.ROOT), StudioIcon.stack(stack), stack));
            }
            items = List.copyOf(list);
            language = current;
        }
        return items;
    }

    private static List<Entry> allEntities() {
        Object current = net.minecraft.locale.Language.getInstance();
        if (entities == null || entityLanguage != current) {
            List<Entry> list = new ArrayList<>();
            for (var type : BuiltInRegistries.ENTITY_TYPE) {
                if (type.getCategory() == net.minecraft.world.entity.MobCategory.MISC && net.minecraft.world.item.SpawnEggItem.byId(type) == null) continue;
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                String name = type.getDescription().getString();
                list.add(new Entry(Type.ENTITY, id, name, (name + " " + id).toLowerCase(Locale.ROOT), StudioIcon.entity(id), ItemStack.EMPTY));
            }
            list.sort(Comparator.comparing(Entry::name));
            entities = List.copyOf(list);
            entityLanguage = current;
        }
        return entities;
    }

    private void filter() {
        String state = tab + "|" + query + "|" + screen.store.revision() + "|" + screen.store.refs.version();
        if (state.equals(shownFor)) return;
        shownFor = state;
        List<Entry> source = switch (tab) {
            case "recent" -> query.isBlank() ? recent() : allItems();
            case "held" -> held();
            case "tags" -> screen.store.refs.choices("item_tag").stream()
                    .map(choice -> new Entry(Type.TAG, choice.value(), choice.label(), (choice.label() + " " + choice.value()).toLowerCase(Locale.ROOT), tagIcon(choice.value()), ItemStack.EMPTY)).toList();
            case "mobs" -> allEntities();
            case "choices" -> screen.store.refs.choices(choices).stream()
                    .map(choice -> new Entry(Type.CHOICE, choice.value(), choice.label(), (choice.label() + " " + choice.value()).toLowerCase(Locale.ROOT), choice.icon(), ItemStack.EMPTY)).toList();
            default -> allItems();
        };
        String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Entry> result = new ArrayList<>();
        for (Entry entry : source) {
            boolean matches = true;
            for (String word : words) if (!word.isEmpty() && !(word.startsWith("@") ? entry.id().startsWith(word.substring(1)) : entry.search().contains(word))) { matches = false; break; }
            if (matches) result.add(entry);
        }
        String plain = query.toLowerCase(Locale.ROOT).trim();
        if (!plain.isEmpty()) result.sort(Comparator.comparingInt(entry -> entry.name().toLowerCase(Locale.ROOT).equals(plain) ? 0
                : entry.name().toLowerCase(Locale.ROOT).startsWith(plain) ? 1 : entry.id().endsWith(":" + plain) ? 1 : 2));
        shown = List.copyOf(result);
        selected = Math.max(0, Math.min(selected, shown.size() - 1));
    }

    private static StudioIcon tagIcon(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) return null;
        var tag = BuiltInRegistries.ITEM.getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, parsed));
        if (tag.isEmpty() || tag.get().size() == 0) return StudioIcon.item("minecraft:bundle");
        return (kit, x, y, size) -> {
            int index = (int) (System.currentTimeMillis() / 900 % tag.get().size());
            StudioIcon.drawStack(kit, new ItemStack(tag.get().get(index)), x, y, size);
        };
    }

    private List<Entry> recent() {
        List<Entry> result = new ArrayList<>();
        for (String id : screen.prefs.recentItems()) {
            ItemStack stack = StudioIcon.stack(id);
            if (!stack.isEmpty()) result.add(new Entry(Type.ITEM, id, stack.getHoverName().getString(), id, StudioIcon.stack(stack), stack));
        }
        return result;
    }

    private static List<Entry> held() {
        List<Entry> result = new ArrayList<>();
        var player = Minecraft.getInstance().player;
        if (player == null) return result;
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(player.getMainHandItem());
        stacks.add(player.getOffhandItem());
        stacks.addAll(player.getInventory().items);
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            boolean known = false;
            for (Entry entry : result) known |= ItemStack.isSameItemSameComponents(entry.stack(), stack);
            if (known) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String name = stack.getHoverName().getString();
            result.add(new Entry(Type.ITEM, id, name, (name + " " + id).toLowerCase(Locale.ROOT), StudioIcon.stack(stack.copyWithCount(1)), stack.copyWithCount(1)));
        }
        return result;
    }

    @Override
    void render(StudioKit kit) {
        filter();
        kit.box(x, y, w, h, StudioTheme.window, StudioTheme.accent);
        kit.field(field(), x + 5, y + 5, w - 10, query, new StudioKit.Field().hint(title.isEmpty() ? StudioText.t("picker.search") : title).max(128).enter(this::choose), value -> {
            query = value;
            selected = 0;
            kit.scrollTo(id, 0);
        });
        int top = y + 24;
        if (tabs.size() > 1) {
            int left = x + 5;
            for (String value : tabs) left += kit.chip(left, top, 80, StudioText.t("picker." + value), null, tab.equals(value), "", () -> {
                tab = value;
                selected = 0;
                kit.scrollTo(id, 0);
            }) + 3;
            top += 18;
        }
        int bottom = y + h - 17;
        boolean grid = !tab.equals("choices");
        columns = grid ? Math.max(1, (w - 12) / 22) : 1;
        cellHeight = grid ? 22 : 15;
        int extra = grid ? 0 : (clearable ? 1 : 0) + (create != null ? 1 : 0);
        int rows = (shown.size() + columns - 1) / columns + extra;
        int offset = kit.scroll(id, x + 4, top, w - 8, bottom - top, rows * cellHeight);
        kit.clip(x + 4, top, w - 8, bottom - top);
        int at = top - offset;
        if (!grid) {
            if (clearable) {
                row(kit, at, StudioText.t("picker.none"), null, StudioTheme.muted, () -> finish(new Picked(Type.CHOICE, "", ItemStack.EMPTY)));
                at += cellHeight;
            }
            if (create != null) {
                row(kit, at, "+ " + (query.isBlank() ? createLabel : createLabel + ": " + query.trim()), null, StudioTheme.accent, () -> {
                    screen.closePopup(this);
                    create.accept(query.trim());
                });
                at += cellHeight;
            }
        }
        for (int index = 0; index < shown.size(); index++) {
            int cellY = at + index / columns * cellHeight;
            if (cellY + cellHeight < top) continue;
            if (cellY > bottom) break;
            Entry entry = shown.get(index);
            int chosen = index;
            if (grid) {
                int cellX = x + 6 + index % columns * 22;
                if (kit.pointed(cellX, cellY, 20, 20)) selected = index;
                kit.box(cellX, cellY, 20, 20, StudioTheme.desk, index == selected ? StudioTheme.accent : StudioTheme.strongLine);
                if (entry.icon() != null) entry.icon().draw(kit, cellX + 2, cellY + 2, 16);
                kit.press(cellX, cellY, 20, 20, () -> {
                    selected = chosen;
                    choose();
                });
            } else {
                if (kit.pointed(x + 4, cellY, w - 8, cellHeight)) selected = index;
                if (index == selected) kit.fill(x + 4, cellY, w - 8, cellHeight, StudioTheme.selected);
                int left = x + 8;
                if (entry.icon() != null) {
                    entry.icon().draw(kit, left, cellY + 2, 11);
                    left += 14;
                }
                kit.text(entry.name(), left, cellY + 4, x + w - 12 - left, StudioTheme.text);
                kit.press(x + 4, cellY, w - 8, cellHeight, () -> {
                    selected = chosen;
                    choose();
                });
            }
        }
        if (shown.isEmpty()) {
            boolean waiting = screen.store.refs.waiting(choices);
            kit.wrap(StudioText.t(waiting ? "picker.loading" : tab.equals("recent") ? "picker.no_recent" : "picker.nothing"), x + 8, top + extra * cellHeight + 6, w - 16, StudioTheme.soft);
        }
        kit.unclip();
        kit.fill(x + 1, bottom, w - 2, 16, StudioTheme.panel);
        kit.fill(x + 1, bottom, w - 2, 1, StudioTheme.line);
        String typed = typedId();
        if (selected < shown.size() && !shown.isEmpty()) {
            Entry entry = shown.get(selected);
            String name = entry.name();
            int nameWidth = grid ? kit.text(name, x + 6, bottom + 5, w / 2, StudioTheme.strong) : 0;
            String shownId = entry.type() == Type.TAG ? "#" + entry.id() : entry.id();
            String fitted = kit.trim(shownId, w - nameWidth - 20);
            kit.right(fitted, x + w - 6, bottom + 5, StudioTheme.soft);
        } else if (!typed.isEmpty()) kit.text(StudioText.t("picker.use_typed", typed), x + 6, bottom + 5, w - 12, StudioTheme.accent);
    }

    private void row(StudioKit kit, int at, String label, StudioIcon icon, int color, Runnable action) {
        if (kit.hover(x + 4, at, w - 8, cellHeight)) kit.fill(x + 4, at, w - 8, cellHeight, StudioTheme.selected);
        kit.text(label, x + 8, at + 4, w - 20, color);
        kit.press(x + 4, at, w - 8, cellHeight, action);
    }

    private String typedId() {
        String value = query.trim();
        return custom && value.contains(":") && ResourceLocation.tryParse(value.startsWith("#") ? value.substring(1) : value) != null ? value : "";
    }

    private void choose() {
        filter();
        if (selected < shown.size() && !shown.isEmpty()) {
            Entry entry = shown.get(selected);
            if (entry.type() == Type.ITEM) screen.prefs.recentItem(entry.id());
            finish(new Picked(entry.type(), entry.id(), entry.stack()));
            return;
        }
        String typed = typedId();
        if (!typed.isEmpty()) {
            Type type = tab.equals("choices") ? Type.CHOICE : tab.equals("mobs") ? Type.ENTITY : typed.startsWith("#") || tab.equals("tags") ? Type.TAG : Type.ITEM;
            finish(new Picked(type, typed.startsWith("#") ? typed.substring(1) : typed, StudioIcon.stack(typed)));
            return;
        }
        if (create != null && !query.isBlank()) {
            screen.closePopup(this);
            create.accept(query.trim());
        }
    }

    void pick(String value) {
        filter();
        for (Entry entry : tab.equals("choices") ? shown : allItems()) if (entry.id().equals(value)) {
            if (entry.type() == Type.ITEM) screen.prefs.recentItem(entry.id());
            finish(new Picked(entry.type(), entry.id(), entry.stack()));
            return;
        }
        throw new IllegalArgumentException("Picker has no " + value);
    }

    private void finish(Picked picked) {
        if (!keepOpen) screen.closePopup(this);
        result.accept(picked);
    }

    @Override
    boolean key(int key, int modifiers) {
        int step = switch (key) {
            case GLFW.GLFW_KEY_DOWN -> columns;
            case GLFW.GLFW_KEY_UP -> -columns;
            case GLFW.GLFW_KEY_RIGHT -> columns > 1 && query.isEmpty() ? 1 : 0;
            case GLFW.GLFW_KEY_LEFT -> columns > 1 && query.isEmpty() ? -1 : 0;
            default -> 0;
        };
        if (step != 0 && !shown.isEmpty()) {
            selected = Math.max(0, Math.min(shown.size() - 1, selected + step));
            int row = selected / columns * cellHeight;
            int view = h - 24 - 17 - (tabs.size() > 1 ? 18 : 0);
            int scroll = screen.kit.scrollOf(id);
            if (row < scroll) screen.kit.scrollTo(id, row);
            if (row + cellHeight > scroll + view) screen.kit.scrollTo(id, row + cellHeight - view);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            choose();
            return true;
        }
        return false;
    }
}
