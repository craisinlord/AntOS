package com.craisinlord.antos.content.guide;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

public final class DisplayFeaturesTest {
    public static void main(String[] args) throws Exception {
        JsonObject document = json("{width:320,height:200,elements:[{id:'altar',type:'scene',width:200,height:180,blocks:[{pos:[0,-2,0],state:'minecraft:oak_door[half=lower]',groups:['base']},{pos:[0,-1,0],state:'minecraft:oak_door[half=upper]'}, {pos:[1,-2,0],state:'minecraft:stone'}, {pos:[2,0,0],state:'test:unplaceable'}, {pos:[3,0,0],state:'minecraft:stone',decorative:true}],steps:[{id:'base',title:'Base',groups:['base']},{id:'door',through_y:-1},{id:'finish',include:['2,0,0']}]}]}");
        CustomDisplays.compile(document, null); CustomDisplays.validate(document);
        JsonObject scene = document.getAsJsonArray("elements").get(0).getAsJsonObject();
        Set<String> all = DisplayFeatures.selectedBlocks(scene, -1, false);
        check(all.size() == 5, "stable coordinate identities");
        List<DisplayFeatures.Material> materials = DisplayFeatures.materials(scene, all, id -> id.equals("test:unplaceable") ? "" : id);
        var door = materials.stream().filter(row -> row.item().equals("minecraft:oak_door")).findFirst().orElseThrow();
        check(door.count() == 1 && door.blocks() == 2 && door.contributors().size() == 2, "multi-part materials counted once with all contributors");
        check(materials.stream().filter(row -> row.item().equals("minecraft:stone")).findFirst().orElseThrow().count() == 1, "decorative blocks excluded");
        check(materials.stream().anyMatch(DisplayFeatures.Material::unresolved), "unplaceable cost explicitly unresolved");
        check(DisplayFeatures.selectedBlocks(scene, 0, false).size() == 1, "group construction selection");
        check(DisplayFeatures.selectedBlocks(scene, 1, false).size() == 3, "cumulative negative layer steps");
        check(DisplayFeatures.selectedBlocks(scene, 1, true).size() == 2, "added materials scope");
        JsonObject reordered = json("{steps:[{id:'finish'},{id:'base'},{id:'door'}]}");
        check(DisplayFeatures.restoreStep(reordered, "door", 1) == 2, "reload preserves selected step by identity after reordering");
        check(DisplayFeatures.restoreStep(reordered, "removed", 1) == -1, "removed step falls back to complete structure");
        check(DisplayFeatures.restoreStep(reordered, "", 1) == 1, "unchanged selection retains valid index");
        check(DisplayFeatures.restoreStep(reordered, "", 9) == -1 && DisplayFeatures.restoreStep(new JsonObject(), "door", 0) == -1, "missing or shortened steps reset selection");
        JsonObject upper = scene.getAsJsonArray("blocks").get(1).getAsJsonObject();
        check(DisplayFeatures.visibility(upper, -2, "through", all) == 0, "through-layer excludes upper layers");
        check(DisplayFeatures.visibility(upper, -2, "context", all) == 2, "context outlines non-selected layer");
        check(DisplayFeatures.visibility(upper, -1, "isolate", all) == 1, "isolate sparse negative levels");
        check(DisplayFeatures.visibility(upper, -1, "all", Set.of()) == 0, "steps constrain layer modes");

        JsonObject overrides = scene.deepCopy();
        overrides.add("material_rules", JsonParser.parseString("[{state:'minecraft:stone',item:'minecraft:cobblestone',count:2,key:'masonry',label:'Masonry'}]"));
        check(DisplayFeatures.materials(overrides, all, id -> id).stream().anyMatch(row -> row.key().equals("masonry") && row.count() == 2), "authored material mappings");
        JsonObject large = json("{blocks:[],material_rules:[{state:'minecraft:stone',count:1000000}]}");
        for (int i = 0; i < 2200; i++) large.getAsJsonArray("blocks").add(json("{id:'block-" + i + "',pos:[0,0,0],state:'minecraft:stone'}"));
        check(DisplayFeatures.materials(large, DisplayFeatures.selectedBlocks(large, -1, false), id -> id).get(0).count() == 2200000000L, "large material totals do not overflow");
        rejects(() -> CustomDisplays.validate(json("{elements:[{type:'scene',blocks:[{pos:[0,0,0],state:'minecraft:stone',material:{count:-1}}]}]}")));
        rejects(() -> CustomDisplays.validate(json("{elements:[{type:'scene',blocks:[],material_rules:[{state:'minecraft:stone',count:1.5}]}]}")));
        rejects(() -> CustomDisplays.validate(json("{elements:[{type:'scene',blocks:[],material_rules:[{state:'minecraft:stone',item:'invalid item'}]}]}")));
        rejects(() -> DisplayFeatures.normalize(json("{elements:[{type:'materials',scene:'missing'}]}")));
        rejects(() -> DisplayFeatures.normalize(json("{elements:[{id:'altar',type:'scene',blocks:[]},{type:'materials',scene:'altar',scope:'typo'}]}")));
        JsonObject wiring = json("{elements:[{type:'scene',blocks:[{pos:[0,0,0],state:'minecraft:redstone_wire'},{pos:[1,0,0],state:'test:unplaceable'}]}]}");
        var wiringItems = DisplaySearch.ingredients(wiring, Set.of(), id -> id.equals("minecraft:redstone_wire") ? "minecraft:redstone" : "");
        check(wiringItems.size() == 1 && wiringItems.get(0).item().equals("minecraft:redstone"), "material search resolves block items and omits unresolved costs");

        JsonObject repeat = json("{width:100,height:100,elements:[{type:'repeat',layout:'grid',columns:2,items:[{item:'minecraft:stone'},{item:'minecraft:gold_ingot'}],element:{type:'slot',item:'$item.item'}}]}");
        CustomDisplays.compile(repeat, null); CustomDisplays.validate(repeat);
        JsonArray children = repeat.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("elements");
        check(children.size() == 2 && children.get(1).getAsJsonObject().get("x").getAsInt() == 22, "bounded repeat layout");
        check(children.get(1).getAsJsonObject().get("item").getAsString().equals("minecraft:gold_ingot"), "typed item-field substitution");

        JsonObject responsive = json("{width:320,height:180,elements:[],layouts:[{id:'compact',min_width:0,width:160,height:240,elements:[]},{id:'standard',min_width:280,width:320,height:180,elements:[]},{id:'expanded',min_width:400,width:400,height:140,elements:[]}]}");
        CustomDisplays.compile(responsive, null); CustomDisplays.validate(responsive);
        check(DisplayFeatures.presentation(responsive, "", 200, "auto").get("height").getAsInt() == 240, "compact breakpoint");
        check(DisplayFeatures.presentation(responsive, "", 320, "auto").get("width").getAsInt() == 320, "standard breakpoint");
        check(DisplayFeatures.presentation(responsive, "", 200, "expanded").get("width").getAsInt() == 400, "explicit presentation");

        Map<ResourceLocation, JsonObject> source = new LinkedHashMap<>();
        source.put(id("test:base"), json("{elements:[{type:'slot',item:'minecraft:iron_ingot',role:'input'},{type:'item',item:'minecraft:diamond'}]}"));
        source.put(id("test:advanced"), json("{elements:[{type:'slot',item:'minecraft:netherite_ingot',role:'output'}]}"));
        source.put(id("test:variants"), json("{variants:[{id:'basic',display:'test:base'},{id:'advanced',display:'test:advanced',requires_entry:'test:unlock'},{id:'secret',display:'test:advanced',hidden:true}]}"));
        JsonObject variants = CustomDisplays.resolve(id("test:variants"), source, new LinkedHashSet<>()); CustomDisplays.compile(variants, null); CustomDisplays.validate(variants);
        check(DisplaySearch.ingredients(variants, Set.of()).stream().noneMatch(row -> row.item().equals("minecraft:netherite_ingot")), "locked and hidden variants excluded from search");
        check(DisplaySearch.ingredients(variants, Set.of("test:unlock")).stream().filter(row -> row.item().equals("minecraft:netherite_ingot")).count() == 1, "accessible variant indexing");
        check(DisplaySearch.ingredients(variants, Set.of()).stream().anyMatch(row -> row.item().equals("minecraft:diamond") && row.role().equals("generic")), "decorative icons do not acquire recipe roles");
        check(DisplayFeatures.presentation(variants, "advanced", 320, "auto").getAsJsonArray("elements").get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:netherite_ingot"), "variant presentation");

        JsonObject preset = DisplayFeatures.preset("machine", json("{inputs:[{item:'minecraft:iron_ingot'}],outputs:[{item:'minecraft:iron_block',count:2}],energy:'800 FE'}"));
        CustomDisplays.compile(preset, null); CustomDisplays.validate(preset);
        check(DisplaySearch.ingredients(preset, Set.of()).stream().anyMatch(row -> row.role().equals("output")), "presets assign semantic roles");
        check(preset.toString().contains("800 FE") && !preset.toString().contains("Duration:"), "presets omit unspecified metadata");
        JsonObject crowdedValues = json("{inputs:[],fluids:[{fluid:'minecraft:water'}]}");
        for (int i = 0; i < 16; i++) crowdedValues.getAsJsonArray("inputs").add(json("{item:'minecraft:stone'}"));
        JsonObject crowded = DisplayFeatures.preset("machine", crowdedValues);
        CustomDisplays.compile(crowded, null); CustomDisplays.validate(crowded);
        var inputBounds = DisplayFeatures.bounds(crowded.getAsJsonArray("elements").get(0).getAsJsonObject());
        JsonObject fluid = crowded.getAsJsonArray("elements").get(crowded.getAsJsonArray("elements").size() - 1).getAsJsonObject();
        check(fluid.get("y").getAsDouble() >= inputBounds.y() + inputBounds.height() + 12, "full preset inputs leave space for fluids");
        check(DisplayFeatures.lint("test:crowded", crowded).isEmpty(), "full preset stays within its canvas");
        JsonObject compact = DisplayFeatures.preset("machine", json("{inputs:[{item:'minecraft:raw_iron'}],outputs:[{item:'minecraft:iron_ingot'}],duration:'100 ticks'}"));
        CustomDisplays.compile(compact, null); CustomDisplays.validate(compact);
        check(compact.get("width").getAsInt() <= 160 && compact.get("height").getAsInt() <= 60 && DisplayFeatures.lint("test:compact", compact).isEmpty(), "presets size their canvas to the content");
        JsonObject radialPreset = DisplayFeatures.preset("radial", json("{focus:{item:'minecraft:book'},inputs:[{item:'minecraft:flint'},{item:'minecraft:flint'},{item:'minecraft:flint'},{item:'minecraft:flint'},{item:'minecraft:flint'},{item:'minecraft:flint'},{item:'minecraft:flint'},{item:'minecraft:flint'}],outputs:[{item:'minecraft:enchanted_book'}]}"));
        CustomDisplays.compile(radialPreset, null); CustomDisplays.validate(radialPreset);
        check(DisplayFeatures.lint("test:radial", radialPreset).isEmpty(), "radial preset children stay inside the canvas");
        JsonObject changing = json("{type:'scene',blocks:[{id:'machine',pos:[0,0,0],state:'minecraft:furnace'},{id:'core',pos:[1,0,0],state:'minecraft:stone'}],steps:[{id:'place',include:['machine','core']},{id:'assemble',states:{machine:'minecraft:furnace[lit=true]',core:'minecraft:air'}}]}");
        DisplayFeatures.normalize(jsonWithScene(changing));
        check(DisplayFeatures.stateAt(changing, 0, changing.getAsJsonArray("blocks").get(0).getAsJsonObject()).equals("minecraft:furnace"), "state before change step");
        check(DisplayFeatures.stateAt(changing, 1, changing.getAsJsonArray("blocks").get(0).getAsJsonObject()).equals("minecraft:furnace[lit=true]"), "state after change step");
        check(DisplayFeatures.stateAt(changing, -1, changing.getAsJsonArray("blocks").get(1).getAsJsonObject()).equals("minecraft:air"), "complete view shows final states");
        check(DisplayFeatures.selectedBlocks(changing, 1, true).equals(Set.of("machine", "core")), "changed blocks count as step additions");
        rejects(() -> { JsonObject bad = changing.deepCopy(); bad.getAsJsonArray("steps").get(1).getAsJsonObject().add("states", json("{unknown:'minecraft:stone'}")); DisplayFeatures.normalize(jsonWithScene(bad)); });
        JsonObject guided = json("{type:'scene',autoplay:true,step_time:2,blocks:[{id:'a',pos:[0,0,0],state:'minecraft:stone',ghost:true}],steps:[{id:'one',include:['a'],yaw:90,pitch:30,zoom:1.5,time:3,markers:[{block:'a',icon:'right_click',text:'literal:Click'},{pos:[0,1,0],item:'minecraft:flint_and_steel'}]}]}");
        DisplayFeatures.normalize(jsonWithScene(guided)); CustomDisplays.validate(jsonWithScene(guided));
        rejects(() -> { JsonObject bad = guided.deepCopy(); bad.getAsJsonArray("steps").get(0).getAsJsonObject().add("markers", JsonParser.parseString("[{block:'missing'}]")); DisplayFeatures.normalize(jsonWithScene(bad)); });
        rejects(() -> { JsonObject bad = guided.deepCopy(); bad.getAsJsonArray("steps").get(0).getAsJsonObject().add("markers", JsonParser.parseString("[{block:'a',icon:'wave'}]")); DisplayFeatures.normalize(jsonWithScene(bad)); });
        rejects(() -> { JsonObject bad = guided.deepCopy(); bad.getAsJsonArray("steps").get(0).getAsJsonObject().addProperty("zoom", 9); DisplayFeatures.normalize(jsonWithScene(bad)); });
        rejects(() -> { JsonObject bad = guided.deepCopy(); bad.addProperty("step_time", 99); CustomDisplays.validate(jsonWithScene(bad)); });
        rejects(() -> DisplayFeatures.preset("machine", json("{columns:0}")));
        rejects(() -> DisplayFeatures.preset("unknown", new JsonObject()));
        rejects(() -> { JsonObject bad = json("{elements:[{id:'same',type:'rect'},{id:'same',type:'rect'}]}"); DisplayFeatures.normalize(bad); });
        rejects(() -> { JsonObject bad = scene.deepCopy(); bad.getAsJsonArray("steps").get(0).getAsJsonObject().add("remove", new JsonArray()); DisplayFeatures.normalize(jsonWithScene(bad)); });
        rejects(() -> { JsonObject bad = scene.deepCopy(); bad.getAsJsonArray("steps").get(0).getAsJsonObject().add("include", JsonParser.parseString("['unknown']")); DisplayFeatures.normalize(jsonWithScene(bad)); });
        try { CustomDisplays.validate(json("{elements:[{type:'group',elements:[{type:'rect',width:0}]}]}")); throw new AssertionError("invalid width accepted"); }
        catch (CustomDisplays.Problem problem) { check(problem.path.equals("elements[0].elements[0]"), "exact nested diagnostic path"); }
        check(!DisplayFeatures.lint("test:lint", json("{width:100,height:100,elements:[{type:'text',x:105,width:10,text:'x'}]}")).isEmpty(), "author warnings do not reject intentional layouts");
        var circle = DisplayFeatures.bounds(json("{type:'circle',x:50,y:50,radius:20}"));
        check(circle.x() == 30 && circle.y() == 30 && circle.width() == 41, "circle visual and interaction bounds");
        var grouped = DisplayFeatures.bounds(json("{type:'group',x:10,y:20,elements:[{type:'rect',x:5,y:7,width:40,height:12}]}"));
        check(grouped.x() == 15 && grouped.y() == 27 && grouped.width() == 40, "group bounds include child offsets");

        DisplayRecipes.register(id("test:adapter"), (recipe, registries) -> json("{inputs:[{item:'minecraft:stone'}],outputs:[{item:'minecraft:diamond'}]}"));
        check(DisplayRecipes.extract("test:adapter", null, null).has("outputs"), "registered mod adapter contract");
        rejects(() -> DisplayRecipes.register(id("test:adapter"), (recipe, registries) -> new JsonObject()));
        rejects(() -> DisplayRecipes.extract("test:missing", null, null));
        DisplayRecipes.register(id("test:invalid_adapter"), (recipe, registries) -> new JsonObject());
        rejects(() -> DisplayRecipes.extract("test:invalid_adapter", null, null));

        Map<ResourceLocation, JsonObject> family = new LinkedHashMap<>();
        family.put(id("test:family_base"), json("{width:100,height:40,variables:{item:{type:'string'}},elements:[{type:'slot',item:'$item'}]}"));
        JsonArray inline = new JsonArray();
        for (int i = 0; i < 40; i++) inline.add(json("{id:'v" + i + "',title:'V" + i + "',group:'G" + (i / 10) + "',template:'test:family_base',values:{item:'minecraft:stone'}}"));
        JsonObject familyDisplay = new JsonObject(); familyDisplay.add("variants", inline);
        family.put(id("test:family"), familyDisplay);
        JsonObject resolvedFamily = CustomDisplays.resolve(id("test:family"), family, new LinkedHashSet<>());
        CustomDisplays.compile(resolvedFamily, null); CustomDisplays.validate(resolvedFamily);
        JsonObject firstVariant = resolvedFamily.getAsJsonArray("variants").get(0).getAsJsonObject();
        check(resolvedFamily.getAsJsonArray("variants").size() == 40 && firstVariant.has("document") && firstVariant.has("group") && !firstVariant.has("template"), "inline template variants with groups");
        JsonObject extras = json("{width:200,height:160,elements:[{type:'conditions',entries:[{kind:'night'},{kind:'full_moon'},{text:'literal:Altar nearby',kind:'altar_power'}]},{type:'recipe_list',x:0,y:20,width:200,height:100,rows:[{title:'literal:A',inputs:['minecraft:stone','#minecraft:logs'],outputs:['minecraft:diamond'],entity:'minecraft:pig'}]},{id:'s',type:'scene',x:0,y:0,width:180,height:140,blocks:[{pos:[0,0,0],state:'minecraft:stone'}],items:[{pos:[0,1,0],id:'minecraft:diamond',from:'b'}],entities:[{pos:[1,0,0],id:'minecraft:villager',profession:'minecraft:farmer',held:'minecraft:wheat'}],moves:[{item:'minecraft:apple',from:[0,2,0],to:[0,1,0],arc:1,steps:['b']}],effects:[{kind:'beam',from:[0,0,0],to:[3,0,0]},{kind:'fire',pos:[0,1,0]},{kind:'glow',pos:[0,0,0],until:'a'}],paths:[{kind:'redstone',points:[[0,0,0],[2,0,0],[2,0,2]]}],steps:[{id:'a',title:'A',conditions:[{kind:'rain'}],progress:{label:'literal:Brewing'}},{id:'b',title:'B',markers:[{block:'0,0,0',icon:'drop'}]}]}]}");
        CustomDisplays.compile(extras, null); CustomDisplays.validate(extras);
        check(DisplaySearch.ingredients(extras, Set.of()).stream().anyMatch(row -> row.item().equals("minecraft:diamond") && row.role().equals("output")), "recipe list rows are searchable");
        rejects(() -> CustomDisplays.validate(json("{elements:[{type:'conditions',entries:[{kind:'tuesday'}]}]}")));
        rejects(() -> CustomDisplays.validate(json("{elements:[{type:'scene',effects:[{kind:'laser_show',pos:[0,0,0]}]}]}")));
        rejects(() -> CustomDisplays.validate(json("{elements:[{type:'scene',paths:[{points:[[0,0,0]]}]}]}")));

        JsonObject flowing = json("{width:200,height:100,reflow:true,auto_height:true,elements:[{type:'group',layout:'column',gap:3,elements:[{type:'text',width:120,text:'literal:A'},{type:'slot',item:'minecraft:stone'}]}]}");
        CustomDisplays.compile(flowing, null); CustomDisplays.validate(flowing);
        check(flowing.getAsJsonArray("elements").get(0).getAsJsonObject().get("layout").getAsString().equals("column"), "column layouts survive compilation for runtime flow");
        check(CustomDisplays.content(JsonParser.parseString("[{text:'a'},{display:'x:y',beside:true}]")).equals(List.of("a", "@beside", "@display:x:y")), "beside displays keep their marker");
        System.out.println("Display feature tests passed: materials, layers, steps, repeats, responsive layouts, variants, search access, diagnostics, presets, and adapter contract");
    }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }
    private static JsonObject jsonWithScene(JsonObject scene) { JsonObject object = new JsonObject(); JsonArray elements = new JsonArray(); elements.add(scene); object.add("elements", elements); return object; }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) { try { action.run(); } catch (RuntimeException expected) { return; } throw new AssertionError("Expected validation failure"); }
}
