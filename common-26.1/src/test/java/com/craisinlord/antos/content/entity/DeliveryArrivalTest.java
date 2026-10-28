package com.craisinlord.antos.content.entity;

public final class DeliveryArrivalTest {
    public static void main(String[] args) {
        close(DeliveryArrival.portalSize(0), 0, "idle portal is hidden");
        close(DeliveryArrival.portalSize(4), 0.5D, "portal opens smoothly");
        close(DeliveryArrival.portalSize(8), 1, "portal reaches full size");
        close(DeliveryArrival.portalSize(20), 1, "portal stays fully open for release");
        close(DeliveryArrival.portalSize(28), 0.5D, "portal closes smoothly");
        close(DeliveryArrival.portalSize(32), 0, "portal closes fully before removal");
        close(DeliveryArrival.portalSize(40), 0, "expired portal stays hidden");
        close(DeliveryArrival.packageScale(0), 1, "sky drop has normal size");
        close(DeliveryArrival.packageScale(20), 0, "package begins inside portal");
        close(DeliveryArrival.packageScale(22.5F), 0.5D, "package emergence interpolates between ticks");
        close(DeliveryArrival.packageScale(25), 1, "package finishes emerging");
        var outdoors = find(new Room(64, Double.NaN));
        check(outdoors != null && !outdoors.portal(), "open sky keeps the sky drop");
        close(outdoors.spawnY(), 128, "outdoor spawn height");

        var house = find(new Room(64, 67));
        check(house != null && house.portal(), "house uses a ceiling portal");
        close(house.spawnY() + 0.95D, 67, "crate fits beneath ceiling");

        var lowRoom = find(new Room(64, 66));
        check(lowRoom != null && lowRoom.portal(), "two-block room fits a portal");
        check(lowRoom.spawnY() > lowRoom.floorY(), "package has room to fall");
        check(find(new Room(64, 65)) == null, "one-block crawlspace waits");

        var nether = DeliveryArrival.find(0, 110, 0, 256, new Room(110, 127));
        check(nether != null && nether.portal(), "Nether roof uses a portal");
        close(nether.spawnY(), 126.05D, "delivery stays below bedrock");

        var slab = find(new Room(64.5D, 67));
        check(slab != null, "slab floor is supported");
        close(slab.floorY(), 64.5D, "slab height is preserved");
        check(find(new Room(70, 73)) == null, "never chooses another floor");
        var jumping = DeliveryArrival.find(0, 65, 0, 319, new Room(64, 67));
        check(jumping != null && jumping.floorY() == 64, "ordering during a jump still finds the floor");
        check(DeliveryArrival.find(0, 66, 0, 319, new Room(64, 67)) == null, "does not search a different story below");
        check(find(new Room(Double.NaN, 67)) == null, "unsupported landing waits");

        var blocked = find(new Room(64, 67) {
            @Override
            public boolean clear(double x, double y, double z, double radius, double height) {
                return x >= 1.0D && super.clear(x, y, z, radius, height);
            }
        });
        check(blocked != null && blocked.x() == 1.5D, "blocked destination searches nearby");

        check(find(new Room(64, 67) {
            @Override
            public boolean clear(double x, double y, double z, double radius, double height) {
                return radius < DeliveryArrival.PORTAL_RADIUS;
            }
        }) == null, "wide portal cannot clip adjacent walls");

        var edge = DeliveryArrival.find(0, 318, 0, 319, new Room(318, Double.NaN));
        check(edge != null && edge.spawnY() + 0.85D <= 319, "build-height limit leaves crate clearance");
        check(DeliveryArrival.find(0, 319, 0, 319, new Room(319, Double.NaN)) == null, "no vertical clearance waits");

        Room changing = new Room(64, 65);
        check(find(changing) == null, "blocked room initially waits");
        changing.roof = 67;
        check(find(changing) != null, "pending delivery can retry when space becomes available");
        System.out.println("Delivery arrival tests passed");
    }

    private static DeliveryArrival.Plan find(DeliveryArrival.Space space) {
        return DeliveryArrival.find(0, 64, 0, 319, space);
    }

    private static class Room implements DeliveryArrival.Space {
        private final double ground;
        private double roof;

        private Room(double ground, double roof) {
            this.ground = ground;
            this.roof = roof;
        }

        @Override
        public double floor(int x, int y, int z) {
            return ground;
        }

        @Override
        public double ceiling(double x, double y, double z, double top) {
            return roof <= top ? roof : Double.NaN;
        }

        @Override
        public boolean clear(double x, double y, double z, double radius, double height) {
            return y >= ground && (!Double.isFinite(roof) || y + height < roof);
        }
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 0.0001D, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
