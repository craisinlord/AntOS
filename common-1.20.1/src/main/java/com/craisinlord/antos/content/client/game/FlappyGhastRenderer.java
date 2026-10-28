package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.client.game.logic.flappyghast.FlappyGhastState;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class FlappyGhastRenderer {
    private static final int HEADER = 25;
    private static final int FOOTER = 15;
    private static final int MOTES = 22;

    private FlappyGhastRenderer() { }

    public record View(int best, boolean sound, String message, String bonus, boolean record) { }

    public static double viewWidth(int width, int height) {
        return Math.max(1, width - 3) * FlappyGhastState.FIELD_HEIGHT / fieldHeight(height);
    }

    public static int sfxLeft(Font font, int width, boolean sound) {
        return width - 1 - font.width(tr(sound ? "computer.antos.flappy_ghast.sfx_on" : "computer.antos.flappy_ghast.sfx_off")) - 7;
    }

    public static void render(GuiGraphics g, Font font, int x, int y, int width, int height, FlappyGhastState state, float partial, View view) {
        int right = x + width - 1;
        int fx = x + 1;
        int fy = y + HEADER;
        int fw = Math.max(1, width - 3);
        int fh = fieldHeight(height);
        float unit = fh / (float) FlappyGhastState.FIELD_HEIGHT;
        float time = state.ticks() + partial;
        g.fill(x, y, right, y + height, Ui.GAME_FILL);
        Field field = new Field(fx, fy, fw, fh, unit);
        scissor(g, fx, fy, fx + fw, fy + fh);
        try {
            drawBackground(g, field, state, time);
            drawPillars(g, field, state, partial, time);
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 250.0F);
            drawLava(g, field, time);
            drawGust(g, field, state, partial, time);
            float gx = (float) state.renderX(partial);
            float gy = (float) state.renderY(partial);
            float squash = state.squashTicks() / (float) FlappyGhastState.SQUASH_TICKS;
            boolean hurt = state.phase() == FlappyGhastState.Phase.DYING || state.phase() == FlappyGhastState.Phase.GAME_OVER;
            float half = (float) FlappyGhastState.SPRITE_SIZE / 2;
            paintGhast(g, field.px(gx - half), field.py(gy - half), unit, state.ticks(), squash, hurt);
            g.pose().popPose();
        } finally {
            g.disableScissor();
        }
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 250.0F);
        g.renderOutline(x, y, width - 1, height - 1, Ui.GAME_OUTLINE);
        drawHud(g, font, x, y, width, height, state, view);
        drawOverlay(g, font, x, y, width, height, state, view);
        g.pose().popPose();
    }

    public static void paintIcon(GuiGraphics g, int x, int y, int size, int ticks, boolean hovered) {
        float unit = size / 16.0F;
        float squash = hovered && ticks % 16 < 5 ? (5 - ticks % 16) / 5.0F : 0.0F;
        float bob = hovered ? 0.0F : (float) Math.sin(ticks * 0.15) * 0.5F;
        paintGhast(g, x, y + bob * unit, unit, hovered ? ticks : 0, squash, false);
    }

    public static void paintGhast(GuiGraphics g, float left, float top, float unit, int ticks, float squash, boolean hurt) {
        left = Math.round(left);
        top = Math.round(top);
        float bodyWidth = 12 + 2 * squash;
        float bodyTop = 2 * squash;
        float bx = 8 - bodyWidth / 2;
        float br = bx + bodyWidth;
        int body = hurt && ticks % 4 < 2 ? Ui.mix(Ui.FLAPPY_GHAST_BODY, Ui.FLAPPY_GHAST_HURT, 0.35F) : Ui.FLAPPY_GHAST_BODY;
        int shade = Ui.mix(Ui.FLAPPY_GHAST_SHADE, body, 0.15F);
        for (int i = 0; i < 3; i++) {
            float tx = 3 + i * 4;
            double wave = Math.sin(ticks * 0.35 + i * 2.1);
            float length = 3 + (wave > 0 ? 1 : 0) - Math.round(squash);
            float sway = wave > 0.7 ? 0.5F : wave < -0.7 ? -0.5F : 0.0F;
            px(g, left, top, unit, tx + sway, 12, tx + sway + 2, 12 + length - 1, body);
            px(g, left, top, unit, tx + sway, 12 + length - 1, tx + sway + 2, 12 + length, shade);
        }
        px(g, left, top, unit, bx, bodyTop, br, 12, body);
        px(g, left, top, unit, br - 1, bodyTop + 1, br, 12, shade);
        px(g, left, top, unit, bx, 11, br, 12, shade);
        float scaleY = (12 - bodyTop) / 12.0F;
        float eyeY = bodyTop + 4 * scaleY;
        float mid = (bx + br) / 2;
        int eyeHeight = hurt ? 2 : 1;
        px(g, left, top, unit, bx + 2, eyeY, bx + 4, eyeY + eyeHeight, Ui.FLAPPY_GHAST_FACE);
        px(g, left, top, unit, br - 4, eyeY, br - 2, eyeY + eyeHeight, Ui.FLAPPY_GHAST_FACE);
        if (hurt) {
            px(g, left, top, unit, bx + 3, eyeY + 2, bx + 4, eyeY + 4, Ui.FLAPPY_GHAST_HURT);
            px(g, left, top, unit, br - 4, eyeY + 2, br - 3, eyeY + 4, Ui.FLAPPY_GHAST_HURT);
        }
        float mouthY = bodyTop + 7 * scaleY;
        px(g, left, top, unit, mid - 2, mouthY, mid + 2, mouthY + (hurt || squash > 0.5F ? 2 : 1), Ui.FLAPPY_GHAST_FACE);
    }

    private static void drawBackground(GuiGraphics g, Field field, FlappyGhastState state, float time) {
        float blend = state.stageBlend();
        blend = blend * blend * (3 - 2 * blend);
        FlappyGhastState.Biome from = state.previousBiome();
        FlappyGhastState.Biome to = state.biome();
        int sky = Ui.mix(sky(from), sky(to), blend);
        int haze = Ui.mix(haze(from), haze(to), blend);
        int mote = Ui.mix(mote(from), mote(to), blend);
        int left = field.x;
        int top = field.y;
        int right = field.x + field.width;
        int bottom = field.y + field.height;
        g.fillGradient(left, top, right, bottom, sky, haze);
        double width = field.width / field.unit;
        double far = time * 0.35;
        for (int i = 0; i < 9; i++) {
            double span = 46;
            double base = wrap(i * span - far, span * 9) - span;
            double rise = 22 + (i * 37 % 5) * 9;
            int ridge = Ui.alpha(Ui.mix(haze, Ui.BACKGROUND, 0.45F), 0xB0);
            field.box(g, base, FlappyGhastState.FLOOR_Y - rise, base + span + 2, FlappyGhastState.FLOOR_Y, ridge);
            field.box(g, base + 10, FlappyGhastState.FLOOR_Y - rise - 8, base + span - 12, FlappyGhastState.FLOOR_Y - rise, ridge);
        }
        g.fillGradient(left, field.iy(FlappyGhastState.FLOOR_Y - 34), right, field.iy(FlappyGhastState.FLOOR_Y), Ui.alpha(Ui.FLAPPY_LAVA, 0x00), Ui.alpha(Ui.FLAPPY_LAVA, 0x48));
        for (int i = 0; i < MOTES; i++) {
            double speed = 0.25 + (i % 4) * 0.12;
            double mx = wrap(i * 71 - time * speed * 3, Math.max(1, width + 20)) - 10;
            double my = wrap(i * 43 + time * (to == FlappyGhastState.Biome.CRIMSON ? 0.2 : -0.25), FlappyGhastState.FLOOR_Y);
            double size = i % 3 == 0 ? 2 : 1;
            int alpha = 0x50 + (int) (Math.sin(time * 0.1 + i) * 0x28 + 0x28);
            field.box(g, mx, my, mx + size, my + size, Ui.alpha(mote, alpha));
        }
    }

    private static void drawPillars(GuiGraphics g, Field field, FlappyGhastState state, float partial, float time) {
        double viewWidth = field.width / field.unit;
        int tile = tileSize(field);
        int fieldTop = field.y;
        int floor = field.iy(FlappyGhastState.FLOOR_Y);
        for (FlappyGhastState.Pillar pillar : state.pillars()) {
            double px = pillar.renderX(partial);
            if (px > viewWidth + 2 || px + FlappyGhastState.PILLAR_WIDTH < -2) continue;
            Tiles tiles = Tiles.of(pillar.biome());
            int left = field.ix(px);
            int right = left + tile * 2;
            int gapTop = field.iy(pillar.gapTop());
            int gapBottom = field.iy(pillar.gapBottom());
            if (pillar.lavafall()) drawLavafall(g, left, right, fieldTop, gapTop, tile, time);
            else {
                for (int n = 0; gapTop - n * tile > fieldTop; n++) {
                    int ty = gapTop - (n + 1) * tile;
                    ResourceLocation texture = n == 0 ? tiles.topCap : tiles.top;
                    tile(g, texture, left, ty, tile);
                    tile(g, texture, left + tile, ty, tile);
                }
                edges(g, left, right, fieldTop, gapTop);
                g.fill(left, gapTop - 1, right, gapTop, Ui.alpha(Ui.BACKGROUND, 0x70));
            }
            for (int n = 0; gapBottom + n * tile < floor; n++) {
                int ty = gapBottom + n * tile;
                ResourceLocation texture = n == 0 ? tiles.bottomCap : tiles.bottom;
                tile(g, texture, left, ty, tile);
                tile(g, texture, left + tile, ty, tile);
            }
            edges(g, left, right, gapBottom, floor);
            g.fill(left, gapBottom, right, gapBottom + 1, Ui.alpha(Ui.TEXT_BRIGHT, 0x30));
            if (pillar.hasQuartz()) {
                double bob = Math.sin(time * 0.2 + pillar.gapCentre()) * 1.5;
                int size = Math.max(8, Math.round(12 * field.unit));
                int qx = left + tile - size / 2;
                int qy = field.iy(pillar.quartzY() + bob) - size / 2;
                g.pose().pushPose();
                g.pose().translate(qx, qy, 20.0F);
                float scale = size / 16.0F;
                g.pose().scale(scale, scale, 1.0F);
                g.renderItem(Tiles.QUARTZ, 0, 0);
                g.pose().popPose();
            }
        }
    }

    private static void edges(GuiGraphics g, int left, int right, int top, int bottom) {
        if (bottom <= top) return;
        int shade = Ui.alpha(Ui.BACKGROUND, 0x60);
        g.fill(left, top, left + 1, bottom, shade);
        g.fill(right - 1, top, right, bottom, shade);
    }

    private static void drawLavafall(GuiGraphics g, int left, int right, int top, int bottom, int tile, float time) {
        if (bottom <= top) return;
        int edge = Math.max(1, tile / 8);
        int width = right - left;
        g.fill(left, top, right, bottom, Ui.FLAPPY_LAVA);
        g.fill(left, top, left + edge, bottom, Ui.FLAPPY_LAVA_DARK);
        g.fill(right - edge, top, right, bottom, Ui.FLAPPY_LAVA_DARK);
        int pitch = Math.max(6, tile * 9 / 16);
        int run = Math.max(2, pitch / 3);
        int flow = (int) (time * pitch / 6.0F) % pitch;
        int[][] columns = {{width / 6, width * 5 / 16}, {width * 9 / 16, width * 13 / 16}};
        for (int c = 0; c < columns.length; c++) {
            int shift = c * pitch / 2;
            for (int ly = top - pitch + flow + shift; ly < bottom; ly += pitch) {
                int y0 = Math.max(top, ly);
                int y1 = Math.min(bottom, ly + run);
                if (y1 > y0) g.fill(left + columns[c][0], y0, left + columns[c][1], y1, Ui.FLAPPY_LAVA_BRIGHT);
            }
        }
        g.fill(left - edge, bottom - edge * 2, right + edge, bottom, Ui.FLAPPY_LAVA_BRIGHT);
        int drip = (int) (time * 2.2F * tile / 16.0F) % Math.max(1, tile + tile / 4);
        int dripX = left + width / 2 - edge;
        g.fill(dripX, bottom + drip, dripX + edge * 2, bottom + drip + edge * 2, Ui.alpha(Ui.FLAPPY_LAVA, Math.max(0, 0xE0 - drip * 0xE0 / Math.max(1, tile + tile / 4))));
    }

    private static void drawLava(GuiGraphics g, Field field, float time) {
        double width = field.width / field.unit;
        double floor = FlappyGhastState.FLOOR_Y;
        field.box(g, -1, floor, width + 1, FlappyGhastState.FIELD_HEIGHT + 1, Ui.FLAPPY_LAVA);
        field.box(g, -1, floor + 6, width + 1, FlappyGhastState.FIELD_HEIGHT + 1, Ui.FLAPPY_LAVA_DARK);
        for (double lx = -(time * 0.8 % 8); lx < width; lx += 8) {
            double crest = Math.sin((lx + time) * 0.3) > 0 ? 1 : 0;
            field.box(g, lx, floor - crest, lx + 5, floor + 1, Ui.FLAPPY_LAVA_BRIGHT);
        }
        for (int i = 0; i < 6; i++) {
            double bubble = (time * 0.7 + i * 17) % 30;
            if (bubble > 4) continue;
            double bx = Math.floorMod(i * 61 + (int) (time / 30) * 23, Math.max(1, (int) width));
            field.box(g, bx, floor + 2 - bubble, bx + 2, floor + 4 - bubble, Ui.FLAPPY_LAVA_BRIGHT);
        }
    }

    private static void drawGust(GuiGraphics g, Field field, FlappyGhastState state, float partial, float time) {
        int warning = state.gustWarning();
        int push = state.gustPush();
        if (warning <= 0 && push <= 0) return;
        double gx = state.renderX(partial);
        double gy = state.renderY(partial);
        int direction = state.gustDirection();
        double side = gx - direction * 16;
        if (warning > 0) {
            boolean lit = warning > 10 ? (int) time % 6 < 4 : (int) time % 2 == 0;
            if (!lit) return;
            for (int i = 0; i < 5; i++) {
                double flicker = Math.sin(time * 0.9 + i * 1.7) * 2;
                double fy = gy - 8 + i * 4;
                double height = 3 + Math.abs(flicker);
                field.box(g, side - 1 + flicker * 0.5, fy, side + 1 + flicker * 0.5, fy + height, Ui.FLAPPY_SOUL_FIRE);
                field.box(g, side - 2, fy + height - 1, side + 2, fy + height, Ui.alpha(Ui.FLAPPY_SOUL_FIRE_DIM, 0xC0));
            }
            return;
        }
        for (int i = 0; i < 4; i++) {
            double ly = gy - 7 + i * 5;
            double streak = (time * 3 + i * 7) % 14;
            double start = side - direction * streak;
            field.box(g, Math.min(start, start + direction * 8), ly, Math.max(start, start + direction * 8), ly + 1, Ui.alpha(Ui.FLAPPY_SOUL_FIRE, 0xB0));
        }
    }

    private static void drawHud(GuiGraphics g, Font font, int x, int y, int width, int height, FlappyGhastState state, View view) {
        int right = x + width - 1;
        String scoreText = tr("computer.antos.flappy_ghast.score", state.score());
        int scoreWidth = font.width(scoreText);
        g.drawString(font, fit(font, tr("computer.antos.flappy_ghast.title"), width - scoreWidth - 21), x + 7, y + 4, Ui.TEXT_STRONG, false);
        g.drawString(font, scoreText, right - scoreWidth - 7, y + 4, Ui.TEXT, false);
        String sfx = tr(view.sound() ? "computer.antos.flappy_ghast.sfx_on" : "computer.antos.flappy_ghast.sfx_off");
        int sfxWidth = font.width(sfx);
        String stats = tr("computer.antos.flappy_ghast.best", view.best()) + "  " + tr("computer.antos.flappy_ghast.quartz", state.quartz())
                + "  " + tr("computer.antos.flappy_ghast.biome." + state.biome().id());
        g.drawString(font, fit(font, stats, width - sfxWidth - 21), x + 7, y + 15, Ui.TEXT_MUTED, false);
        g.drawString(font, sfx, right - sfxWidth - 7, y + 15, view.sound() ? Ui.TEXT_MUTED : Ui.ERROR, false);
        g.fill(x + 7, y + 23, right - 7, y + 24, Ui.LINE);
        if (state.paused() || state.phase() == FlappyGhastState.Phase.GAME_OVER) return;
        if (state.phase() == FlappyGhastState.Phase.READY) {
            g.drawCenteredString(font, fit(font, tr("computer.antos.flappy_ghast.start"), width - 14), x + width / 2, y + height - 11, Ui.TEXT);
            return;
        }
        boolean event = !view.message().isEmpty();
        String hint = event ? view.message() : tr("computer.antos.flappy_ghast.hint");
        g.drawCenteredString(font, fit(font, hint, width - 14), x + width / 2, y + height - 11, event ? Ui.ACCENT : Ui.TEXT_MUTED);
    }

    private static void drawOverlay(GuiGraphics g, Font font, int x, int y, int width, int height, FlappyGhastState state, View view) {
        int fieldY = y + HEADER;
        int fieldH = fieldHeight(height);
        if (state.paused()) {
            GameOverlay.render(g, font, x, fieldY, width, fieldH, Component.translatable("computer.antos.flappy_ghast.paused"),
                    Component.translatable("computer.antos.flappy_ghast.resume"), Ui.ACCENT);
        } else if (state.phase() == FlappyGhastState.Phase.READY) {
            GameOverlay.render(g, font, x, fieldY, width, fieldH, Component.translatable("computer.antos.flappy_ghast.ready"), null, Ui.ACCENT);
            if (!view.bonus().isEmpty()) {
                String text = fit(font, view.bonus(), width - 24);
                int badgeWidth = font.width(text) + 12;
                int badgeX = x + (width - badgeWidth) / 2;
                int badgeY = Math.max(fieldY + 2, fieldY + fieldH / 2 - 9) + 22;
                if (badgeY + 13 <= fieldY + fieldH) {
                    g.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + 13, Ui.alpha(Ui.PANEL, 0xE8));
                    g.renderOutline(badgeX, badgeY, badgeWidth, 13, Ui.BORDER_STRONG);
                    g.drawCenteredString(font, text, x + width / 2, badgeY + 3, Ui.ACCENT);
                }
            }
        } else if (state.phase() == FlappyGhastState.Phase.GAME_OVER) {
            boolean record = view.record();
            Component title = Component.translatable(record ? "computer.antos.flappy_ghast.new_best" : "computer.antos.flappy_ghast.game_over", state.score());
            Component hint = Component.translatable("computer.antos.flappy_ghast.restart");
            GameOverlay.render(g, font, x, fieldY, width, fieldH, title, hint, record ? Ui.ACCENT : Ui.ERROR);
        }
    }

    private static void tile(GuiGraphics g, ResourceLocation texture, int x, int y, int size) {
        g.blit(texture, x, y, size, size, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    private static int tileSize(Field field) {
        return Math.max(4, Math.round((float) FlappyGhastState.BLOCK_SIZE * field.unit));
    }

    private static void scissor(GuiGraphics g, int left, int top, int right, int bottom) {
        Matrix4f matrix = g.pose().last().pose();
        Vector3f start = matrix.transformPosition(left, top, 0.0F, new Vector3f());
        Vector3f end = matrix.transformPosition(right, bottom, 0.0F, new Vector3f());
        g.enableScissor((int) Math.floor(Math.min(start.x, end.x)), (int) Math.floor(Math.min(start.y, end.y)),
                (int) Math.ceil(Math.max(start.x, end.x)), (int) Math.ceil(Math.max(start.y, end.y)));
    }

    private static void px(GuiGraphics g, float left, float top, float unit, float x0, float y0, float x1, float y1, int color) {
        int a = Math.round(left + x0 * unit);
        int b = Math.round(top + y0 * unit);
        int c = Math.round(left + x1 * unit);
        int d = Math.round(top + y1 * unit);
        if (c > a && d > b) g.fill(a, b, c, d, color);
    }

    private static double wrap(double value, double span) {
        return value - Math.floor(value / span) * span;
    }

    private static int fieldHeight(int height) {
        return Math.max(20, height - HEADER - FOOTER);
    }

    private static int sky(FlappyGhastState.Biome biome) {
        return switch (biome) {
            case CRIMSON -> Ui.FLAPPY_CRIMSON_SKY;
            case WARPED -> Ui.FLAPPY_WARPED_SKY;
            case SOUL_SAND_VALLEY -> Ui.FLAPPY_SOUL_SKY;
            case BASALT_DELTAS -> Ui.FLAPPY_BASALT_SKY;
        };
    }

    private static int haze(FlappyGhastState.Biome biome) {
        return switch (biome) {
            case CRIMSON -> Ui.FLAPPY_CRIMSON_HAZE;
            case WARPED -> Ui.FLAPPY_WARPED_HAZE;
            case SOUL_SAND_VALLEY -> Ui.FLAPPY_SOUL_HAZE;
            case BASALT_DELTAS -> Ui.FLAPPY_BASALT_HAZE;
        };
    }

    private static int mote(FlappyGhastState.Biome biome) {
        return switch (biome) {
            case CRIMSON -> Ui.FLAPPY_CRIMSON_SPORE;
            case WARPED -> Ui.FLAPPY_WARPED_SPORE;
            case SOUL_SAND_VALLEY -> Ui.FLAPPY_SOUL_ASH;
            case BASALT_DELTAS -> Ui.FLAPPY_BASALT_ASH;
        };
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String fit(Font font, String text, int width) {
        return GameOverlay.fit(font, text, width);
    }

    private record Field(int x, int y, int width, int height, float unit) {
        float px(double lx) { return (float) (x + lx * unit); }
        float py(double ly) { return (float) (y + ly * unit); }
        int ix(double lx) { return Math.round(px(lx)); }
        int iy(double ly) { return Math.round(py(ly)); }

        void box(GuiGraphics g, double x0, double y0, double x1, double y1, int color) {
            int a = ix(x0);
            int b = iy(y0);
            int c = ix(x1);
            int d = iy(y1);
            if (c > a && d > b) g.fill(a, b, c, d, color);
        }
    }

    private static final class Tiles {
        private static final ItemStack QUARTZ = new ItemStack(Items.QUARTZ);
        private static final Tiles CRIMSON = new Tiles("crimson_stem", "nether_wart_block", "netherrack", "crimson_nylium_side");
        private static final Tiles WARPED = new Tiles("warped_stem", "warped_wart_block", "netherrack", "warped_nylium_side");
        private static final Tiles SOUL = new Tiles("bone_block_side", "bone_block_top", "soul_soil", "soul_sand");
        private static final Tiles BASALT = new Tiles("basalt_side", "polished_basalt_side", "blackstone", "polished_blackstone");
        private final ResourceLocation top;
        private final ResourceLocation topCap;
        private final ResourceLocation bottom;
        private final ResourceLocation bottomCap;

        private Tiles(String top, String topCap, String bottom, String bottomCap) {
            this.top = texture(top);
            this.topCap = texture(topCap);
            this.bottom = texture(bottom);
            this.bottomCap = texture(bottomCap);
        }

        private static ResourceLocation texture(String name) {
            return new ResourceLocation("minecraft", "textures/block/" + name + ".png");
        }

        static Tiles of(FlappyGhastState.Biome biome) {
            return switch (biome) {
                case CRIMSON -> CRIMSON;
                case WARPED -> WARPED;
                case SOUL_SAND_VALLEY -> SOUL;
                case BASALT_DELTAS -> BASALT;
            };
        }
    }
}
