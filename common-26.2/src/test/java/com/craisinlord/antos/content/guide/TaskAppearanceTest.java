package com.craisinlord.antos.content.guide;

import com.google.gson.JsonParser;

public final class TaskAppearanceTest {
    public static void main(String[] args) {
        check(parse("{}").equals(TaskAppearance.DEFAULT), "existing tasks retain defaults");
        check(parse("{\"shape\":\"unknown\",\"size\":null,\"icon_scale\":[]}").equals(TaskAppearance.DEFAULT), "invalid appearance uses defaults");
        check(parse("{\"shape\":{},\"size\":\"2\",\"icon_scale\":true}").equals(TaskAppearance.DEFAULT), "wrong types use defaults");
        check(parse("{\"size\":1e100,\"icon_scale\":-1e100}").equals(TaskAppearance.DEFAULT), "nonfinite values use defaults");
        var appearance = parse("{\"shape\":\"HeXaGoN\",\"size\":1.5,\"icon_scale\":0.8}");
        check(appearance.shape() == TaskAppearance.Shape.HEXAGON && appearance.size() == 1.5F && appearance.iconScale() == 0.8F, "scales are independent");
        check(parse("{\"size\":0,\"icon_scale\":100}").size() == 0.5F, "node minimum is bounded");
        check(parse("{\"size\":100,\"icon_scale\":0}").size() == 3F, "node maximum is bounded");
        check(parse("{\"icon_scale\":0}").iconScale() == 0.25F && parse("{\"icon_scale\":100}").iconScale() == 2F, "icon scale is bounded");
        for (var shape : TaskAppearance.Shape.values()) {
            check(parse("{\"shape\":\"" + shape.name().toLowerCase(java.util.Locale.ROOT) + "\"}").shape() == shape, "every shape parses");
            check(shape.contains(0.5, 0.5), "shape center is clickable");
            check(!shape.contains(-0.1, 0.5) && !shape.contains(0.5, 1.1), "outside points are excluded");
            if (shape != TaskAppearance.Shape.SQUARE) check(!shape.contains(0.01, 0.01), "polygon corners are excluded");
            double centerX = 0, centerY = 0;
            double sideLength = shape.sides() == 0 ? 0 : Math.hypot(shape.vertexX(1) - shape.vertexX(0), shape.vertexY(1) - shape.vertexY(0));
            double vertexRadius = shape.sides() == 0 ? 0 : Math.hypot(shape.vertexX(0) - 0.5, shape.vertexY(0) - 0.5);
            for (int i = 0; i < shape.sides(); i++) {
                int next = (i + 1) % shape.sides();
                check(Math.abs(Math.hypot(shape.vertexX(next) - shape.vertexX(i), shape.vertexY(next) - shape.vertexY(i)) - sideLength) < 1e-9, "polygon sides have equal lengths");
                check(Math.abs(Math.hypot(shape.vertexX(i) - 0.5, shape.vertexY(i) - 0.5) - vertexRadius) < 1e-9, "polygon vertices have equal radii");
                centerX += shape.vertexX(i);
                centerY += shape.vertexY(i);
            }
            if (shape.sides() > 0) check(Math.abs(centerX / shape.sides() - 0.5) < 1e-9 && Math.abs(centerY / shape.sides() - 0.5) < 1e-9, "polygon center is the node center");
            double radius = shape.iconFit() * 0.499;
            for (double x : new double[]{0.5 - radius, 0.5 + radius}) {
                for (double y : new double[]{0.5 - radius, 0.5 + radius}) check(shape.contains(x, y), "default icon fits shape");
            }
            for (int row = 0; row < 96; row++) {
                double y = (row + 0.5) / 96;
                double left = shape.edge(y, false), right = shape.edge(y, true);
                check(left >= -1e-9 && right <= 1 + 1e-9 && right >= left, "scanline edges are valid");
                if (right > left) check(shape.contains((left + right) / 2, y), "visible scanline is clickable");
                else check(!shape.contains(0.5, y), "empty scanlines are excluded");
                check(!shape.contains(left - 0.001, y) && !shape.contains(right + 0.001, y), "outside scanline is excluded");
                for (int column = 0; column < 96; column++) {
                    double x = (column + 0.5) / 96;
                    check(shape.contains(x, y) == shape.contains(1 - x, y), "shape is mirror symmetric horizontally");
                }
            }
        }
        System.out.println("Task appearance tests passed");
    }

    private static TaskAppearance parse(String json) {
        return TaskAppearance.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
