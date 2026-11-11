package com.craisinlord.antos.api.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class TerminalActionRegistry {
    private static final Map<ResourceLocation, Action> ACTIONS = new ConcurrentHashMap<>();

    private TerminalActionRegistry() { }

    public static void register(ResourceLocation id, Action action) {
        if (id == null || action == null) throw new IllegalArgumentException("Terminal action id and action are required");
        ACTIONS.put(id, action);
    }

    public static Action get(ResourceLocation id) {
        return id == null ? null : ACTIONS.get(id);
    }

    public static boolean isRegistered(ResourceLocation id) {
        return id != null && ACTIONS.containsKey(id);
    }

    @FunctionalInterface
    public interface Action {
        Result run(Context context);
    }

    public record Context(ServerPlayer player, BlockPos computerPos, boolean remote, List<String> arguments, String directory) {
        public Context {
            arguments = List.copyOf(arguments);
        }
    }

    public record Line(String text, String style, int speed, int delay, List<String> arguments) {
        public Line {
            text = text == null ? "" : text;
            style = style == null ? "output" : style;
            speed = Math.max(0, Math.min(400, speed));
            delay = Math.max(0, Math.min(10000, delay));
            arguments = arguments == null ? List.of() : List.copyOf(arguments);
        }

        public static Line output(String text, Object... arguments) { return new Line(text, "output", 0, 0, strings(arguments)); }
        public static Line highlight(String text, Object... arguments) { return new Line(text, "highlight", 0, 0, strings(arguments)); }
        public static Line system(String text, Object... arguments) { return new Line(text, "system", 0, 0, strings(arguments)); }
        public static Line error(String text, Object... arguments) { return new Line(text, "error", 0, 0, strings(arguments)); }

        public Line typed(int speed) { return new Line(text, style, speed, delay, arguments); }
        public Line delayed(int delay) { return new Line(text, style, speed, delay, arguments); }

        private static List<String> strings(Object... arguments) {
            List<String> values = new ArrayList<>(arguments.length);
            for (Object argument : arguments) values.add(String.valueOf(argument));
            return values;
        }
    }

    public record Result(boolean success, List<Line> lines) {
        public Result {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        public static Result success(Line... lines) { return new Result(true, List.of(lines)); }
        public static Result success(List<Line> lines) { return new Result(true, lines); }
        public static Result error(Line... lines) { return new Result(false, List.of(lines)); }
        public static Result error(List<Line> lines) { return new Result(false, lines); }
    }
}
