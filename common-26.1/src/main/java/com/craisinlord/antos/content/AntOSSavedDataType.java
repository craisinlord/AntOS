package com.craisinlord.antos.content;

import com.mojang.serialization.Codec;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class AntOSSavedDataType {
    private AntOSSavedDataType() {}

    public static void migrateLegacyFile(MinecraftServer server, String id) {
        Path dataFolder = server.getWorldPath(LevelResource.DATA);
        Path legacy = dataFolder.resolve(id + ".dat");
        Path current = dataFolder.resolve("antos").resolve(id + ".dat");
        if (!Files.exists(legacy) || Files.exists(current)) return;
        Path temp = current.resolveSibling(current.getFileName() + ".migrating");
        try {
            Files.createDirectories(current.getParent());
            Files.copy(legacy, temp, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, current, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temp, current);
            }
        } catch (java.io.IOException exception) {
            try {
                Files.deleteIfExists(temp);
            } catch (java.io.IOException ignored) {
            }
            com.craisinlord.antos.AntOS.LOGGER.error("Could not migrate AntOS saved data {}; refusing to load it empty", id, exception);
            throw new java.io.UncheckedIOException("Could not migrate AntOS saved data " + legacy, exception);
        }
    }

    public static boolean isWritten(MinecraftServer server, String id, CompoundTag expected) {
        Path file = Identifier.fromNamespaceAndPath("antos", id).withSuffix(".dat").resolveAgainst(server.getWorldPath(LevelResource.DATA));
        if (!Files.isRegularFile(file)) return false;
        try {
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data").filter(expected::equals).isPresent();
        } catch (java.io.IOException | RuntimeException exception) {
            return false;
        }
    }

    public static void migrateLegacyPrefix(MinecraftServer server, String prefix) {
        Path dataFolder = server.getWorldPath(LevelResource.DATA);
        try (var files = Files.newDirectoryStream(dataFolder, prefix + "*.dat")) {
            for (Path file : files) migrateLegacyFile(server, file.getFileName().toString().replaceFirst("\\.dat$", ""));
        } catch (java.io.IOException exception) {
            com.craisinlord.antos.AntOS.LOGGER.warn("Could not migrate AntOS saved data files with prefix {}", prefix, exception);
        }
    }
    public static <T extends SavedData> SavedDataType<T> create(String id, Supplier<T> factory,
                                                                 Function<CompoundTag, T> decoder,
                                                                 BiFunction<T, CompoundTag, CompoundTag> encoder) {
        Codec<T> codec = CompoundTag.CODEC.xmap(decoder,
                value -> encoder.apply(value, new CompoundTag()));
        return new SavedDataType<>(Identifier.fromNamespaceAndPath("antos", id), factory, codec, null);
    }
}