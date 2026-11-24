package com.craisinlord.antos.content.editor;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class AuthoringFiles {
    public static final String PACK_NAME = "antos-authored";
    private AuthoringFiles() { }

    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public static Path document(Path datapacks, AuthoringKind kind, String namespace, String path) throws IOException {
        if (!namespace.matches("[a-z0-9_\\-.]+") || namespace.equals(".") || namespace.equals("..") || path.isBlank() || path.length() > 180
                || !path.matches("[a-z0-9_\\-./]+") || path.startsWith("/") || path.endsWith("/")
                || java.util.Arrays.stream(path.split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals("..")))
            throw new IllegalArgumentException("Use a valid namespace:path ID without empty, dot or parent segments");
        if (!kind.includes(kind.directory() + "/" + path + ".json")) throw new IllegalArgumentException("Task IDs cannot use category, group or reward_pool subdirectories");
        Path root = datapacks.toAbsolutePath().normalize();
        Path result = root.resolve(PACK_NAME).resolve("data").resolve(namespace).resolve(kind.directory()).resolve(path + ".json").normalize();
        if (!result.startsWith(root.resolve(PACK_NAME))) throw new IllegalArgumentException("Invalid editor path");
        for (Path current = result; current != null && current.startsWith(root); current = current.getParent())
            if (Files.isSymbolicLink(current) || Files.exists(root) && Files.exists(current)
                    && !current.toRealPath().startsWith(root.toRealPath())) throw new IOException("Editor output cannot use symbolic links or directory junctions");
        return result;
    }

    public static void save(Path datapacks, AuthoringKind kind, String namespace, String path, String text) throws IOException {
        Path file = document(datapacks, kind, namespace, path);
        Files.createDirectories(file.getParent());
        Path pack = datapacks.toAbsolutePath().normalize().resolve(PACK_NAME);
        Path metadata = pack.resolve("pack.mcmeta");
        if (!Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) {
            JsonObject settings = new JsonObject();
            settings.addProperty("pack_format", 15);
            settings.addProperty("description", "AntOS in-game authored content");
            JsonObject root = new JsonObject();
            root.add("pack", settings);
            atomicWrite(metadata, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        }
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            Path backup = file.resolveSibling(file.getFileName() + ".previous");
            if (Files.isSymbolicLink(backup)) throw new IOException("Editor backup cannot be a symbolic link");
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        atomicWrite(file, text);
    }

    public static void atomicWrite(Path file, String text) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), ".antos-editor-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static void remove(Path file) throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + ".previous");
        if (Files.isSymbolicLink(file) || Files.isSymbolicLink(backup)) throw new IOException("Editor files cannot be symbolic links");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(file);
    }
}
