package com.craisinlord.antos.content;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class SavedDataMigrationTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path world = Files.createTempDirectory("antos-saved-data");
        try {
            Path overworld = world.resolve("dimensions/minecraft/overworld/data");
            check(AntOSSavedDataType.dataFolder(world).equals(overworld.normalize()), "Data folder matches where 26.x stores overworld data");

            Path legacy = write(world.resolve("data/antos_antmail.dat"), "from 1.21.1");
            Path current = overworld.resolve("antos/antos_antmail.dat");
            AntOSSavedDataType.migrateLegacyFile(world, "antos_antmail");
            check(read(current).equals("from 1.21.1"), "A 1.21.1 file is copied to where 26.x reads it");
            check(read(legacy).equals("from 1.21.1"), "The 1.21.1 file stays as a backup");
            check(!Files.exists(world.resolve("data/antos/antos_antmail.dat")), "Nothing is written to the unused world data folder");

            write(current, "newer 26.x data");
            AntOSSavedDataType.migrateLegacyFile(world, "antos_antmail");
            check(read(current).equals("newer 26.x data"), "Existing 26.x data is never overwritten");

            write(overworld.resolve("antos_antazon.dat"), "moved by an upgrader");
            AntOSSavedDataType.migrateLegacyFile(world, "antos_antazon");
            check(read(overworld.resolve("antos/antos_antazon.dat")).equals("moved by an upgrader"), "A legacy file in the overworld folder is found too");

            write(world.resolve("data/antos_blockle.dat"), "world copy");
            write(overworld.resolve("antos_blockle.dat"), "overworld copy");
            AntOSSavedDataType.migrateLegacyFile(world, "antos_blockle");
            check(read(overworld.resolve("antos/antos_blockle.dat")).equals("world copy"), "The original 1.21.1 location wins");

            AntOSSavedDataType.migrateLegacyFile(world, "antos_missing");
            check(!Files.exists(overworld.resolve("antos/antos_missing.dat")), "A fresh world creates nothing");

            for (String id : new String[]{"antos_game_data", "antos_game_rewards", "antos_computer_workspaces"}) {
                write(world.resolve("data/" + id + ".dat"), id);
                AntOSSavedDataType.migrateLegacyFile(world, id);
                check(read(overworld.resolve("antos/" + id + ".dat")).equals(id), id + " migrates");
            }

            write(world.resolve("data/antos_account_one.dat"), "one");
            write(world.resolve("data/antos_account_two.dat"), "two");
            write(world.resolve("data/antos_mailbox_ant.dat"), "not an account");
            AntOSSavedDataType.migrateLegacyPrefix(world, "antos_account_");
            check(read(overworld.resolve("antos/antos_account_one.dat")).equals("one")
                    && read(overworld.resolve("antos/antos_account_two.dat")).equals("two"), "Account shards migrate by prefix");
            check(!Files.exists(overworld.resolve("antos/antos_mailbox_ant.dat")), "A prefix only moves its own files");

            Path empty = Files.createTempDirectory("antos-saved-data-empty");
            AntOSSavedDataType.migrateLegacyPrefix(empty, "antos_account_");
            Files.delete(empty);
            System.out.println("SavedDataMigrationTest passed");
        } finally {
            try (var files = Files.walk(world)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static Path write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    private static String read(Path file) throws Exception {
        return Files.exists(file) ? Files.readString(file) : "<missing>";
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
