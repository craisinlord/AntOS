package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FileListing {
    private static ComputerFileSystemClientState.State source;
    private static List<String> files = List.of();
    private static List<Line> lines = List.of();
    private static Map<String, Line> byPath = Map.of();

    private FileListing() {
    }

    static List<Line> lines() {
        refresh();
        return lines;
    }

    static boolean exists(String path) {
        refresh();
        return byPath.containsKey(path);
    }

    private static void refresh() {
        ComputerFileSystemClientState.State state = ComputerFileSystemClientState.get();
        List<String> latest = state.files();
        if (state == source && sameElements(latest, files)) return;
        List<Line> parsed = new ArrayList<>(latest.size());
        Map<String, Line> paths = new HashMap<>();
        for (String value : latest) {
            int first = value.indexOf('\t');
            if (first < 0) continue;
            int second = value.indexOf('\t', first + 1);
            Line line = new Line(value.substring(0, first),
                    second < 0 ? value.substring(first + 1) : value.substring(first + 1, second),
                    second < 0 ? null : value.substring(second + 1));
            parsed.add(line);
            paths.putIfAbsent(line.path(), line);
        }
        source = state;
        files = latest;
        lines = List.copyOf(parsed);
        byPath = paths;
    }

    private static boolean sameElements(List<String> left, List<String> right) {
        if (left == right) return true;
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) if (left.get(index) != right.get(index)) return false;
        return true;
    }

    record Line(String type, String path, String size) { }
}
