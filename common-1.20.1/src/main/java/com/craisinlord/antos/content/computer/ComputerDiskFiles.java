package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.computer.paint.AntPaintCanvas;
import com.craisinlord.antos.content.computer.paint.AntPaintFile;
import com.craisinlord.antos.content.computer.paint.AntPaintFileCodec;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.guide.DiskFeatures;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ComputerDiskFiles {
    private ComputerDiskFiles() { }

    public static Map<ResourceLocation, String> load(ResourceManager manager) {
        Map<ResourceLocation, String> files = new LinkedHashMap<>();
        for (var entry : manager.listResources("computer/disk_file", id -> true).entrySet()) {
            ResourceLocation file = new ResourceLocation(entry.getKey().getNamespace(), entry.getKey().getPath().substring("computer/disk_file/".length()));
            try (var stream = entry.getValue().open()) {
                byte[] bytes = stream.readNBytes(ComputerFileSystem.MAX_FILE_SIZE * 4 + 1);
                if (bytes.length > ComputerFileSystem.MAX_FILE_SIZE * 4) throw new IllegalArgumentException("Disk file is too large");
                String text = new String(bytes, StandardCharsets.UTF_8);
                if (text.length() > ComputerFileSystem.MAX_FILE_SIZE) throw new IllegalArgumentException("Disk file is too large");
                files.put(file, text);
            } catch (Exception exception) { AntOS.LOGGER.warn("Cannot load disk file {}", file, exception); }
        }
        return Map.copyOf(files);
    }

    public static ResourceLocation sourceId(ResourceLocation source) {
        String path = source.getPath();
        for (String prefix : new String[]{"computer/disk_file/", "disk_file/", "disk_files/"}) if (path.startsWith(prefix)) { path = path.substring(prefix.length()); break; }
        return new ResourceLocation(source.getNamespace(), path);
    }

    public static String destination(ComputerGuideData.Disk disk, DiskFeatures.File file) {
        return destination("Disks", Component.translatable(disk.titleKey()).getString(), file);
    }

    public static String destination(String root, String folder, DiskFeatures.File file) {
        String path = file.path().replace('\\', '/');
        if (path.endsWith(".paint")) path = path.substring(0, path.length() - 6) + ".antpaint";
        if (!path.contains("/")) {
            String title = folder.replaceAll("[^\\p{L}\\p{N} _-]", "_");
            int available = Math.max(1, ComputerFileSystem.MAX_PATH_LENGTH - path.length() - root.length() - 3);
            if (title.length() > available) title = title.substring(0, available);
            path = root + "/" + title + "/" + path;
        }
        return ComputerFileSystem.normalize(path);
    }

    public static void copy(ComputerFileSystem files, DiskProfileState state, ComputerGuideData.Disk disk,
                            Map<ResourceLocation, String> sources, String player, long time) {
        copy(files, state, disk.id().toString(), "Disks", Component.translatable(disk.titleKey()).getString(), disk.features().files(), sources, player, time);
    }

    public static void copy(ComputerFileSystem files, DiskProfileState state, String owner, String root, String folder, java.util.List<DiskFeatures.File> entries,
                            Map<ResourceLocation, String> sources, String player, long time) {
        for (DiskFeatures.File file : entries) {
            String key = owner + "|" + file.path();
            if (state.hasFile(key)) continue;
            String path = destination(root, folder, file);
            if (path.length() > ComputerFileSystem.MAX_PATH_LENGTH || path.contains("..") || path.indexOf('\0') >= 0
                    || path.indexOf('\n') >= 0 || path.indexOf('\r') >= 0 || ComputerFileSystem.isProtected(path)) {
                AntOS.LOGGER.warn("Ignoring unsafe file path {} on {}", path, owner);
                continue;
            }
            if (files.get(path) != null) { state.recordFile(key, path); continue; }
            String contents = sources.get(sourceId(file.source()));
            if (contents == null) { AntOS.LOGGER.warn("{} refers to missing file {}", owner, file.source()); continue; }
            try {
                contents = path.endsWith(".antpaint") ? paint(contents, path, time) : contents.replace("{player}", player);
                if (!directories(files, path)) continue;
                ComputerFileSystem.Result result = files.createTextFile(path, contents);
                if (result.successful()) state.recordFile(key, path);
                else AntOS.LOGGER.warn("Cannot copy {} file {}: {}", owner, path, result.error());
            } catch (RuntimeException exception) { AntOS.LOGGER.warn("Cannot copy {} file {}", owner, path, exception); }
        }
    }

    private static boolean directories(ComputerFileSystem files, String path) {
        int slash = path.indexOf('/', 1);
        while (slash >= 0) {
            String directory = path.substring(0, slash);
            if (files.get(directory) == null && !files.createDirectory(directory).successful()) return false;
            if (files.get(directory).type() != ComputerFileSystem.ComputerFile.Type.DIRECTORY) return false;
            slash = path.indexOf('/', slash + 1);
        }
        return true;
    }

    public static String paint(String text, String path, long time) {
        AntPaintCanvas canvas;
        if (text.stripLeading().startsWith("{")) {
            var object = JsonParser.parseString(text).getAsJsonObject();
            var rows = object.getAsJsonArray("pixels");
            if (rows == null || rows.size() != AntPaintCanvas.HEIGHT) throw new IllegalArgumentException("Paint needs 24 pixel rows");
            canvas = new AntPaintCanvas();
            for (int y = 0; y < rows.size(); y++) {
                String row = rows.get(y).getAsString();
                if (row.length() != AntPaintCanvas.WIDTH || !row.matches("[01]{32}")) throw new IllegalArgumentException("Paint rows need 32 binary pixels");
                for (int x = 0; x < row.length(); x++) canvas.set(x, y, row.charAt(x) == '1');
            }
        } else canvas = AntPaintFileCodec.decode(Base64.getDecoder().decode(text.strip())).canvas().copy();
        String name = path.substring(path.lastIndexOf('/') + 1);
        return Base64.getEncoder().encodeToString(AntPaintFileCodec.encode(new AntPaintFile(UUID.randomUUID().toString(), name, canvas, time, time)));
    }
}
