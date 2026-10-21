package com.craisinlord.antos.content.client.palette;

import com.craisinlord.antos.AntOS;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class WallpaperRecolor {
    private static final Map<ResourceLocation, Entry> CACHE = new HashMap<>();
    private static int nextId;

    private WallpaperRecolor() { }

    public static ResourceLocation recolored(ResourceLocation source) {
        long signature = Ui.wallpaperSignature();
        Entry entry = CACHE.get(source);
        if (entry != null && entry.signature() == signature) return entry.id();
        var resource = Minecraft.getInstance().getResourceManager().getResource(source);
        if (resource.isEmpty()) return null;
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            NativeImage output = new NativeImage(image.getWidth(), image.getHeight(), false);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    output.setPixelRGBA(x, y, swap(Ui.wallpaperPixel(swap(image.getPixelRGBA(x, y)))));
                }
            }
            ResourceLocation id = entry != null ? entry.id() : ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "wallpaper_recolor/" + nextId++);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(output));
            CACHE.put(source, new Entry(signature, id));
            return id;
        } catch (Exception exception) {
            return null;
        }
    }

    private static int swap(int color) {
        return color & 0xFF00FF00 | (color & 0x00FF0000) >> 16 | (color & 0x000000FF) << 16;
    }

    private record Entry(long signature, ResourceLocation id) { }
}
