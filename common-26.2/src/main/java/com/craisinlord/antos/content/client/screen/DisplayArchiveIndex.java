package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.guide.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

final class DisplayArchiveIndex {
    record Result(ComputerGuideData.Entry entry, String display, String variant, String role) { }
    private record Indexed(Result target, DisplaySearch.Ingredient ingredient) { }
    private static long revision = -1, guideRevision = -1;
    private static Object language;
    private static Set<String> access = Set.of();
    private static List<Indexed> index = List.of();
    private static Map<Identifier, String> terms = Map.of();
    private static long epoch, lastTagCheck;
    private static int tagHash;
    private static Set<String> tags = Set.of();
    private DisplayArchiveIndex() { }

    private static void ensure(List<ComputerGuideData.Entry> entries) {
        long now = System.currentTimeMillis();
        if (now - lastTagCheck >= 1000) {
            lastTagCheck = now; int nextHash = 1;
            for (String tag : tags) {
                var values = BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, Identifier.parse(tag)));
                nextHash = 31 * nextHash + values.map(set -> set.stream().limit(128).map(holder -> BuiltInRegistries.ITEM.getKey(holder.value())).toList().hashCode()).orElse(0);
            }
            if (nextHash != tagHash) { tagHash = nextHash; revision = -1; }
        }
        Set<String> ids = new HashSet<>(); entries.forEach(entry -> ids.add(entry.id().toString()));
        Object currentLanguage = net.minecraft.locale.Language.getInstance();
        if (revision == CustomDisplays.revision() && guideRevision == ComputerGuideData.definitionRevision() && language == currentLanguage && access.equals(ids)) return;
        revision = CustomDisplays.revision(); guideRevision = ComputerGuideData.definitionRevision(); language = currentLanguage; access = Set.copyOf(ids);
        epoch++;
        Set<String> nextTags = new TreeSet<>();
        List<Indexed> next = new ArrayList<>(); Map<Identifier, String> text = new HashMap<>();
        for (var entry : entries) {
            StringBuilder searchable = new StringBuilder();
            for (String part : entry.descriptionKeys()) if (part.startsWith("@display:")) {
                String id = part.substring(9); var document = CustomDisplays.get(id); if (document == null) continue;
                for (String label : DisplaySearch.labels(document, access)) searchable.append(' ').append(label.startsWith("literal:") ? label.substring(8) : Component.translatable(label).getString());
                for (var ingredient : DisplaySearch.ingredients(document, access, block -> BuiltInRegistries.BLOCK.getOptional(Identifier.parse(block)).map(value -> BuiltInRegistries.ITEM.getKey(value.asItem()).toString()).filter(item -> !item.equals("minecraft:air")).orElse(""))) {
                    if (next.size() >= 32768) break;
                    next.add(new Indexed(new Result(entry, id, ingredient.variant(), ingredient.role()), ingredient));
                    if (!ingredient.item().isEmpty()) {
                        Identifier item = Identifier.tryParse(ingredient.item());
                        if (item != null) BuiltInRegistries.ITEM.getOptional(item).ifPresent(value -> searchable.append(' ').append(value.getDefaultInstance().getHoverName().getString()));
                    } else {
                        if (nextTags.size() < 256) nextTags.add(ingredient.tag());
                        Identifier tagId = Identifier.tryParse(ingredient.tag());
                        if (tagId != null) BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, tagId)).ifPresent(set -> set.stream().limit(128).forEach(holder -> searchable.append(' ').append(holder.value().getDefaultInstance().getHoverName().getString())));
                    }
                }
            }
            text.put(entry.id(), searchable.toString().toLowerCase(Locale.ROOT));
        }
        index = List.copyOf(next); terms = Map.copyOf(text); tags = Set.copyOf(nextTags);
    }

    static long update(List<ComputerGuideData.Entry> available) { ensure(available); return epoch; }

    static String text(ComputerGuideData.Entry entry, List<ComputerGuideData.Entry> available) { ensure(available); return terms.getOrDefault(entry.id(), ""); }

    static List<Result> related(String item, List<ComputerGuideData.Entry> available) {
        ensure(available);
        Set<Result> result = new LinkedHashSet<>();
        Identifier itemId = Identifier.tryParse(item);
        if (itemId == null) return List.of();
        var value = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        for (Indexed entry : index) {
            if (entry.ingredient.role().equals("generic")) continue;
            boolean matches = item.equals(entry.ingredient.item());
            if (!entry.ingredient.tag().isBlank() && value != null) matches |= value.builtInRegistryHolder().is(TagKey.create(Registries.ITEM, Identifier.parse(entry.ingredient.tag())));
            if (matches) result.add(entry.target);
        }
        return List.copyOf(result);
    }

    static String label(Result result) {
        String relation = result.role.equals("output") ? "produced_by" : result.role.equals("material") ? "structure_material" : result.role.equals("tool") ? "tool_for" : "used_in";
        return Component.translatable("computer.antos.display." + relation).getString() + ": " + ComputerGuideData.archiveTitle(result.entry).getString();
    }
}
