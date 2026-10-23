package com.craisinlord.antos.content.guide;

import com.google.gson.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;

public final class CustomDisplaysTest {
    public static void main(String[] args) throws Exception {
        JsonObject valid = json("{width:320,height:180,elements:[{type:'slot',item:'minecraft:diamond'}]}");
        CustomDisplays.validate(valid);
        rejects(() -> CustomDisplays.validate(json("{version:2,elements:[]}")));
        rejects(() -> CustomDisplays.validate(json("{width:0,elements:[]}")));
        rejects(() -> CustomDisplays.validate(json("{elements:[{type:'script'}]}")));
        rejects(() -> CustomDisplays.validate(json("{elements:[{type:'scene',blocks:[{pos:[100,0,0],state:'minecraft:stone'}]}]}")));
        rejects(() -> CustomDisplays.validate(json("{elements:[{type:'bar',value:2}]}")));
        rejects(() -> CustomDisplays.validate(json("{elements:[{type:'slot',item:'$missing'}]}")));
        rejects(() -> CustomDisplays.validate(json("{elements:[{type:'rect',width:'$missing'}]}")));
        rejects(() -> CustomDisplays.validate(json("{elements:[{type:'slot',count:1.5}]}")));
        rejects(() -> CustomDisplays.layoutGroup(json("{layout:'grid',columns:1.5,elements:[]}")));
        JsonObject deep = json("{elements:[]}");
        JsonObject nested = deep;
        for (int i = 0; i < 20; i++) {
            JsonObject child = json("{id:'nested',elements:[]}"); JsonArray layouts = new JsonArray(); layouts.add(child); nested.add("layouts", layouts); nested = child;
        }
        try { CustomDisplays.compile(deep, null); throw new AssertionError("deep layouts accepted"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains("nesting limit"), "nested compilation is bounded before validation"); }

        Identifier template = Identifier.parse("test:template"), instance = Identifier.parse("test:instance");
        Map<Identifier, JsonObject> source = new LinkedHashMap<>();
        source.put(template, json("{width:320,height:100,variables:{input:{type:'string'},count:{type:'number',default:2}},elements:[{type:'slot',item:'$input',count:'$count'}]}"));
        source.put(instance, json("{template:'test:template',values:{input:'minecraft:gold_ingot'}}"));
        JsonObject result = CustomDisplays.resolve(instance, source, new LinkedHashSet<>());
        check(result.getAsJsonArray("elements").get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:gold_ingot"), "template substitution");
        check(result.getAsJsonArray("elements").get(0).getAsJsonObject().get("count").getAsInt() == 2, "typed defaults");
        check(source.get(template).has("variables"), "resolution does not mutate templates");
        source.put(instance, json("{template:'test:template',values:{input:4}}"));
        rejects(() -> CustomDisplays.resolve(instance, source, new LinkedHashSet<>()));
        source.put(instance, json("{template:'test:template',values:{input:'minecraft:stone',unknown:3}}"));
        rejects(() -> CustomDisplays.resolve(instance, source, new LinkedHashSet<>()));
        source.put(instance, json("{template:'test:template',elements:[]}"));
        rejects(() -> CustomDisplays.resolve(instance, source, new LinkedHashSet<>()));
        source.put(instance, json("{template:'test:instance'}"));
        rejects(() -> CustomDisplays.resolve(instance, source, new LinkedHashSet<>()));
        source.put(instance, json("{preset:'machine',elements:[]}"));
        rejects(() -> CustomDisplays.resolve(instance, source, new LinkedHashSet<>()));
        source.put(instance, json("{recipe_binding:{recipe:'test:missing'},elements:[]}"));
        try { CustomDisplays.resolve(instance, source, new LinkedHashSet<>()); throw new AssertionError("unused binding accepted"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains("requires a preset or template"), "unused bindings fail before recipe resolution"); }
        source.put(instance, json("{preset:'machine',title:'literal:Custom machine',minimum_scale:0.8}"));
        JsonObject titled = CustomDisplays.resolve(instance, source, new LinkedHashSet<>());
        check(titled.get("title").getAsString().equals("literal:Custom machine") && titled.get("minimum_scale").getAsDouble() == 0.8, "presets preserve authored display metadata");

        JsonArray pattern = JsonParser.parseString("[[\"AB\",\" A\"],[\" B\",\"  \"]]").getAsJsonArray();
        JsonArray blocks = CustomDisplays.patternBlocks(pattern, json("{A:'minecraft:stone',B:'minecraft:gold_block'}"));
        check(blocks.size() == 4, "pattern skips spaces");
        JsonArray position = blocks.get(3).getAsJsonObject().getAsJsonArray("pos");
        check(position.get(0).getAsInt() == 1 && position.get(1).getAsInt() == 1 && position.get(2).getAsInt() == 0, "bottom-up Y, row Z, character X");
        rejects(() -> CustomDisplays.patternBlocks(pattern, json("{A:'minecraft:stone'}")));
        rejects(() -> CustomDisplays.patternBlocks(JsonParser.parseString("[[\"AA\",\"A\"]]").getAsJsonArray(), json("{A:'minecraft:stone'}")));

        CompoundTag root = new CompoundTag();
        ListTag palette = new ListTag();
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:oak_stairs");
        CompoundTag properties = new CompoundTag(); properties.putString("facing", "east");
        state.put("Properties", properties); palette.add(state); root.put("palette", palette);
        ListTag nbtBlocks = new ListTag();
        CompoundTag block = new CompoundTag(); block.putInt("state", 0);
        ListTag pos = new ListTag(); pos.add(IntTag.valueOf(1)); pos.add(IntTag.valueOf(2)); pos.add(IntTag.valueOf(3));
        block.put("pos", pos);
        CompoundTag unsafeData = new CompoundTag(); unsafeData.putString("Command", "must never be copied"); block.put("nbt", unsafeData);
        nbtBlocks.add(block); root.put("blocks", nbtBlocks);
        JsonObject normalized = CustomDisplays.structureBlocks(root).get(0).getAsJsonObject();
        check(normalized.get("state").getAsString().equals("minecraft:oak_stairs[facing=east]"), "NBT blockstate preservation");
        check(normalized.keySet().equals(Set.of("pos", "state")), "NBT behavior and inventories discarded");
        block.putInt("state", 99);
        rejects(() -> CustomDisplays.structureBlocks(root));

        CustomDisplays.apply(Map.of(template, valid));
        JsonObject copy = CustomDisplays.get(template.toString()); copy.addProperty("width", 1);
        check(CustomDisplays.get(template.toString()).get("width").getAsInt() == 320, "registry defensive copy");
        long revision = CustomDisplays.revision();
        Map<Identifier, JsonObject> decoded = CustomDisplays.decode(CustomDisplays.encode());
        check(decoded.size() == 1, "network round trip");
        CustomDisplays.apply(Map.of());
        check(CustomDisplays.revision() > revision && CustomDisplays.get(template.toString()) == null, "reload clears removed definitions");
        check(CustomDisplays.content(JsonParser.parseString("[{text:'before'},{display:'test:template'},{text:'after'}]")).equals(List.of("before", "@display:test:template", "after")), "ordered content");

        JsonObject grid = json("{layout:'grid',columns:2,cell_width:20,cell_height:30,elements:[{type:'slot'},{type:'slot'},{type:'slot'}]}");
        CustomDisplays.layoutGroup(grid);
        check(grid.getAsJsonArray("elements").get(2).getAsJsonObject().get("y").getAsInt() == 30, "grid row placement");
        JsonObject radial = json("{layout:'radial',radius:40,elements:[{type:'slot'},{type:'slot'},{type:'slot'},{type:'slot'}]}");
        CustomDisplays.layoutGroup(radial);
        check(Math.abs(radial.getAsJsonArray("elements").get(0).getAsJsonObject().get("y").getAsDouble() + 49) < 0.001, "radial starts at north");

        String contentSnapshot = "{entries:[{id:'test:article',title:'literal:Test',content:[{text:'before'},{display:'test:template'},{text:'after'}]}],tasks:[{id:'test:task',title:'Test',content:[{text:'intro'},{display:'test:template'}]}],displays:{'test:template':{version:1,width:320,height:100,elements:[]}}}";
        ComputerGuideData.applyNetworkSnapshot(contentSnapshot);
        check(ComputerGuideData.entry(Identifier.parse("test:article")).descriptionKeys().equals(List.of("before", "@display:test:template", "after")), "Archive structured content");
        check(ComputerGuideData.task(Identifier.parse("test:task")).showcase().equals(List.of("intro", "@display:test:template")), "Task structured content");
        String encodedGuide = ComputerGuideData.archiveDefinitions().json();
        ComputerGuideData.applyNetworkSnapshot(encodedGuide);
        check(ComputerGuideData.entry(Identifier.parse("test:article")).descriptionKeys().get(1).equals("@display:test:template"), "Archive content survives guide sync");
        String firstHash = ComputerGuideData.archiveDefinitions().hash();
        CustomDisplays.apply(Map.of());
        check(ComputerGuideData.archiveDefinitions().hash().equals(firstHash), "display reloads sync separately from guide definitions");

        if (args.length > 0) {
            java.nio.file.Path examples = java.nio.file.Path.of(args[0], "examples/datapack-template/data/examplemod/computer/display");
            Map<Identifier, JsonObject> authored = new LinkedHashMap<>();
            try (var files = java.nio.file.Files.list(examples)) {
                for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) authored.put(Identifier.parse("examplemod:" + file.getFileName().toString().replace(".json", "")), json(java.nio.file.Files.readString(file)));
            }
            for (var entry : authored.entrySet()) {
                if (entry.getValue().has("variables") || entry.getValue().has("recipe_binding") || entry.getValue().has("variants")) continue;
                JsonObject display = CustomDisplays.resolve(entry.getKey(), authored, new LinkedHashSet<>());
                CustomDisplays.compile(display, null);
                CustomDisplays.validate(display);
            }
        }
        System.out.println("Custom display validation, templates, patterns, NBT, sync, and content tests passed");
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Expected validation failure");
    }
}
