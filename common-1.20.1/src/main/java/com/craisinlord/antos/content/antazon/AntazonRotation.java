package com.craisinlord.antos.content.antazon;

import net.minecraft.resources.ResourceLocation;

public final class AntazonRotation {
    private AntazonRotation() { }

    public static int[] shuffledOrder(ResourceLocation seedId, long cycle, int size) {
        int[] order = new int[size];
        for (int index = 0; index < size; index++) order[index] = index;
        java.util.Random random = new java.util.Random(seedId.toString().hashCode() * 0x9E3779B97F4A7C15L + cycle);
        for (int index = size - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            int value = order[index];
            order[index] = order[swap];
            order[swap] = value;
        }
        return order;
    }

    public static int slot(ResourceLocation seedId, boolean shuffled, long position, int size, OrderCache cache) {
        long cycle = Math.floorDiv(position, size);
        int slot = (int) Math.floorMod(position, (long) size);
        if (!shuffled) return slot;
        if (cache.order == null || cache.cycle != cycle) {
            cache.order = shuffledOrder(seedId, cycle, size);
            cache.cycle = cycle;
        }
        return cache.order[slot];
    }

    public static long period(long everyMinecraftDays, long everyRealHours, long day, long nowMillis) {
        if (everyRealHours > 0L) return Math.floorDiv(nowMillis, everyRealHours * 3_600_000L);
        return Math.floorDiv(day, Math.max(1L, everyMinecraftDays));
    }

    public static long endsInMillis(long everyMinecraftDays, long everyRealHours, long gameTime, long nowMillis) {
        if (everyRealHours > 0L) {
            long periodMillis = everyRealHours * 3_600_000L;
            return (Math.floorDiv(nowMillis, periodMillis) + 1L) * periodMillis - nowMillis;
        }
        long periodTicks = Math.max(1L, everyMinecraftDays) * 24000L;
        return ((Math.floorDiv(gameTime, periodTicks) + 1L) * periodTicks - gameTime) * 50L;
    }

    public static final class OrderCache {
        private int[] order;
        private long cycle = Long.MIN_VALUE;
    }
}
