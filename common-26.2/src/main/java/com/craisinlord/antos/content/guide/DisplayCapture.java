package com.craisinlord.antos.content.guide;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class DisplayCapture {
    private DisplayCapture() { }

    public static int capture(CommandSourceStack source, BlockPos first, BlockPos second, String name) {
        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        int sizeX = max.getX() - min.getX() + 1, sizeY = max.getY() - min.getY() + 1, sizeZ = max.getZ() - min.getZ() + 1;
        if (sizeX > 64 || sizeY > 64 || sizeZ > 64) {
            source.sendFailure(Component.translatable("commands.antos.display.capture.too_big"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        JsonArray blocks = new JsonArray();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (blocks.size() >= CustomDisplays.MAX_BLOCKS) {
                source.sendFailure(Component.translatable("commands.antos.display.capture.too_big"));
                return 0;
            }
            JsonObject block = new JsonObject();
            JsonArray position = new JsonArray();
            position.add(pos.getX() - min.getX()); position.add(pos.getY() - min.getY()); position.add(pos.getZ() - min.getZ());
            block.add("pos", position);
            block.addProperty("state", net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(state));
            blocks.add(block);
        }
        JsonArray entities = new JsonArray();
        for (Entity entity : level.getEntities((Entity) null, new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1), entity -> !(entity instanceof Player))) {
            if (entities.size() >= 64) break;
            JsonObject object = new JsonObject();
            JsonArray position = new JsonArray();
            position.add(round(entity.getX() - min.getX() - .5)); position.add(round(entity.getY() - min.getY())); position.add(round(entity.getZ() - min.getZ() - .5));
            object.add("pos", position);
            object.addProperty("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            object.addProperty("rotation", Math.round(entity.getYRot()));
            if (entity instanceof LivingEntity living) {
                if (!living.getMainHandItem().isEmpty()) object.addProperty("held", BuiltInRegistries.ITEM.getKey(living.getMainHandItem().getItem()).toString());
                if (!living.getOffhandItem().isEmpty()) object.addProperty("offhand", BuiltInRegistries.ITEM.getKey(living.getOffhandItem().getItem()).toString());
                if (living.isBaby()) object.addProperty("baby", true);
            }
            if (entity instanceof net.minecraft.world.entity.npc.villager.Villager villager)
                object.addProperty("profession", villager.getVillagerData().profession().unwrapKey().map(key -> key.identifier().toString()).orElse("minecraft:none"));
            entities.add(object);
        }
        JsonObject scene = new JsonObject();
        scene.addProperty("id", "scene");
        scene.addProperty("type", "scene");
        scene.addProperty("x", 2); scene.addProperty("y", 2);
        scene.addProperty("width", 196); scene.addProperty("height", 176);
        scene.addProperty("yaw", 45); scene.addProperty("pitch", 30);
        scene.add("blocks", blocks);
        if (!entities.isEmpty()) scene.add("entities", entities);
        JsonArray elements = new JsonArray();
        elements.add(scene);
        JsonObject display = new JsonObject();
        display.addProperty("version", 1);
        display.addProperty("title", "literal:" + name);
        display.addProperty("width", 200); display.addProperty("height", 180);
        display.add("elements", elements);
        Path file = source.getServer().getServerDirectory().resolve("antos").resolve("captures").resolve(name + ".json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(display), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            source.sendFailure(Component.translatable("commands.antos.display.capture.failed", exception.getMessage()));
            return 0;
        }
        int blockCount = blocks.size(), entityCount = entities.size();
        source.sendSuccess(() -> Component.translatable("commands.antos.display.capture.done", blockCount, entityCount, file.toString()), false);
        return blockCount;
    }

    private static double round(double value) { return Math.round(value * 100) / 100.0; }
}
