package com.craisinlord.antos.content.client.screen;

public final class SceneClipTest {
    public static void main(String[] args) {
        int[] hero = SceneClip.toPose(50, 30, 250, 130, 50, 30, 1);
        check(hero[0] == 0 && hero[1] == 0 && hero[2] == 200 && hero[3] == 100, "A hero scene clips to its own local rectangle");

        int[] body = SceneClip.toPose(70, 40, 370, 190, 40, 10, 1.5F);
        check(body[0] == 20 && body[1] == 20 && body[2] == 220 && body[3] == 120, "A scaled document clip converts through its scale");
        check(40 + body[0] * 1.5F == 70 && 10 + body[3] * 1.5F == 190, "Converted corners land back on the root rectangle");

        int[] fractional = SceneClip.toPose(71, 41, 100, 100, 40, 10, 1.5F);
        check(40 + fractional[0] * 1.5F <= 71 && 40 + fractional[2] * 1.5F >= 100, "Rounding never shrinks the visible scene");

        int[] scrolled = SceneClip.toPose(50, 30, 250, 130, 50, -70, 1);
        check(scrolled[1] == 100 && scrolled[3] == 200, "A scrolled scene keeps its clip on the visible part");

        int[] empty = SceneClip.toPose(100, 100, 40, 40, 0, 0, 1);
        check(empty[2] >= empty[0] && empty[3] >= empty[1], "An empty clip never produces a negative size");
        System.out.println("SceneClipTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
