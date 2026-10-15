package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.AntOS;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.saveddata.SavedData;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SavedDataFiles {
    private SavedDataFiles() {
    }

    public static boolean write(SavedData data, File file, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("data", data.save(new CompoundTag()));
        NbtUtils.addCurrentDataVersion(tag);
        Path target = file.toPath();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            NbtIo.writeCompressed(tag, temp.toFile());
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            AntOS.LOGGER.error("Could not save AntOS data {}", file, exception);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
            return false;
        }
    }
}
