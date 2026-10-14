package com.craisinlord.antos.content.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class DiskDefinitionTest {
    public static void main(String[] args) throws Exception {
        var existing = parse("{\"category\":\"research\",\"title\":\"disk.title\",\"entries\":[\"examplemod:notes\"],\"tasks\":[\"examplemod:research\"],\"wallpaper\":\"examplemod:meadow\"}");
        check(existing.category().equals(ResourceLocation.parse("examplemod:research")), "existing category namespace is preserved");
        check(existing.entries().size() == 1 && existing.tasks().size() == 1 && existing.wallpaper().equals("examplemod:meadow"), "existing disk content is preserved");
        check(!existing.hidden() && existing.descriptionKey().isEmpty(), "existing definitions use library defaults");
        var secret = parse("{\"hidden\":true,\"description\":\"disk.description\"}");
        check(secret.hidden() && secret.descriptionKey().equals("disk.description"), "secret disks and descriptions parse");
        for (String value : new String[]{"null", "{}", "[]", "\"true\"", "1"}) {
            check(!parse("{\"hidden\":" + value + "}").hidden(), "malformed hidden field does not hide existing disks");
        }
        var compatible = new ComputerGuideData.Disk(existing.id(), existing.category(), existing.titleKey(), existing.entries(), existing.wallpaper(), existing.tasks());
        check(compatible.equals(existing), "existing Java constructor remains compatible");
        var rewarding = parse("{\"rewards\":[{\"type\":\"antcoins\",\"amount\":100},{\"type\":\"item\",\"item\":\"minecraft:emerald\",\"count\":2},{\"type\":\"mail\",\"message\":\"examplemod:thanks\"},{\"type\":\"experience\",\"points\":10},{\"type\":\"experience_levels\",\"levels\":2},{\"type\":\"advancement\",\"advancement\":\"minecraft:story/mine_stone\"},{\"type\":\"effect\",\"effect\":\"minecraft:speed\",\"duration_ticks\":100}]}");
        check(rewarding.rewards().size() == 7, "supported disk rewards use the shared parser");
        check(ComputerGuideData.parseRewards(rewarding.id(), ComputerGuideData.encodeRewards(rewarding.rewards()), false).equals(rewarding.rewards()), "disk reward network encoding round trips");
        var rejected = parse("{\"rewards\":[{\"type\":\"archive\",\"entry\":\"examplemod:notes\"},{\"type\":\"choice\"},{\"type\":\"item_pool\",\"pool\":\"examplemod:pool\",\"mode\":\"choice\"},{\"type\":\"antcoins\",\"amount\":5}]}");
        check(rejected.rewards().size() == 1 && rejected.rewards().getFirst().antcoins() == 5, "disk interactive and Archive rewards are rejected without losing valid rewards");
        var capped = parse("{\"rewards\":[{\"type\":\"item\",\"item\":\"minecraft:stone\",\"count\":108},{\"type\":\"item\",\"item\":\"minecraft:stone\",\"count\":1}]}");
        check(capped.rewards().size() == 1, "disk direct item rewards respect the existing combined cap");
        var taskRewards = ComputerGuideData.parseRewards(existing.id(), JsonParser.parseString("[{\"type\":\"archive\",\"entry\":\"examplemod:notes\"},{\"type\":\"choice\",\"options\":[{\"id\":\"a\",\"rewards\":[{\"type\":\"antcoins\",\"amount\":1}]},{\"id\":\"b\",\"rewards\":[{\"type\":\"experience\",\"points\":2}]}]}]"), true);
        check(taskRewards.size() == 2, "ordinary task Archive and choice rewards still parse");
        check(ComputerGuideData.parseRewards(existing.id(), ComputerGuideData.encodeRewards(taskRewards), true).equals(taskRewards), "shared parser retains task reward choice contents");
        System.out.println("Disk definition tests passed");
    }

    private static ComputerGuideData.Disk parse(String json) throws Exception {
        Method parser = ComputerGuideData.class.getDeclaredMethod("parseDisk", ResourceLocation.class, JsonElement.class, Map.class);
        parser.setAccessible(true);
        ResourceLocation id = ResourceLocation.parse("examplemod:disk");
        Map<ResourceLocation, ComputerGuideData.Disk> disks = new HashMap<>();
        parser.invoke(null, id, JsonParser.parseString(json), disks);
        return disks.get(id);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
