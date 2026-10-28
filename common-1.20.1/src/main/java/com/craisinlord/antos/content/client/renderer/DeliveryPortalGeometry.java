package com.craisinlord.antos.content.client.renderer;

final class DeliveryPortalGeometry {
    private static final int SEGMENTS = 32;

    private DeliveryPortalGeometry() { }

    interface Vertex {
        void add(float x, float y, float z);
    }

    static void emit(float radius, float y, Vertex vertices) {
        for (int i = 0; i < SEGMENTS; i++) {
            double first = i * Math.PI * 2.0D / SEGMENTS;
            double second = (i + 1) * Math.PI * 2.0D / SEGMENTS;
            float x1 = (float) Math.cos(first) * radius;
            float z1 = (float) Math.sin(first) * radius;
            float x2 = (float) Math.cos(second) * radius;
            float z2 = (float) Math.sin(second) * radius;
            vertices.add(0.0F, y, 0.0F);
            vertices.add(x1, y, z1);
            vertices.add(x2, y, z2);
            vertices.add(0.0F, y, 0.0F);
            vertices.add(0.0F, y, 0.0F);
            vertices.add(x2, y, z2);
            vertices.add(x1, y, z1);
            vertices.add(0.0F, y, 0.0F);
        }
    }
}
