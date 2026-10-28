package com.craisinlord.antos.content.entity;

public final class DeliveryArrival {
    public static final int OPEN_TICKS = 20;
    public static final int CLOSE_TICKS = 12;
    public static final double PORTAL_RADIUS = 0.9D;

    private DeliveryArrival() { }

    public interface Space {
        double floor(int x, int y, int z);
        double ceiling(double x, double y, double z, double top);
        boolean clear(double x, double y, double z, double radius, double height);
    }

    public record Plan(double x, double floorY, double z, double spawnY, boolean portal) { }

    public static float portalSize(float ticks) {
        if (ticks <= 0.0F || ticks >= OPEN_TICKS + CLOSE_TICKS) return 0.0F;
        float progress = Math.max(0.0F, Math.min(1.0F,
                Math.min(ticks / 8.0F, (OPEN_TICKS + CLOSE_TICKS - ticks) / 8.0F)));
        return progress * progress * (3.0F - 2.0F * progress);
    }

    public static float packageScale(float ticks) {
        return ticks == 0.0F ? 1.0F : Math.max(0.0F, Math.min(1.0F, (ticks - OPEN_TICKS) / 5.0F));
    }

    public static Plan find(int x, int y, int z, double maxY, Space space) {
        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    double floor = space.floor(x + dx, y, z + dz);
                    if (!Double.isFinite(floor) || floor < y - 1.25D || floor > y + 0.75D) continue;
                    double centerX = x + dx + 0.5D;
                    double centerZ = z + dz + 0.5D;
                    if (!space.clear(centerX, floor, centerZ, 0.45D, 0.85D)) continue;
                    double top = Math.min(floor + 64.0D, maxY - 0.85D);
                    if (top < floor) continue;
                    double ceiling = space.ceiling(centerX, floor + 0.85D, centerZ, top + 0.85D);
                    boolean portal = Double.isFinite(ceiling);
                    double spawn = portal ? ceiling - 0.95D : top;
                    if (portal && (spawn < floor + 0.35D
                            || !space.clear(centerX, ceiling - 0.18D, centerZ, PORTAL_RADIUS, 0.1D))) continue;
                    if (!space.clear(centerX, floor, centerZ, 0.45D, spawn - floor + 0.85D)) continue;
                    return new Plan(centerX, floor, centerZ, spawn, portal);
                }
            }
        }
        return null;
    }
}
