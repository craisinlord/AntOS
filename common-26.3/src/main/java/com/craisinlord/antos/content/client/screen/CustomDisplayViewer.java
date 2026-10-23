package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.guide.CustomDisplays;
import com.craisinlord.antos.content.guide.DisplayFeatures;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.platform.InputConstants;
import static com.craisinlord.antos.content.guide.CustomDisplays.number;
import static com.craisinlord.antos.content.guide.CustomDisplays.string;

final class CustomDisplayViewer {
    private final ComputerScreen screen;
    private final Map<String, JsonObject> definitions = new HashMap<>();
    private final Map<String, View> views = new LinkedHashMap<>();
    private final Map<String, BlockState> blockStates = new HashMap<>();
    private final Map<JsonObject, List<ItemStack>> ingredients = new IdentityHashMap<>();
    private final Map<String, ItemStack> stacks = new HashMap<>();
    private final Map<String, String> texts = new HashMap<>();
    private final Map<String, Integer> heights = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private String language = "";
    private List<ComputerGuideData.Entry> availableThisFrame;
    private final Map<String, net.minecraft.world.entity.Entity> entities = new HashMap<>();
    private final Map<String, Optional<net.minecraft.world.level.block.entity.BlockEntity>> blockEntities = new HashMap<>();
    private final Map<String, Optional<net.minecraft.client.renderer.texture.TextureAtlasSprite>> fluidSprites = new HashMap<>();
    private Object level;
    private final List<Hit> hits = new ArrayList<>();
    private final List<SceneHit> scenes = new ArrayList<>();
    private final List<ScrollHit> scrolls = new ArrayList<>();
    private List<Choice> chooser = List.of();
    private String expanded;
    private String expandedKey;
    private String focus;
    private SceneHit dragging;
    private double lastX, lastY;
    private long revision = -1;
    private String tooltip = "";
    private int focusIndex = -1;
    private boolean overlay;
    private int chooserOffset;
    private int clipLeft, clipRight, clipTop, clipBottom;
    private TaskRichText richText;
    private String instanceKey = "";
    private JsonObject activeDocument = new JsonObject();
    private final Map<String, JsonObject> activeScenes = new HashMap<>();
    private final Map<MaterialKey, List<DisplayFeatures.Material>> materialCache = new HashMap<>();
    private final Map<String, JsonObject> presentations = new HashMap<>();
    private final IdentityHashMap<JsonObject, SceneInfo> sceneInfo = new IdentityHashMap<>();
    private boolean authorMode;
    private boolean boundsOverlay;
    private String presentation = "auto";
    private String hoveredBlock = "";
    private static final Map<String, String> REMEMBERED = new HashMap<>();
    private final Map<JsonObject, Optional<net.minecraft.world.entity.Entity>> propEntities = new IdentityHashMap<>();
    private final Map<JsonObject, List<JsonObject>> rowSlots = new IdentityHashMap<>();
    private View searchFocus;
    private String pickerKey;
    private JsonObject pickerDocument;
    private String pickerQuery = "";
    private final Map<String, Integer> tabs = new HashMap<>();
    private final Map<String, Integer> measured = new HashMap<>();
    private final Map<String, Integer> measuredHeights = new HashMap<>();
    private boolean reflowActive;
    private int contentBottom;
    private int pickerOffset;

    void preview(String id, JsonObject document) {
        authorMode = true;
        dragging = null; hits.clear(); scenes.clear(); scrolls.clear(); chooser = List.of();
        definitions.clear(); materialCache.clear(); presentations.clear(); sceneInfo.clear();
        if (document != null) definitions.put(id, document.deepCopy());
        revision = CustomDisplays.revision();
    }
    void boundsOverlay(boolean enabled) { boundsOverlay = enabled; }
    void presentation(String id) { presentation = id; }
    void selectVariant(String key, String variant) { view(key, new JsonObject()).variant = variant; }

    CustomDisplayViewer(ComputerScreen screen) { this.screen = screen; }

    void beginFrame() {
        if (level != Minecraft.getInstance().level) {
            level = Minecraft.getInstance().level;
            entities.clear(); propEntities.clear(); ingredients.clear(); stacks.clear(); blockEntities.clear(); blockModels.clear(); blockParts.clear();
        }
        String selected = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!selected.equals(language)) { language = selected; texts.clear(); labels.clear(); }
        availableThisFrame = null;
        accessibleIdsThisFrame = null;
        if (revision != CustomDisplays.revision()) {
            revision = CustomDisplays.revision();
            if (!authorMode) definitions.clear();
            measured.clear(); measuredHeights.clear(); heights.clear(); blockStates.clear(); ingredients.clear(); stacks.clear(); texts.clear(); labels.clear(); entities.clear(); propEntities.clear(); rowSlots.clear(); blockEntities.clear(); fluidSprites.clear(); materialCache.clear(); presentations.clear(); sceneInfo.clear(); heroes.clear(); blockModels.clear(); blockParts.clear();
            if (richText != null) richText.clearCache();
            chooser = List.of(); focus = null; dragging = null; searchFocus = null; pickerKey = null; pickerDocument = null;
        }
        hits.clear(); scenes.clear(); scrolls.clear(); tooltip = ""; overlay = false;
    }

    static final int HEADER = 13;
    static final int BODY_CAP = 180;
    static final int CAP_FROM = 240;

    int render(ThemedGraphics g, String id, String key, int x, int y, int width, int top, int bottom) {
        JsonObject definition = definitions.computeIfAbsent(id, CustomDisplays::get);
        if (definition == null) return screen.wrap(g, label("missing", id), x, y, width, Ui.ERROR) + 4;
        int viewport = Math.max(120, bottom - top);
        String heightKey = key + "|" + width + "|" + viewport;
        Integer known = heights.get(heightKey);
        if (known != null && (y >= bottom || y + known <= top)) return y + known;
        JsonObject base = definition;
        definition = prepare(definition, key, width, presentation);
        Hero hero = hero(definition);
        JsonObject body = hero == null ? definition : hero.rest();
        int heroHeight = hero == null ? 0 : heroHeight(hero.scene(), width - 2, viewport);
        boolean details = body.has("elements") && !body.getAsJsonArray("elements").isEmpty();
        float scale = Math.min((float) number(body, "maximum_scale", 1), width / (float) number(body, "width", 320));
        int inset = (int) Math.max(0, (width - number(body, "width", 320) * scale) / 2);
        boolean summary = hero == null && scale < number(definition, "minimum_scale", .6);
        int detailHeight = details ? (int) Math.ceil(effectiveHeight(body) * scale) : 0;
        int contentHeight = heroHeight + detailHeight + (hero != null && details ? 2 : 0);
        int bodyHeight = authorMode || detailHeight <= CAP_FROM ? contentHeight : heroHeight + BODY_CAP;
        boolean variants = base.has("variants") && !accessibleVariants(base).isEmpty();
        int header = hero != null && flag(definition, "hide_title") && !variants ? 0 : HEADER;
        int height = summary ? 30 : bodyHeight + header + 3;
        if (heights.size() > 1024) heights.clear();
        heights.put(heightKey, height);
        if (y >= bottom || y + height <= top) return y + height;
        int oldLeft = clipLeft, oldRight = clipRight, oldTop = clipTop, oldBottom = clipBottom;
        clipLeft = x; clipRight = x + width; clipTop = Math.max(y, top); clipBottom = Math.min(y + height, bottom);
        screen.enableComputerScissor(g, x, Math.max(y, top), x + width, Math.min(y + height, bottom));
        try {
            g.fill(x, y, x + width, y + height - 1, style("panel", Ui.BACKGROUND));
            outline(g, x, y, width, height - 1, style("frame", Ui.BORDER_STRONG));
            Runnable expand = () -> { expanded = id; expandedKey = key; focusIndex = -1; };
            if (header > 0) {
                int selectorWidth = variants ? Math.min(150, width / 2) : 0;
                button(g, x + width - 41, y + 1, 39, label("expand"), expand);
                if (selectorWidth > 0) compactVariants(g, base, key, x + width - 44 - selectorWidth, y + 1, selectorWidth);
                if (!flag(definition, "hide_title")) g.drawString(font(), screen.trimToWidth(text(string(definition, "title", id)), Math.max(10, width - 50 - selectorWidth)), x + 4, y + 3, Ui.TEXT_STRONG, false);
            }
            if (summary) { g.drawString(font(), label("expand_hint"), x + 4, y + 16, Ui.TEXT_MUTED, false); return y + height; }
            View state = view(key + "/body", definition);
            int maxOffset = Math.max(0, contentHeight - bodyHeight);
            state.materialOffset = Math.max(0, Math.min(maxOffset, state.materialOffset));
            clipTop = Math.max(top, y + header); clipBottom = Math.min(bottom, y + header + bodyHeight);
            if (maxOffset > 0 && interactive() && clipBottom > clipTop) scrolls.add(new ScrollHit(x, clipTop, width, clipBottom - clipTop, state, maxOffset, 12));
            screen.enableComputerScissor(g, clipLeft, clipTop, clipRight, clipBottom);
            int cursor = y + header - state.materialOffset;
            try {
                if (hero != null) {
                    heroExpand = header == 0 ? expand : null;
                    g.pose().pushPose();
                    g.pose().translate(x + 1, cursor, 0);
                    try { scene(g, hero.scene(), instanceKey + "/hero", 0, 0, width - 2, heroHeight, x + 1, cursor, 1); }
                    finally { g.pose().popPose(); heroExpand = null; }
                    cursor += heroHeight + 2;
                }
                if (details) {
                    g.pose().pushPose();
                    g.pose().translate(x + inset, cursor, 0);
                    g.pose().scale(scale, scale, 1);
                    try { renderDocument(g, body, x + inset, cursor, scale); }
                    finally { g.pose().popPose(); }
                }
            } finally { g.disableScissor(); }
            if (maxOffset > 0) {
                int track = bodyHeight, thumb = Math.max(8, track * bodyHeight / Math.max(1, contentHeight));
                int offset = (track - thumb) * state.materialOffset / maxOffset;
                g.fill(x + width - 3, y + header, x + width - 1, y + header + track, Ui.SURFACE);
                g.fill(x + width - 3, y + header + offset, x + width - 1, y + header + offset + thumb, Ui.ACCENT);
            }
        } finally {
            g.disableScissor();
            clipLeft = oldLeft; clipRight = oldRight; clipTop = oldTop; clipBottom = oldBottom;
        }
        return y + height;
    }

    private record Hero(JsonObject scene, JsonObject rest) { }

    private final Map<JsonObject, Optional<Hero>> heroes = new IdentityHashMap<>();
    private Runnable heroExpand;
    float screenShare = .7F;

    private Hero hero(JsonObject document) {
        if (heroes.size() > 512) heroes.clear();
        return heroes.computeIfAbsent(document, ignored -> {
            JsonArray elements = document.has("elements") ? document.getAsJsonArray("elements") : null;
            if (elements == null) return Optional.empty();
            for (JsonElement value : elements) {
                JsonObject e = value.getAsJsonObject();
                if (!string(e, "type", "").equals("scene") || !flag(e, "hero")) continue;
                JsonObject rest = new JsonObject();
                document.entrySet().forEach(entry -> rest.add(entry.getKey(), entry.getValue()));
                JsonArray others = new JsonArray();
                for (JsonElement other : elements) if (other != value) others.add(other);
                rest.add("elements", others);
                return Optional.of(new Hero(e, rest));
            }
            return Optional.empty();
        }).orElse(null);
    }

    static final int STEP_BAR = 34;

    private int heroHeight(JsonObject scene, int width, int viewport) {
        int cap = Math.max(90, (int) (viewport * number(scene, "max_screen", screenShare)));
        int view = Math.max(90, Math.min(cap, (int) (width * number(scene, "ratio", .62))));
        return view + (scene.has("steps") && !scene.getAsJsonArray("steps").isEmpty() ? STEP_BAR : 0) + (overlay ? (width < 180 ? 40 : 27) : 0);
    }

    int renderTabs(ThemedGraphics g, List<String> ids, String key, int x, int y, int width, int top, int bottom) {
        if (ids.size() == 1) return render(g, ids.get(0), key + "/" + ids.get(0), x, y, width, top, bottom);
        int selected = Math.floorMod(tabs.getOrDefault(key, 0), ids.size());
        int stripHeight = 13;
        if (y + stripHeight > top && y < bottom) {
            int oldLeft = clipLeft, oldRight = clipRight, oldTop = clipTop, oldBottom = clipBottom;
            clipLeft = x; clipRight = x + width; clipTop = Math.max(y, top); clipBottom = Math.min(y + stripHeight, bottom);
            try {
                int tabWidth = width / ids.size();
                if (tabWidth >= 46) {
                    for (int i = 0; i < ids.size(); i++) {
                        int index = i;
                        pill(g, x + i * tabWidth, y, tabWidth - 2, title(ids.get(i)), i == selected, () -> tabs.put(key, index));
                    }
                } else {
                    button(g, x, y, 12, "<", () -> tabs.put(key, Math.floorMod(selected - 1, ids.size())));
                    pill(g, x + 14, y, width - 28, title(ids.get(selected)) + "  (" + (selected + 1) + "/" + ids.size() + ")", true, () -> tabs.put(key, (selected + 1) % ids.size()));
                    button(g, x + width - 12, y, 12, ">", () -> tabs.put(key, (selected + 1) % ids.size()));
                }
            } finally { clipLeft = oldLeft; clipRight = oldRight; clipTop = oldTop; clipBottom = oldBottom; }
        }
        return render(g, ids.get(selected), key + "/" + ids.get(selected), x, y + stripHeight, width, top, bottom);
    }

    void selectTab(String key, List<String> ids, String id) {
        int index = ids.indexOf(id);
        if (index >= 0) tabs.put(key, index);
    }

    String title(String id) {
        JsonObject definition = definitions.computeIfAbsent(id, CustomDisplays::get);
        return definition == null ? id : text(string(definition, "title", id));
    }

    private void compactVariants(ThemedGraphics g, JsonObject document, String key, int x, int y, int width) {
        View state = view(key, document);
        List<JsonObject> choices = accessibleVariants(document);
        if (choices.isEmpty()) return;
        int selected = 0;
        for (int i = 0; i < choices.size(); i++) if (string(choices.get(i), "id", "").equals(state.variant)) selected = i;
        int current = selected;
        JsonObject chosen = choices.get(selected);
        if (choices.size() > 1) button(g, x, y, 11, "<", () -> state.variant = string(choices.get(Math.floorMod(current - 1, choices.size())), "id", ""));
        String caption = text(string(chosen, "title", state.variant)) + (choices.size() > 1 ? " (" + (selected + 1) + "/" + choices.size() + ")" : "");
        button(g, x + 13, y, width - 26, caption, () -> {
            if (choices.size() > 3) { pickerKey = key; pickerDocument = document; pickerQuery = ""; pickerOffset = Math.max(0, current - 3); focusIndex = -1; }
            else state.variant = string(choices.get((current + 1) % choices.size()), "id", "");
        });
        if (choices.size() > 1) button(g, x + width - 11, y, 11, ">", () -> state.variant = string(choices.get((current + 1) % choices.size()), "id", ""));
    }

    private boolean flag(JsonObject document, String field) {
        return document.has(field) && document.get(field).isJsonPrimitive() && document.get(field).getAsBoolean();
    }

    private double effectiveHeight(JsonObject document) {
        double authored = number(document, "height", 180);
        if (!flag(document, "auto_height")) return authored;
        Integer measured = measuredHeights.get(instanceKey);
        return measured == null ? authored : Math.max(24, measured);
    }

    private void renderDocument(ThemedGraphics g, JsonObject document, float ox, float oy, float scale) {
        boolean previous = reflowActive;
        reflowActive = flag(document, "reflow");
        contentBottom = 0;
        try { elements(g, document.getAsJsonArray("elements"), instanceKey, ox, oy, scale, 0); }
        finally {
            if (flag(document, "auto_height") && contentBottom > 0) {
                if (measuredHeights.size() > 1024) measuredHeights.clear();
                measuredHeights.put(instanceKey, contentBottom + 3);
            }
            reflowActive = previous;
        }
    }

    private JsonObject prepare(JsonObject document, String key, int width, String requested) {
        View root = view(key, document);
        if (document.has("variants")) {
            List<JsonObject> choices = accessibleVariants(document);
            if (!choices.stream().anyMatch(value -> string(value, "id", "").equals(root.variant))) {
                String remembered = REMEMBERED.get(key);
                root.variant = remembered != null && choices.stream().anyMatch(value -> string(value, "id", "").equals(remembered)) ? remembered : choices.isEmpty() ? "" : string(choices.get(0), "id", "");
            }
            if (!root.variant.isEmpty()) REMEMBERED.put(key, root.variant);
        }
        String presentationKey = key + "/" + root.variant + "/" + width + "/" + requested;
        if (presentations.size() > 512) presentations.clear();
        JsonObject result = presentations.computeIfAbsent(presentationKey, ignored -> DisplayFeatures.presentation(document, root.variant, width, requested));
        instanceKey = key + "/variant/" + root.variant;
        activeDocument = result; activeScenes.clear();
        collectScenes(result.getAsJsonArray("elements"));
        return result;
    }

    private List<JsonObject> accessibleVariants(JsonObject document) {
        Set<String> accessible = authorMode ? Set.of() : accessibleIds();
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement value : document.getAsJsonArray("variants")) {
            JsonObject variant = value.getAsJsonObject();
            if (!authorMode && (variant.has("hidden") && variant.get("hidden").getAsBoolean() || variant.has("requires_entry") && !accessible.contains(string(variant, "requires_entry", "")))) continue;
            result.add(variant);
        }
        return result;
    }

    private void variants(ThemedGraphics g, JsonObject document, String key, int x, int y, int width) {
        if (!document.has("variants")) return;
        View state = view(key, document);
        List<JsonObject> choices = accessibleVariants(document);
        if (choices.isEmpty()) return;
        int tabWidth = Math.min(100, Math.max(35, (width - 10) / Math.min(3, choices.size())));
        if (choices.size() <= 3) for (int i = 0; i < choices.size(); i++) {
            JsonObject variant = choices.get(i); String id = string(variant, "id", "");
            pill(g, x + 4 + i * tabWidth, y, tabWidth - 3, text(string(variant, "title", id)), id.equals(state.variant), () -> state.variant = id);
        } else {
            int selected = 0;
            for (int i = 0; i < choices.size(); i++) if (string(choices.get(i), "id", "").equals(state.variant)) selected = i;
            int current = selected;
            button(g, x + 4, y, 12, "<", () -> state.variant = string(choices.get(Math.floorMod(current - 1, choices.size())), "id", ""));
            JsonObject chosen = choices.get(selected);
            String group = chosen.has("group") ? text(string(chosen, "group", "")) + ": " : "";
            button(g, x + 18, y, width - 36, group + text(string(chosen, "title", state.variant)) + "  (" + (selected + 1) + "/" + choices.size() + ")", () -> { pickerKey = key; pickerDocument = document; pickerQuery = ""; pickerOffset = Math.max(0, current - 3); focusIndex = -1; });
            button(g, x + width - 16, y, 12, ">", () -> state.variant = string(choices.get((current + 1) % choices.size()), "id", ""));
        }
    }

    private void collectScenes(JsonArray elements) {
        if (elements == null) return;
        for (JsonElement value : elements) {
            JsonObject e = value.getAsJsonObject();
            if (string(e, "type", "").equals("scene")) activeScenes.put(string(e, "id", ""), e);
            if (e.has("elements")) collectScenes(e.getAsJsonArray("elements"));
        }
    }

    private View sceneView(JsonObject scene) {
        View state = view(instanceKey + "/scene/" + string(scene, "id", ""), scene);
        state.defaults(scene);
        SceneInfo info = info(scene);
        String signature = info.signature;
        if (!state.signature.equals(signature)) {
            state.signature = signature; state.zoom = state.initialZoom; state.highlighted = Set.of(); state.selectedBlock = ""; state.selectionRevision++;
        }
        if (state.layer != Integer.MIN_VALUE && !info.layers.contains(state.layer)) state.layer = Integer.MIN_VALUE;
        if (!scene.has("steps") || state.step >= scene.getAsJsonArray("steps").size()) state.step = -1;
        String steps = info.steps;
        if (!steps.equals(state.stepsSignature)) {
            state.step = DisplayFeatures.restoreStep(scene, state.stepId, state.step);
            state.stepsSignature = steps;
        }
        state.stepId = state.step >= 0 ? string(scene.getAsJsonArray("steps").get(state.step).getAsJsonObject(), "id", "") : "";
        if (!state.started) { state.started = true; state.playing = scene.has("autoplay") && scene.get("autoplay").getAsBoolean() && scene.has("steps"); state.stepChangedAt = net.minecraft.util.Util.getMillis(); }
        return state;
    }

    private void materials(ThemedGraphics g, JsonObject e, int x, int y, int w, int h, float ox, float oy, float scale) {
        JsonObject scene = activeScenes.get(string(e, "scene", ""));
        if (scene == null) { g.drawString(font(), label("missing_scene"), x, y, Ui.ERROR, false); drawnHeight = 10; return; }
        View state = sceneView(scene);
        View listState = view(instanceKey + "/materials/" + string(e, "id", ""), e);
        String scope = string(e, "scope", "scene");
        SceneInfo info = info(scene);
        Set<String> selection = info.selection(scope.equals("scene") ? -1 : state.step, scope.equals("added"));
        if (scope.equals("visible")) {
            Set<String> filtered = new HashSet<>();
            for (JsonElement value : scene.getAsJsonArray("blocks")) if (DisplayFeatures.visibility(value.getAsJsonObject(), state.layer, state.mode, selection) == 1) filtered.add(DisplayFeatures.blockId(value.getAsJsonObject()));
            selection = filtered;
        }
        MaterialKey cacheKey = new MaterialKey(info, scope, state.step, state.layer, state.mode);
        List<DisplayFeatures.Material> rows = materialCache.get(cacheKey);
        if (rows == null) {
            rows = DisplayFeatures.materials(scene, selection, block -> BuiltInRegistries.BLOCK.getOptional(Identifier.parse(block)).map(value -> BuiltInRegistries.ITEM.getKey(value.asItem()).toString()).filter(id -> !id.equals("minecraft:air")).orElse(""));
            if (materialCache.size() > 256) materialCache.clear(); materialCache.put(cacheKey, rows);
        }
        if (!string(e, "layout", "grid").equals("list")) { materialGrid(g, e, rows, state, listState, scope, x, y, w, h, ox, oy, scale); return; }
        boolean caption = h >= 56;
        int visible = Math.max(1, (h - 16 - (caption ? 11 : 0)) / 21);
        if (listState.revealedSelection != state.selectionRevision) {
            listState.revealedSelection = state.selectionRevision;
            if (state.selectedBlock.isEmpty()) listState.materialOffset = 0;
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).contributors().contains(state.selectedBlock)) { listState.materialOffset = i; break; }
        }
        listState.materialOffset = Math.max(0, Math.min(Math.max(0, rows.size() - visible), listState.materialOffset));
        g.drawString(font(), label("materials"), x + 2, y + 2, Ui.TEXT_STRONG, false);
        int shown = Math.min(visible, rows.size() - listState.materialOffset);
        if (caption) g.drawString(font(), screen.trimToWidth(label("materials_scope_" + scope), w - 4), x + 2, y + 15 + Math.max(1, shown) * 21, Ui.TEXT_MUTED, false);
        drawnHeight = Math.min(h, 15 + Math.max(1, shown) * 21 + (caption ? 11 : 0));
        for (int i = 0; i < Math.min(visible, rows.size() - listState.materialOffset); i++) {
            DisplayFeatures.Material row = rows.get(i + listState.materialOffset); int ry = y + 14 + i * 21;
            if (!row.item().isBlank() && !row.unresolved()) screen.renderArchiveAsset(g, row.item(), "", "", "", x + 9, ry + 8, 16, 0, 1, false);
            String name = materialName(row);
            g.drawString(font(), screen.trimToWidth(name, Math.max(10, w - 24)), x + 21, ry, row.unresolved() ? Ui.ERROR : Ui.TEXT, false);
            g.drawString(font(), row.unresolved() ? label("unresolved") : "x" + row.count(), x + 21, ry + 10, Ui.TEXT_MUTED, false);
            if (state.highlighted.equals(row.contributors())) outline(g, x, ry, w, 20, Ui.FOCUS);
            if (hovered(ox + x * scale, oy + ry * scale, w * scale, 20 * scale)) tooltip = name + "\n" + label("block_count", row.blocks());
            hit(ox + x * scale, oy + ry * scale, w * scale, 20 * scale, () -> state.highlighted = state.highlighted.equals(row.contributors()) ? Set.of() : row.contributors());
        }
        float left = Math.max(clipLeft, ox + x * scale), right = Math.min(clipRight, ox + (x + w) * scale), top = Math.max(clipTop, oy + y * scale), bottom = Math.min(clipBottom, oy + (y + h) * scale);
        if (interactive() && right > left && bottom > top) scrolls.add(new ScrollHit(left, top, right - left, bottom - top, listState, Math.max(0, rows.size() - visible)));
    }

    private void materialGrid(ThemedGraphics g, JsonObject e, List<DisplayFeatures.Material> rows, View state, View listState, String scope, int x, int y, int w, int h, float ox, float oy, float scale) {
        int cell = 20, columns = Math.max(1, (w - 2) / cell), visibleRows = Math.max(1, (h - 12) / cell);
        int totalRows = (rows.size() + columns - 1) / columns;
        if (listState.revealedSelection != state.selectionRevision) {
            listState.revealedSelection = state.selectionRevision;
            if (state.selectedBlock.isEmpty()) listState.materialOffset = 0;
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).contributors().contains(state.selectedBlock)) { listState.materialOffset = i / columns; break; }
        }
        listState.materialOffset = Math.max(0, Math.min(Math.max(0, totalRows - visibleRows), listState.materialOffset));
        drawnHeight = Math.min(h, 12 + Math.max(1, Math.min(totalRows, visibleRows)) * cell);
        g.drawString(font(), label("materials"), x + 1, y + 1, Ui.TEXT_STRONG, false);
        if (hovered(ox + x * scale, oy + y * scale, w * scale, 10 * scale)) tooltip = label("materials_scope_" + scope);
        for (int i = listState.materialOffset * columns; i < Math.min(rows.size(), (listState.materialOffset + visibleRows) * columns); i++) {
            DisplayFeatures.Material row = rows.get(i);
            int index = i - listState.materialOffset * columns, cx = x + index % columns * cell, cy = y + 11 + index / columns * cell;
            boolean selected = state.highlighted.equals(row.contributors());
            g.fill(cx, cy, cx + cell - 2, cy + cell - 2, selected ? Ui.HOVER : Ui.PANEL);
            outline(g, cx, cy, cell - 2, cell - 2, selected ? Ui.FOCUS : Ui.LINE);
            ItemStack stack = ItemStack.EMPTY;
            if (!row.unresolved() && !row.item().isBlank()) stack = stack(row.item());
            if (!stack.isEmpty()) { screen.noteItem(g, stack, cx + 1, cy + 1, 16); g.renderItem(stack, cx + 1, cy + 1); g.renderItemDecorations(font(), stack, cx + 1, cy + 1, row.count() > 1 ? compactCount(row.count()) : null); }
            else g.drawString(font(), "?", cx + 6, cy + 5, Ui.ERROR, false);
            if (hovered(ox + cx * scale, oy + cy * scale, (cell - 2) * scale, (cell - 2) * scale)) tooltip = materialName(row) + "\n" + (row.unresolved() ? label("unresolved") : "x" + row.count()) + "\n" + label("block_count", row.blocks());
            hit(ox + cx * scale, oy + cy * scale, (cell - 2) * scale, (cell - 2) * scale, () -> state.highlighted = state.highlighted.equals(row.contributors()) ? Set.of() : row.contributors());
        }
        float left = Math.max(clipLeft, ox + x * scale), right = Math.min(clipRight, ox + (x + w) * scale), top = Math.max(clipTop, oy + y * scale), bottom = Math.min(clipBottom, oy + (y + h) * scale);
        if (interactive() && right > left && bottom > top) scrolls.add(new ScrollHit(left, top, right - left, bottom - top, listState, Math.max(0, totalRows - visibleRows)));
    }

    private String materialName(DisplayFeatures.Material row) {
        if (row.unresolved()) return row.label();
        if (row.label().equals(row.item())) {
            ItemStack stack = stack(row.item());
            return stack.isEmpty() ? row.item() : texts.computeIfAbsent("item:" + row.item(), ignored -> stack.getHoverName().getString());
        }
        return text(row.label());
    }

    private static String compactCount(long count) { return count >= 1000000 ? count / 1000000 + "m" : count >= 10000 ? count / 1000 + "k" : Long.toString(count); }

    private void requirements(ThemedGraphics g, JsonObject e, int x, int y, int w) {
        for (JsonElement value : e.getAsJsonArray("entries")) {
            JsonObject entry = value.getAsJsonObject();
            y = screen.wrap(g, "[?] " + text(string(entry, "text", "")), x, y, w, Ui.TEXT) + 3;
        }
    }

    private void elements(ThemedGraphics g, JsonArray elements, String key, float ox, float oy, float scale, int depth) {
        boolean reflow = depth == 0 && reflowActive;
        Map<JsonObject, Integer> shifts = reflow ? new IdentityHashMap<>() : null;
        int index = 0;
        for (JsonElement value : elements) {
            JsonObject e = value.getAsJsonObject();
            String elementKey = key + "/" + (e.has("id") ? e.get("id").getAsString() : Integer.toString(index));
            index++;
            int shift = reflow ? shift(elements, e, key, shifts) : 0;
            if (shift != 0) { g.pose().pushPose(); g.pose().translate(0, shift, 0); }
            try { element(g, e, elementKey, ox, oy + shift * scale, scale, depth); }
            finally { if (shift != 0) g.pose().popPose(); }
            if (reflow) {
                DisplayFeatures.Bounds bounds = DisplayFeatures.bounds(e);
                int bottomEdge = measured.containsKey(elementKey) && measurable(e) ? measured.get(elementKey) : (int) Math.ceil(bounds.y() + bounds.height());
                contentBottom = Math.max(contentBottom, bottomEdge + shift);
            }
        }
    }

    private int drawnHeight;

    private static boolean measurable(JsonObject e) {
        String type = string(e, "type", "");
        return type.equals("text") || type.equals("materials") || type.equals("group") && string(e, "layout", "manual").equals("column");
    }

    private int shift(JsonArray elements, JsonObject e, String key, Map<JsonObject, Integer> memo) {
        Integer known = memo.get(e);
        if (known != null) return known;
        memo.put(e, 0);
        DisplayFeatures.Bounds bounds = DisplayFeatures.bounds(e);
        int result = Integer.MIN_VALUE, index = 0;
        for (JsonElement value : elements) {
            JsonObject other = value.getAsJsonObject();
            String otherKey = key + "/" + (other.has("id") ? other.get("id").getAsString() : Integer.toString(index));
            index++;
            if (other == e) continue;
            DisplayFeatures.Bounds above = DisplayFeatures.bounds(other);
            double authoredBottom = above.y() + above.height();
            if (above.y() >= bounds.y() || above.x() >= bounds.x() + bounds.width() || bounds.x() >= above.x() + above.width()) continue;
            if (authoredBottom > bounds.y() + 1 && !measurable(other)) {
                if (authoredBottom >= bounds.y() + bounds.height()) continue;
                result = Math.max(result, shift(elements, other, key, memo) + (int) Math.ceil(authoredBottom) + 2 - (int) bounds.y());
                continue;
            }
            int base = shift(elements, other, key, memo), candidate = base;
            if (measurable(other)) {
                Integer bottomEdge = measured.get(otherKey);
                if (bottomEdge == null) continue;
                int growth = bottomEdge - (int) Math.ceil(authoredBottom);
                candidate = base + growth;
                if (growth > 0) candidate = Math.max(candidate, base + bottomEdge + 2 - (int) bounds.y());
            }
            result = Math.max(result, candidate);
        }
        if (result == Integer.MIN_VALUE) result = 0;
        memo.put(e, result);
        return result;
    }

    private int flow(ThemedGraphics g, JsonObject group, String key, float ox, float oy, float scale, int depth, boolean row) {
        int gap = n(group, "gap", 4), cursor = 0, index = 0;
        for (JsonElement value : group.getAsJsonArray("elements")) {
            JsonObject child = value.getAsJsonObject();
            String childKey = key + "/" + (child.has("id") ? child.get("id").getAsString() : Integer.toString(index));
            index++;
            g.pose().pushPose();
            g.pose().translate(row ? cursor : 0, row ? 0 : cursor, 0);
            try { element(g, child, childKey, ox + (row ? cursor * scale : 0), oy + (row ? 0 : cursor * scale), scale, depth); }
            finally { g.pose().popPose(); }
            DisplayFeatures.Bounds bounds = DisplayFeatures.bounds(child);
            int size;
            if (row) size = (int) Math.ceil(bounds.x() + bounds.width());
            else size = measurable(child) && measured.containsKey(childKey) ? measured.get(childKey) : (int) Math.ceil(bounds.y() + bounds.height());
            cursor += size + gap;
        }
        return Math.max(0, cursor - gap);
    }

    private void element(ThemedGraphics g, JsonObject e, String elementKey, float ox, float oy, float scale, int depth) {
        int x = n(e, "x", 0), y = n(e, "y", 0), w = n(e, "width", 18), h = n(e, "height", 18);
        int color = color(e, "color", Ui.ACCENT);
        String type = string(e, "type", "");
        DisplayFeatures.Bounds hitBounds = DisplayFeatures.bounds(e);
        if (type.equals("scene") || type.equals("materials") || type.equals("text") || type.equals("recipe_list")) { w = (int) hitBounds.width(); h = (int) hitBounds.height(); }
        try {
            switch (type) {
                case "rect" -> { if (!e.has("fill") || e.get("fill").getAsBoolean()) g.fill(x, y, x + w, y + h, color); else outline(g, x, y, w, h, color); }
                case "circle" -> circle(g, x, y, n(e, "radius", 20), color, e.has("fill") && e.get("fill").getAsBoolean());
                case "line" -> line(g, x, y, n(e, "x2", x + w), n(e, "y2", y), color);
                case "arrow" -> arrow(g, x, y, n(e, "x2", x + w), n(e, "y2", y), color);
                case "polygon" -> {
                    JsonArray points = e.getAsJsonArray("points");
                    for (int i = 0; i < points.size(); i++) {
                        JsonArray a = points.get(i).getAsJsonArray(), b = points.get((i + 1) % points.size()).getAsJsonArray();
                        line(g, x + a.get(0).getAsInt(), y + a.get(1).getAsInt(), x + b.get(0).getAsInt(), y + b.get(1).getAsInt(), color);
                    }
                }
                case "text" -> {
                    String label = text(string(e, "text", ""));
                    String align = string(e, "align", "left");
                    int offset = 0;
                    if (!align.equals("left")) {
                        int measured = font().width(com.craisinlord.antos.content.guide.TaskMarkup.strip(label));
                        offset = align.equals("center") ? Math.max(0, (w - measured) / 2) : align.equals("right") ? Math.max(0, w - measured) : 0;
                    }
                    if (richText == null) richText = new TaskRichText(screen, font());
                    int textBottom = richText.render(g, label, x + offset, y, Math.max(1, w - offset), color(e, "color", Ui.TEXT), true);
                    hitBounds = new DisplayFeatures.Bounds(x, y, w, textBottom - y);
                if (measured.size() > 4096) measured.clear();
                measured.put(elementKey, textBottom);
                }
                case "bar" -> {
                    double fill = number(e, "value", 0);
                    if (number(e, "animate", 0) > 0) fill = (net.minecraft.util.Util.getMillis() % (long) (number(e, "animate", 0) * 1000)) / (number(e, "animate", 0) * 1000);
                    g.fill(x, y, x + w, y + h, Ui.SURFACE); g.fill(x, y, x + (int) (w * fill), y + h, color); outline(g, x, y, w, h, Ui.LINE);
                }
                case "conditions" -> hitBounds = new DisplayFeatures.Bounds(x, y, w, chips(g, e.getAsJsonArray("entries"), x, y, w, ox, oy, scale));
                case "recipe_list" -> recipeList(g, e, elementKey, x, y, w, h, ox, oy, scale);
                case "slot", "item" -> slot(g, e, elementKey, x, y, ox, oy, scale, type.equals("slot"));
                case "entity" -> screen.renderArchiveAsset(g, "", string(e, "entity", ""), "", "", x + w / 2, y + h / 2, Math.min(w, h), (float) number(e, "rotation", 0), 1, true);
                case "fluid" -> fluid(g, e, x, y, w, h, ox, oy, scale);
                case "image" -> {
                    Identifier texture = Identifier.parse(string(e, "texture", ""));
                    int tint = color(e, "tint", Ui.TEXT_BRIGHT);
                    g.setColor((tint >> 16 & 255) / 255F, (tint >> 8 & 255) / 255F, (tint & 255) / 255F, (tint >>> 24) / 255F);
                    try { g.blit(texture, x, y, w, h, n(e, "u", 0), n(e, "v", 0), n(e, "source_width", w), n(e, "source_height", h), n(e, "texture_width", 256), n(e, "texture_height", 256)); }
                    finally { g.setColor(1, 1, 1, 1); }
                }
                case "recipe" -> screen.renderArchiveRecipe(g, string(e, "recipe", ""), x, y, w);
                case "scene" -> scene(g, e, elementKey, x, y, w, h, ox, oy, scale);
                case "materials" -> { drawnHeight = h; materials(g, e, x, y, w, h, ox, oy, scale); measured.put(elementKey, y + drawnHeight); }
                case "requirements" -> requirements(g, e, x, y, w);
                case "group" -> {
                    g.pose().pushPose();
                    g.pose().translate(x, y, 0);
                    try { elements(g, e.getAsJsonArray("elements"), elementKey, ox + x * scale, oy + y * scale, scale, depth + 1); }
                    finally { g.pose().popPose(); }
                }
            }
            float hx = ox + (float) hitBounds.x() * scale, hy = oy + (float) hitBounds.y() * scale, hw = (float) hitBounds.width() * scale, hh = (float) hitBounds.height() * scale;
            if (e.has("tooltip") && hovered(hx, hy, hw, hh)) tooltip = text(e.get("tooltip").getAsString());
            if (e.has("archive") || e.has("task")) hit(hx, hy, hw, hh, () -> navigate(e));
            else if (e.has("jei")) hit(hx, hy, hw, hh, () -> { if (!authorMode) screen.openInJei(string(e, "jei", ""), e.has("jei_uses") && e.get("jei_uses").getAsBoolean()); });
            if (authorMode && boundsOverlay) {
                outline(g, (int) hitBounds.x(), (int) hitBounds.y(), (int) hitBounds.width(), (int) hitBounds.height(), Ui.FOCUS);
                if (hovered(hx, hy, hw, hh)) tooltip = string(e, "id", elementKey) + "\n" + type + " @ " + x + ", " + y + "\n" + Math.round(scale * 100) + "%";
            }
        } catch (RuntimeException exception) {
            g.drawString(font(), "?", x, y, Ui.ERROR, false);
        }
    }

    private void slot(ThemedGraphics g, JsonObject e, String key, int x, int y, float ox, float oy, float scale, boolean border) {
        int size = n(e, "size", 18);
        View view = view(key, e);
        if (ingredients.size() > 4096) ingredients.clear();
        List<ItemStack> choices = ingredients.computeIfAbsent(e, this::choices);
        ItemStack stack = choices.isEmpty() ? ItemStack.EMPTY : choices.get(Math.floorMod(view.choice, choices.size()));
        if (border) { g.fill(x, y, x + size, y + size, style("slot_background", Ui.PANEL)); outline(g, x, y, size, size, style(string(e, "role", "generic").equals("output") ? "output_border" : "slot_border", Ui.LINE)); }
        if (!stack.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + 1, y + 1, 0);
            g.pose().scale((size - 2) / 16F, (size - 2) / 16F, 1);
            try { screen.noteItem(g, stack, 0, 0, 16); g.renderItem(stack, 0, 0); g.renderItemDecorations(font(), stack, 0, 0, n(e, "count", 1) > 1 ? Integer.toString(n(e, "count", 1)) : null); }
            finally { g.pose().popPose(); }
        } else if (!e.has("empty")) g.drawString(font(), "?", x + 5, y + 5, Ui.TEXT_MUTED, false);
        if (hovered(ox + x * scale, oy + y * scale, size * scale, size * scale)) {
            tooltip = stack.isEmpty() ? label("missing_ingredient") : stack.getHoverName().getString();
            if (choices.size() > 1) tooltip += "\n" + label("alternatives", view.choice % choices.size() + 1, choices.size());
            String role = string(e, "role", "generic");
            if (!role.equals("generic")) tooltip += "\n" + label("role_" + role);
            if (e.has("consumption")) tooltip += "\n" + text(string(e, "consumption", ""));
        }
        String role = string(e, "role", "generic");
        if (!role.equals("generic") && styleFlag("role_badges")) g.drawString(font(), label("badge_" + role), x + size - 5, y - 3, role.equals("output") ? Ui.TEXT_STRONG : Ui.TEXT_MUTED, false);
        hit(ox + x * scale, oy + y * scale, size * scale, size * scale, () -> {
            if (!stack.isEmpty()) ingredient(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        });
        if (choices.size() > 1) {
            g.drawString(font(), ">", x + size - 5, y + size - 4, Ui.TEXT_STRONG, false);
            hit(ox + (x + size - 6) * scale, oy + (y + size - 5) * scale, 7 * scale, 7 * scale, () -> view.choice = (view.choice + 1) % choices.size());
        }
    }

    private List<ItemStack> choices(JsonObject e) {
        List<ItemStack> result = new ArrayList<>();
        if (e.has("empty") && e.get("empty").getAsBoolean()) return result;
        if (Minecraft.getInstance().level != null) {
            var ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().level.registryAccess());
            if (e.has("stack")) ItemStack.CODEC.parse(ops, e.get("stack")).result().ifPresent(result::add);
            if (e.has("stacks")) for (JsonElement value : e.getAsJsonArray("stacks")) ItemStack.CODEC.parse(ops, value).result().ifPresent(result::add);
        }
        if (e.has("tag")) {
            var tag = TagKey.create(Registries.ITEM, Identifier.parse(string(e, "tag", "")));
            BuiltInRegistries.ITEM.get(tag).ifPresent(set -> set.stream().limit(128).forEach(holder -> result.add(new ItemStack(holder.value()))));
        }
        if (e.has("choices")) for (JsonElement choice : e.getAsJsonArray("choices")) addItem(result, choice.getAsString());
        if (e.has("item")) addItem(result, e.get("item").getAsString());
        return List.copyOf(result);
    }

    private void addItem(List<ItemStack> result, String id) {
        BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).ifPresent(item -> { ItemStack stack = new ItemStack(item); if (!stack.isEmpty()) result.add(stack); });
    }

    private void fluid(ThemedGraphics g, JsonObject e, int x, int y, int w, int h, float ox, float oy, float scale) {
        int color = color(e, "color", Ui.ACCENT);
        String id = string(e, "fluid", "minecraft:water");
        int amount = n(e, "amount", 1000), capacity = n(e, "capacity", 1000);
        g.fill(x, y, x + w, y + h, Ui.DEEP);
        double ratio = Math.min(1, amount / (double) Math.max(1, capacity));
        int fx = x + 1, fy, fw, fh;
        if (string(e, "direction", "up").equals("right")) { fy = y + 1; fw = (int) ((w - 2) * ratio); fh = h - 2; }
        else { fh = (int) ((h - 2) * ratio); fy = y + h - 1 - fh; fw = w - 2; }
        var sprite = fluidSprite(id);
        if (sprite != null && fw > 0 && fh > 0) {
            int tint = e.has("color") ? color : fluidTint(id);
            for (int ty = 0; ty < fh; ty += 16) for (int tx = 0; tx < fw; tx += 16)
                g.blitSprite(sprite, fx + tx, fy + ty, Math.min(16, fw - tx), Math.min(16, fh - ty), tint);
        } else if (fw > 0 && fh > 0) g.fill(fx, fy, fx + fw, fy + fh, color);
        outline(g, x, y, w, h, Ui.LINE);
        if (hovered(ox + x * scale, oy + y * scale, w * scale, h * scale)) tooltip = text(string(e, "name", id)) + "\n" + amount + " / " + capacity + " mB";
    }

    private void scene(ThemedGraphics g, JsonObject e, String key, int x, int y, int w, int h, float ox, float oy, float scale) {
        View view = sceneView(e);
        SceneInfo info = info(e);
        JsonArray steps = e.has("steps") ? e.getAsJsonArray("steps") : new JsonArray();
        animate(view, e, steps);
        Set<String> stepBlocks = info.selection(view.step, false);
        Set<String> stepAdditions = view.step < 0 ? Set.of() : info.selection(view.step, true);
        boolean full = overlay, narrow = w < 180;
        int stripHeight = steps.isEmpty() ? 0 : STEP_BAR;
        int controlsHeight = full ? (narrow ? 40 : 27) : 0;
        int viewportHeight = Math.max(30, h - stripHeight - controlsHeight);
        double minX = info.minX, maxX = info.maxX, minY = info.minY, maxY = info.maxY, minZ = info.minZ, maxZ = info.maxZ;
        double yaw = Math.toRadians(view.yaw), pitch = Math.toRadians(view.pitch);
        double dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
        double projectedWidth = Math.abs(Math.cos(yaw)) * dx + Math.abs(Math.sin(yaw)) * dz;
        double projectedHeight = Math.abs(Math.cos(pitch)) * dy + Math.abs(Math.sin(pitch)) * (Math.abs(Math.sin(yaw)) * dx + Math.abs(Math.cos(yaw)) * dz);
        float size = (float) Math.min((w - 12) / (projectedWidth + 1), (viewportHeight - 8) / (projectedHeight + 1)) * view.zoom;
        g.fill(x, y, x + w, y + h, style("scene_background", Ui.DEEP));
        if (styleFlag("scene_grid")) {
            for (int gx = x + 8; gx < x + w; gx += 16) line(g, gx, y, gx, y + viewportHeight, Ui.LINE);
            for (int gy = y + 8; gy < y + viewportHeight; gy += 16) line(g, x, gy, x + w, gy, Ui.LINE);
        }
        outline(g, x, y, w, h, Ui.LINE);
        boolean viewportHovered = hovered(ox + x * scale, oy + y * scale, w * scale, viewportHeight * scale);
        int left = Math.max(clipLeft, (int) (ox + x * scale)), top = Math.max(clipTop, (int) (oy + y * scale)), right = Math.min(clipRight, (int) (ox + (x + w) * scale)), bottom = Math.min(clipBottom, (int) (oy + (y + viewportHeight) * scale));
        screen.enableComputerScissor(g, left, top, right, bottom);
        List<float[]> markerPoints = new ArrayList<>();
        List<JsonObject> markers = new ArrayList<>();
        org.joml.Matrix3x2f parent = new org.joml.Matrix3x2f(g.pose().matrix());
        org.joml.Vector2f center = parent.transformPosition(x + w / 2F, y + viewportHeight / 2F, new org.joml.Vector2f());
        float unit = (float) Math.hypot(parent.m00(), parent.m01());
        JsonArray origin = e.has("origin") ? e.getAsJsonArray("origin") : null;
        float tx = (float) (origin == null ? -(minX + maxX) / 2 : -origin.get(0).getAsDouble());
        float ty = (float) (origin == null ? -(minY + maxY) / 2 : -origin.get(1).getAsDouble());
        float tz = (float) (origin == null ? -(minZ + maxZ) / 2 : -origin.get(2).getAsDouble());
        org.joml.Matrix4f sceneMatrix = new org.joml.Matrix4f().translate(center.x, center.y, 0).scale(size * unit, -size * unit, size * unit)
                .rotateX((float) Math.toRadians(view.pitch)).rotateY((float) Math.toRadians(view.yaw)).translate(tx, ty, tz);
        draws = new ArrayList<>();
        com.mojang.blaze3d.vertex.PoseStack pose = new com.mojang.blaze3d.vertex.PoseStack();
        try {
            hoveredBlock = "";
            view.activeBlocks = stepBlocks;
            String[] states = info.statesAt(view.step);
            pickBlock(sceneMatrix, info, states, view, ox + x * scale, oy + y * scale, w * scale, viewportHeight * scale);
            float progress = view.step < 0 ? 1 : Math.min(1, (net.minecraft.util.Util.getMillis() - view.stepChangedAt) / 450F);
            float ease = 1 - (1 - progress) * (1 - progress) * (1 - progress);
            Set<String> animating = progress < 1 ? stepAdditions : Set.of();
            int[] entityIndices = entityIndices(info, view.step, states, key);
            boolean[] entityFlags = info.entity.get(view.step);
            for (int i = 0; i < info.list.size(); i++) {
                SceneBlock block = info.list.get(i);
                if (visibility(block, view.layer, view.mode, stepBlocks) != 1 || animating.contains(block.id())) continue;
                BlockState state = blockStates.computeIfAbsent(states[i], this::blockState);
                if (state == null || state.isAir()) continue;
                pose.pushPose();
                pose.translate(block.x(), block.y(), block.z());
                try {
                    if (!entityFlags[i] || state.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) drawBlock(pose, state, block.ghost() ? .3F : 1);
                    if (!state.getFluidState().isEmpty()) fluidVolume(pose, state, block.ghost() ? .3F : 1);
                } catch (RuntimeException ignored) {
                } finally { pose.popPose(); }
            }
            boolean everything = view.layer != Integer.MIN_VALUE && view.mode.equals("context");
            BitSet candidates = new BitSet(info.list.size());
            if (everything) candidates.set(0, info.list.size());
            else {
                for (int index : entityIndices) candidates.set(index);
                markIndices(candidates, info, animating);
                markIndices(candidates, info, view.highlighted);
                if (!hoveredBlock.isEmpty()) markIndices(candidates, info, Set.of(hoveredBlock));
                if (view.step >= 0) for (int index : info.highlighted) candidates.set(index);
            }
            for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {
                SceneBlock block = info.list.get(i);
                int visibility = visibility(block, view.layer, view.mode, stepBlocks);
                if (visibility == 0) continue;
                BlockState state = blockStates.computeIfAbsent(states[i], this::blockState);
                if (state == null || state.isAir()) continue;
                boolean added = animating.contains(block.id());
                boolean entity = visibility == 1 && entityFlags[i];
                boolean marked = view.highlighted.contains(block.id()) || block.id().equals(hoveredBlock);
                boolean flagged = view.step >= 0 && block.highlight() && stepAdditions.contains(block.id());
                if (!added && !entity && !marked && !flagged && visibility == 1) continue;
                pose.pushPose();
                pose.translate(block.x(), block.y() + (added ? (1 - ease) * .8 : 0), block.z());
                try {
                    if (visibility == 1 && (added || entity)) {
                        float alpha = (block.ghost() ? .3F : 1) * (added ? Math.max(.15F, ease) : 1);
                        if (entity) sceneBlockEntity(pose, key + "/" + block.id(), state, states[i], block);
                        if (added && (!entity || state.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL)) drawBlock(pose, state, alpha);
                        if (added && !state.getFluidState().isEmpty()) fluidVolume(pose, state, alpha);
                    }
                    if (visibility == 2 || marked || flagged) {
                        int color = visibility == 2 ? Ui.TEXT_DIM : Ui.ACCENT;
                        lineBox(pose, -.005, -.005, -.005, 1.005, 1.005, 1.005, (color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, marked || visibility == 2 ? 1 : .35F);
                    }
                }
                finally { pose.popPose(); }
            }
            sceneObjects(pose, e, "items", view, steps);
            sceneObjects(pose, e, "entities", view, steps);
            moves(pose, e, steps, view);
            effects(pose, e, steps, view);
            paths(pose, e, steps, view);
            if (view.step >= 0 && steps.get(view.step).getAsJsonObject().has("markers")) {
                org.joml.Matrix3x2f inverse = new org.joml.Matrix3x2f(parent).invert();
                for (JsonElement value : steps.get(view.step).getAsJsonObject().getAsJsonArray("markers")) {
                    JsonObject marker = value.getAsJsonObject();
                    float mx, my, mz;
                    if (marker.has("block")) {
                        SceneBlock target = info.byId.get(string(marker, "block", ""));
                        if (target == null) continue;
                        mx = (float) target.x(); my = (float) target.y(); mz = (float) target.z();
                    } else {
                        JsonArray pos = marker.getAsJsonArray("pos");
                        mx = (float) pos.get(0).getAsDouble(); my = (float) pos.get(1).getAsDouble(); mz = (float) pos.get(2).getAsDouble();
                    }
                    org.joml.Vector3f point = sceneMatrix.transformPosition(new org.joml.Vector3f(mx + .5F, my + 1.1F, mz + .5F));
                    org.joml.Vector2f local = inverse.transformPosition(point.x(), point.y(), new org.joml.Vector2f());
                    markerPoints.add(new float[]{local.x(), local.y()});
                    markers.add(marker);
                }
            }
            submitScene(g, parent, center, unit * size, tx, ty, tz, view, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1, x, y, w, viewportHeight);
        } finally {
            draws = null;
        }
        for (int i = 0; i < markers.size(); i++) marker(g, markers.get(i), markerPoints.get(i)[0], markerPoints.get(i)[1], ox, oy, scale);
        if (view.step >= 0) stepOverlay(g, steps.get(view.step).getAsJsonObject(), view, e, x, y, w, viewportHeight, ox, oy, scale);
        else if (e.has("conditions")) chips(g, e.getAsJsonArray("conditions"), x + 4, y + 4, w - 8, ox, oy, scale);
        g.disableScissor();
        if (!full && viewportHovered) {
            int bx = x + w - 3;
            if (heroExpand != null) { Runnable open = heroExpand; bx -= 40; smallButton(g, label("expand"), bx, y + 3, 39, ox, oy, scale, open); bx -= 1; }
            bx -= 12; smallButton(g, "R", bx, y + 3, 11, ox, oy, scale, view::reset);
            bx -= 13; smallButton(g, "+", bx, y + 3, 11, ox, oy, scale, () -> { view.zoom = Math.min(4, view.zoom * 1.15F); view.cancelCamera(); });
            bx -= 13; smallButton(g, "-", bx, y + 3, 11, ox, oy, scale, () -> { view.zoom = Math.max(.3F, view.zoom / 1.15F); view.cancelCamera(); });
        }
        if (full) {
            List<Integer> layers = new ArrayList<>();
            layers.add(Integer.MIN_VALUE); layers.addAll(info.layers);
            int by = y + viewportHeight + 2;
            smallButton(g, label("reset"), x + 3, by, 40, ox, oy, scale, view::reset);
            smallButton(g, "-", x + 46, by, 12, ox, oy, scale, () -> { view.zoom = Math.max(.3F, view.zoom / 1.15F); view.cancelCamera(); });
            smallButton(g, "+", x + 60, by, 12, ox, oy, scale, () -> { view.zoom = Math.min(4, view.zoom * 1.15F); view.cancelCamera(); });
            int layerX = narrow ? x + 3 : x + 76, layerY = narrow ? by + 13 : by;
            smallButton(g, "<", layerX, layerY, 12, ox, oy, scale, () -> { view.layer = layers.get(Math.floorMod(layers.indexOf(view.layer) - 1, layers.size())); if (view.mode.equals("all")) view.mode = "isolate"; });
            smallButton(g, ">", layerX + 14, layerY, 12, ox, oy, scale, () -> { view.layer = layers.get((layers.indexOf(view.layer) + 1) % layers.size()); if (view.mode.equals("all")) view.mode = "isolate"; });
            g.drawString(font(), screen.trimToWidth(label("layer", view.layer == Integer.MIN_VALUE ? label("all") : Integer.toString(view.layer)), w - (narrow ? 34 : 110)), layerX + 30, layerY + 2, Ui.TEXT, false);
            List<String> modes = List.of("all", "isolate", "through", "context");
            int modeY = by + (narrow ? 26 : 13);
            smallButton(g, label("mode_" + view.mode), x + 3, modeY, 55, ox, oy, scale, () -> view.mode = modes.get((modes.indexOf(view.mode) + 1) % modes.size()));
            smallButton(g, label("camera_" + view.camera), x + 61, modeY, 42, ox, oy, scale, () -> { view.cancelCamera(); view.camera = (view.camera + 1) % 4; view.yaw = view.camera == 3 ? 90 : view.camera == 0 ? 45 : 0; view.pitch = view.camera == 1 ? 90 : view.camera == 0 ? 25 : 0; });
            smallButton(g, label("fit"), x + (narrow ? 76 : 106), narrow ? by : modeY, 27, ox, oy, scale, () -> { view.cancelCamera(); view.zoom = view.initialZoom; });
        }
        if (!steps.isEmpty()) {
            int count = steps.size(), sy = y + h - 13, cy = y + viewportHeight;
            g.fill(x + 1, cy, x + w - 1, y + h - 1, Ui.PANEL);
            line(g, x + 1, cy, x + w - 1, cy, Ui.LINE);
            JsonObject current = view.step >= 0 ? steps.get(view.step).getAsJsonObject() : null;
            if (current != null) {
                String title = text(string(current, "title", Integer.toString(view.step + 1)));
                String detail = text(string(current, "text", ""));
                Component caption = Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD).append(Component.literal(detail.isBlank() ? "" : "  " + detail).withStyle(style -> style.withBold(false)));
                var lines = font().split(caption, Math.max(20, w - 8));
                for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font(), lines.get(i), x + 4, cy + 3 + i * 9, Ui.TEXT, false);
                if (lines.size() > 2 && hovered(ox + x * scale, oy + cy * scale, w * scale, 20 * scale)) tooltip = title + (detail.isBlank() ? "" : "\n" + detail);
            }
            smallButton(g, "<", x + 3, sy, 12, ox, oy, scale, () -> { view.playing = false; view.step = view.step <= -1 ? count - 1 : view.step - 1; });
            smallButton(g, ">", x + 17, sy, 12, ox, oy, scale, () -> { view.playing = false; view.step = view.step >= count - 1 ? -1 : view.step + 1; });
            smallButton(g, view.playing ? label("pause") : label("play"), x + 31, sy, 30, ox, oy, scale, () -> { view.playing = !view.playing; view.stepChangedAt = net.minecraft.util.Util.getMillis(); if (view.playing && view.step < 0) view.step = 0; });
            int dotsX = x + 67, room = w - 72;
            if (count * 9 <= room) {
                for (int i = 0; i < count; i++) {
                    int index = i, dotX = dotsX + i * 9;
                    boolean over = hovered(ox + (dotX - 1) * scale, oy + (sy + 1) * scale, 8 * scale, 9 * scale);
                    g.fill(dotX, sy + 3, dotX + 6, sy + 9, index == view.step ? Ui.ACCENT : over ? Ui.TEXT : Ui.TEXT_DIM);
                    if (over) tooltip = (i + 1) + ". " + text(string(steps.get(i).getAsJsonObject(), "title", Integer.toString(i + 1)));
                    hit(ox + (dotX - 1) * scale, oy + (sy + 1) * scale, 8 * scale, 9 * scale, () -> { view.playing = false; view.step = index; view.stepChangedAt = net.minecraft.util.Util.getMillis(); });
                }
            } else if (view.step >= 0) g.drawString(font(), (view.step + 1) + "/" + count, dotsX, sy + 2, Ui.TEXT_MUTED, false);
        }
        float hitLeft = Math.max(clipLeft, ox + x * scale), hitRight = Math.min(clipRight, ox + (x + w) * scale), hitTop = Math.max(clipTop, oy + y * scale), hitBottom = Math.min(clipBottom, oy + (y + viewportHeight) * scale);
        if (interactive() && hitRight > hitLeft && hitBottom > hitTop) scenes.add(new SceneHit(instanceKey + "/scene/" + string(e, "id", ""), view, hitLeft, hitTop, hitRight - hitLeft, hitBottom - hitTop, hoveredBlock, e));
    }

    private int chips(ThemedGraphics g, JsonArray entries, int x, int y, int w, float ox, float oy, float scale) {
        if (entries == null) return 0;
        int cx = x, cy = y;
        for (JsonElement value : entries) {
            JsonObject entry = value.getAsJsonObject();
            String kind = string(entry, "kind", "custom");
            String caption = entry.has("text") ? text(string(entry, "text", "")) : label("condition." + kind);
            int chipWidth = Math.min(w, 16 + font().width(caption));
            if (cx > x && cx + chipWidth > x + w) { cx = x; cy += 14; }
            g.fill(cx, cy, cx + chipWidth, cy + 12, Ui.PANEL);
            outline(g, cx, cy, chipWidth, 12, Ui.LINE);
            if (entry.has("item")) {
                ItemStack icon = stack(string(entry, "item", ""));
                if (!icon.isEmpty()) { g.pose().pushPose(); g.pose().translate(cx + 1, cy + 1, 0); g.pose().scale(.625F, .625F, 1); try { g.renderItem(icon, 0, 0); } finally { g.pose().popPose(); } }
            } else conditionIcon(g, kind, cx + 1, cy + 1);
            g.drawString(font(), screen.trimToWidth(caption, chipWidth - 15), cx + 13, cy + 2, Ui.TEXT, false);
            if (entry.has("tooltip") && hovered(ox + cx * scale, oy + cy * scale, chipWidth * scale, 12 * scale)) tooltip = text(string(entry, "tooltip", ""));
            cx += chipWidth + 3;
        }
        return cy - y + 12;
    }

    private static void conditionIcon(ThemedGraphics g, String kind, int x, int y) {
        int cx = x + 5, cy = y + 5;
        switch (kind) {
            case "day", "light" -> { circle(g, cx, cy, 3, Ui.DISPLAY_SUN, true); g.fill(cx, y, cx + 1, y + 1, Ui.DISPLAY_SUN); g.fill(cx, y + 9, cx + 1, y + 10, Ui.DISPLAY_SUN); g.fill(x, cy, x + 1, cy + 1, Ui.DISPLAY_SUN); g.fill(x + 9, cy, x + 10, cy + 1, Ui.DISPLAY_SUN); }
            case "night" -> { circle(g, cx, cy, 4, Ui.DISPLAY_MOON, true); circle(g, cx + 2, cy - 1, 3, Ui.PANEL, true); }
            case "full_moon" -> { circle(g, cx, cy, 4, Ui.DISPLAY_MOON, true); g.fill(cx - 1, cy - 1, cx + 1, cy, Ui.DISPLAY_MOON_SHADE); }
            case "new_moon", "dark" -> { circle(g, cx, cy, 4, Ui.DISPLAY_DARK, true); circle(g, cx, cy, 4, Ui.DISPLAY_MOON_RIM, false); }
            case "rain" -> { for (int i = 0; i < 3; i++) g.fill(x + 1 + i * 3, y + 2 + i % 2 * 2, x + 2 + i * 3, y + 6 + i % 2 * 2, Ui.DISPLAY_RAIN); }
            case "thunder", "power", "redstone" -> {
                int color = kind.equals("redstone") ? Ui.DISPLAY_REDSTONE : Ui.DISPLAY_SUN;
                g.fill(x + 5, y, x + 7, y + 4, color); g.fill(x + 3, y + 4, x + 7, y + 5, color); g.fill(x + 3, y + 5, x + 5, y + 10, color);
            }
            case "clear", "open_sky" -> { g.fill(x, y + 1, x + 10, y + 9, Ui.DISPLAY_SKY); g.fill(x + 2, y + 3, x + 6, y + 4, Ui.WORLD_NEUTRAL_TINT); }
            case "underground" -> { g.fill(x, y + 1, x + 10, y + 9, Ui.DISPLAY_DIRT); g.fill(x, y + 1, x + 10, y + 3, Ui.DISPLAY_GRASS); }
            case "altar_power" -> { triangle(g, cx, y, x + 1, cy, x + 9, cy, Ui.DISPLAY_ALTAR); triangle(g, cx, y + 10, x + 9, cy, x + 1, cy, Ui.DISPLAY_ALTAR); }
            case "in_liquid" -> { g.fill(x, y + 4, x + 10, y + 9, Ui.DISPLAY_WATER); g.fill(x + 1, y + 3, x + 4, y + 4, Ui.DISPLAY_WATER); g.fill(x + 6, y + 3, x + 9, y + 4, Ui.DISPLAY_WATER); }
            case "dimension" -> { circle(g, cx, cy, 4, Ui.DISPLAY_PORTAL, false); circle(g, cx, cy, 2, Ui.DISPLAY_PORTAL, true); }
            case "biome" -> triangle(g, cx, y + 1, x + 1, y + 9, x + 9, y + 9, Ui.DISPLAY_LEAF);
            case "time" -> { circle(g, cx, cy, 4, Ui.TEXT, false); g.fill(cx, cy - 3, cx + 1, cy + 1, Ui.TEXT); g.fill(cx, cy, cx + 3, cy + 1, Ui.TEXT); }
            case "fire" -> { triangle(g, cx, y, x + 1, y + 10, x + 9, y + 10, Ui.DISPLAY_FIRE); triangle(g, cx, y + 4, x + 3, y + 10, x + 7, y + 10, Ui.DISPLAY_SUN); }
            default -> circle(g, cx, cy, 3, Ui.ACCENT, true);
        }
    }

    private void searchBox(ThemedGraphics g, View state, int x, int y, int w, float ox, float oy, float scale) {
        boolean focused = searchFocus == state;
        g.fill(x, y, x + w, y + 12, Ui.DEEP);
        outline(g, x, y, w, 12, focused ? Ui.ACCENT : Ui.LINE);
        String shown = state.query.isEmpty() && !focused ? label("search") : state.query + (focused && net.minecraft.util.Util.getMillis() / 500 % 2 == 0 ? "_" : "");
        g.drawString(font(), screen.trimToWidth(shown, w - 6), x + 3, y + 2, state.query.isEmpty() && !focused ? Ui.TEXT_MUTED : Ui.TEXT, false);
        hit(ox + x * scale, oy + y * scale, w * scale, 12 * scale, () -> searchFocus = state);
    }

    private void recipeList(ThemedGraphics g, JsonObject e, String key, int x, int y, int w, int h, float ox, float oy, float scale) {
        View state = view(key, e);
        JsonArray rows = e.has("rows") ? e.getAsJsonArray("rows") : new JsonArray();
        boolean search = e.has("search") ? e.get("search").getAsBoolean() : rows.size() > 6;
        int top = y;
        if (search) { searchBox(g, state, x, y, w, ox, oy, scale); top += 15; }
        if (!state.query.equals(state.filterQuery) || state.filtered == null) {
            state.filterQuery = state.query;
            String query = state.query.toLowerCase(Locale.ROOT).trim();
            List<Integer> matches = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) if (query.isEmpty() || rowText(rows.get(i).getAsJsonObject()).contains(query)) matches.add(i);
            state.filtered = matches;
            state.materialOffset = 0;
        }
        int rowHeight = 22, titleWidth = (int) Math.min(number(e, "title_width", 96), w * .45);
        int visible = Math.max(1, (y + h - top) / rowHeight);
        List<Integer> filtered = state.filtered;
        state.materialOffset = Math.max(0, Math.min(Math.max(0, filtered.size() - visible), state.materialOffset));
        if (filtered.isEmpty()) g.drawString(font(), label("no_results"), x + 4, top + 4, Ui.TEXT_MUTED, false);
        for (int i = 0; i < Math.min(visible, filtered.size() - state.materialOffset); i++) {
            int index = filtered.get(i + state.materialOffset);
            JsonObject row = rows.get(index).getAsJsonObject();
            int ry = top + i * rowHeight;
            if (i % 2 == 0) g.fill(x, ry, x + w, ry + rowHeight - 1, Ui.PANEL);
            String title = text(string(row, "title", ""));
            String group = row.has("group") ? text(string(row, "group", "")) : "";
            if (!group.isEmpty()) {
                g.drawString(font(), screen.trimToWidth(title, titleWidth - 4), x + 4, ry + 2, Ui.TEXT, false);
                g.drawString(font(), screen.trimToWidth(group, titleWidth - 4), x + 4, ry + 11, Ui.TEXT_MUTED, false);
            } else g.drawString(font(), screen.trimToWidth(title, titleWidth - 4), x + 4, ry + 7, Ui.TEXT, false);
            if (hovered(ox + x * scale, oy + ry * scale, titleWidth * scale, rowHeight * scale)) tooltip = title + (group.isEmpty() ? "" : "\n" + group) + (row.has("text") ? "\n" + text(string(row, "text", "")) : "");
            if (row.has("archive") || row.has("task")) hit(ox + x * scale, oy + ry * scale, titleWidth * scale, rowHeight * scale, () -> navigate(row));
            List<JsonObject> slots = rowSlots.computeIfAbsent(row, this::rowSlots);
            int sx = x + titleWidth + 2, right = x + w - 3;
            boolean arrowDrawn = false;
            for (int i2 = 0; i2 < slots.size(); i2++) {
                JsonObject slot = slots.get(i2);
                boolean output = string(slot, "role", "input").equals("output");
                if (output && !arrowDrawn) { arrowDrawn = true; if (sx + 34 <= right) { arrow(g, sx + 1, ry + 10, sx + 13, ry + 10, Ui.ACCENT); sx += 16; } }
                if (sx + 18 > right) { g.drawString(font(), "+", sx, ry + 6, Ui.TEXT_MUTED, false); break; }
                slot(g, slot, key + "/" + index + "/" + i2, sx, ry + 2, ox, oy, scale, true);
                sx += 19;
            }
            if (row.has("entity")) {
                if (!arrowDrawn && sx + 34 <= right) { arrow(g, sx + 1, ry + 10, sx + 13, ry + 10, Ui.ACCENT); sx += 16; }
                if (sx + 18 <= right) {
                    screen.renderArchiveAsset(g, "", string(row, "entity", ""), "", "", sx + 9, ry + 10, 18, 200, 1, true);
                    if (hovered(ox + sx * scale, oy + ry * scale, 18 * scale, rowHeight * scale)) tooltip = entityName(string(row, "entity", ""));
                }
            }
        }
        if (filtered.size() > visible) {
            int barHeight = visible * rowHeight;
            int thumb = Math.max(6, barHeight * visible / filtered.size());
            int offset = (barHeight - thumb) * state.materialOffset / Math.max(1, filtered.size() - visible);
            g.fill(x + w - 2, top, x + w, top + barHeight, Ui.SURFACE);
            g.fill(x + w - 2, top + offset, x + w, top + offset + thumb, Ui.ACCENT);
        }
        float left = Math.max(clipLeft, ox + x * scale), right = Math.min(clipRight, ox + (x + w) * scale), topEdge = Math.max(clipTop, oy + top * scale), bottom = Math.min(clipBottom, oy + (y + h) * scale);
        if (interactive() && right > left && bottom > topEdge) scrolls.add(new ScrollHit(left, topEdge, right - left, bottom - topEdge, state, Math.max(0, filtered.size() - visible)));
    }

    private String entityName(String id) {
        Identifier entity = Identifier.tryParse(id);
        if (entity == null) return id;
        return texts.computeIfAbsent("entity:" + entity, ignored -> BuiltInRegistries.ENTITY_TYPE.getOptional(entity).map(type -> type.getDescription().getString()).orElse(entity.toString()));
    }

    private List<JsonObject> rowSlots(JsonObject row) {
        List<JsonObject> result = new ArrayList<>();
        for (String field : List.of("inputs", "outputs")) if (row.has(field)) for (JsonElement value : row.getAsJsonArray(field)) {
            JsonObject slot = new JsonObject();
            if (value.isJsonPrimitive()) {
                String id = value.getAsString();
                if (id.startsWith("#")) slot.addProperty("tag", id.substring(1)); else slot.addProperty("item", id);
            } else for (var entry : value.getAsJsonObject().entrySet()) slot.add(entry.getKey(), entry.getValue().deepCopy());
            slot.addProperty("type", "slot");
            if (!slot.has("role") || field.equals("outputs")) slot.addProperty("role", field.equals("outputs") ? "output" : "input");
            result.add(slot);
        }
        return result;
    }

    private String rowText(JsonObject row) {
        StringBuilder builder = new StringBuilder();
        for (String field : List.of("title", "group", "text")) if (row.has(field)) builder.append(text(string(row, field, ""))).append(' ');
        for (JsonObject slot : rowSlots.computeIfAbsent(row, this::rowSlots)) {
            String id = string(slot, "item", "");
            if (!id.isEmpty()) { ItemStack stack = stack(id); builder.append(stack.isEmpty() ? id : texts.computeIfAbsent("item:" + id, ignored -> stack.getHoverName().getString())).append(' '); }
        }
        if (row.has("entity")) builder.append(entityName(string(row, "entity", "")));
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    boolean charTyped(char character) {
        if (character < 32) return false;
        if (pickerKey != null) { if (pickerQuery.length() < 48) pickerQuery += character; pickerOffset = 0; return true; }
        if (searchFocus != null) { if (searchFocus.query.length() < 48) searchFocus.query += character; return true; }
        return false;
    }

    private List<JsonObject> pickerChoices() {
        List<JsonObject> result = new ArrayList<>();
        if (pickerDocument == null) return result;
        String query = pickerQuery.toLowerCase(Locale.ROOT).trim();
        for (JsonObject variant : accessibleVariants(pickerDocument)) {
            String haystack = (text(string(variant, "title", string(variant, "id", ""))) + " " + (variant.has("group") ? text(string(variant, "group", "")) : "")).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || haystack.contains(query)) result.add(variant);
        }
        return result;
    }

    private void renderPicker(ThemedGraphics g, int x, int y, int w, int h) {
        View state = view(pickerKey, pickerDocument);
        String key = pickerKey;
        g.drawString(font(), label("choose_variant"), x + 7, y + 7, Ui.TEXT_STRONG, false);
        g.fill(x + 8, y + 20, x + w - 8, y + 32, Ui.DEEP);
        outline(g, x + 8, y + 20, w - 16, 12, Ui.ACCENT);
        String shown = pickerQuery.isEmpty() ? label("search") : pickerQuery + (net.minecraft.util.Util.getMillis() / 500 % 2 == 0 ? "_" : "");
        g.drawString(font(), screen.trimToWidth(shown, w - 22), x + 11, y + 22, pickerQuery.isEmpty() ? Ui.TEXT_MUTED : Ui.TEXT, false);
        List<Object> lines = new ArrayList<>();
        String group = null;
        for (JsonObject variant : pickerChoices()) {
            String current = variant.has("group") ? text(string(variant, "group", "")) : "";
            if (!current.equals(group)) { if (!current.isEmpty()) lines.add(current); group = current; }
            lines.add(variant);
        }
        int visible = Math.max(1, (h - 58) / 13);
        pickerOffset = Math.max(0, Math.min(pickerOffset, Math.max(0, lines.size() - visible)));
        if (lines.isEmpty()) g.drawString(font(), label("no_results"), x + 10, y + 40, Ui.TEXT_MUTED, false);
        int columnWidth = w - 16;
        for (int i = 0; i < Math.min(visible, lines.size() - pickerOffset); i++) {
            Object line = lines.get(i + pickerOffset);
            int ly = y + 37 + i * 13;
            if (line instanceof String header) { g.drawString(font(), screen.trimToWidth(header, columnWidth), x + 9, ly + 2, Ui.TEXT_MUTED, false); continue; }
            JsonObject variant = (JsonObject) line;
            String id = string(variant, "id", "");
            int indent = variant.has("group") ? 8 : 0;
            if (variant.has("icon")) {
                ItemStack icon = stack(string(variant, "icon", ""));
                if (!icon.isEmpty()) { g.pose().pushPose(); g.pose().translate(x + 9 + indent, ly + 1, 0); g.pose().scale(.625F, .625F, 1); try { g.renderItem(icon, 0, 0); } finally { g.pose().popPose(); } }
                indent += 12;
            }
            pill(g, x + 8 + indent, ly, columnWidth - indent, text(string(variant, "title", id)), id.equals(state.variant), () -> { state.variant = id; REMEMBERED.put(key, id); pickerKey = null; pickerDocument = null; focusIndex = -1; });
        }
        if (lines.size() > visible) {
            button(g, x + 8, y + h - 16, 45, label("previous"), () -> pickerOffset = Math.max(0, pickerOffset - visible));
            button(g, x + 57, y + h - 16, 45, label("next"), () -> pickerOffset = Math.min(Math.max(0, lines.size() - visible), pickerOffset + visible));
            g.drawString(font(), (pickerOffset + 1) + "-" + Math.min(lines.size(), pickerOffset + visible) + " / " + lines.size(), x + 108, y + h - 14, Ui.TEXT_MUTED, false);
        }
    }

    private static boolean shown(JsonObject object, JsonArray steps, int step) {
        if (steps.isEmpty()) return true;
        if (object.has("steps")) {
            if (step < 0) return object.has("final") && object.get("final").getAsBoolean();
            String current = string(steps.get(step).getAsJsonObject(), "id", "");
            for (JsonElement id : object.getAsJsonArray("steps")) if (id.getAsString().equals(current)) return true;
            return false;
        }
        if (object.has("from") && step >= 0 && step < stepIndex(steps, string(object, "from", ""))) return false;
        if (object.has("until") && (step < 0 || step > stepIndex(steps, string(object, "until", "")))) return false;
        return true;
    }

    private static int stepIndex(JsonArray steps, String id) {
        for (int i = 0; i < steps.size(); i++) if (string(steps.get(i).getAsJsonObject(), "id", "").equals(id)) return i;
        return -1;
    }

    private void animate(View view, JsonObject scene, JsonArray steps) {
        long now = net.minecraft.util.Util.getMillis();
        if (view.playing && !steps.isEmpty()) {
            double seconds = view.step >= 0 ? number(steps.get(view.step).getAsJsonObject(), "time", number(scene, "step_time", 2.5)) : number(scene, "step_time", 2.5);
            if (now - view.stepChangedAt > seconds * 1000) view.step = view.step >= steps.size() - 1 ? -1 : view.step + 1;
        }
        if (view.step != view.shownStep) {
            view.shownStep = view.step;
            view.stepChangedAt = now;
            JsonObject step = view.step >= 0 ? steps.get(view.step).getAsJsonObject() : null;
            if (step != null && (step.has("yaw") || step.has("pitch") || step.has("zoom"))) {
                view.targetYaw = (float) number(step, "yaw", view.yaw); view.targetPitch = (float) number(step, "pitch", view.pitch); view.targetZoom = (float) number(step, "zoom", view.zoom); view.cameraDriven = true;
            } else if (view.cameraDriven) {
                view.targetYaw = view.initialYaw; view.targetPitch = view.initialPitch; view.targetZoom = view.initialZoom; view.cameraDriven = false;
            }
        }
        if (!Float.isNaN(view.targetYaw)) {
            float yawDelta = ((view.targetYaw - view.yaw) % 360 + 540) % 360 - 180;
            view.yaw += yawDelta * .2F; view.pitch += (view.targetPitch - view.pitch) * .2F; view.zoom += (view.targetZoom - view.zoom) * .2F;
            if (Math.abs(yawDelta) < .2F && Math.abs(view.targetPitch - view.pitch) < .2F && Math.abs(view.targetZoom - view.zoom) < .005F) { view.yaw = view.targetYaw; view.pitch = view.targetPitch; view.zoom = view.targetZoom; view.targetYaw = Float.NaN; }
        }
    }

    private void marker(ThemedGraphics g, JsonObject marker, float px, float py, float ox, float oy, float scale) {
        float bob = (float) Math.sin(net.minecraft.util.Util.getMillis() / 260.0) * 1.5F;
        int bx = Math.round(px) - 8, by = Math.round(py + bob) - 18;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        try {
            g.fill(bx, by, bx + 16, by + 16, Ui.BACKGROUND);
            outline(g, bx, by, 16, 16, Ui.ACCENT);
            triangle(g, bx + 5, by + 16, bx + 11, by + 16, bx + 8, by + 19, Ui.ACCENT);
            String icon = string(marker, "icon", marker.has("item") ? "item" : "right_click");
            if (marker.has("item")) {
                BuiltInRegistries.ITEM.getOptional(Identifier.parse(string(marker, "item", ""))).ifPresent(item -> {
                    g.pose().pushPose(); g.pose().translate(bx + 2, by + 2, 0); g.pose().scale(.75F, .75F, 1);
                    try { g.renderItem(new ItemStack(item), 0, 0); } finally { g.pose().popPose(); }
                });
            }
            if (icon.equals("right_click") || icon.equals("left_click")) mouse(g, marker.has("item") ? bx + 10 : bx + 5, marker.has("item") ? by + 8 : by + 3, icon.equals("right_click"));
            else if (icon.equals("look")) { g.fill(bx + 4, by + 7, bx + 12, by + 9, Ui.ACCENT); g.fill(bx + 7, by + 6, bx + 9, by + 10, Ui.TEXT_STRONG); }
            else if (icon.equals("drop") && !marker.has("item")) { g.fill(bx + 7, by + 3, bx + 9, by + 10, Ui.ACCENT); triangle(g, bx + 4, by + 9, bx + 12, by + 9, bx + 8, by + 14, Ui.ACCENT); }
            else if (icon.equals("drop")) triangle(g, bx + 10, by + 10, bx + 16, by + 10, bx + 13, by + 15, Ui.ACCENT);
            else if (icon.equals("warning")) { g.fill(bx + 7, by + 3, bx + 9, by + 10, Ui.ERROR); g.fill(bx + 7, by + 11, bx + 9, by + 13, Ui.ERROR); }
            else if (icon.equals("sneak")) { g.fill(bx + 3, by + 11, bx + 13, by + 13, Ui.ACCENT); triangle(g, bx + 4, by + 4, bx + 12, by + 4, bx + 8, by + 9, Ui.ACCENT); }
        } finally { g.pose().popPose(); }
        if (marker.has("text") && hovered(ox + bx * scale, oy + by * scale, 16 * scale, 16 * scale)) tooltip = text(string(marker, "text", ""));
    }

    private static void mouse(ThemedGraphics g, int x, int y, boolean right) {
        g.fill(x, y, x + 6, y + 9, Ui.TEXT);
        g.fill(x + 1, y + 1, x + 5, y + 8, Ui.BACKGROUND);
        g.fill(x + (right ? 3 : 1), y + 1, x + (right ? 5 : 3), y + 4, Ui.ACCENT);
        g.fill(x + 1, y + 4, x + 5, y + 5, Ui.TEXT);
    }

    private static final int FULL_BRIGHT = 15728880;
    private static final net.minecraft.core.Direction[] FACES = {null, net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.UP, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST};
    private List<com.craisinlord.antos.content.client.renderer.DisplaySceneRenderer.Draw> draws;
    private final Map<BlockState, net.minecraft.client.renderer.block.BlockModelRenderState> blockModels = new HashMap<>();
    private final Map<BlockState, List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>> blockParts = new HashMap<>();

    private void record(com.mojang.blaze3d.vertex.PoseStack pose, com.craisinlord.antos.content.client.renderer.DisplaySceneRenderer.Draw draw) {
        if (draws == null) return;
        org.joml.Matrix4f at = new org.joml.Matrix4f(pose.last().pose());
        draws.add((target, collector, camera) -> { target.mulPose(at); draw.draw(target, collector, camera); });
    }

    private void submitScene(ThemedGraphics g, org.joml.Matrix3x2f parent, org.joml.Vector2f center, float pixels, float tx, float ty, float tz, View view, float diagonal, int x, int y, int w, int h) {
        if (draws == null || draws.isEmpty()) return;
        org.joml.Vector2f a = parent.transformPosition(x, y, new org.joml.Vector2f());
        org.joml.Vector2f b = parent.transformPosition(x + w, y + h, new org.joml.Vector2f());
        int x0 = (int) Math.floor(Math.min(a.x, b.x)), y0 = (int) Math.floor(Math.min(a.y, b.y)), x1 = (int) Math.ceil(Math.max(a.x, b.x)), y1 = (int) Math.ceil(Math.max(a.y, b.y));
        if (x1 <= x0 || y1 <= y0) return;
        float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float depth = Math.min(1, 1800 / Math.max(1, diagonal * pixels * guiScale));
        org.joml.Matrix4f projection = new org.joml.Matrix4f().translate(center.x - (x0 + x1) / 2F, center.y - (y0 + y1) / 2F, 0).scale(pixels, -pixels, -pixels * depth)
                .rotateX((float) Math.toRadians(view.pitch)).rotateY((float) Math.toRadians(view.yaw)).translate(tx, ty, tz);
        org.joml.Quaternionf camera = new org.joml.Quaternionf().rotationYXZ((float) Math.toRadians(-view.yaw), (float) Math.toRadians(-view.pitch), 0).rotateY((float) Math.PI);
        g.flush();
        g.submitPicture(new com.craisinlord.antos.content.client.renderer.DisplaySceneRenderer.State(List.copyOf(draws), projection, camera, x0, y0, x1, y1, g.scissor()));
    }

    private void drawBlock(com.mojang.blaze3d.vertex.PoseStack pose, BlockState state, float alpha) {
        var minecraft = Minecraft.getInstance();
        if (blockModels.size() > 4096) { blockModels.clear(); blockParts.clear(); }
        var model = blockModels.computeIfAbsent(state, ignored -> {
            var created = new net.minecraft.client.renderer.block.BlockModelRenderState();
            new net.minecraft.client.renderer.block.BlockModelResolver(minecraft.getModelManager()).update(created, state, net.minecraft.client.renderer.block.model.BlockDisplayContext.create());
            return created;
        });
        if (alpha >= 1) {
            record(pose, (target, collector, camera) -> model.submit(target, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0));
            return;
        }
        var parts = blockParts.computeIfAbsent(state, ignored -> {
            List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> collected = new ArrayList<>();
            minecraft.getModelManager().getBlockStateModelSet().get(state).collectParts(net.minecraft.util.RandomSource.create(42), collected);
            return List.copyOf(collected);
        });
        int[] tints = model.tintLayers().toIntArray();
        int alphaBits = Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24;
        record(pose, (target, collector, camera) -> collector.submitCustomGeometry(target, net.minecraft.client.renderer.Sheets.translucentBlockItemSheet(), (at, consumer) -> {
            var instance = new com.mojang.blaze3d.vertex.QuadInstance();
            instance.setLightCoords(FULL_BRIGHT);
            instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
            for (var part : parts) for (var face : FACES) for (var quad : part.getQuads(face)) {
                int tint = quad.materialInfo().tintIndex();
                instance.setColor(alphaBits | (tint >= 0 && tint < tints.length ? tints[tint] : 0xFFFFFF) & 0xFFFFFF);
                consumer.putBakedQuad(at, quad, instance);
            }
        }));
    }

    private void lineBox(com.mojang.blaze3d.vertex.PoseStack pose, double x0, double y0, double z0, double x1, double y1, double z1, float r, float gr, float b, float a) {
        int color = Math.round(a * 255) << 24 | Math.round(r * 255) << 16 | Math.round(gr * 255) << 8 | Math.round(b * 255);
        float ax = (float) x0, ay = (float) y0, az = (float) z0, bx = (float) x1, by = (float) y1, bz = (float) z1;
        record(pose, (target, collector, camera) -> collector.submitCustomGeometry(target, net.minecraft.client.renderer.rendertype.RenderTypes.lines(), (at, lines) -> {
            segment3d(lines, at, ax, ay, az, bx, ay, az, color); segment3d(lines, at, ax, ay, az, ax, by, az, color); segment3d(lines, at, ax, ay, az, ax, ay, bz, color);
            segment3d(lines, at, bx, ay, az, bx, by, az, color); segment3d(lines, at, bx, ay, az, bx, ay, bz, color); segment3d(lines, at, ax, by, az, bx, by, az, color);
            segment3d(lines, at, ax, by, az, ax, by, bz, color); segment3d(lines, at, ax, ay, bz, bx, ay, bz, color); segment3d(lines, at, ax, ay, bz, ax, by, bz, color);
            segment3d(lines, at, bx, by, az, bx, by, bz, color); segment3d(lines, at, ax, by, bz, bx, by, bz, color); segment3d(lines, at, bx, ay, bz, bx, by, bz, color);
        }));
    }

    private static void segment3d(com.mojang.blaze3d.vertex.VertexConsumer lines, com.mojang.blaze3d.vertex.PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1, length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < .0001F) return;
        lines.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, dx / length, dy / length, dz / length).setLineWidth(2);
        lines.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, dx / length, dy / length, dz / length).setLineWidth(2);
    }

    private void sceneObjects(com.mojang.blaze3d.vertex.PoseStack pose, JsonObject scene, String kind, View view, JsonArray steps) {
        if (!scene.has(kind)) return;
        for (JsonElement value : scene.getAsJsonArray(kind)) {
            JsonObject object = value.getAsJsonObject();
            if (!shown(object, steps, view.step)) continue;
            JsonArray pos = object.getAsJsonArray("pos");
            if (view.layer != Integer.MIN_VALUE && !view.mode.equals("all")) {
                int objectLayer = (int) Math.floor(pos.get(1).getAsDouble());
                if (view.mode.equals("through") ? objectLayer > view.layer : objectLayer != view.layer) continue;
            }
            String id = string(object, "id", "");
            pose.pushPose();
            pose.translate(pos.get(0).getAsDouble() + .5, pos.get(1).getAsDouble() + (kind.equals("items") ? .5 : 0), pos.get(2).getAsDouble() + .5);
            if (object.has("scale")) { float size = (float) number(object, "scale", 1); pose.scale(size, size, size); }
            try {
                if (kind.equals("items")) {
                    if (object.has("spin") && object.get("spin").getAsBoolean()) pose.rotate(Axis.YP.rotationDegrees(net.minecraft.util.Util.getMillis() % 3600 / 10F));
                    else if (object.has("rotation")) pose.rotate(Axis.YP.rotationDegrees((float) number(object, "rotation", 0)));
                    drawItem(pose, stack(id));
                } else if (Minecraft.getInstance().level != null) {
                    var entity = entityFor(object);
                    if (entity != null) drawEntity(pose, entity, (float) number(object, "rotation", 0));
                }
            } finally { pose.popPose(); }
        }
    }

    private void drawItem(com.mojang.blaze3d.vertex.PoseStack pose, ItemStack item) {
        if (item.isEmpty()) return;
        var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, item, net.minecraft.world.item.ItemDisplayContext.FIXED, Minecraft.getInstance().level, null, 0);
        record(pose, (target, collector, camera) -> state.submit(target, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0));
    }

    private void drawEntity(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.entity.Entity entity, float rotation) {
        entity.setYRot(rotation);
        entity.setYHeadRot(rotation);
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) { living.yBodyRot = rotation; living.yBodyRotO = rotation; living.yHeadRotO = rotation; }
        entity.yRotO = rotation;
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var state = dispatcher.extractEntity(entity, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.lightCoords = FULL_BRIGHT;
        record(pose, (target, collector, camera) -> dispatcher.submit(state, camera, 0, 0, 0, target, collector));
    }

    private static final Set<String> ENTITY_PROPS = Set.of("nbt", "held", "offhand", "helmet", "chestplate", "leggings", "boots", "baby", "profession", "pose", "name");

    private net.minecraft.world.entity.Entity entityFor(JsonObject object) {
        String id = string(object, "id", "");
        if (ENTITY_PROPS.stream().noneMatch(object::has)) return entities.computeIfAbsent(id, ignored -> BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id)).map(type -> (net.minecraft.world.entity.Entity) type.create(Minecraft.getInstance().level, net.minecraft.world.entity.EntitySpawnReason.COMMAND)).orElse(null));
        if (propEntities.size() > 256) propEntities.clear();
        return propEntities.computeIfAbsent(object, ignored -> Optional.ofNullable(createEntity(object))).orElse(null);
    }

    private net.minecraft.world.entity.Entity createEntity(JsonObject object) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        try {
            net.minecraft.nbt.CompoundTag tag = object.has("nbt") ? net.minecraft.nbt.TagParser.parseCompoundFully(string(object, "nbt", "")) : new net.minecraft.nbt.CompoundTag();
            tag.putString("id", string(object, "id", ""));
            var entity = net.minecraft.world.entity.EntityType.create(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag), level, new net.minecraft.world.entity.EntitySpawnRequest(net.minecraft.world.entity.EntitySpawnReason.COMMAND, false)).orElse(null);
            if (entity == null) return null;
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                equip(living, net.minecraft.world.entity.EquipmentSlot.MAINHAND, object, "held");
                equip(living, net.minecraft.world.entity.EquipmentSlot.OFFHAND, object, "offhand");
                equip(living, net.minecraft.world.entity.EquipmentSlot.HEAD, object, "helmet");
                equip(living, net.minecraft.world.entity.EquipmentSlot.CHEST, object, "chestplate");
                equip(living, net.minecraft.world.entity.EquipmentSlot.LEGS, object, "leggings");
                equip(living, net.minecraft.world.entity.EquipmentSlot.FEET, object, "boots");
            }
            if (object.has("baby")) {
                boolean baby = object.get("baby").getAsBoolean();
                if (entity instanceof net.minecraft.world.entity.AgeableMob ageable) ageable.setBaby(baby);
                else if (entity instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) zombie.setBaby(baby);
            }
            if (object.has("profession") && entity instanceof net.minecraft.world.entity.npc.villager.Villager villager) {
                Identifier profession = Identifier.tryParse(string(object, "profession", ""));
                if (profession != null) BuiltInRegistries.VILLAGER_PROFESSION.get(profession).ifPresent(value -> villager.setVillagerData(villager.getVillagerData().withProfession(value).withLevel(2)));
            }
            switch (string(object, "pose", "")) {
                case "sleeping" -> entity.setPose(net.minecraft.world.entity.Pose.SLEEPING);
                case "crouching" -> entity.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                case "swimming" -> entity.setPose(net.minecraft.world.entity.Pose.SWIMMING);
                case "sitting" -> { if (entity instanceof net.minecraft.world.entity.TamableAnimal tame) tame.setInSittingPose(true); }
                default -> { }
            }
            if (object.has("name")) { entity.setCustomName(Component.literal(text(string(object, "name", "")))); entity.setCustomNameVisible(true); }
            return entity;
        } catch (Exception exception) {
            return null;
        }
    }

    private void equip(net.minecraft.world.entity.LivingEntity living, net.minecraft.world.entity.EquipmentSlot slot, JsonObject object, String field) {
        if (!object.has(field)) return;
        ItemStack item = stack(string(object, field, ""));
        if (!item.isEmpty()) living.setItemSlot(slot, item.copy());
    }

    private void moves(com.mojang.blaze3d.vertex.PoseStack pose, JsonObject scene, JsonArray steps, View view) {
        if (!scene.has("moves")) return;
        long now = net.minecraft.util.Util.getMillis();
        for (JsonElement value : scene.getAsJsonArray("moves")) {
            JsonObject move = value.getAsJsonObject();
            if (!shown(move, steps, view.step)) continue;
            ItemStack item = stack(string(move, "item", ""));
            if (item.isEmpty()) continue;
            double duration = number(move, "duration", 1.5) * 1000, pause = number(move, "pause", .5) * 1000;
            long elapsed = now - (view.step >= 0 ? view.stepChangedAt : 0) - (long) (number(move, "delay", 0) * 1000);
            if (elapsed < 0) continue;
            boolean loop = !move.has("loop") || move.get("loop").getAsBoolean();
            double t = loop ? Math.min(1, (elapsed % (long) (duration + pause)) / duration) : Math.min(1, elapsed / duration);
            if (move.has("hide_at_end") && move.get("hide_at_end").getAsBoolean() && t >= 1) continue;
            double eased = t * t * (3 - 2 * t);
            JsonArray from = move.getAsJsonArray("from"), to = move.getAsJsonArray("to");
            double px = lerp(from, to, 0, eased), py = lerp(from, to, 1, eased) + number(move, "arc", 0) * 4 * eased * (1 - eased), pz = lerp(from, to, 2, eased);
            pose.pushPose();
            try {
                pose.translate(px + .5, py + .5, pz + .5);
                float size = (float) number(move, "scale", .6);
                pose.scale(size, size, size);
                pose.rotate(Axis.YP.rotationDegrees(now % 3600 / 10F));
                drawItem(pose, item);
            } finally { pose.popPose(); }
        }
    }

    private static double lerp(JsonArray from, JsonArray to, int axis, double t) {
        double a = from.get(axis).getAsDouble(), b = to.get(axis).getAsDouble();
        return a + (b - a) * t;
    }

    private void effects(com.mojang.blaze3d.vertex.PoseStack pose, JsonObject scene, JsonArray steps, View view) {
        if (!scene.has("effects")) return;
        long now = net.minecraft.util.Util.getMillis();
        for (JsonElement value : scene.getAsJsonArray("effects")) {
            JsonObject effect = value.getAsJsonObject();
            if (!shown(effect, steps, view.step)) continue;
            String kind = string(effect, "kind", "");
            int color = color(effect, "color", switch (kind) { case "beam" -> Ui.DISPLAY_BEAM; case "glow" -> Ui.DISPLAY_SUN; case "smoke" -> Ui.DISPLAY_SMOKE; case "spark" -> Ui.DISPLAY_SPARK; default -> Ui.DISPLAY_FIRE; });
            float r = (color >> 16 & 255) / 255F, gr = (color >> 8 & 255) / 255F, b = (color & 255) / 255F;
            if (kind.equals("beam")) {
                JsonArray from = effect.getAsJsonArray("from"), to = effect.getAsJsonArray("to");
                float pulse = .65F + .35F * (float) Math.sin(now / 110.0);
                float width = (float) number(effect, "size", 1) * .05F;
                beam(pose, (float) from.get(0).getAsDouble() + .5F, (float) from.get(1).getAsDouble() + .5F, (float) from.get(2).getAsDouble() + .5F,
                        (float) to.get(0).getAsDouble() + .5F, (float) to.get(1).getAsDouble() + .5F, (float) to.get(2).getAsDouble() + .5F, width, r, gr, b, pulse);
                continue;
            }
            JsonArray pos = effect.getAsJsonArray("pos");
            double px = pos.get(0).getAsDouble(), py = pos.get(1).getAsDouble(), pz = pos.get(2).getAsDouble();
            double size = number(effect, "size", 1);
            switch (kind) {
                case "fire", "soul_fire" -> {
                    BlockState fire = blockStates.computeIfAbsent(kind.equals("fire") ? "minecraft:fire" : "minecraft:soul_fire", this::blockState);
                    pose.pushPose();
                    try { pose.translate(px, py, pz); drawBlock(pose, fire, 1); }
                    finally { pose.popPose(); }
                }
                case "glow" -> {
                    float pulse = .45F + .4F * (float) Math.sin(now / 200.0);
                    double grow = .04 + .03 * Math.sin(now / 200.0);
                    lineBox(pose, px - grow, py - grow, pz - grow, px + size + grow, py + size + grow, pz + size + grow, r, gr, b, pulse);
                }
                default -> {
                    int count = (int) number(effect, "count", 6);
                    double speed = kind.equals("smoke") ? .45 : kind.equals("spark") ? 1.4 : .8;
                    for (int i = 0; i < count; i++) {
                        double phase = (now / 1000.0 * speed + i / (double) count) % 1;
                        double angle = i * 2.399 + (kind.equals("spark") ? phase * 3 : 0);
                        double spread = (kind.equals("spark") ? phase * .6 : .25 + phase * (kind.equals("smoke") ? .25 : .05)) * size;
                        double cx = px + .5 + Math.cos(angle) * spread, cz = pz + .5 + Math.sin(angle) * spread, cy = py + .2 + phase * (kind.equals("spark") ? .6 : 1.3) * size;
                        double half = (kind.equals("smoke") ? .07 + phase * .06 : .04) * size;
                        lineBox(pose, cx - half, cy - half, cz - half, cx + half, cy + half, cz + half, r, gr, b, (float) (1 - phase));
                    }
                }
            }
        }
    }

    private void beam(com.mojang.blaze3d.vertex.PoseStack pose, float x1, float y1, float z1, float x2, float y2, float z2, float width, float r, float gr, float b, float alpha) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < .001F) return;
        float nx = dx / length, ny = dy / length, nz = dz / length;
        org.joml.Vector3f side = Math.abs(ny) < .9F ? new org.joml.Vector3f(0, 1, 0).cross(nx, ny, nz).normalize() : new org.joml.Vector3f(1, 0, 0).cross(nx, ny, nz).normalize();
        org.joml.Vector3f up = new org.joml.Vector3f(nx, ny, nz).cross(side).normalize();
        float[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int rgb = Math.round(r * 255) << 16 | Math.round(gr * 255) << 8 | Math.round(b * 255);
        record(pose, (target, collector, camera) -> collector.submitCustomGeometry(target, net.minecraft.client.renderer.rendertype.RenderTypes.lines(), (at, lines) -> {
            for (float[] offset : offsets) {
                float ox = (side.x * offset[0] + up.x * offset[1]) * width, oy = (side.y * offset[0] + up.y * offset[1]) * width, oz = (side.z * offset[0] + up.z * offset[1]) * width;
                float a = offset[0] == 0 && offset[1] == 0 ? alpha : alpha * .6F;
                segment3d(lines, at, x1 + ox, y1 + oy, z1 + oz, x2 + ox, y2 + oy, z2 + oz, Math.round(a * 255) << 24 | rgb);
            }
        }));
    }

    private void paths(com.mojang.blaze3d.vertex.PoseStack pose, JsonObject scene, JsonArray steps, View view) {
        if (!scene.has("paths")) return;
        long now = net.minecraft.util.Util.getMillis();
        for (JsonElement value : scene.getAsJsonArray("paths")) {
            JsonObject line = value.getAsJsonObject();
            if (!shown(line, steps, view.step)) continue;
            String kind = string(line, "kind", "cable");
            int color = color(line, "color", switch (kind) { case "pipe" -> Ui.DISPLAY_PIPE; case "redstone" -> Ui.DISPLAY_REDSTONE; case "fluid" -> Ui.DISPLAY_WATER; case "item" -> Ui.DISPLAY_ITEM_LINE; case "data" -> Ui.DISPLAY_DATA; case "beam" -> Ui.DISPLAY_BEAM; default -> Ui.DISPLAY_SUN; });
            float r = (color >> 16 & 255) / 255F, gr = (color >> 8 & 255) / 255F, b = (color & 255) / 255F;
            JsonArray points = line.getAsJsonArray("points");
            float offset = (float) number(line, "offset", .5);
            double total = 0;
            for (int i = 0; i + 1 < points.size(); i++) {
                JsonArray a = points.get(i).getAsJsonArray(), c = points.get(i + 1).getAsJsonArray();
                float x1 = (float) a.get(0).getAsDouble() + offset, y1 = (float) a.get(1).getAsDouble() + offset, z1 = (float) a.get(2).getAsDouble() + offset;
                float x2 = (float) c.get(0).getAsDouble() + offset, y2 = (float) c.get(1).getAsDouble() + offset, z2 = (float) c.get(2).getAsDouble() + offset;
                beam(pose, x1, y1, z1, x2, y2, z2, kind.equals("pipe") ? .06F : .025F, r, gr, b, .9F);
                total += Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
            }
            if (line.has("flow") && !line.get("flow").getAsBoolean() || total <= 0) continue;
            double travel = now / 1000.0 * number(line, "speed", 1.5);
            for (double d = travel % 1; d < total; d += 1) {
                double remaining = d;
                for (int i = 0; i + 1 < points.size(); i++) {
                    JsonArray a = points.get(i).getAsJsonArray(), c = points.get(i + 1).getAsJsonArray();
                    double sx = c.get(0).getAsDouble() - a.get(0).getAsDouble(), sy = c.get(1).getAsDouble() - a.get(1).getAsDouble(), sz = c.get(2).getAsDouble() - a.get(2).getAsDouble();
                    double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
                    if (remaining > length) { remaining -= length; continue; }
                    double t = length == 0 ? 0 : remaining / length;
                    double cx = a.get(0).getAsDouble() + offset + sx * t, cy = a.get(1).getAsDouble() + offset + sy * t, cz = a.get(2).getAsDouble() + offset + sz * t;
                    lineBox(pose, cx - .07, cy - .07, cz - .07, cx + .07, cy + .07, cz + .07, Math.min(1, r + .3F), Math.min(1, gr + .3F), Math.min(1, b + .3F), 1);
                    break;
                }
            }
        }
    }

    private void stepOverlay(ThemedGraphics g, JsonObject step, View view, JsonObject scene, int x, int y, int w, int viewportHeight, float ox, float oy, float scale) {
        int top = y + 4;
        if (step.has("image")) {
            JsonObject image = step.getAsJsonObject("image");
            int iw = n(image, "width", 48), ih = n(image, "height", 48);
            try {
                g.pose().pushPose(); g.pose().translate(0, 0, 350);
                g.fill(x + 3, top - 1, x + 5 + iw, top + ih + 1, Ui.BACKGROUND);
                g.blit(Identifier.parse(string(image, "texture", "")), x + 4, top, iw, ih, n(image, "u", 0), n(image, "v", 0), n(image, "source_width", iw), n(image, "source_height", ih), n(image, "texture_width", 256), n(image, "texture_height", 256));
            } catch (RuntimeException ignored) {
            } finally { g.pose().popPose(); }
            top += ih + 4;
        }
        JsonArray conditions = step.has("conditions") ? step.getAsJsonArray("conditions") : scene.has("conditions") ? scene.getAsJsonArray("conditions") : null;
        if (conditions != null) {
            g.pose().pushPose(); g.pose().translate(0, 0, 350);
            try { top += chips(g, conditions, x + 4, top, w - 8, ox, oy, scale) + 2; }
            finally { g.pose().popPose(); }
        }
        if (step.has("progress")) {
            JsonObject progress = step.getAsJsonObject("progress");
            double seconds = number(progress, "seconds", number(step, "time", number(scene, "step_time", 2.5)));
            double fill = Math.min(1, (net.minecraft.util.Util.getMillis() - view.stepChangedAt) / (seconds * 1000));
            int by = y + viewportHeight - 9, bw = w - 8;
            g.pose().pushPose(); g.pose().translate(0, 0, 350);
            try {
                if (progress.has("label")) g.drawString(font(), screen.trimToWidth(text(string(progress, "label", "")), bw), x + 4, by - 10, Ui.TEXT_STRONG, true);
                g.fill(x + 4, by, x + 4 + bw, by + 5, Ui.SURFACE);
                g.fill(x + 4, by, x + 4 + (int) (bw * fill), by + 5, color(progress, "color", Ui.ACCENT));
                outline(g, x + 4, by, bw, 5, Ui.LINE);
            } finally { g.pose().popPose(); }
        }
    }

    private int[] entityIndices(SceneInfo info, int step, String[] states, String key) {
        return info.entityIndices.computeIfAbsent(step, ignored -> {
            boolean[] flags = new boolean[info.list.size()];
            int count = 0;
            for (int i = 0; i < flags.length; i++) {
                BlockState state = blockStates.computeIfAbsent(states[i], this::blockState);
                flags[i] = state != null && !state.isAir() && drawnByEntity(key, state, states[i], info.list.get(i));
                if (flags[i]) count++;
            }
            info.entity.put(step, flags);
            int[] result = new int[count];
            for (int i = 0, j = 0; i < flags.length; i++) if (flags[i]) result[j++] = i;
            return result;
        });
    }

    private static void markIndices(BitSet target, SceneInfo info, Set<String> ids) {
        for (String id : ids) { Integer index = info.indexOf.get(id); if (index != null) target.set(index); }
    }

    private Optional<net.minecraft.world.level.block.entity.BlockEntity> blockEntity(String key, BlockState state, String stateId, SceneBlock block) {
        var level = Minecraft.getInstance().level;
        if (level == null || !state.hasBlockEntity() || !(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock entityBlock)) return Optional.empty();
        if (blockEntities.size() > 1024) blockEntities.clear();
        return blockEntities.computeIfAbsent(key + "|" + stateId, ignored -> {
            try {
                var created = entityBlock.newBlockEntity(net.minecraft.core.BlockPos.containing(block.x(), block.y(), block.z()), state);
                if (created != null) created.setLevel(level);
                if (created != null && block.json().has("nbt")) created.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), net.minecraft.nbt.TagParser.parseCompoundFully(block.json().get("nbt").getAsString())));
                if (created != null && Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(created) == null) return Optional.empty();
                return Optional.ofNullable(created);
            } catch (RuntimeException | com.mojang.brigadier.exceptions.CommandSyntaxException exception) { return Optional.empty(); }
        });
    }

    private boolean drawnByEntity(String key, BlockState state, String stateId, SceneBlock block) {
        return !block.ghost() && state.hasBlockEntity() && blockEntity(key + "/" + block.id(), state, stateId, block).isPresent();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void sceneBlockEntity(com.mojang.blaze3d.vertex.PoseStack pose, String key, BlockState state, String stateId, SceneBlock block) {
        var entity = blockEntity(key, state, stateId, block);
        if (entity.isEmpty()) return;
        try {
            var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
            net.minecraft.client.renderer.blockentity.BlockEntityRenderer renderer = dispatcher.getRenderer(entity.get());
            if (renderer == null) return;
            var renderState = (net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState) renderer.createRenderState();
            renderer.extractRenderState(entity.get(), renderState, 1.0F, net.minecraft.world.phys.Vec3.atCenterOf(entity.get().getBlockPos()), null);
            renderState.lightCoords = FULL_BRIGHT;
            record(pose, (target, collector, camera) -> dispatcher.submit(renderState, target, collector, camera));
        } catch (RuntimeException exception) { blockEntities.put(key + "|" + stateId, Optional.empty()); }
    }

    private void fluidVolume(com.mojang.blaze3d.vertex.PoseStack stack, BlockState state, float alpha) {
        var fluid = state.getFluidState();
        String id = BuiltInRegistries.FLUID.getKey(fluid.getType()).toString();
        var sprite = fluidSprite(id);
        if (sprite == null) return;
        int tint = fluidTint(id);
        float r = (tint >> 16 & 255) / 255F, gr = (tint >> 8 & 255) / 255F, b = (tint & 255) / 255F;
        float top = fluid.isSource() ? .875F : Math.max(.125F, fluid.getAmount() / 9F);
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1(), vs = sprite.getV(1 - top);
        float a = .85F * alpha;
        record(stack, (target, collector, camera) -> collector.submitCustomGeometry(target, net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            quad(buffer, pose, 0, top, 0, 0, top, 1, 1, top, 1, 1, top, 0, u0, v0, u1, v1, 0, 1, 0, r, gr, b, a);
            quad(buffer, pose, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, u0, v0, u1, v1, 0, -1, 0, r, gr, b, a);
            quad(buffer, pose, 0, 0, 0, 0, top, 0, 1, top, 0, 1, 0, 0, u0, vs, u1, v1, 0, 0, -1, r, gr, b, a);
            quad(buffer, pose, 1, 0, 1, 1, top, 1, 0, top, 1, 0, 0, 1, u0, vs, u1, v1, 0, 0, 1, r, gr, b, a);
            quad(buffer, pose, 0, 0, 1, 0, top, 1, 0, top, 0, 0, 0, 0, u0, vs, u1, v1, -1, 0, 0, r, gr, b, a);
            quad(buffer, pose, 1, 0, 0, 1, top, 0, 1, top, 1, 1, 0, 1, u0, vs, u1, v1, 1, 0, 0, r, gr, b, a);
        }));
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer buffer, com.mojang.blaze3d.vertex.PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float u0, float v0, float u1, float v1, float nx, float ny, float nz, float r, float g, float b, float a) {
        vertex(buffer, pose, x0, y0, z0, u0, v1, nx, ny, nz, r, g, b, a);
        vertex(buffer, pose, x1, y1, z1, u0, v0, nx, ny, nz, r, g, b, a);
        vertex(buffer, pose, x2, y2, z2, u1, v0, nx, ny, nz, r, g, b, a);
        vertex(buffer, pose, x3, y3, z3, u1, v1, nx, ny, nz, r, g, b, a);
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer buffer, com.mojang.blaze3d.vertex.PoseStack.Pose pose, float x, float y, float z, float u, float v, float nx, float ny, float nz, float r, float g, float b, float a) {
        buffer.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }

    private net.minecraft.client.renderer.texture.TextureAtlasSprite fluidSprite(String id) {
        return fluidSprites.computeIfAbsent(id, ignored -> {
            try {
                return BuiltInRegistries.FLUID.getOptional(Identifier.parse(id))
                    .filter(fluid -> !fluid.defaultFluidState().isEmpty())
                    .map(fluid -> Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState()).stillMaterial().sprite())
                    .filter(sprite -> !sprite.contents().name().getPath().equals("missingno"));
            } catch (RuntimeException exception) { return Optional.empty(); }
        }).orElse(null);
    }

    private static int fluidTint(String id) { return id.equals("minecraft:water") || id.equals("minecraft:flowing_water") ? Ui.WORLD_WATER_TINT : Ui.WORLD_NEUTRAL_TINT; }

    private void pickBlock(org.joml.Matrix4f sceneMatrix, SceneInfo info, String[] states, View view, float x, float y, float w, float h) {
        if (!hovered(x, y, w, h)) return;
        org.joml.Matrix4f inverse = new org.joml.Matrix4f(sceneMatrix).invert();
        float mx = screen.width / 2F + screen.session.mouseX * screen.uiScale();
        float my = screen.height / 2F + screen.session.mouseY * screen.uiScale();
        org.joml.Vector3f a = inverse.transformPosition(new org.joml.Vector3f(mx, my, 10000));
        org.joml.Vector3f b = inverse.transformPosition(new org.joml.Vector3f(mx, my, -10000));
        double ox = a.x, oy = a.y, oz = a.z, dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double ix = 1 / dx, iy = 1 / dy, iz = 1 / dz;
        double nearest = Double.MAX_VALUE;
        SceneBlock picked = null;
        BlockState pickedState = null;
        for (int i = 0; i < info.list.size(); i++) {
            SceneBlock block = info.list.get(i);
            double t1 = (block.x() - ox) * ix, t2 = (block.x() + 1 - ox) * ix;
            double near = Math.min(t1, t2), far = Math.max(t1, t2);
            t1 = (block.y() - oy) * iy; t2 = (block.y() + 1 - oy) * iy;
            near = Math.max(near, Math.min(t1, t2)); far = Math.min(far, Math.max(t1, t2));
            t1 = (block.z() - oz) * iz; t2 = (block.z() + 1 - oz) * iz;
            near = Math.max(near, Math.min(t1, t2)); far = Math.min(far, Math.max(t1, t2));
            if (far < Math.max(0, near) || near >= nearest || near > 1) continue;
            if (visibility(block, view.layer, view.mode, view.activeBlocks) != 1) continue;
            BlockState state = blockStates.computeIfAbsent(states[i], this::blockState);
            if (state == null || state.isAir()) continue;
            nearest = near;
            picked = block; pickedState = state;
        }
        if (picked != null) {
            tooltip = pickedState.getBlock().getName().getString() + "\n" + (int) picked.x() + ", " + (int) picked.y() + ", " + (int) picked.z();
            hoveredBlock = picked.id();
            if (!flag(picked.json(), "decorative")) screen.noteHovered(new ItemStack(pickedState.getBlock().asItem()));
        }
    }

    private static int visibility(SceneBlock block, int layer, String mode, Set<String> stepBlocks) {
        if (!stepBlocks.contains(block.id())) return 0;
        if (layer == Integer.MIN_VALUE || mode.equals("all")) return 1;
        return switch (mode) {
            case "through" -> block.layer() <= layer ? 1 : 0;
            case "context" -> block.layer() == layer ? 1 : 2;
            default -> block.layer() == layer ? 1 : 0;
        };
    }

    private BlockState blockState(String id) {
        try { return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, id, false).blockState(); }
        catch (Exception exception) { return net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(); }
    }

    void renderOverlay(ThemedGraphics g) {
        if (expanded != null || !chooser.isEmpty() || pickerKey != null) {
            tooltip = "";
            screen.beginLayer(g);
            hits.clear(); scenes.clear(); scrolls.clear(); overlay = true;
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            int x = -screen.desktopWidth() / 2 + 12, y = -screen.desktopHeight() / 2 + 25, w = screen.desktopWidth() - 24, h = screen.desktopHeight() - 40;
            clipLeft = x; clipRight = x + w; clipTop = y; clipBottom = y + h;
            try {
                panel(g, x, y, w, h);
                button(g, x + w - 47, y + 3, 42, label("close"), () -> { if (pickerKey != null) { pickerKey = null; pickerDocument = null; } else if (!chooser.isEmpty()) chooser = List.of(); else expanded = null; focusIndex = -1; });
                if (pickerKey != null) renderPicker(g, x, y, w, h);
                else if (!chooser.isEmpty()) {
                    g.drawString(font(), label("choose"), x + 7, y + 7, Ui.TEXT_STRONG, false);
                    int visible = Math.max(1, (h - 47) / 13);
                    chooserOffset = Math.max(0, Math.min(chooserOffset, Math.max(0, chooser.size() - visible)));
                    for (int i = 0; i < Math.min(chooser.size() - chooserOffset, visible); i++) {
                        var choice = chooser.get(i + chooserOffset);
                        String caption = choice.target == null ? ComputerGuideData.archiveTitle(choice.entry).getString() : DisplayArchiveIndex.label(choice.target);
                        button(g, x + 8, y + 26 + i * 13, w - 16, caption, () -> openChoice(choice));
                    }
                    if (chooser.size() > visible) {
                        button(g, x + 8, y + h - 16, 45, label("previous"), () -> chooserOffset = Math.max(0, chooserOffset - visible));
                        button(g, x + 57, y + h - 16, 45, label("next"), () -> chooserOffset = Math.min(Math.max(0, chooser.size() - visible), chooserOffset + visible));
                    }
                } else {
                    JsonObject definition = definitions.computeIfAbsent(expanded, CustomDisplays::get);
                    if (definition != null) {
                        JsonObject base = definition;
                        definition = prepare(definition, expandedKey, w - 12, presentation.equals("auto") ? "expanded" : presentation);
                        g.drawString(font(), screen.trimToWidth(text(string(definition, "title", expanded)), w - 65), x + 7, y + 7, Ui.TEXT_STRONG, false);
                        variants(g, base, expandedKey, x, y + 17, w);
                        int start = y + (base.has("variants") ? 34 : 24);
                        Hero hero = hero(definition);
                        JsonObject body = hero == null ? definition : hero.rest();
                        int heroHeight = hero == null ? 0 : heroHeight(hero.scene(), w - 12, (int) ((y + h - start) / .85));
                        boolean details = body.has("elements") && !body.getAsJsonArray("elements").isEmpty();
                        float scale = Math.min(Math.max(1.5F, (float) number(body, "maximum_scale", 1)), (w - 12) / (float) number(body, "width", 320));
                        int inset = (int) Math.max(0, (w - 12 - number(body, "width", 320) * scale) / 2);
                        View root = view(expandedKey, base);
                        int total = heroHeight + (details ? (int) (effectiveHeight(body) * scale) + 4 : 0);
                        int maxScroll = Math.max(0, total - (y + h - start - 5));
                        root.materialOffset = Math.min(root.materialOffset, maxScroll);
                        scrolls.add(new ScrollHit(x + 3, start, w - 6, y + h - start - 3, root, maxScroll));
                        screen.enableComputerScissor(g, x + 3, start, x + w - 3, y + h - 3);
                        clipLeft = x + 3; clipRight = x + w - 3; clipTop = start; clipBottom = y + h - 3;
                        int cursor = start - root.materialOffset;
                        if (hero != null) {
                            g.pose().pushPose();
                            g.pose().translate(x + 6, cursor, 0);
                            try { scene(g, hero.scene(), instanceKey + "/hero", 0, 0, w - 12, heroHeight, x + 6, cursor, 1); }
                            finally { g.pose().popPose(); }
                            cursor += heroHeight + 4;
                        }
                        g.pose().pushPose();
                        g.pose().translate(x + 6 + inset, cursor, 0); g.pose().scale(scale, scale, 1);
                        try { if (details) renderDocument(g, body, x + 6 + inset, cursor, scale); }
                        finally { g.pose().popPose(); g.disableScissor(); }
                    } else g.drawString(font(), screen.trimToWidth(label("missing", expanded), w - 16), x + 8, y + 28, Ui.ERROR, false);
                }
            } finally { g.pose().popPose(); }
        }
        if (focusIndex >= 0 && focusIndex < hits.size()) {
            Hit hit = hits.get(focusIndex);
            g.pose().pushPose();
            g.pose().translate(0, 0, 600);
            try { outline(g, (int) hit.x, (int) hit.y, (int) hit.w, (int) hit.h, Ui.FOCUS); }
            finally { g.pose().popPose(); }
        }
        if (!tooltip.isBlank()) {
            String hint = screen.lookupHint();
            if (!hint.isEmpty()) tooltip += "\n" + hint;
            g.pose().pushPose();
            g.pose().translate(0, 0, 700);
            try {
                var wrapped = font().split(Component.literal(tooltip), 180);
                var lines = wrapped.subList(0, Math.min(wrapped.size(), (screen.desktopHeight() - 16) / 10));
                int w = lines.stream().mapToInt(font()::width).max().orElse(0) + 8, h = lines.size() * 10 + 6;
                int x = Math.max(-screen.desktopWidth() / 2 + 2, Math.min(screen.desktopWidth() / 2 - w - 2, screen.session.mouseX + 7));
                int y = Math.max(-screen.desktopHeight() / 2 + 2, Math.min(screen.desktopHeight() / 2 - h - 2, screen.session.mouseY + 12));
                panel(g, x, y, w, h);
                for (int i = 0; i < lines.size(); i++) g.drawString(font(), lines.get(i), x + 4, y + 4 + i * 10, Ui.TEXT, false);
            } finally { g.pose().popPose(); }
        }
    }

    boolean click(double x, double y, int button) {
        searchFocus = null;
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).inside(x, y)) { focusIndex = i; hits.get(i).action.run(); return true; }
            for (int i = scenes.size() - 1; i >= 0; i--) if (scenes.get(i).inside(x, y)) {
                dragging = scenes.get(i); dragging.view.dragDistance = 0; focus = dragging.key; lastX = x; lastY = y; return true;
            }
        }
        if (!modal()) focusIndex = -1;
        return modal();
    }

    boolean modal() { return expanded != null || !chooser.isEmpty() || pickerKey != null; }

    boolean drag(double x, double y) {
        if (dragging == null) return false;
        dragging.view.cancelCamera();
        dragging.view.yaw += (float) (x - lastX);
        dragging.view.dragDistance += Math.abs(x - lastX) + Math.abs(y - lastY);
        dragging.view.pitch = Math.max(-90, Math.min(90, dragging.view.pitch + (float) (y - lastY)));
        lastX = x; lastY = y; return true;
    }

    void release() {
        if (dragging != null && dragging.view.dragDistance < 3 && !dragging.picked.isBlank()) {
            List<DisplayFeatures.Material> rows = DisplayFeatures.materials(dragging.scene, DisplayFeatures.selectedBlocks(dragging.scene, -1, false), id -> BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).map(block -> BuiltInRegistries.ITEM.getKey(block.asItem()).toString()).orElse(""));
            dragging.view.selectedBlock = dragging.picked; dragging.view.selectionRevision++;
            for (var row : rows) if (row.contributors().contains(dragging.picked)) { dragging.view.highlighted = row.contributors(); break; }
        }
        dragging = null;
    }

    boolean scroll(double x, double y, double amount) {
        if (pickerKey != null) { pickerOffset = Math.max(0, pickerOffset - (int) Math.signum(amount) * 3); return true; }
        if (!chooser.isEmpty()) { chooserOffset = Math.max(0, Math.min(chooser.size() - 1, chooserOffset - (int) Math.signum(amount) * 3)); return true; }
        for (SceneHit hit : scenes) if (hit.inside(x, y) && (hit.key.equals(focus) || expanded != null)) {
            hit.view.zoom = Math.max(.3F, Math.min(4, hit.view.zoom * (float) Math.pow(1.12, amount))); return true;
        }
        for (int i = scrolls.size() - 1; i >= 0; i--) {
            ScrollHit hit = scrolls.get(i);
            if (hit.inside(x, y) && hit.maximum > 0) { hit.view.materialOffset = Math.max(0, Math.min(hit.maximum, hit.view.materialOffset - (int) Math.signum(amount) * hit.step)); return true; }
        }
        return modal();
    }

    boolean key(int key) {
        if (pickerKey != null) {
            if (key == InputConstants.KEY_ESCAPE) { pickerKey = null; pickerDocument = null; focusIndex = -1; return true; }
            if (key == InputConstants.KEY_BACKSPACE && !pickerQuery.isEmpty()) { pickerQuery = pickerQuery.substring(0, pickerQuery.length() - 1); pickerOffset = 0; return true; }
            if (key != InputConstants.KEY_TAB && key != InputConstants.KEY_RETURN && key != InputConstants.KEY_SPACE) return true;
        } else if (searchFocus != null) {
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN) { searchFocus = null; return true; }
            if (key == InputConstants.KEY_BACKSPACE) { if (!searchFocus.query.isEmpty()) searchFocus.query = searchFocus.query.substring(0, searchFocus.query.length() - 1); return true; }
            return true;
        }
        boolean modal = modal();
        if (key == InputConstants.KEY_ESCAPE) {
            if (!chooser.isEmpty()) { chooser = List.of(); return true; }
            if (expanded != null) { expanded = null; focusIndex = -1; return true; }
            focusIndex = -1; focus = null; return false;
        }
        if ((modal || focusIndex >= 0 || key == InputConstants.KEY_F6) && (key == InputConstants.KEY_TAB || key == InputConstants.KEY_F6) && !hits.isEmpty()) { focusIndex = Math.floorMod(focusIndex + ((com.craisinlord.antos.content.client.KeyboardState.isDown(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT) || com.craisinlord.antos.content.client.KeyboardState.isDown(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT)) ? -1 : 1), hits.size()); return true; }
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE) && focusIndex >= 0 && focusIndex < hits.size()) { hits.get(focusIndex).action.run(); return true; }
        if (modal && !scenes.isEmpty()) {
            SceneHit selected = scenes.stream().filter(scene -> scene.key.equals(focus)).findFirst().orElse(scenes.get(0));
            View view = selected.view;
            switch (key) {
                case InputConstants.KEY_LEFT -> view.yaw -= 10;
                case InputConstants.KEY_RIGHT -> view.yaw += 10;
                case InputConstants.KEY_UP -> view.pitch = Math.min(90, view.pitch + 10);
                case InputConstants.KEY_DOWN -> view.pitch = Math.max(-90, view.pitch - 10);
                case InputConstants.KEY_R -> view.reset();
                case InputConstants.KEY_P -> { view.playing = !view.playing; view.stepChangedAt = net.minecraft.util.Util.getMillis(); if (view.playing && view.step < 0 && selected.scene.has("steps")) view.step = 0; }
                case InputConstants.KEY_EQUALS, InputConstants.KEY_ADD -> view.zoom = Math.min(4, view.zoom * 1.15F);
                case InputConstants.KEY_MINUS, com.craisinlord.antos.content.client.KeyboardState.NUMPAD_SUBTRACT -> view.zoom = Math.max(.3F, view.zoom / 1.15F);
                case InputConstants.KEY_M -> { List<String> modes = List.of("all", "isolate", "through", "context"); view.mode = modes.get((modes.indexOf(view.mode) + 1) % modes.size()); }
                case InputConstants.KEY_T -> { view.pitch = 90; view.yaw = 0; view.camera = 1; }
                case InputConstants.KEY_F -> { view.pitch = 0; view.yaw = 0; view.camera = 2; }
                case InputConstants.KEY_S -> { view.pitch = 0; view.yaw = 90; view.camera = 3; }
                case InputConstants.KEY_HOME -> view.zoom = view.initialZoom;
                case InputConstants.KEY_PAGEUP, InputConstants.KEY_PAGEDOWN -> {
                    SortedSet<Integer> levels = new TreeSet<>(); levels.add(Integer.MIN_VALUE);
                    if (selected.scene.has("blocks")) for (JsonElement block : selected.scene.getAsJsonArray("blocks")) levels.add((int) Math.floor(block.getAsJsonObject().getAsJsonArray("pos").get(1).getAsDouble()));
                    List<Integer> values = new ArrayList<>(levels);
                    view.layer = values.get(Math.floorMod(values.indexOf(view.layer) + (key == InputConstants.KEY_PAGEUP ? 1 : -1), values.size())); if (view.mode.equals("all")) view.mode = "isolate";
                }
                case InputConstants.KEY_LBRACKET, InputConstants.KEY_RBRACKET -> {
                    if (selected.scene.has("steps")) { view.playing = false; int count = selected.scene.getAsJsonArray("steps").size(); view.step = Math.floorMod(view.step + 1 + (key == InputConstants.KEY_RBRACKET ? 1 : -1), count + 1) - 1; }
                }
            }
        }
        return modal;
    }

    private void navigate(JsonObject e) {
        if (authorMode) return;
        if (e.has("archive")) available().stream().filter(entry -> entry.id().toString().equals(e.get("archive").getAsString())).findFirst().ifPresent(this::openEntry);
        else if (e.has("task")) { expanded = null; screen.tasks.openDisplayTask(e.get("task").getAsString()); }
    }

    private List<ComputerGuideData.Entry> available() {
        if (availableThisFrame == null) availableThisFrame = ComputerGuideData.entriesFor(screen.workspaceDiskIds(), ComputerArchiveUnlockClientState.get());
        return availableThisFrame;
    }

    private Set<String> accessibleIdsThisFrame;
    private Set<String> accessibleIds() {
        List<ComputerGuideData.Entry> entries = available();
        if (accessibleIdsThisFrame == null) { accessibleIdsThisFrame = new HashSet<>(); for (var entry : entries) accessibleIdsThisFrame.add(entry.id().toString()); }
        return accessibleIdsThisFrame;
    }

    void close() {
        blockModels.clear();
        blockParts.clear();
        blockEntities.clear();
        entities.clear();
    }

    private void ingredient(String id) {
        if (authorMode) return;
        List<ComputerGuideData.Entry> accessible = available();
        List<Choice> matches = new ArrayList<>();
        for (var entry : accessible) if (id.equals(entry.itemId()) || id.equals(entry.coverItemId())) matches.add(new Choice(entry, null));
        for (var result : DisplayArchiveIndex.related(id, accessible)) matches.add(new Choice(result.entry(), result));
        if (matches.size() == 1) openChoice(matches.get(0));
        else if (matches.size() > 1) { chooser = List.copyOf(matches); chooserOffset = 0; focusIndex = -1; }
        else screen.openInJei(id, false);
    }

    private void openChoice(Choice choice) {
        if (available().stream().noneMatch(entry -> entry.id().equals(choice.entry.id()))) { chooser = List.of(); return; }
        openEntry(choice.entry);
        if (choice.target != null) screen.session.archive.revealDisplay(choice.target.display(), choice.target.variant());
    }

    private void openEntry(ComputerGuideData.Entry entry) { expanded = null; chooser = List.of(); screen.session.archive.select(entry); screen.open("ARCHIVE"); }
    void closeOverlay() { expanded = null; chooser = List.of(); }
    private View view(String key, JsonObject e) {
        if (views.size() > 4096) views.clear();
        return views.computeIfAbsent(key, ignored -> new View((float) number(e, "yaw", 45), (float) number(e, "pitch", 25), (float) number(e, "scale", 1)));
    }

    private SceneInfo info(JsonObject scene) {
        if (sceneInfo.size() > 512) sceneInfo.clear();
        return sceneInfo.computeIfAbsent(scene, SceneInfo::new);
    }

    private static final class SceneInfo {
        final JsonObject scene;
        final JsonArray blocks;
        final List<SceneBlock> list = new ArrayList<>();
        final Map<String, SceneBlock> byId = new HashMap<>();
        final Map<Integer, String[]> states = new HashMap<>();
        final Map<Integer, int[]> entityIndices = new HashMap<>();
        final Map<Integer, boolean[]> entity = new HashMap<>();
        final Map<String, Integer> indexOf = new HashMap<>();
        final int[] highlighted;
        final String signature, steps;
        final SortedSet<Integer> layers = new TreeSet<>();
        final Map<Integer, Set<String>> cumulative = new HashMap<>(), additions = new HashMap<>();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        SceneInfo(JsonObject scene) {
            this.scene = scene; blocks = scene.has("blocks") ? scene.getAsJsonArray("blocks") : new JsonArray();
            signature = blocks.toString(); steps = scene.has("steps") ? scene.get("steps").toString() : "";
            for (JsonElement value : blocks) {
                JsonObject block = value.getAsJsonObject();
                JsonArray pos = block.getAsJsonArray("pos");
                double bx = pos.get(0).getAsDouble(), by = pos.get(1).getAsDouble(), bz = pos.get(2).getAsDouble();
                SceneBlock entry = new SceneBlock(block.has("id") ? block.get("id").getAsString() : DisplayFeatures.positionId(pos), bx, by, bz, (int) Math.floor(by),
                        block.has("ghost") && block.get("ghost").getAsBoolean(), block.has("highlight") && block.get("highlight").getAsBoolean(), block);
                indexOf.put(entry.id(), list.size());
                list.add(entry);
                byId.put(entry.id(), entry);
            }
            highlighted = java.util.stream.IntStream.range(0, list.size()).filter(i -> list.get(i).highlight()).toArray();
            List<JsonElement> positions = new ArrayList<>(blocks.asList());
            for (String kind : List.of("items", "entities")) if (scene.has(kind)) positions.addAll(scene.getAsJsonArray(kind).asList());
            for (JsonElement value : positions) {
                JsonArray p = value.getAsJsonObject().getAsJsonArray("pos");
                double x = p.get(0).getAsDouble(), y = p.get(1).getAsDouble(), z = p.get(2).getAsDouble();
                minX = Math.min(minX, x); maxX = Math.max(maxX, x + 1); minY = Math.min(minY, y); maxY = Math.max(maxY, y + 1); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z + 1);
                layers.add((int) Math.floor(y));
            }
            if (positions.isEmpty()) { minX = minY = minZ = 0; maxX = maxY = maxZ = 1; }
        }
        String[] statesAt(int step) {
            return states.computeIfAbsent(step, ignored -> {
                String[] result = new String[list.size()];
                for (int i = 0; i < result.length; i++) result[i] = DisplayFeatures.stateAt(scene, step, list.get(i).json());
                return result;
            });
        }
        Set<String> selection(int step, boolean added) { return (added ? additions : cumulative).computeIfAbsent(step, ignored -> DisplayFeatures.selectedBlocks(scene, step, added)); }
    }

    private record MaterialKey(SceneInfo scene, String scope, int step, int layer, String mode) { }
    private record SceneBlock(String id, double x, double y, double z, int layer, boolean ghost, boolean highlight, JsonObject json) { }

    private boolean interactive() { return overlay || expanded == null && chooser.isEmpty() && pickerKey == null && screen.session.mouseX > -9999; }
    private boolean hovered(float x, float y, float w, float h) { return interactive() && screen.session.mouseX >= clipLeft && screen.session.mouseX < clipRight && screen.session.mouseY >= clipTop && screen.session.mouseY < clipBottom && screen.inside((int) x, (int) y, (int) w, (int) h, screen.session.mouseX, screen.session.mouseY); }
    private void hit(float x, float y, float w, float h, Runnable action) {
        float left = Math.max(x, clipLeft), top = Math.max(y, clipTop), right = Math.min(x + w, clipRight), bottom = Math.min(y + h, clipBottom);
        if (interactive() && right > left && bottom > top) hits.add(new Hit(left, top, right - left, bottom - top, action));
    }
    private void smallButton(ThemedGraphics g, String label, int x, int y, int w, float ox, float oy, float scale, Runnable action) {
        g.fill(x, y, x + w, y + 11, hovered(ox + x * scale, oy + y * scale, w * scale, 11 * scale) ? Ui.HOVER : Ui.PANEL);
        outline(g, x, y, w, 11, Ui.LINE);
        g.drawString(font(), screen.trimToWidth(label, w - 2), x + 2, y + 2, Ui.TEXT, false);
        hit(ox + x * scale, oy + y * scale, w * scale, 11 * scale, action);
    }
    private void button(ThemedGraphics g, int x, int y, int w, String label, Runnable action) { smallButton(g, label, x, y, w, 0, 0, 1, action); }
    private void pill(ThemedGraphics g, int x, int y, int w, String label, boolean selected, Runnable action) {
        boolean over = hovered(x, y, w, 11);
        g.fill(x, y, x + w, y + 11, selected ? Ui.HOVER : over ? Ui.SURFACE : Ui.PANEL);
        outline(g, x, y, w, 11, selected ? Ui.ACCENT : Ui.LINE);
        String trimmed = screen.trimToWidth(label, w - 4);
        g.drawString(font(), trimmed, x + (w - font().width(trimmed)) / 2, y + 2, selected ? Ui.TEXT_STRONG : Ui.TEXT_MUTED, false);
        hit(x, y, w, 11, action);
    }
    private void panel(ThemedGraphics g, int x, int y, int w, int h) { g.fill(x, y, x + w, y + h, Ui.BACKGROUND); outline(g, x, y, w, h, Ui.BORDER_STRONG); }
    private static void outline(ThemedGraphics g, int x, int y, int w, int h, int color) { g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color); g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color); }
    private static void line(ThemedGraphics g, int x, int y, int x2, int y2, int color) {
        if (x == x2 || y == y2) { g.fill(Math.min(x, x2), Math.min(y, y2), Math.max(x, x2) + 1, Math.max(y, y2) + 1, color); return; }
        segment(g, x + .5F, y + .5F, x2 + .5F, y2 + .5F, 1, color);
    }
    private static void arrow(ThemedGraphics g, int x, int y, int x2, int y2, int color) {
        float sx = x + .5F, sy = y + .5F, ex = x2 + .5F, ey = y2 + .5F;
        float dx = ex - sx, dy = ey - sy, length = (float) Math.hypot(dx, dy);
        if (length < 1) return;
        float ux = dx / length, uy = dy / length, head = Math.min(7, length * .6F), half = head * .55F;
        float bx = ex - ux * head, by = ey - uy * head;
        segment(g, sx, sy, bx + ux * .5F, by + uy * .5F, 1.5F, color);
        triangle(g, ex, ey, bx - uy * half, by + ux * half, bx + uy * half, by - ux * half, color);
    }
    private static void segment(ThemedGraphics g, float x1, float y1, float x2, float y2, float width, int color) {
        float dx = x2 - x1, dy = y2 - y1, length = (float) Math.hypot(dx, dy);
        if (length < .001F) return;
        float nx = -dy / length * width / 2, ny = dx / length * width / 2;
        quad2d(g, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, color);
    }
    private static void triangle(ThemedGraphics g, float ax, float ay, float bx, float by, float cx, float cy, int color) { quad2d(g, ax, ay, bx, by, cx, cy, cx, cy, color); }
    private static void quad2d(ThemedGraphics g, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int color) {
        org.joml.Matrix3x2f matrix = new org.joml.Matrix3x2f(g.pose().matrix());
        float[] points = {ax, ay, bx, by, cx, cy, dx, dy};
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i += 2) {
            org.joml.Vector2f point = matrix.transformPosition(points[i], points[i + 1], new org.joml.Vector2f());
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y);
        }
        var scissor = g.scissor();
        var area = new net.minecraft.client.gui.navigation.ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY), Math.max(1, (int) Math.ceil(maxX - minX)), Math.max(1, (int) Math.ceil(maxY - minY)));
        var bounds = scissor == null ? area : scissor.intersection(area);
        if (bounds == null) return;
        g.flush();
        g.submitElement(new Quad2d(matrix, points, g.tint(color), scissor, bounds));
    }

    private record Quad2d(org.joml.Matrix3x2f pose, float[] points, int color, net.minecraft.client.gui.navigation.ScreenRectangle scissorArea, net.minecraft.client.gui.navigation.ScreenRectangle bounds) implements net.minecraft.client.renderer.state.gui.GuiElementRenderState {
        @Override
        public void buildVertices(com.mojang.blaze3d.vertex.VertexConsumer consumer) {
            for (int i = 0; i < 8; i += 2) consumer.addVertexWith2DPose(pose, points[i], points[i + 1]).setColor(color);
            for (int i = 6; i >= 0; i -= 2) consumer.addVertexWith2DPose(pose, points[i], points[i + 1]).setColor(color);
        }

        @Override
        public com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline() { return net.minecraft.client.renderer.RenderPipelines.GUI; }

        @Override
        public net.minecraft.client.gui.render.TextureSetup textureSetup() { return net.minecraft.client.gui.render.TextureSetup.noTexture(); }
    }
    private static void circle(ThemedGraphics g, int x, int y, int radius, int color, boolean fill) {
        if (fill) for (int dy = -radius; dy <= radius; dy++) { int dx = (int) Math.sqrt(radius * radius - dy * dy); g.fill(x - dx, y + dy, x + dx + 1, y + dy + 1, color); }
        else for (int i = 0; i < 120; i++) line(g, x + (int) (radius * Math.cos(i * Math.PI / 60)), y + (int) (radius * Math.sin(i * Math.PI / 60)), x + (int) (radius * Math.cos((i + 1) * Math.PI / 60)), y + (int) (radius * Math.sin((i + 1) * Math.PI / 60)), color);
    }
    private static int n(JsonObject e, String field, int fallback) { return (int) number(e, field, fallback); }
    private boolean styleFlag(String key) {
        if (!activeDocument.has("style")) return false;
        JsonObject style = activeDocument.getAsJsonObject("style");
        return style.has(key) && style.get(key).isJsonPrimitive() && style.get(key).getAsBoolean();
    }
    private int style(String key, int fallback) {
        return activeDocument.has("style") ? color(activeDocument.getAsJsonObject("style"), key, fallback) : fallback;
    }
    private static int color(JsonObject e, String key, int fallback) {
        String value = string(e, key, "");
        if (value.startsWith("#")) try { return (int) (Long.parseLong(value.substring(1), 16) | (255L << 24)); } catch (NumberFormatException ignored) { }
        return switch (value) { case "text" -> Ui.TEXT; case "muted" -> Ui.TEXT_MUTED; case "line" -> Ui.LINE; case "background" -> Ui.BACKGROUND; case "error" -> Ui.ERROR; default -> fallback; };
    }
    private static net.minecraft.client.gui.Font font() { return Minecraft.getInstance().font; }
    private String text(String text) {
        if (text.startsWith("literal:")) return text.substring(8);
        if (texts.size() > 2048) texts.clear();
        return texts.computeIfAbsent(text, key -> Component.translatable(key).getString());
    }
    private String label(String key, Object... args) {
        if (args.length > 0) return Component.translatable("computer.antos.display." + key, args).getString();
        return labels.computeIfAbsent(key, ignored -> Component.translatable("computer.antos.display." + key).getString());
    }
    private ItemStack stack(String id) {
        return stacks.computeIfAbsent(id, ignored -> BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY));
    }
    private static final class View {
        float initialYaw, initialPitch, initialZoom;
        float yaw, pitch, zoom;
        int layer = Integer.MIN_VALUE, choice;
        int step = -1, camera, materialOffset;
        String mode = "all", variant = "", signature = "", stepsSignature = "", stepId = "", query = "", filterQuery;
        List<Integer> filtered;
        int shownStep = -1;
        long stepChangedAt;
        boolean playing, cameraDriven, started;
        float targetYaw = Float.NaN, targetPitch, targetZoom;
        String selectedBlock = "";
        long selectionRevision, revealedSelection;
        Set<String> highlighted = Set.of(), activeBlocks = Set.of();
        double dragDistance;
        View(float yaw, float pitch, float zoom) { initialYaw = yaw; initialPitch = pitch; initialZoom = zoom; reset(); }
        void defaults(JsonObject scene) {
            float nextYaw = (float) number(scene, "yaw", 45), nextPitch = (float) number(scene, "pitch", 25), nextZoom = (float) number(scene, "scale", 1);
            if (initialYaw != nextYaw || initialPitch != nextPitch || initialZoom != nextZoom) {
                initialYaw = nextYaw; initialPitch = nextPitch; initialZoom = nextZoom;
                yaw = nextYaw; pitch = nextPitch; zoom = nextZoom; camera = 0;
            }
        }
        void cancelCamera() { targetYaw = Float.NaN; cameraDriven = false; }
        void reset() { targetYaw = Float.NaN; cameraDriven = false; playing = false; yaw = initialYaw; pitch = initialPitch; zoom = initialZoom; layer = Integer.MIN_VALUE; mode = "all"; step = -1; stepId = ""; camera = 0; materialOffset = 0; selectedBlock = ""; highlighted = Set.of(); selectionRevision++; }
    }
    private record Hit(float x, float y, float w, float h, Runnable action) { boolean inside(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; } }
    private record Choice(ComputerGuideData.Entry entry, DisplayArchiveIndex.Result target) { }
    private record SceneHit(String key, View view, float x, float y, float w, float h, String picked, JsonObject scene) { boolean inside(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; } }
    private record ScrollHit(float x, float y, float w, float h, View view, int maximum, int step) {
        ScrollHit(float x, float y, float w, float h, View view, int maximum) { this(x, y, w, h, view, maximum, maximum > 50 ? 12 : 1); }
        boolean inside(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }
}
