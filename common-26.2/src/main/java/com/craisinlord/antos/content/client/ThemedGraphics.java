package com.craisinlord.antos.content.client;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ThemedGraphics {
    private static final Field GUI_RENDER_STATE_FIELD = field(GuiGraphicsExtractor.class, "guiRenderState");
    private static final Field ITEM_LAYERS_FIELD = field(ItemStackRenderState.class, "layers");
    private static final Field ITEM_LAYER_COUNT_FIELD = field(ItemStackRenderState.class, "activeLayerCount");
    private static final Field LAYER_QUADS_FIELD = field(ItemStackRenderState.LayerRenderState.class, "quads");
    private static final Method PREPARE_QUAD_LIST_METHOD = method(ItemStackRenderState.LayerRenderState.class, "prepareQuadList");
    private static final Method LAYER_SET_QUADS_METHOD = singleArgumentMethod(ItemStackRenderState.LayerRenderState.class, "setQuads");
    private static final ClassValue<ItemQuadsAccess> ITEM_QUADS_ACCESS = new ClassValue<>() {
        @Override
        protected ItemQuadsAccess computeValue(Class<?> type) {
            try {
                Constructor<?> constructor = type.getDeclaredConstructor(List.class, List.class, List.class);
                constructor.setAccessible(true);
                return new ItemQuadsAccess(method(type, "all"), method(type, "solid"), method(type, "translucent"), constructor);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return new ItemQuadsAccess(null, null, null, null);
            }
        }
    };
    private static final Map<BakedQuad, Map<Integer, BakedQuad>> TINTED_QUAD_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicBoolean ITEM_TINT_WARNING_LOGGED = new AtomicBoolean();
    private final GuiGraphicsExtractor source;
    private final LegacyPose pose;
    private int colorMultiplier = 0xFFFFFFFF;

    public ThemedGraphics(GuiGraphicsExtractor source) {
        this.source = source;
        this.pose = new LegacyPose(source);
    }

    public GuiGraphicsExtractor source() {
        return source;
    }

    public LegacyPose pose() {
        return pose;
    }

    public void flush() {
        source.nextStratum();
    }

    public void drawManaged(Runnable action) {
        action.run();
        source.nextStratum();
    }

    public void setColor(float red, float green, float blue, float alpha) {
        colorMultiplier = channel(alpha) << 24 | channel(red) << 16 | channel(green) << 8 | channel(blue);
    }

    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        source.fill(minX, minY, maxX, maxY, applyColor(ComputerTheme.remap(color)));
    }

    public void fillGradient(int minX, int minY, int maxX, int maxY, int colorFrom, int colorTo) {
        source.fillGradient(minX, minY, maxX, maxY, applyColor(ComputerTheme.remap(colorFrom)), applyColor(ComputerTheme.remap(colorTo)));
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        source.text(font, text, x, y, applyColor(ComputerTheme.remap(color)), dropShadow);
        return font.width(text);
    }

    public int drawString(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, false);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
        source.text(font, text, x, y, applyColor(ComputerTheme.remap(color)), dropShadow);
        return font.width(text);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return drawString(font, text, x, y, color, false);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean dropShadow) {
        source.text(font, text, x, y, applyColor(ComputerTheme.remap(color)), dropShadow);
        return font.width(text);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, false);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        source.centeredText(font, text, x, y, applyColor(ComputerTheme.remap(color)));
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        source.centeredText(font, text, x, y, applyColor(ComputerTheme.remap(color)));
    }

    public void drawCenteredString(Font font, FormattedCharSequence text, int x, int y, int color) {
        source.centeredText(font, text, x, y, applyColor(ComputerTheme.remap(color)));
    }

    public void renderOutline(int x, int y, int width, int height, int color) {
        source.outline(x, y, width, height, applyColor(ComputerTheme.remap(color)));
    }

    public void renderItem(ItemStack stack, int x, int y) {
        if (colorMultiplier == 0xFFFFFFFF) {
            source.item(stack, x, y);
            return;
        }
        Set<GuiItemRenderState> previous = Collections.newSetFromMap(new IdentityHashMap<>());
        GuiRenderState renderState = guiRenderState();
        if (renderState != null) renderState.forEachItem(previous::add);
        source.item(stack, x, y);
        if (renderState == null) return;
        renderState.forEachItem(item -> {
            if (!previous.contains(item)) tintItem(item.itemStackRenderState(), colorMultiplier);
        });
    }

    public void blit(Identifier texture, int x, int y, int width, int height, int u, int v,
                     int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        source.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) u, (float) v, width, height,
                sourceWidth, sourceHeight, textureWidth, textureHeight, colorMultiplier);
    }

    private int applyColor(int color) {
        int alpha = ((color >>> 24) * (colorMultiplier >>> 24) + 127) / 255;
        int red = ((color >> 16 & 255) * (colorMultiplier >> 16 & 255) + 127) / 255;
        int green = ((color >> 8 & 255) * (colorMultiplier >> 8 & 255) + 127) / 255;
        int blue = ((color & 255) * (colorMultiplier & 255) + 127) / 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private GuiRenderState guiRenderState() {
        if (GUI_RENDER_STATE_FIELD == null) {
            logItemTintWarning("Unable to access GUI item render state for tinting", null);
            return null;
        }
        try {
            return (GuiRenderState) GUI_RENDER_STATE_FIELD.get(source);
        } catch (IllegalAccessException | RuntimeException exception) {
            logItemTintWarning("Unable to read GUI item render state for tinting", exception);
            return null;
        }
    }

    private static void tintItem(TrackingItemStackRenderState item, int color) {
        if (ITEM_LAYERS_FIELD == null || ITEM_LAYER_COUNT_FIELD == null || LAYER_QUADS_FIELD == null) {
            logItemTintWarning("Unable to access item geometry for GUI tinting", null);
            return;
        }
        try {
            ItemStackRenderState.LayerRenderState[] layers =
                    (ItemStackRenderState.LayerRenderState[]) ITEM_LAYERS_FIELD.get(item);
            int layerCount = ITEM_LAYER_COUNT_FIELD.getInt(item);
            for (int index = 0; index < layerCount; index++) {
                ItemStackRenderState.LayerRenderState layer = layers[index];
                IntList tints = layer.tintLayers();
                int tintIndex = tints.size();
                for (int tint = 0; tint < tintIndex; tint++) tints.set(tint, ARGB.multiply(tints.getInt(tint), color));
                if (PREPARE_QUAD_LIST_METHOD != null) {
                    @SuppressWarnings("unchecked")
                    List<BakedQuad> quads = (List<BakedQuad>) PREPARE_QUAD_LIST_METHOD.invoke(layer);
                    if (tintQuadList(quads, tintIndex)) tints.add(color);
                    continue;
                }
                Object quads = LAYER_QUADS_FIELD.get(layer);
                if (quads == null) continue;
                ItemQuadsAccess access = ITEM_QUADS_ACCESS.get(quads.getClass());
                if (!access.available() || LAYER_SET_QUADS_METHOD == null) continue;
                @SuppressWarnings("unchecked")
                List<BakedQuad> all = (List<BakedQuad>) access.all().invoke(quads);
                @SuppressWarnings("unchecked")
                List<BakedQuad> solid = (List<BakedQuad>) access.solid().invoke(quads);
                @SuppressWarnings("unchecked")
                List<BakedQuad> translucent = (List<BakedQuad>) access.translucent().invoke(quads);
                List<BakedQuad> tintedAll = tintedQuadListCopy(all, tintIndex);
                if (tintedAll == null) continue;
                List<BakedQuad> tintedSolid = tintedQuadListCopy(solid, tintIndex);
                List<BakedQuad> tintedTranslucent = tintedQuadListCopy(translucent, tintIndex);
                Object updated = access.constructor().newInstance(tintedAll, tintedSolid == null ? solid : tintedSolid,
                        tintedTranslucent == null ? translucent : tintedTranslucent);
                LAYER_SET_QUADS_METHOD.invoke(layer, updated);
                tints.add(color);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logItemTintWarning("Unable to apply GUI item tint", exception);
        }
    }

    private static boolean tintQuadList(List<BakedQuad> quads, int tintIndex) throws ReflectiveOperationException {
        boolean changed = false;
        for (int index = 0; index < quads.size(); index++) {
            BakedQuad quad = quads.get(index);
            if (alreadyTinted(quad, tintIndex)) continue;
            quads.set(index, tintedQuad(quad, tintIndex));
            changed = true;
        }
        return changed;
    }

    private static List<BakedQuad> tintedQuadListCopy(List<BakedQuad> quads, int tintIndex) throws ReflectiveOperationException {
        List<BakedQuad> result = null;
        for (int index = 0; index < quads.size(); index++) {
            BakedQuad quad = quads.get(index);
            if (alreadyTinted(quad, tintIndex)) {
                if (result != null) result.add(quad);
                continue;
            }
            if (result == null) result = new ArrayList<>(quads.size());
            if (index > 0) result.addAll(quads.subList(0, index));
            result.add(tintedQuad(quad, tintIndex));
        }
        return result;
    }

    private static boolean alreadyTinted(BakedQuad quad, int tintCount) {
        int tintIndex = quad.materialInfo().tintIndex();
        return tintIndex >= 0 && tintIndex < tintCount;
    }

    private static BakedQuad tintedQuad(BakedQuad quad, int tintIndex) throws ReflectiveOperationException {
        return TINTED_QUAD_CACHE.computeIfAbsent(quad, ignored -> new java.util.HashMap<>()).computeIfAbsent(tintIndex, ignored -> {
            try {
                Object material = replaceRecordComponent(quad.materialInfo(), "tintIndex", tintIndex);
                return (BakedQuad) replaceRecordComponent(quad, "materialInfo", material);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static Object replaceRecordComponent(Object record, String name, Object value) throws ReflectiveOperationException {
        RecordComponent[] components = record.getClass().getRecordComponents();
        if (components == null) throw new IllegalArgumentException("Expected record: " + record.getClass().getName());
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        boolean replaced = false;
        for (int index = 0; index < components.length; index++) {
            RecordComponent component = components[index];
            parameterTypes[index] = component.getType();
            Method accessor = component.getAccessor();
            accessor.setAccessible(true);
            arguments[index] = component.getName().equals(name) ? value : accessor.invoke(record);
            replaced |= component.getName().equals(name);
        }
        if (!replaced) throw new NoSuchFieldException(record.getClass().getName() + "." + name);
        Constructor<?> constructor = record.getClass().getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private static Method method(Class<?> owner, String name) {
        try {
            Method method = owner.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static Method singleArgumentMethod(Class<?> owner, String name) {
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            try {
                method.setAccessible(true);
                return method;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private record ItemQuadsAccess(Method all, Method solid, Method translucent, Constructor<?> constructor) {
        private boolean available() {
            return all != null && solid != null && translucent != null && constructor != null;
        }
    }

    private static void logItemTintWarning(String message, Throwable cause) {
        if (!ITEM_TINT_WARNING_LOGGED.compareAndSet(false, true)) return;
        if (cause == null) com.craisinlord.antos.AntOS.LOGGER.warn(message);
        else com.craisinlord.antos.AntOS.LOGGER.warn(message, cause);
    }

    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        source.enableScissor(minX, minY, maxX, maxY);
    }

    public void disableScissor() {
        source.disableScissor();
    }

    public static final class LegacyPose {
        private final GuiGraphicsExtractor source;
        private final Matrix3x2fStack matrix;

        private LegacyPose(GuiGraphicsExtractor source) {
            this.source = source;
            this.matrix = source.pose();
        }

        public void pushPose() {
            matrix.pushMatrix();
        }

        public void popPose() {
            matrix.popMatrix();
        }

        public void translate(double x, double y, double z) {
            if (z > 0.0D) source.nextStratum();
            matrix.translate((float) x, (float) y);
        }

        public void translate(float x, float y, float z) {
            translate((double) x, (double) y, (double) z);
        }

        public void scale(double x, double y, double z) {
            matrix.scale((float) x, (float) y);
        }

        public void scale(float x, float y, float z) {
            scale((double) x, (double) y, (double) z);
        }

        public void rotate(float radians) {
            matrix.rotate(radians);
        }

        public Matrix3x2fStack matrix() {
            return matrix;
        }
    }
}
