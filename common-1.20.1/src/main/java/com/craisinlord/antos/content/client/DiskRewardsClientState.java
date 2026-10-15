package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;

public final class DiskRewardsClientState {
    private static final Map<String, ArrayDeque<Award>> AWARDS = new ConcurrentHashMap<>();

    private DiskRewardsClientState() { }

    public static void update(ComputerAccessResultPayload result) {
        if (!result.data().startsWith(ComputerAccessPayload.DISK_REWARDS + "\0")) return;
        try {
            String[] envelope = result.data().split("\u0000", 3);
            var root = JsonParser.parseString(envelope[2]).getAsJsonObject();
            String key = "workspace:" + root.get("workspace").getAsString();
            ResourceLocation disk = ResourceLocation.tryParse(root.get("disk").getAsString());
            ArrayDeque<Award> queue = AWARDS.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            if (queue.size() >= 16) queue.removeFirst();
            queue.addLast(new Award(root.get("title").getAsString(), decode(disk, root.get("rewards"))));
        } catch (RuntimeException ignored) { }
    }

    public static List<ComputerGuideData.TaskReward> decode(ResourceLocation id, JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<ComputerGuideData.TaskReward> receipt = new ArrayList<>();
        for (JsonElement reward : element.getAsJsonArray()) {
            com.google.gson.JsonArray single = new com.google.gson.JsonArray();
            single.add(reward);
            receipt.addAll(ComputerGuideData.parseRewards(id, single, false));
        }
        return List.copyOf(receipt);
    }

    public static Award poll() {
        ArrayDeque<Award> queue = AWARDS.get(ComputerWorkspaceClientKey.of());
        return queue == null ? null : queue.pollFirst();
    }

    public static void clearAll() { AWARDS.clear(); }

    public static String label(ComputerGuideData.TaskReward reward) {
        Component text = switch (reward.type()) {
            case "item" -> {
                var item = BuiltInRegistries.ITEM.getOptional(reward.itemId());
                yield Component.translatable("computer.antos.disks.reward.item", reward.count(), item.isPresent() ? new ItemStack(item.get()).getHoverName() : Component.translatable("computer.antos.disks.reward.unknown_item"));
            }
            case "antcoins" -> Component.translatable("computer.antos.disks.reward.antcoins", reward.antcoins());
            case "experience" -> Component.translatable("computer.antos.disks.reward.experience", reward.experiencePoints());
            case "experience_levels" -> Component.translatable("computer.antos.disks.reward.levels", reward.count());
            case "advancement" -> Component.translatable("computer.antos.disks.reward.advancement");
            case "effect" -> {
                var effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(Registries.MOB_EFFECT, reward.target()));
                yield effect.isPresent() ? Component.translatable("computer.antos.disks.reward.effect", effect.get().value().getDisplayName()) : Component.translatable("computer.antos.disks.reward.unknown_effect");
            }
            case "mail" -> Component.translatable("computer.antos.disks.reward.mail", reward.subject().isBlank() ? Component.translatable("computer.antos.disks.reward.message") : Component.literal(reward.subject()));
            default -> Component.translatable("computer.antos.disks.reward.received");
        };
        return AntOSPlayerText.apply(text.getString());
    }

    public record Award(String title, List<ComputerGuideData.TaskReward> rewards) { }
}
