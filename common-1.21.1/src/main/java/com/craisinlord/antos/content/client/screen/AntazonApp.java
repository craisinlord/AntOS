package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.AntOSPlayerText;
import com.craisinlord.antos.content.client.AntazonClientState;
import com.craisinlord.antos.content.client.ComputerTasksClientState;
import com.craisinlord.antos.content.network.ComputerNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.BLACK;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.DARK_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HOVER_FILL;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;

final class AntazonApp extends ComputerApp {
    private final State state = new State();
    void tick(boolean loggedIn, boolean windowOpen) {
        if (state.walletRefreshTicks > 0 && --state.walletRefreshTicks == 0) ComputerNetworking.requestAntazonWallet();
        if (loggedIn && !state.prefetched && AntOSSettings.appEnabled("ANTAZON")) {
            state.prefetched = true;
            ComputerNetworking.requestAntazon();
            ComputerNetworking.requestAntazonWishlist();
            ComputerNetworking.requestAntazonCart();
        }
        if (loggedIn && windowOpen && --state.refreshTicks <= 0) {
            state.refreshTicks = 200;
            ComputerNetworking.requestAntazon();
            ComputerNetworking.requestAntazonWallet();
            if (state.mode.equals("sell") && !state.priceGuide) ComputerNetworking.requestAntazonSellState();
        }
    }

    private static final int MUTED = 0xFF7EA77E;
    private static final int DIVIDER = 0xFF315531;
    private static final int DIM = 0xFF638063;
    private static final int RED = 0xFFFF7777;
    private static final int PANEL = 0xFF071007;
    private static final int ROW = 25;
    private final List<Hit> hits = new ArrayList<>();
    private final List<ScrollArea> scrollAreas = new ArrayList<>();
    private double clickY;

    private record Hit(int left, int top, int right, int bottom, Runnable action) { }
    private record ScrollArea(int left, int top, int right, int bottom, java.util.function.IntConsumer action) { }

    private void hit(int x, int y, int w, int h, Runnable action) {
        if (w > 0 && h > 0) hits.add(new Hit(x, y, x + w, y + h, action));
    }

    private void clippedHit(int x, int y, int w, int h, int clipTop, int clipBottom, Runnable action) {
        int top = Math.max(y, clipTop);
        int bottom = Math.min(y + h, clipBottom);
        if (bottom > top) hit(x, top, w, bottom - top, action);
    }

    private void scrollArea(int x, int y, int w, int h, java.util.function.IntConsumer action) {
        if (w > 0 && h > 0) scrollAreas.add(new ScrollArea(x, y, x + w, y + h, action));
    }

    private void scrollbar(GuiGraphics g, int x, int top, int height, int visible, int total, int scroll, int maximum, java.util.function.IntConsumer setter) {
        screen.drawScrollbar(g, x, top, height, visible, total, scroll, maximum);
        if (maximum > 0) hit(x - 2, top, 6, height, () -> setter.accept((int) Math.round(Math.max(0.0D, Math.min(1.0D,
                (clickY - top) / Math.max(1, height))) * maximum)));
    }

    boolean click(double mouseX, double mouseY) {
        clickY = mouseY;
        state.focus = 0;
        for (int index = hits.size() - 1; index >= 0; index--) {
            Hit hit = hits.get(index);
            if (mouseX >= hit.left() && mouseX < hit.right() && mouseY >= hit.top() && mouseY < hit.bottom()) {
                hit.action().run();
                return true;
            }
        }
        return true;
    }

    boolean scroll(double mouseX, double mouseY, double scrollY) {
        int delta = -(int) Math.signum(scrollY);
        for (int index = scrollAreas.size() - 1; index >= 0; index--) {
            ScrollArea area = scrollAreas.get(index);
            if (mouseX >= area.left() && mouseX < area.right() && mouseY >= area.top() && mouseY < area.bottom()) {
                area.action().accept(delta);
                return true;
            }
        }
        return true;
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, boolean enabled, Runnable action) {
        button(g, x, y, w, 18, label, enabled, false, action);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled, boolean primary, Runnable action) {
        boolean hover = enabled && screen.hovered(x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? HOVER_FILL : primary && enabled ? 0xFF143014 : DARK_GREEN);
        screen.box(g, x, y, x + w, y + h, !enabled ? DIVIDER : hover ? PALE_GREEN : primary ? GREEN : DIVIDER);
        centered(g, label, x + w / 2.0F, y + (h - 8) / 2.0F + 1, w - 6, !enabled ? DIM : primary ? GREEN : PALE_GREEN);
        if (enabled) hit(x, y, w, h, action);
    }

    private void chip(GuiGraphics g, int x, int y, int w, boolean selected, String label, Runnable action) {
        screen.drawFilterButton(g, x, y, w, selected, label);
        hit(x, y, w, 17, action);
    }

    private void centered(GuiGraphics g, String label, float centerX, float y, int maxWidth, int color) {
        float scale = Math.min(1.0F, Math.max(0.1F, maxWidth / (float) Math.max(1, font.width(label))));
        g.pose().pushPose();
        try {
            g.pose().translate(centerX, y, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, label, -font.width(label) / 2, 0, color, false);
        } finally {
            g.pose().popPose();
        }
    }

    private void small(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(0.85F, 0.85F, 1.0F);
            g.drawString(font, screen.trimToWidth(text, Math.max(1, (int) (maxWidth / 0.85F))), 0, 0, color, false);
        } finally {
            g.pose().popPose();
        }
    }

    private void chartSmall(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(0.68F, 0.68F, 1.0F);
            g.drawString(font, screen.trimToWidth(text, Math.max(1, (int) (maxWidth / 0.68F))), 0, 0, color, false);
        } finally {
            g.pose().popPose();
        }
    }

    private int smallWidth(String text) {
        return (int) Math.ceil(font.width(text) * 0.85F);
    }

    private void searchField(GuiGraphics g, int x, int y, int w, String value, String placeholder, int focusId, Runnable onClear) {
        boolean focused = state.focus == focusId;
        boolean hover = screen.hovered(x, y, w, 17);
        g.fill(x, y, x + w, y + 17, hover || focused ? HOVER_FILL : DARK_GREEN);
        screen.box(g, x, y, x + w, y + 17, focused ? PALE_GREEN : DIVIDER);
        int clearWidth = value.isEmpty() ? 0 : 14;
        String text = value.isEmpty() && !focused ? placeholder : value;
        int textWidth = w - 8 - clearWidth;
        while (focused && text.length() > 1 && font.width(text + "|") > textWidth) text = text.substring(1);
        if (focused && screen.caretVisible()) text = text + "|";
        g.drawString(font, screen.trimToWidth(text, textWidth), x + 4, y + 5, value.isEmpty() && !focused ? MUTED : PALE_GREEN, false);
        hit(x, y, w - clearWidth, 17, () -> state.focus = focusId);
        if (clearWidth > 0) {
            g.drawCenteredString(font, "x", x + w - 8, y + 4, screen.hovered(x + w - clearWidth, y, clearWidth, 17) ? GREEN : MUTED);
            hit(x + w - clearWidth, y, clearWidth, 17, onClear);
        }
    }

    private void coin(GuiGraphics g, int x, int y) {
        String[] coin = {"..###..", ".#+++#.", "#++#++#", "#+###+#", "#++#++#", ".#+++#.", "..###.."};
        for (int row = 0; row < coin.length; row++) {
            for (int column = 0; column < coin[row].length(); column++) {
                char pixel = coin[row].charAt(column);
                if (pixel == '#') g.fill(x + column, y + row, x + column + 1, y + row + 1, GREEN);
                else if (pixel == '+') g.fill(x + column, y + row, x + column + 1, y + row + 1, 0xFF1F4A1F);
            }
        }
    }

    private String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private int amountWidth(AntazonClientState.PaymentRow payment, long amount) {
        String number = number(amount);
        return payment.type().equals("antcoins") ? font.width(number) + 9 : font.width(number + " " + itemName(payment.resource()));
    }

    private int drawAmount(GuiGraphics g, AntazonClientState.PaymentRow payment, long amount, int x, int y, int color) {
        String number = number(amount);
        if (payment.type().equals("antcoins")) {
            g.drawString(font, number, x, y, color, false);
            coin(g, x + font.width(number) + 2, y);
            return x + font.width(number) + 9;
        }
        String text = number + " " + itemName(payment.resource());
        g.drawString(font, text, x, y, color, false);
        return x + font.width(text);
    }

    private String amountText(AntazonClientState.PaymentRow payment, long amount) {
        return number(amount) + (payment.type().equals("antcoins") ? " " + AntOSPlayerText.currencySymbol() : " " + itemName(payment.resource()));
    }

    private String itemName(String itemId) {
        try {
            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
            if (item != null && item != net.minecraft.world.item.Items.AIR) return Component.translatable(item.getDescriptionId()).getString();
        } catch (RuntimeException ignored) { }
        return prettyItem(itemId);
    }

    private int inventoryCount(String itemId) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        try {
            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
            return item == null || item == net.minecraft.world.item.Items.AIR ? 0 : player.getInventory().countItem(item);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private String name(AntazonClientState.ProductRow product) {
        return variantName(product, state.productId.equals(product.id()) ? state.selectedVariant : "");
    }

    private String variantName(AntazonClientState.ProductRow product, String variant) {
        String text = Component.translatable(product.name()).getString();
        if (variant == null || variant.isBlank()) {
            if (product.variantMode().equals("choose") && !product.variants().isEmpty()) variant = product.variants().get(0);
            else if (text.contains("{item}") && product.variantMode().equals("all")) return text.replace("{item}", "all variants");
            else if (text.contains("{item}") && product.variantMode().equals("random")) return text.replace("{item}", "random variant");
            else return text;
        }
        String item = itemName(variant);
        return text.contains("{item}") ? text.replace("{item}", item) : text + " - " + item;
    }

    private AntazonClientState.PreviewAsset variantPreview(AntazonClientState.ProductRow product, String variant) {
        if ((variant == null || variant.isBlank()) && product.variantMode().equals("choose") && !product.variants().isEmpty()) variant = product.variants().get(0);
        return variant == null || variant.isBlank() ? product.thumbnail() : new AntazonClientState.PreviewAsset(variant, "");
    }

    private long owned(AntazonClientState.PaymentRow payment) {
        if (payment.type().equals("antcoins")) return AntazonClientState.wallet();
        if (payment.type().equals("item")) return inventoryCount(payment.resource());
        return payment.owned();
    }

    private AntazonClientState.PaymentRow payment(AntazonClientState.ProductRow product, int option) {
        if (product.payments().isEmpty()) return null;
        return product.payments().get(Math.max(0, Math.min(product.payments().size() - 1, option)));
    }

    private boolean affordable(AntazonClientState.ProductRow product, int option, int units) {
        var payment = payment(product, option);
        return payment != null && owned(payment) >= total(product, payment, Math.max(1, units));
    }

    private long total(AntazonClientState.ProductRow product, AntazonClientState.PaymentRow payment, int units) {
        if (!product.onSale() || product.dealRemaining() < 0) return (long) payment.price() * units;
        int discounted = Math.min(units, product.dealRemaining());
        return (long) payment.price() * discounted + (long) payment.amount() * (units - discounted);
    }

    private int bestOption(AntazonClientState.ProductRow product, int units) {
        for (int option = 0; option < product.payments().size(); option++) if (affordable(product, option, units)) return option;
        return 0;
    }

    private int purchasesLeft(AntazonClientState.ProductRow product) {
        int perPurchase = Math.max(1, product.quantity());
        int maximum = 64;
        if (product.remaining() >= 0) maximum = Math.min(maximum, product.remaining() / perPurchase);
        if (product.limit() > 0) maximum = Math.min(maximum, Math.max(0, product.limit() - product.limitUsed()) / perPurchase);
        return Math.max(0, maximum);
    }

    private String blocker(AntazonClientState.ProductRow product) {
        if (product.locked()) return "LOCKED";
        if (onCooldown(product)) return "COOLDOWN " + cooldown(product);
        if (product.remaining() >= 0 && product.remaining() < Math.max(1, product.quantity())) return "SOLD OUT";
        if (product.limit() > 0 && purchasesLeft(product) == 0) return "LIMIT REACHED";
        return "";
    }

    private String stockLabel(AntazonClientState.ProductRow product) {
        if (product.remaining() < 0) return "IN STOCK";
        int left = product.remaining() / Math.max(1, product.quantity());
        return left <= 3 ? "ONLY " + left + " LEFT" : left + " LEFT";
    }

    private String duration(long ticks) {
        long remaining = Math.max(0L, ticks);
        long days = remaining / 24000L;
        long hours = remaining % 24000L / 1000L;
        long minutes = remaining % 1000L * 60L / 1000L;
        if (days > 0L) return days + "D " + hours + "H";
        if (hours > 0L) return hours + "H " + minutes + "M";
        return Math.max(1L, minutes) + "M";
    }

    private String countdown(long deadline, boolean real) {
        long remaining = Math.max(0L, deadline - System.currentTimeMillis());
        if (real) return realDuration(remaining);
        double days = Math.max(0.1D, Math.ceil(remaining / 120_000.0D) / 10.0D);
        return (days == Math.rint(days) ? Long.toString((long) days) : String.format(java.util.Locale.ROOT, "%.1f", days))
                + (days == 1.0D ? " MINECRAFT DAY" : " MINECRAFT DAYS");
    }

    private String realDuration(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        long days = seconds / 86400L;
        long hours = seconds % 86400L / 3600L;
        long minutes = seconds % 3600L / 60L;
        if (days > 0L) return days + "D " + hours + "H";
        if (hours > 0L) return hours + "H " + minutes + "M";
        if (minutes > 0L) return minutes + "M " + seconds % 60L + "S";
        return Math.max(1L, seconds) + "S";
    }

    private int drawStars(GuiGraphics g, double rating, int x, int y) {
        int filled = (int) Math.round(rating);
        int step = font.width("★") + 1;
        for (int star = 0; star < 5; star++) g.drawString(font, "★", x + star * step, y, star < filled ? GREEN : DIVIDER, false);
        return x + step * 5;
    }

    private void renderAsset(GuiGraphics g, AntazonClientState.PreviewAsset asset, int centerX, int centerY, int size, boolean greenTint) {
        renderAsset(g, asset, centerX, centerY, size, greenTint, true);
    }

    private void renderAsset(GuiGraphics g, AntazonClientState.PreviewAsset asset, int centerX, int centerY, int size, boolean greenTint, boolean renderMobFromSpawnEgg) {
        g.fill(centerX - size / 2, centerY - size / 2, centerX + size / 2, centerY + size / 2, 0xFF102010);
        boolean previousTint = screen.archiveGreenTint;
        screen.archiveGreenTint = greenTint;
        try {
            if (!screen.renderArchiveAsset(g, asset.item(), asset.entity(), "", "", centerX, centerY, size, 0.0F, 1.0F, renderMobFromSpawnEgg)) {
                g.drawString(font, Component.literal("?"), centerX - 3, centerY - 4, PALE_GREEN, false);
            }
        } finally {
            screen.archiveGreenTint = previousTint;
        }
    }

    private void renderLoading(GuiGraphics g, int x, int y, int w, String label) {
        String dots = ".".repeat((int) (System.currentTimeMillis() / 400L % 4L));
        g.drawCenteredString(font, label + dots, x + w / 2, y, PALE_GREEN);
    }

    private void renderEmpty(GuiGraphics g, int x, int y, int w, String title, String detail, String action, Runnable onAction) {
        g.drawCenteredString(font, title, x + w / 2, y, GREEN);
        if (!detail.isBlank()) small(g, detail, x + w / 2 - Math.min(w, smallWidth(detail)) / 2, y + 13, w, MUTED);
        if (action != null) {
            int buttonWidth = Math.max(96, font.width(action) + 16);
            button(g, x + w / 2 - buttonWidth / 2, y + 28, buttonWidth, 18, action, true, true, onAction);
        }
    }

    void render(GuiGraphics g, int x, int y, int w, int h) {
        hits.clear();
        scrollAreas.clear();
        if (!AntazonClientState.onboardingReceived()) {
            renderLoading(g, x, y + h / 2 - 10, w, "CONNECTING TO ANTAZON");
            return;
        }
        if (!AntazonClientState.onboardingComplete()) {
            renderOnboarding(g, x, y, w, h);
            return;
        }
        renderHeader(g, x, y, w);
        switch (state.mode) {
            case "product" -> renderProduct(g, x, y, w, h);
            case "review" -> renderReview(g, x, y, w, h);
            case "cart" -> renderCart(g, x, y, w, h);
            case "orders" -> renderOrders(g, x, y, w, h);
            case "saved" -> renderSaved(g, x, y, w, h);
            case "sell" -> renderSell(g, x, y, w, h);
            default -> renderCatalog(g, x, y, w, h);
        }
        renderFooter(g, x, y, w, h, state.footer);
        state.footer = "";
    }

    private int tab() {
        return switch (state.mode) {
            case "sell" -> 1;
            case "orders" -> 2;
            case "saved" -> 3;
            case "cart" -> -1;
            default -> 0;
        };
    }

    private void switchTab(int tab) {
        state.focus = 0;
        state.buyConfirmUntil = 0L;
        state.checkoutConfirmUntil = 0L;
        switch (tab) {
            case 1 -> {
                state.mode = "sell";
                state.sellConfirm = false;
                AntazonClientState.clearSellFeedback();
                ComputerNetworking.requestAntazonSellState();
                ComputerNetworking.requestAntazonPrices();
            }
            case 2 -> {
                state.mode = "orders";
                state.orderSelection = -1;
                ComputerNetworking.requestAntazonOrders();
            }
            case 3 -> {
                state.mode = "saved";
                ComputerNetworking.requestAntazonWishlist();
                markDealsSeen();
            }
            case -1 -> {
                state.mode = "cart";
                ComputerNetworking.requestAntazonCart();
            }
            default -> state.mode = "catalog";
        }
    }

    private void renderHeader(GuiGraphics g, int x, int y, int w) {
        int cartCount = AntazonClientState.cart().stream()
                .mapToInt(AntazonClientState.CartLine::units).sum();
        String cartLabel = cartCount > 0 ? "CART " + cartCount : "CART";
        int cartWidth = Math.max(52, font.width(cartLabel) + 14);
        String wallet = number(AntazonClientState.wallet());
        int walletWidth = font.width(wallet) + 21;
        int slot = Math.max(10, (w - cartWidth - walletWidth - 8) / 4);
        int groupWidth = slot * 4;
        int savedCount = AntazonClientState.wishlist().size();
        String[] labels = {"SHOP", "SELL", "ORDERS", savedCount > 0 ? "SAVED " + savedCount : "SAVED"};
        int selectedTab = tab();
        g.fill(x, y, x + groupWidth, y + 20, DARK_GREEN);
        for (int tab = 0; tab < labels.length; tab++) {
            int tabX = x + tab * slot;
            boolean selected = tab == selectedTab;
            boolean hover = screen.hovered(tabX, y, slot, 20);
            g.fill(tabX, y, tabX + slot, y + 20, selected ? PALE_GREEN : hover ? HOVER_FILL : DARK_GREEN);
            if (tab > 0) g.fill(tabX, y + 2, tabX + 1, y + 18, DIVIDER);
            screen.drawTabLabel(g, labels[tab], tabX, y, slot, selected ? BLACK : GREEN);
            if (tab == 3 && hasUnseenDeals()) g.fill(tabX + slot - 7, y + 4, tabX + slot - 4, y + 7, selected ? BLACK : GREEN);
            int target = tab;
            hit(tabX, y, slot, 20, () -> switchTab(target));
        }
        screen.box(g, x, y, x + groupWidth, y + 20, GREEN);
        int walletX = x + groupWidth + 4;
        g.fill(walletX, y, walletX + walletWidth, y + 20, PANEL);
        screen.box(g, walletX, y, walletX + walletWidth, y + 20, DIVIDER);
        coin(g, walletX + 6, y + 7);
        g.drawString(font, wallet, walletX + 15, y + 6, PALE_GREEN, false);
        int cartX = x + w - cartWidth;
        boolean cartSelected = state.mode.equals("cart");
        boolean cartHover = screen.hovered(cartX, y, cartWidth, 20);
        g.fill(cartX, y, cartX + cartWidth, y + 20, cartSelected ? PALE_GREEN : cartHover ? HOVER_FILL : DARK_GREEN);
        screen.box(g, cartX, y, cartX + cartWidth, y + 20, cartHover ? PALE_GREEN : GREEN);
        screen.drawTabLabel(g, cartLabel, cartX, y, cartWidth, cartSelected ? BLACK : GREEN);
        hit(cartX, y, cartWidth, 20, () -> switchTab(-1));
    }

    private void renderFooter(GuiGraphics g, int x, int y, int w, int h, String fallback) {
        var notice = AntazonClientState.notice();
        int footerY = y + h - 8;
        if (notice != null && System.currentTimeMillis() - notice.createdAt() < 6000L) {
            String text = noticeText(notice);
            g.fill(x - 2, footerY - 3, x + w, y + h, notice.success() ? 0xFF102A10 : 0xFF2A1010);
            small(g, text, x + 2, footerY, w - 4, notice.success() ? GREEN : RED);
            return;
        }
        if (!fallback.isBlank()) small(g, fallback, x + 2, footerY, w - 4, MUTED);
    }

    private String noticeText(AntazonClientState.Notice notice) {
        var product = AntazonClientState.product(notice.product());
        String name = product == null ? "" : name(product);
        return switch (notice.kind()) {
            case "purchase" -> notice.success()
                    ? "ORDER PLACED // " + name + " X" + notice.units() + " // WATCH THE SKY FOR YOUR DELIVERY"
                    : (name.isBlank() ? "" : name + " // ") + failure(notice.code());
            case "cart" -> notice.success() ? "ADDED TO CART // " + name + " X" + notice.units() : failure(notice.code());
            case "prepare" -> notice.success() ? "MOVED INTO THE SHIPPING CONTAINER" : failure(notice.code());
            case "review" -> notice.success() ? "REVIEW POSTED // THANKS FOR THE FEEDBACK" : failure(notice.code());
            default -> failure(notice.code());
        };
    }

    private String failure(String code) {
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "out_of_stock" -> "SOLD OUT";
            case "player_limit" -> "PURCHASE LIMIT REACHED";
            case "cooldown" -> "STILL ON COOLDOWN";
            case "task_locked" -> "LOCKED // FINISH THE REQUIRED TASK FIRST";
            case "insufficient_payment", "payment_unavailable" -> "NOT ENOUGH TO PAY FOR THIS";
            case "crate_unavailable", "crate_required", "chest_required" -> "SELLING REQUIRES A SHIPPING CONTAINER NEARBY";
            case "delivery_unavailable" -> "DELIVERY UNAVAILABLE";
            case "product_unavailable" -> "THIS PRODUCT IS NO LONGER AVAILABLE";
            case "variant_required" -> "CHOOSE A VARIANT FIRST";
            case "variant_unavailable" -> "THAT VARIANT IS NO LONGER AVAILABLE";
            case "unauthorized" -> "SIGN IN TO USE ANTAZON";
            case "already_reviewed" -> "YOU ALREADY REVIEWED THIS PRODUCT";
            case "purchase_required" -> "BUY THIS PRODUCT BEFORE REVIEWING IT";
            case "invalid_review" -> "ADD A RATING, TITLE AND REVIEW";
            case "unsupported_item" -> "ANTAZON DOES NOT BUY THIS ITEM";
            case "insufficient_inventory" -> "NOT ENOUGH IN YOUR INVENTORY";
            case "crate_full" -> "THE SHIPPING CONTAINER IS FULL";
            case "cart_full" -> "YOUR CART IS FULL";
            case "invalid_response" -> "ANTAZON RESPONSE INVALID";
            default -> "REQUEST FAILED";
        };
    }

    private List<String> categories(List<AntazonClientState.ProductRow> products) {
        List<String> categories = new ArrayList<>();
        categories.add("ALL");
        products.stream().map(product -> product.category().toUpperCase(Locale.ROOT)).distinct().sorted().forEach(categories::add);
        return categories;
    }

    private List<AntazonClientState.ProductRow> filterProducts(List<AntazonClientState.ProductRow> products) {
        String query = state.search.trim().toLowerCase(Locale.ROOT);
        return products.stream()
                .filter(product -> !product.hiddenUntilUnlocked() || !product.locked())
                .filter(product -> state.category.equals("ALL") || product.category().equalsIgnoreCase(state.category))
                .filter(product -> !state.filterDeals || product.onSale())
                .filter(product -> !state.filterStock || blocker(product).isEmpty())
                .filter(product -> !state.filterAfford || java.util.stream.IntStream.range(0, product.payments().size()).anyMatch(option -> affordable(product, option, 1)))
                .filter(product -> query.isBlank()
                        || name(product).toLowerCase(Locale.ROOT).contains(query)
                        || product.category().toLowerCase(Locale.ROOT).contains(query)
                        || product.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(query))
                        || product.id().toLowerCase(Locale.ROOT).contains(query))
                .sorted(java.util.Comparator.comparingInt((AntazonClientState.ProductRow product) -> blocker(product).isEmpty() ? 0 : 1))
                .toList();
    }

    private void renderCatalog(GuiGraphics g, int x, int y, int w, int h) {
        if (!AntazonClientState.hasSnapshot()) {
            renderLoading(g, x, y + h / 2, w, "LOADING SUPPLIES");
            return;
        }
        List<AntazonClientState.ProductRow> products = AntazonClientState.products().stream()
                .filter(product -> !product.hiddenUntilUnlocked() || !product.locked()).toList();
        int top = y + 24;
        int chipWidth = 46;
        int searchWidth = w - chipWidth * 3 - 12;
        searchField(g, x, top, searchWidth, state.search, "SEARCH SUPPLIES", 1, () -> {
            state.search = "";
            state.scroll = 0;
        });
        int chipX = x + searchWidth + 4;
        long dealCount = products.stream().filter(AntazonClientState.ProductRow::onSale).count();
        chip(g, chipX, top, chipWidth, state.filterDeals, dealCount > 0 ? "DEALS " + dealCount : "DEALS", () -> { state.filterDeals = !state.filterDeals; state.scroll = 0; });
        chip(g, chipX + chipWidth + 4, top, chipWidth, state.filterStock, "IN STOCK", () -> { state.filterStock = !state.filterStock; state.scroll = 0; });
        chip(g, chipX + (chipWidth + 4) * 2, top, chipWidth, state.filterAfford, "AFFORDABLE", () -> { state.filterAfford = !state.filterAfford; state.scroll = 0; });
        int bodyTop = top + 22;
        int bodyBottom = y + h - 2;
        List<String> categories = categories(products);
        if (!categories.contains(state.category)) state.category = "ALL";
        int railWidth = w >= 300 ? 86 : 0;
        if (railWidth > 0) {
            int railRows = Math.max(1, (bodyBottom - bodyTop) / 15);
            int railMaximum = Math.max(0, categories.size() - railRows);
            state.categoryScroll = Math.max(0, Math.min(railMaximum, state.categoryScroll));
            for (int index = state.categoryScroll; index < categories.size() && index < state.categoryScroll + railRows; index++) {
                String category = categories.get(index);
                int rowY = bodyTop + (index - state.categoryScroll) * 15;
                boolean selected = category.equals(state.category);
                if (selected || screen.hovered(x, rowY, railWidth, 14)) g.fill(x, rowY, x + railWidth, rowY + 14, HOVER_FILL);
                if (selected) g.fill(x, rowY, x + 2, rowY + 14, GREEN);
                long count = category.equals("ALL") ? products.size() : products.stream().filter(product -> product.category().equalsIgnoreCase(category)).count();
                String countLabel = Long.toString(count);
                int countWidth = smallWidth(countLabel);
                small(g, category, x + 6, rowY + 4, railWidth - countWidth - 12, selected ? GREEN : 0xFF87B787);
                small(g, countLabel, x + railWidth - countWidth - 4, rowY + 4, countWidth + 2, selected ? PALE_GREEN : DIM);
                hit(x, rowY, railWidth, 14, () -> {
                    state.category = category;
                    state.scroll = 0;
                });
            }
            scrollArea(x, bodyTop, railWidth, bodyBottom - bodyTop, delta -> state.categoryScroll = Math.max(0, Math.min(railMaximum, state.categoryScroll + delta)));
            g.fill(x + railWidth + 3, bodyTop, x + railWidth + 4, bodyBottom, DIVIDER);
        }
        int listX = railWidth > 0 ? x + railWidth + 8 : x;
        int listWidth = x + w - listX - 5;
        List<AntazonClientState.ProductRow> filtered = filterProducts(products);
        if (filtered.isEmpty()) {
            boolean filteredOut = !products.isEmpty();
            renderEmpty(g, listX, bodyTop + 24, listWidth, filteredOut ? "NO MATCHING SUPPLIES" : "NO SUPPLIES LISTED YET",
                    filteredOut ? "TRY ANOTHER SEARCH OR CLEAR YOUR FILTERS" : "CHECK BACK AFTER THE NEXT RESTOCK",
                    filteredOut ? "CLEAR FILTERS" : null, () -> {
                        state.search = "";
                        state.category = "ALL";
                        state.filterDeals = false;
                        state.filterStock = false;
                        state.filterAfford = false;
                    });
            return;
        }
        int visible = Math.max(1, (bodyBottom - bodyTop) / ROW);
        int maximum = Math.max(0, filtered.size() - visible);
        state.scroll = Math.max(0, Math.min(maximum, state.scroll));
        for (int index = state.scroll; index < filtered.size() && index < state.scroll + visible; index++) {
            var product = filtered.get(index);
            int rowY = bodyTop + (index - state.scroll) * ROW;
            renderProductRow(g, product, listX, rowY, listWidth, screen.hovered(listX, rowY, listWidth, ROW - 1));
            hit(listX, rowY, listWidth, ROW - 1, () -> openProduct(product.id(), "catalog"));
        }
        scrollbar(g, x + w - 3, bodyTop, bodyBottom - bodyTop, visible, filtered.size(), state.scroll, maximum, value -> state.scroll = value);
        scrollArea(listX, bodyTop, x + w - listX, bodyBottom - bodyTop, delta -> state.scroll = Math.max(0, Math.min(maximum, state.scroll + delta)));
        state.footer = "SHOWING " + (state.scroll + 1) + "-" + Math.min(filtered.size(), state.scroll + visible) + " OF " + filtered.size();
        if (state.filterDeals) filtered.stream().filter(product -> product.onSale() && product.dealEndsAt() > 0L)
                .min(java.util.Comparator.comparingLong(AntazonClientState.ProductRow::dealEndsAt))
                .ifPresent(product -> state.footer += " · NEXT DEAL ENDS IN " + countdown(product.dealEndsAt(), product.dealReal()));
    }

    private void renderProductRow(GuiGraphics g, AntazonClientState.ProductRow product, int x, int y, int w, boolean hover) {
        String blocker = blocker(product);
        boolean available = blocker.isEmpty();
        if (hover) g.fill(x, y, x + w, y + ROW - 1, HOVER_FILL);
        renderAsset(g, product.thumbnail(), x + 11, y + 13, 20, product.greenTint(), product.renderMobFromSpawnEgg());
        var payment = payment(product, 0);
        int priceRight = x + w - 4;
        int priceWidth = payment == null ? 0 : amountWidth(payment, payment.price());
        if (payment != null) {
            int priceColor = !available ? DIM : affordable(product, 0, 1) ? GREEN : MUTED;
            drawAmount(g, payment, payment.price(), priceRight - priceWidth, y + 4, priceColor);
        }
        int textX = x + 26;
        g.drawString(font, screen.trimToWidth(name(product), Math.max(1, priceRight - priceWidth - 8 - textX)), textX, y + 4, available ? GREEN : MUTED, false);
        String badge = !available ? blocker : product.onSale() ? "-" + product.dealDiscount() + "%" : "";
        int badgeWidth = badge.isEmpty() ? 0 : smallWidth(badge) + 6;
        int badgeX = priceRight - badgeWidth;
        if (!badge.isEmpty()) {
            boolean deal = available;
            g.fill(badgeX, y + 14, priceRight, y + 23, deal ? GREEN : 0xFF1A2A1A);
            small(g, badge, badgeX + 3, y + 15, badgeWidth, deal ? BLACK : product.locked() ? MUTED : RED);
        }
        int metaRight = badgeX - 4;
        if (product.dealActive() && payment != null && payment.amount() != payment.price()) {
            String was = number(payment.amount());
            int wasWidth = smallWidth(was);
            small(g, was, metaRight - wasWidth, y + 15, wasWidth + 2, MUTED);
            g.fill(metaRight - wasWidth - 1, y + 18, metaRight + 1, y + 19, MUTED);
            metaRight -= wasWidth + 6;
        }
        List<String> meta = new ArrayList<>();
        meta.add(product.category().toUpperCase(Locale.ROOT));
        if (available) meta.add(stockLabel(product));
        if (!product.reviews().isEmpty()) meta.add("★" + String.format(Locale.ROOT, "%.1f", product.rating()));
        if (product.payments().size() > 1) meta.add(product.payments().size() + " WAYS TO PAY");
        small(g, String.join(" · ", meta), textX, y + 15, Math.max(1, metaRight - textX), MUTED);
        g.fill(x, y + ROW - 1, x + w, y + ROW, DIVIDER);
    }

    void openProduct(String productId, String returnMode) {
        if (!productId.equals(state.productId)) {
            state.galleryIndex = -1;
            state.recipeItem = "";
        }
        state.productId = productId;
        state.returnMode = returnMode;
        state.mode = "product";
        state.quantity = 1;
        state.detailScroll = 0;
        state.buyConfirmUntil = 0L;
        state.focus = 0;
        var product = AntazonClientState.product(productId);
        state.paymentOption = product == null ? 0 : bestOption(product, 1);
        state.selectedVariant = product != null && product.variantMode().equals("choose") && !product.variants().isEmpty() ? product.variants().get(0) : "";
    }

    private void back() {
        state.mode = switch (state.returnMode) {
            case "saved", "cart", "orders" -> state.returnMode;
            default -> "catalog";
        };
        state.focus = 0;
    }

    private void addToCart(AntazonClientState.ProductRow product, int option, int units) {
        List<AntazonClientState.CartLine> lines = new ArrayList<>(AntazonClientState.cart());
        boolean merged = false;
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            if (line.product().equals(product.id()) && line.option() == option && line.variant().equals(state.selectedVariant)) {
                lines.set(index, new AntazonClientState.CartLine(line.product(), option, Math.min(64, line.units() + units), line.variant()));
                merged = true;
            }
        }
        if (!merged) {
            if (lines.size() >= 32) {
                AntazonClientState.pushNotice("cart", "cart_full", product.id(), units, false);
                return;
            }
            lines.add(new AntazonClientState.CartLine(product.id(), option, Math.min(64, units), state.selectedVariant));
        }
        AntazonClientState.setCart(lines);
        AntazonClientState.pushNotice("cart", "added", product.id(), units, true);
    }

    private void toggleSaved(String productId) {
        try { ComputerNetworking.toggleAntazonWishlist(ResourceLocation.parse(productId)); }
        catch (RuntimeException ignored) { }
    }

    private void renderProduct(GuiGraphics g, int x, int y, int w, int h) {
        var product = AntazonClientState.product(state.productId);
        if (product == null) {
            if (AntazonClientState.hasSnapshot()) back();
            else renderLoading(g, x, y + h / 2, w, "LOADING PRODUCT");
            return;
        }
        if (product.variantMode().equals("choose") && !product.variants().contains(state.selectedVariant) && !product.variants().isEmpty()) state.selectedVariant = product.variants().get(0);
        int top = y + 24;
        boolean saved = AntazonClientState.wishlist().contains(product.id());
        button(g, x, top, 40, "BACK", true, this::back);
        button(g, x + 44, top, 52, saved ? "SAVED" : "SAVE", true, () -> toggleSaved(product.id()));
        button(g, x + 100, top, 52, "SHARE", true, () -> screen.session.antmail.composeAntazonMail("Antazon product link", "ANTAZON PRODUCT LINK\nantazon://product/" + product.id()));
        String crumb = (state.returnMode.equals("saved") ? "SAVED" : "SHOP") + " > " + product.category().toUpperCase(Locale.ROOT);
        small(g, crumb, x + w - smallWidth(crumb) - 2, top + 6, w - 160, MUTED);
        g.fill(x, top + 21, x + w, top + 22, DIVIDER);
        int bodyTop = top + 25;
        int bodyBottom = y + h - 12;
        int boxWidth = Math.max(112, Math.min(132, w / 3));
        int boxX = x + w - boxWidth;
        renderBuyBox(g, product, boxX, bodyTop, boxWidth, bodyBottom - bodyTop);
        int detailX = x;
        int detailWidth = boxX - x - 12;
        screen.enableComputerScissor(g, detailX, bodyTop, detailX + detailWidth + 6, bodyBottom);
        int line = bodyTop - state.detailScroll;
        try {
            int imageSize = 44;
            g.fill(detailX, line, detailX + imageSize, line + imageSize, DARK_GREEN);
            var shown = state.galleryIndex >= 0 && state.galleryIndex < product.gallery().size()
                    ? product.gallery().get(state.galleryIndex) : product.thumbnail();
            if (product.variantMode().equals("choose")) shown = variantPreview(product, state.selectedVariant);
            renderAsset(g, shown, detailX + imageSize / 2, line + imageSize / 2, 40, product.greenTint(), product.renderMobFromSpawnEgg());
            screen.box(g, detailX, line, detailX + imageSize, line + imageSize, DIVIDER);
            int textX = detailX + imageSize + 7;
            int textWidth = detailWidth - imageSize - 7;
            int textLine = screen.wrap(g, name(product), textX, line + 1, textWidth, GREEN);
            if (product.reviews().isEmpty()) small(g, "NO REVIEWS YET", textX, textLine + 1, textWidth, MUTED);
            else {
                int starsEnd = drawStars(g, product.rating(), textX, textLine);
                small(g, String.format(Locale.ROOT, "%.1f · %d REVIEW%s", product.rating(), product.reviews().size(), product.reviews().size() == 1 ? "" : "S"),
                        starsEnd + 4, textLine + 1, textWidth - (starsEnd - textX) - 4, MUTED);
            }
            textLine += 11;
            if (product.dealActive()) {
                String dealLabel = Component.translatable(product.dealLabel()).getString().toUpperCase(Locale.ROOT);
                String dealLine = product.onSale()
                        ? dealLabel + " // " + product.dealDiscount() + "% OFF" + (product.dealEndsAt() > 0L ? " // ENDS IN " + countdown(product.dealEndsAt(), product.dealReal()) : "")
                        : dealLabel + " // DEAL LIMIT REACHED";
                small(g, dealLine, textX, textLine + 1, textWidth, product.onSale() ? GREEN : MUTED);
                textLine += 11;
                if (product.onSale() && product.dealRemaining() > 0) {
                    small(g, product.dealRemaining() + " LEFT AT THE DEAL PRICE, THEN REGULAR PRICE", textX, textLine + 1, textWidth, MUTED);
                    textLine += 11;
                }
            }
            int galleryY = line + imageSize + 4;
            if (!product.gallery().isEmpty()) {
                for (int index = -1; index < product.gallery().size(); index++) {
                    int thumbX = detailX + (index + 1) * 20;
                    if (thumbX + 18 > detailX + detailWidth) break;
                    var asset = index < 0 ? product.thumbnail() : product.gallery().get(index);
                    renderAsset(g, asset, thumbX + 9, galleryY + 9, 16, product.greenTint(), product.renderMobFromSpawnEgg());
                    screen.box(g, thumbX, galleryY, thumbX + 18, galleryY + 18, index == state.galleryIndex ? GREEN : DIVIDER);
                    int galleryIndex = index;
                    clippedHit(thumbX, galleryY, 18, 18, bodyTop, bodyBottom, () -> state.galleryIndex = galleryIndex);
                }
                galleryY += 22;
            }
            line = Math.max(textLine, galleryY) + 4;
            if (product.variantMode().equals("choose") && !product.variants().isEmpty()) {
                int selectedIndex = Math.max(0, product.variants().indexOf(state.selectedVariant));
                int selectorY = line;
                int selectorWidth = Math.min(detailWidth, 150);
                button(g, detailX, selectorY, 18, 16, "<", product.variants().size() > 1, false, () -> {
                    state.selectedVariant = product.variants().get((selectedIndex + product.variants().size() - 1) % product.variants().size());
                    state.buyConfirmUntil = 0L;
                });
                small(g, itemName(state.selectedVariant).toUpperCase(Locale.ROOT), detailX + 22, selectorY + 4, selectorWidth - 44, PALE_GREEN);
                button(g, detailX + selectorWidth - 18, selectorY, 18, 16, ">", product.variants().size() > 1, false, () -> {
                    state.selectedVariant = product.variants().get((selectedIndex + 1) % product.variants().size());
                    state.buyConfirmUntil = 0L;
                });
                line += 20;
            }
            if (product.locked()) {
                line = screen.renderTaskSectionLabel(g, "HOW TO UNLOCK", detailX, line, detailWidth) + 2;
                line = screen.wrap(g, product.unlockMode().equals("any") ? "Complete any of these tasks:" : "Complete these tasks:", detailX, line, detailWidth, PALE_GREEN) + 1;
                for (String taskId : product.unlockTasks()) {
                    var task = ComputerTasksClientState.get().stream()
                            .filter(row -> row.id().equals(taskId)).findFirst().orElse(null);
                    boolean known = task != null && task.visible();
                    String title = known ? AntOSPlayerText.apply(Component.translatable(task.title()).getString()) : taskId;
                    String prefix = task != null && task.complete() ? "[X] " : "[ ] ";
                    int rowY = line;
                    if (known && screen.hovered(detailX, rowY - 1, detailWidth, 11)) g.fill(detailX, rowY - 1, detailX + detailWidth, rowY + 10, HOVER_FILL);
                    line = screen.wrap(g, prefix + title + (known ? "  >" : ""), detailX + 2, line, detailWidth - 4, known ? GREEN : MUTED);
                    if (known) clippedHit(detailX, rowY - 1, detailWidth, line - rowY, bodyTop, bodyBottom, () -> openTask(taskId));
                }
                line += 4;
            }
            line = screen.renderTaskSectionLabel(g, "IN EVERY ORDER", detailX, line, detailWidth) + 2;
            if (product.variantMode().equals("all")) {
                for (String variant : product.variants()) {
                    small(g, number(product.quantity()) + "x " + itemName(variant), detailX + 20, line + 3, detailWidth - 24, PALE_GREEN);
                    line += 13;
                }
            } else if (product.variantMode().equals("random")) {
                line = screen.wrap(g, "One random item from the variant tag is selected at checkout.", detailX + 20, line + 2, detailWidth - 24, PALE_GREEN) + 2;
            } else if (product.variantMode().equals("choose") && !state.selectedVariant.isBlank()) {
                line = screen.wrap(g, number(product.quantity()) + "x " + itemName(state.selectedVariant), detailX + 20, line + 2, detailWidth - 24, PALE_GREEN) + 2;
            }
            for (var reward : product.rewards()) {
                int rowY = line;
                boolean expanded = state.recipeItem.equals(reward.item());
                if (screen.hovered(detailX, rowY, detailWidth, 16)) g.fill(detailX, rowY, detailX + detailWidth, rowY + 16, HOVER_FILL);
                boolean previousTint = screen.archiveGreenTint;
                screen.archiveGreenTint = product.greenTint();
                try {
                    screen.renderArchiveAsset(g, reward.item(), "", "", "", detailX + 8, rowY + 8, 14, 0.0F, 0.8F, product.renderMobFromSpawnEgg());
                } finally {
                    screen.archiveGreenTint = previousTint;
                }
                g.drawString(font, screen.trimToWidth(number((long) reward.count() * Math.max(1, product.quantity())) + "x " + itemName(reward.item()), detailWidth - 40),
                        detailX + 20, rowY + 4, PALE_GREEN, false);
                String toggle = expanded ? "HIDE" : "RECIPE";
                small(g, toggle, detailX + detailWidth - smallWidth(toggle) - 2, rowY + 5, 40, MUTED);
                clippedHit(detailX, rowY, detailWidth, 16, bodyTop, bodyBottom, () -> state.recipeItem = expanded ? "" : reward.item());
                line += 17;
                if (expanded) line = screen.renderTaskItemRecipe(g, reward.item(), detailX + 20, line, detailWidth - 22) + 3;
            }
            small(g, "+ 1x Chest // packaging you can ship back to Antazon", detailX + 20, line + 1, detailWidth - 20, MUTED);
            line += 14;
            String description = Component.translatable(product.description()).getString();
            if (product.variantMode().equals("choose") && !state.selectedVariant.isBlank()) description = description.replace("{item}", itemName(state.selectedVariant));
            if (!description.isBlank()) {
                line = screen.renderTaskSectionLabel(g, "DESCRIPTION", detailX, line, detailWidth) + 2;
                line = screen.wrap(g, description, detailX, line, detailWidth, PALE_GREEN) + 4;
            }
            line = screen.renderTaskSectionLabel(g, "REVIEWS", detailX, line, detailWidth) + 3;
            if (product.reviews().isEmpty()) {
                line = screen.wrap(g, "No reviews yet.", detailX, line, detailWidth, MUTED) + 2;
            } else {
                g.pose().pushPose();
                try {
                    g.pose().translate(detailX, line, 0.0F);
                    g.pose().scale(1.6F, 1.6F, 1.0F);
                    g.drawString(font, String.format(Locale.ROOT, "%.1f", product.rating()), 0, 0, GREEN, false);
                } finally {
                    g.pose().popPose();
                }
                small(g, product.reviews().size() + " REVIEW" + (product.reviews().size() == 1 ? "" : "S"), detailX, line + 16, 44, MUTED);
                int barX = detailX + 50;
                int barWidth = Math.max(10, detailWidth - 74);
                for (int stars = 5; stars >= 1; stars--) {
                    int count = stars;
                    long matching = product.reviews().stream().filter(review -> review.rating() == count).count();
                    int barY = line + (5 - stars) * 8;
                    chartSmall(g, Integer.toString(stars), barX, barY, 8, MUTED);
                    g.fill(barX + 7, barY + 1, barX + 7 + barWidth, barY + 5, DARK_GREEN);
                    int filled = (int) (barWidth * matching / Math.max(1, product.reviews().size()));
                    if (filled > 0) g.fill(barX + 7, barY + 1, barX + 7 + filled, barY + 5, GREEN);
                    chartSmall(g, Long.toString(matching), barX + barWidth + 10, barY, 14, MUTED);
                }
                line += 43;
                for (var review : product.reviews()) {
                    g.fill(detailX, line, detailX + detailWidth, line + 1, DIVIDER);
                    line += 4;
                    small(g, review.author().toUpperCase(Locale.ROOT) + (review.badge().isBlank() ? "" : " · " + review.badge()), detailX, line, detailWidth, MUTED);
                    line += 9;
                    int starsEnd = drawStars(g, review.rating(), detailX, line);
                    g.drawString(font, screen.trimToWidth(Component.translatable(review.title()).getString(), detailX + detailWidth - starsEnd - 4), starsEnd + 4, line, GREEN, false);
                    line += 11;
                    line = screen.wrap(g, Component.translatable(review.body()).getString(), detailX, line, detailWidth, PALE_GREEN) + 3;
                }
            }
            if (product.purchased() && !product.reviewed()) {
                button(g, detailX, line + 2, 104, 18, "WRITE A REVIEW", line + 20 <= bodyBottom && line + 2 >= bodyTop, false, () -> startReview(product.id()));
                line += 24;
            } else if (!product.purchased()) {
                small(g, "BUY THIS PRODUCT TO LEAVE A REVIEW", detailX, line + 2, detailWidth, DIM);
                line += 12;
            }
        } finally {
            g.disableScissor();
        }
        int contentHeight = line + state.detailScroll - bodyTop;
        int maximum = Math.max(0, contentHeight - (bodyBottom - bodyTop) + 4);
        state.detailScroll = Math.max(0, Math.min(maximum, state.detailScroll));
        scrollbar(g, detailX + detailWidth + 3, bodyTop, bodyBottom - bodyTop, bodyBottom - bodyTop, contentHeight, state.detailScroll, maximum,
                value -> state.detailScroll = value);
        scrollArea(detailX, bodyTop, detailWidth + 8, bodyBottom - bodyTop, delta -> state.detailScroll = Math.max(0, Math.min(maximum, state.detailScroll + delta * 12)));
        if (maximum > 0 && state.detailScroll < maximum) state.footer = "SCROLL FOR CONTENTS AND REVIEWS";
    }

    private void openTask(String taskId) {
        screen.open("TASKS");
        screen.tasks.select(taskId);
    }

    private void renderBuyBox(GuiGraphics g, AntazonClientState.ProductRow product, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        screen.box(g, x, y, x + w, y + h, DIVIDER);
        int innerX = x + 6;
        int innerWidth = w - 12;
        int bottom = y + h - 6;
        if (product.payments().isEmpty()) {
            g.drawString(font, "NOT FOR SALE", innerX, y + 8, MUTED, false);
            return;
        }
        state.paymentOption = Math.max(0, Math.min(product.payments().size() - 1, state.paymentOption));
        var payment = payment(product, state.paymentOption);
        String blocker = blocker(product);
        int purchasesLeft = purchasesLeft(product);
        state.quantity = Math.max(1, Math.min(Math.max(1, purchasesLeft), state.quantity));
        int units = state.quantity;
        int line = y + 6;
        g.pose().pushPose();
        try {
            g.pose().translate(innerX, line, 0.0F);
            g.pose().scale(1.5F, 1.5F, 1.0F);
            drawAmount(g, payment, payment.price(), 0, 0, blocker.isEmpty() ? GREEN : MUTED);
        } finally {
            g.pose().popPose();
        }
        line += 15;
        if (product.dealActive() && payment.amount() != payment.price()) {
            String was = "WAS " + amountText(payment, payment.amount());
            small(g, was, innerX, line, innerWidth - 32, MUTED);
            int strikeStart = innerX + smallWidth("WAS ");
            g.fill(strikeStart - 1, line + 3, innerX + Math.min(innerWidth - 32, smallWidth(was)) + 1, line + 4, MUTED);
            String off = "-" + product.dealDiscount() + "%";
            int offWidth = smallWidth(off) + 6;
            g.fill(x + w - 6 - offWidth, line - 2, x + w - 6, line + 8, GREEN);
            small(g, off, x + w - 3 - offWidth, line, offWidth, BLACK);
            line += 10;
        }
        int stepperY = bottom - 76;
        boolean blocked = !blocker.isEmpty();
        String availability = blocked ? blocker : stockLabel(product);
        g.drawString(font, screen.trimToWidth(availability, innerWidth), innerX, line + 2, blocked ? (product.locked() ? MUTED : RED) : GREEN, false);
        line += 12;
        if (product.remaining() >= 0 && product.restockAt() > 0L && line + 9 < stepperY) {
            small(g, "RESTOCKS IN " + countdown(product.restockAt(), product.restockReal()), innerX, line, innerWidth, MUTED);
            line += 9;
        }
        if (product.rotatesAt() > 0L && line + 9 < stepperY) {
            small(g, "CHANGES IN " + countdown(product.rotatesAt(), product.rotatesReal()), innerX, line, innerWidth, MUTED);
            line += 9;
        }
        if (product.limit() > 0 && line + 9 < stepperY) {
            int perPurchase = Math.max(1, product.quantity());
            String reset = switch (product.limitReset()) {
                case "minecraft_day" -> " TODAY";
                case "minecraft_week" -> " THIS WEEK";
                default -> "";
            };
            small(g, "BOUGHT " + product.limitUsed() / perPurchase + " OF " + product.limit() / perPurchase + reset, innerX, line, innerWidth, MUTED);
            line += 9;
        }
        if (product.payments().size() > 1 && line + 15 <= stepperY) {
            line += 1;
            int count = product.payments().size();
            boolean affordable = affordable(product, state.paymentOption, units);
            g.fill(innerX - 2, line, innerX + innerWidth + 2, line + 13, DARK_GREEN);
            screen.box(g, innerX - 2, line, innerX + innerWidth + 2, line + 13, DIVIDER);
            button(g, innerX - 2, line, 13, 13, "<", true, false, () -> {
                state.paymentOption = (state.paymentOption + count - 1) % count;
                state.buyConfirmUntil = 0L;
            });
            button(g, innerX + innerWidth - 11, line, 13, 13, ">", true, false, () -> {
                state.paymentOption = (state.paymentOption + 1) % count;
                state.buyConfirmUntil = 0L;
            });
            String optionLabel = "PAY " + (state.paymentOption + 1) + "/" + count + ": "
                    + (payment.type().equals("antcoins") ? AntOSPlayerText.currencyName().toUpperCase(Locale.ROOT) : itemName(payment.resource()).toUpperCase(Locale.ROOT));
            int labelWidth = Math.min(innerWidth - 28, smallWidth(optionLabel));
            small(g, optionLabel, innerX + innerWidth / 2 - labelWidth / 2, line + 3, innerWidth - 28, affordable ? PALE_GREEN : DIM);
            line += 15;
        }
        if (blocked) {
            String reason = product.locked() ? "FINISH THE UNLOCK TASKS TO BUY THIS" : blocker.startsWith("COOLDOWN") ? "YOU CAN BUY THIS AGAIN SOON"
                    : blocker.equals("SOLD OUT") ? product.restockAt() > 0L ? "CHECK BACK AFTER THE RESTOCK" : "OUT OF STOCK" : "YOU HAVE HIT THE PURCHASE LIMIT";
            screen.wrapLimited(g, reason, innerX, bottom - 62, innerWidth, bottom - 40, MUTED);
            button(g, innerX, bottom - 38, innerWidth, 18, "ADD TO CART", false, false, () -> { });
            button(g, innerX, bottom - 18, innerWidth, 18, blocker, false, true, () -> { });
            return;
        }
        small(g, "QTY", innerX, stepperY + 5, 20, MUTED);
        int stepperX = x + w - 6 - 58;
        button(g, stepperX, stepperY, 16, 16, "-", units > 1, false, () -> {
            state.quantity = Math.max(1, state.quantity - 1);
            state.buyConfirmUntil = 0L;
        });
        g.drawCenteredString(font, Integer.toString(units), stepperX + 29, stepperY + 4, GREEN);
        button(g, stepperX + 42, stepperY, 16, 16, "+", units < purchasesLeft, false, () -> {
            state.quantity = Math.min(Math.max(1, purchasesLeft(product)), state.quantity + 1);
            state.buyConfirmUntil = 0L;
        });
        if (purchasesLeft < 64) small(g, "MAX " + purchasesLeft, innerX + 20, stepperY + 5, 30, DIM);
        long total = total(product, payment, units);
        long owned = owned(payment);
        boolean affordable = owned >= total;
        int totalY = bottom - 56;
        small(g, "TOTAL", innerX, totalY + 1, 30, MUTED);
        int totalWidth = amountWidth(payment, total);
        if (totalWidth <= innerWidth - 30) drawAmount(g, payment, total, x + w - 6 - totalWidth, totalY, affordable ? GREEN : RED);
        else small(g, amountText(payment, total), innerX + 30, totalY + 1, innerWidth - 30, affordable ? GREEN : RED);
        String have = affordable ? "AFTER: " + amountText(payment, owned - total) : "YOU HAVE " + amountText(payment, owned);
        small(g, have, innerX, totalY + 9, innerWidth, affordable ? MUTED : RED);
        int option = state.paymentOption;
        button(g, innerX, bottom - 38, innerWidth, 18, "ADD TO CART", true, false, () -> addToCart(product, option, state.quantity));
        boolean confirming = System.currentTimeMillis() < state.buyConfirmUntil;
        String buyLabel = !affordable ? "NOT ENOUGH" : confirming ? "CONFIRM " + amountText(payment, total) : "BUY NOW";
        button(g, innerX, bottom - 18, innerWidth, 18, buyLabel, affordable, true, () -> {
            if (System.currentTimeMillis() < state.buyConfirmUntil) {
                state.buyConfirmUntil = 0L;
                try { ComputerNetworking.purchaseAntazon(ResourceLocation.parse(product.id()), Integer.toString(option), state.selectedVariant, state.quantity); }
                catch (RuntimeException ignored) { }
            } else {
                state.buyConfirmUntil = System.currentTimeMillis() + 4000L;
            }
        });
    }

    private void startReview(String productId) {
        state.productId = productId;
        state.mode = "review";
        state.reviewRating = 0;
        state.reviewTitle = "";
        state.reviewBody = "";
        state.focus = 3;
    }

    private void renderReview(GuiGraphics g, int x, int y, int w, int h) {
        var product = AntazonClientState.product(state.productId);
        if (product == null) {
            state.mode = "catalog";
            return;
        }
        int top = y + 24;
        button(g, x, top, 40, "BACK", true, () -> {
            state.mode = "product";
            state.focus = 0;
        });
        boolean ready = state.reviewRating > 0 && !state.reviewTitle.isBlank() && !state.reviewBody.isBlank();
        button(g, x + w - 84, top, 84, 18, "POST REVIEW", ready, true, () -> {
            try {
                ComputerNetworking.reviewAntazonProduct(ResourceLocation.parse(product.id()), state.reviewRating,
                        state.reviewTitle.trim(), state.reviewBody.trim());
            } catch (RuntimeException ignored) { }
            state.mode = "product";
            state.focus = 0;
        });
        g.drawString(font, screen.trimToWidth("REVIEW // " + name(product), w - 140), x + 46, top + 5, GREEN, false);
        g.fill(x, top + 21, x + w, top + 22, DIVIDER);
        int line = top + 28;
        small(g, "YOUR RATING", x, line + 2, 60, MUTED);
        int step = font.width("★") + 4;
        for (int star = 1; star <= 5; star++) {
            int starX = x + 62 + (star - 1) * step;
            boolean lit = star <= state.reviewRating || screen.hovered(x + 62, line - 2, step * 5, 12) && screen.mouseX() >= starX;
            g.drawString(font, "★", starX, line, lit ? GREEN : DIVIDER, false);
            int rating = star;
            hit(starX - 1, line - 2, step, 12, () -> state.reviewRating = rating);
        }
        line += 16;
        small(g, "TITLE", x, line, 40, MUTED);
        line += 9;
        searchField(g, x, line, w, state.reviewTitle, "SUM IT UP IN A FEW WORDS", 3, () -> state.reviewTitle = "");
        line += 22;
        small(g, "REVIEW", x, line, 40, MUTED);
        String counter = state.reviewBody.length() + "/512";
        small(g, counter, x + w - smallWidth(counter), line, 40, DIM);
        line += 9;
        int areaBottom = y + h - 14;
        boolean focused = state.focus == 4;
        g.fill(x, line, x + w, areaBottom, focused ? HOVER_FILL : DARK_GREEN);
        screen.box(g, x, line, x + w, areaBottom, focused ? PALE_GREEN : DIVIDER);
        String body = state.reviewBody.isEmpty() && !focused ? "What did you like? What could be better?" : state.reviewBody + (focused && screen.caretVisible() ? "|" : "");
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(body), w - 10);
        int visible = Math.max(1, (areaBottom - line - 8) / 11);
        int start = Math.max(0, lines.size() - visible);
        for (int index = start; index < lines.size(); index++) {
            g.drawString(font, lines.get(index), x + 5, line + 5 + (index - start) * 11, state.reviewBody.isEmpty() && !focused ? MUTED : PALE_GREEN, false);
        }
        hit(x, line, w, areaBottom - line, () -> state.focus = 4);
    }

    private void setCart(List<AntazonClientState.CartLine> lines) {
        state.checkoutConfirmUntil = 0L;
        AntazonClientState.setCart(lines);
    }

    private void renderCart(GuiGraphics g, int x, int y, int w, int h) {
        if (!AntazonClientState.checkout().isEmpty()) {
            renderCheckout(g, x, y, w, h);
            return;
        }
        List<AntazonClientState.CartLine> lines = AntazonClientState.cart();
        int top = y + 24;
        g.drawString(font, "YOUR CART", x, top + 5, GREEN, false);
        if (!AntazonClientState.hasCartSnapshot() && lines.isEmpty()) {
            renderLoading(g, x, y + h / 2, w, "LOADING CART");
            return;
        }
        if (lines.isEmpty()) {
            renderEmpty(g, x, y + h / 2 - 24, w, "YOUR CART IS EMPTY", "ADD SUPPLIES FROM THE SHOP TO BUY THEM TOGETHER", "BROWSE SUPPLIES", () -> switchTab(0));
            return;
        }
        button(g, x + w - 70, top, 70, 18, "CLEAR CART", true, false, () -> setCart(List.of()));
        int listTop = top + 22;
        int footerTop = y + h - 40;
        int rowHeight = 30;
        int visible = Math.max(1, (footerTop - listTop) / rowHeight);
        int maximum = Math.max(0, lines.size() - visible);
        state.cartScroll = Math.max(0, Math.min(maximum, state.cartScroll));
        int rowWidth = w - 6;
        for (int index = state.cartScroll; index < lines.size() && index < state.cartScroll + visible; index++) {
            var line = lines.get(index);
            int lineIndex = index;
            int rowY = listTop + (index - state.cartScroll) * rowHeight;
            var product = AntazonClientState.product(line.product());
            int removeX = x + rowWidth - 16;
            button(g, removeX, rowY + 6, 16, 16, "x", true, false, () -> {
                List<AntazonClientState.CartLine> updated = new ArrayList<>(AntazonClientState.cart());
                if (lineIndex < updated.size()) updated.remove(lineIndex);
                setCart(updated);
            });
            if (product == null) {
                g.drawString(font, screen.trimToWidth(line.product(), rowWidth - 60), x + 26, rowY + 5, MUTED, false);
                small(g, "NO LONGER AVAILABLE", x + 26, rowY + 17, rowWidth - 60, RED);
                g.fill(x, rowY + rowHeight - 1, x + rowWidth, rowY + rowHeight, DIVIDER);
                continue;
            }
            int stepperX = removeX - 62;
            int maxUnits = Math.max(1, Math.min(64, purchasesLeft(product)));
            button(g, stepperX, rowY + 6, 16, 16, "-", line.units() > 1, false, () -> updateCartLine(lineIndex, line.option(), line.units() - 1));
            g.drawCenteredString(font, Integer.toString(line.units()), stepperX + 29, rowY + 10, GREEN);
            button(g, stepperX + 42, rowY + 6, 16, 16, "+", line.units() < maxUnits, false, () -> updateCartLine(lineIndex, line.option(), line.units() + 1));
            renderAsset(g, variantPreview(product, line.variant()), x + 11, rowY + 14, 20, product.greenTint(), product.renderMobFromSpawnEgg());
            String blocker = blocker(product);
            var payment = payment(product, line.option());
            long total = payment == null ? 0L : total(product, payment, line.units());
            int amountRight = stepperX - 8;
            int amountWidth = payment == null ? 0 : amountWidth(payment, total);
            boolean affordable = affordable(product, line.option(), line.units());
            if (payment != null) drawAmount(g, payment, total, amountRight - amountWidth, rowY + 5, blocker.isEmpty() ? affordable ? GREEN : RED : DIM);
            g.drawString(font, screen.trimToWidth(variantName(product, line.variant()), Math.max(1, amountRight - amountWidth - 8 - (x + 26))), x + 26, rowY + 5, blocker.isEmpty() ? GREEN : MUTED, false);
            if (!blocker.isEmpty()) small(g, blocker, x + 26, rowY + 17, amountRight - x - 26, product.locked() ? MUTED : RED);
            else if (payment != null) {
                String payLabel = "PAY WITH " + (payment.type().equals("antcoins") ? AntOSPlayerText.currencyName().toUpperCase(Locale.ROOT) : itemName(payment.resource()).toUpperCase(Locale.ROOT))
                        + (product.payments().size() > 1 ? "  >" : "");
                int payWidth = Math.min(amountRight - x - 26, smallWidth(payLabel) + 6);
                boolean switchable = product.payments().size() > 1;
                if (switchable) {
                    boolean hover = screen.hovered(x + 24, rowY + 15, payWidth, 10);
                    g.fill(x + 24, rowY + 15, x + 24 + payWidth, rowY + 25, hover ? HOVER_FILL : DARK_GREEN);
                    hit(x + 24, rowY + 15, payWidth, 10, () -> updateCartLine(lineIndex, (line.option() + 1) % product.payments().size(), line.units()));
                }
                small(g, payLabel, x + 27, rowY + 17, payWidth - 4, switchable ? PALE_GREEN : MUTED);
            }
            hit(x, rowY, Math.max(1, amountRight - amountWidth - 8 - x), 14, () -> openProduct(product.id(), "cart"));
            g.fill(x, rowY + rowHeight - 1, x + rowWidth, rowY + rowHeight, DIVIDER);
        }
        scrollbar(g, x + w - 3, listTop, footerTop - listTop - 2, visible, lines.size(), state.cartScroll, maximum, value -> state.cartScroll = value);
        scrollArea(x, listTop, w, footerTop - listTop, delta -> state.cartScroll = Math.max(0, Math.min(maximum, state.cartScroll + delta)));
        Map<String, Long> totals = new LinkedHashMap<>();
        Map<String, AntazonClientState.PaymentRow> currencies = new LinkedHashMap<>();
        boolean anyBuyable = false;
        boolean allAffordable = true;
        for (var line : lines) {
            var product = AntazonClientState.product(line.product());
            if (product == null || !blocker(product).isEmpty()) continue;
            var payment = payment(product, line.option());
            if (payment == null) continue;
            anyBuyable = true;
            String key = payment.type() + ":" + payment.resource();
            totals.merge(key, total(product, payment, line.units()), Long::sum);
            currencies.putIfAbsent(key, payment);
        }
        for (Map.Entry<String, Long> entry : totals.entrySet()) if (owned(currencies.get(entry.getKey())) < entry.getValue()) allAffordable = false;
        g.fill(x, footerTop, x + w, footerTop + 1, GREEN);
        small(g, "SUBTOTAL", x, footerTop + 5, 50, MUTED);
        int amountX = x;
        int amountY = footerTop + 14;
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            var payment = currencies.get(entry.getKey());
            boolean enough = owned(payment) >= entry.getValue();
            if (amountX > x) {
                g.drawString(font, "+", amountX + 2, amountY, MUTED, false);
                amountX += 10;
            }
            if (amountX + amountWidth(payment, entry.getValue()) > x + w - 100) {
                g.drawString(font, "...", amountX, amountY, MUTED, false);
                break;
            }
            amountX = drawAmount(g, payment, entry.getValue(), amountX, amountY, enough ? GREEN : RED) + 2;
        }
        Long coins = totals.get("antcoins:antcoins");
        if (coins == null) coins = totals.entrySet().stream().filter(entry -> entry.getKey().startsWith("antcoins:")).map(Map.Entry::getValue).findFirst().orElse(null);
        if (coins != null) {
            long after = AntazonClientState.wallet() - coins;
            String afterLabel = after >= 0 ? "BALANCE AFTER: " + number(after) + " " + AntOSPlayerText.currencySymbol() : "NEED " + number(-after) + " MORE " + AntOSPlayerText.currencySymbol();
            small(g, afterLabel, x + 52, footerTop + 5, w - 160, after >= 0 ? MUTED : RED);
        }
        boolean confirming = System.currentTimeMillis() < state.checkoutConfirmUntil;
        boolean canCheckout = anyBuyable && allAffordable;
        button(g, x + w - 92, footerTop + 6, 92, 20, !anyBuyable ? "NOTHING TO BUY" : !allAffordable ? "NOT ENOUGH" : confirming ? "CONFIRM ORDER" : "CHECKOUT", canCheckout, true, () -> {
            if (System.currentTimeMillis() < state.checkoutConfirmUntil) {
                state.checkoutConfirmUntil = 0L;
                ComputerNetworking.checkoutAntazon();
            } else {
                state.checkoutConfirmUntil = System.currentTimeMillis() + 4000L;
            }
        });
        int units = lines.stream().mapToInt(AntazonClientState.CartLine::units).sum();
        state.footer = lines.size() + " LINE" + (lines.size() == 1 ? "" : "S") + " · " + units + " ITEM" + (units == 1 ? "" : "S") + " · ONE DELIVERY PER LINE";
    }

    private void updateCartLine(int index, int option, int units) {
        List<AntazonClientState.CartLine> lines = new ArrayList<>(AntazonClientState.cart());
        if (index < 0 || index >= lines.size()) return;
        var line = lines.get(index);
        lines.set(index, new AntazonClientState.CartLine(line.product(), option, Math.max(1, Math.min(64, units)), line.variant()));
        setCart(lines);
    }

    private void renderCheckout(GuiGraphics g, int x, int y, int w, int h) {
        List<AntazonClientState.CheckoutLine> results = AntazonClientState.checkout();
        long ordered = results.stream().filter(result -> result.status().equals("accepted")).count();
        long failed = results.size() - ordered;
        int top = y + 24;
        g.drawString(font, failed == 0 ? "ORDER CONFIRMED" : ordered == 0 ? "CHECKOUT FAILED" : "PARTIALLY ORDERED", x, top + 2, failed == 0 ? GREEN : ordered == 0 ? RED : PALE_GREEN, false);
        String summary = ordered + " ORDERED" + (failed > 0 ? " · " + failed + " LEFT IN YOUR CART" : "") + (ordered > 0 ? " · WATCH THE SKY FOR DELIVERIES" : "");
        small(g, summary, x, top + 13, w, MUTED);
        g.fill(x, top + 23, x + w, top + 24, DIVIDER);
        int rowY = top + 28;
        int buttonsY = y + h - 34;
        for (var result : results) {
            if (rowY + 20 > buttonsY - 2) {
                small(g, "+" + (results.size() - results.indexOf(result)) + " MORE", x, rowY + 2, w, MUTED);
                break;
            }
            boolean success = result.status().equals("accepted");
            var product = AntazonClientState.product(result.product());
            g.drawString(font, "●", x + 1, rowY + 5, success ? GREEN : RED, false);
            if (product != null) renderAsset(g, product.thumbnail(), x + 19, rowY + 9, 16, product.greenTint(), product.renderMobFromSpawnEgg());
            String right = success ? receiptText(result.receipt()) : failure(result.status());
            int rightWidth = Math.min(w / 2, font.width(right));
            g.drawString(font, screen.trimToWidth((product == null ? result.product() : variantName(product, result.variant())) + " X" + result.units(), w - rightWidth - 40), x + 30, rowY + 5, success ? PALE_GREEN : MUTED, false);
            g.drawString(font, screen.trimToWidth(right, rightWidth), x + w - rightWidth, rowY + 5, success ? GREEN : RED, false);
            g.fill(x, rowY + 19, x + w, rowY + 20, DIVIDER);
            rowY += 20;
        }
        button(g, x, buttonsY, 96, 20, "VIEW ORDERS", ordered > 0, false, () -> {
            AntazonClientState.clearCheckout();
            switchTab(2);
        });
        button(g, x + w / 2 - 48, buttonsY, 96, 20, failed > 0 ? "BACK TO CART" : "DONE", true, false, AntazonClientState::clearCheckout);
        button(g, x + w - 96, buttonsY, 96, 20, "KEEP SHOPPING", true, true, () -> {
            AntazonClientState.clearCheckout();
            switchTab(0);
        });
    }

    private String receiptText(String receipt) {
        if (receipt == null || receipt.isBlank() || receipt.equals("standard")) return "PAID";
        int separator = receipt.indexOf(' ');
        if (separator < 0) return receipt;
        String amount = receipt.substring(0, separator);
        String resource = receipt.substring(separator + 1);
        try { amount = number(Long.parseLong(amount)); } catch (NumberFormatException ignored) { }
        return amount + (resource.equals("antcoins") ? " " + AntOSPlayerText.currencySymbol() : " " + itemName(resource));
    }

    private String age(long gameTime) {
        long days = Math.max(0L, (gameTime() - gameTime) / 24000L);
        return days == 0L ? "TODAY" : days == 1L ? "1 DAY AGO" : days + " DAYS AGO";
    }

    private void renderOrders(GuiGraphics g, int x, int y, int w, int h) {
        List<AntazonClientState.OrderRow> orders = AntazonClientState.orders();
        if (state.orderSelection >= 0 && state.orderSelection < orders.size()) {
            renderOrderDetail(g, orders.get(state.orderSelection), x, y, w, h);
            return;
        }
        state.orderSelection = -1;
        int top = y + 24;
        g.drawString(font, "ORDER HISTORY", x, top + 5, GREEN, false);
        if (orders.isEmpty()) {
            renderEmpty(g, x, y + h / 2 - 24, w, "NO ORDERS YET", "EVERYTHING YOU BUY SHOWS UP HERE", "BROWSE SUPPLIES", () -> switchTab(0));
            return;
        }
        int listTop = top + 20;
        int listBottom = y + h - 12;
        int visible = Math.max(1, (listBottom - listTop) / ROW);
        int maximum = Math.max(0, orders.size() - visible);
        state.ordersScroll = Math.max(0, Math.min(maximum, state.ordersScroll));
        int rowWidth = w - 6;
        for (int index = state.ordersScroll; index < orders.size() && index < state.ordersScroll + visible; index++) {
            var order = orders.get(index);
            var product = AntazonClientState.product(order.product());
            int rowY = listTop + (index - state.ordersScroll) * ROW;
            if (screen.hovered(x, rowY, rowWidth, ROW - 1)) g.fill(x, rowY, x + rowWidth, rowY + ROW - 1, HOVER_FILL);
            if (product != null) renderAsset(g, product.thumbnail(), x + 11, rowY + 13, 20, product.greenTint(), product.renderMobFromSpawnEgg());
            String age = age(order.gameTime());
            int ageWidth = smallWidth(age);
            small(g, age, x + rowWidth - ageWidth - 4, rowY + 5, ageWidth + 2, MUTED);
            int purchases = product == null ? order.units() : Math.max(1, order.units() / Math.max(1, product.quantity()));
            String name = (product == null ? prettyItem(order.product()) : name(product)) + " X" + purchases;
            g.drawString(font, screen.trimToWidth(name, rowWidth - ageWidth - 36), x + 26, rowY + 4, GREEN, false);
            small(g, "PAID " + receiptText(order.option()) + " · " + order.status(), x + 26, rowY + 15, rowWidth - 30, MUTED);
            g.fill(x, rowY + ROW - 1, x + rowWidth, rowY + ROW, DIVIDER);
            int selection = index;
            hit(x, rowY, rowWidth, ROW - 1, () -> {
                state.orderSelection = selection;
                state.detailScroll = 0;
            });
        }
        scrollbar(g, x + w - 3, listTop, listBottom - listTop, visible, orders.size(), state.ordersScroll, maximum, value -> state.ordersScroll = value);
        scrollArea(x, listTop, w, listBottom - listTop, delta -> state.ordersScroll = Math.max(0, Math.min(maximum, state.ordersScroll + delta)));
        state.footer = "SHOWING " + (state.ordersScroll + 1) + "-" + Math.min(orders.size(), state.ordersScroll + visible) + " OF " + orders.size() + " ORDERS";
    }

    private void renderOrderDetail(GuiGraphics g, AntazonClientState.OrderRow order, int x, int y, int w, int h) {
        var product = AntazonClientState.product(order.product());
        int top = y + 24;
        button(g, x, top, 40, "BACK", true, () -> state.orderSelection = -1);
        int purchases = product == null ? order.units() : Math.max(1, order.units() / Math.max(1, product.quantity()));
        if (product != null) {
            int option = 0;
            int separator = order.option().indexOf(' ');
            String resource = separator < 0 ? "" : order.option().substring(separator + 1);
            for (int index = 0; index < product.payments().size(); index++) {
                var payment = product.payments().get(index);
                if (payment.resource().equals(resource) || payment.type().equals("antcoins") && resource.equals("antcoins")) option = index;
            }
            int buyAgainOption = option;
            button(g, x + 44, top, 72, 18, "BUY AGAIN", blocker(product).isEmpty(), true, () -> {
                addToCart(product, buyAgainOption, Math.min(purchases, Math.max(1, purchasesLeft(product))));
                switchTab(-1);
            });
            button(g, x + 120, top, 84, "VIEW PRODUCT", true, () -> openProduct(product.id(), "orders"));
            if (product.purchased() && !product.reviewed()) button(g, x + 208, top, 92, "WRITE A REVIEW", true, () -> startReview(product.id()));
        }
        g.fill(x, top + 21, x + w, top + 22, DIVIDER);
        int line = top + 27;
        if (product != null) renderAsset(g, product.thumbnail(), x + 16, line + 16, 32, product.greenTint(), product.renderMobFromSpawnEgg());
        g.drawString(font, screen.trimToWidth(product == null ? prettyItem(order.product()) : name(product), w - 44), x + 40, line + 4, GREEN, false);
        small(g, "ORDERED DAY " + (order.gameTime() / 24000L + 1) + " · " + age(order.gameTime()), x + 40, line + 17, w - 44, MUTED);
        line += 38;
        line = screen.renderTaskSectionLabel(g, "ORDER", x, line, w) + 2;
        String[][] rows = {{"QUANTITY", Integer.toString(purchases)}, {"PAID", receiptText(order.option())}, {"STATUS", order.status()}};
        for (String[] row : rows) {
            small(g, row[0], x, line + 1, 80, MUTED);
            g.drawString(font, screen.trimToWidth(row[1], w - 90), x + w - Math.min(w - 90, font.width(row[1])), line, PALE_GREEN, false);
            line += 11;
        }
        if (product != null && !product.rewards().isEmpty()) {
            line = screen.renderTaskSectionLabel(g, "DELIVERED", x, line + 4, w) + 2;
            for (var reward : product.rewards()) {
                if (line + 11 > y + h - 12) {
                    small(g, "...", x, line, 20, MUTED);
                    break;
                }
                screen.renderArchiveAsset(g, reward.item(), "", "", "", x + 6, line + 4, 10, 0.0F, 0.7F, product.renderMobFromSpawnEgg());
                g.drawString(font, screen.trimToWidth(number((long) reward.count() * order.units()) + "x " + itemName(reward.item()), w - 20), x + 16, line, PALE_GREEN, false);
                line += 11;
            }
        }
    }

    private void renderSaved(GuiGraphics g, int x, int y, int w, int h) {
        List<String> saved = AntazonClientState.wishlist();
        int top = y + 24;
        g.drawString(font, "SAVED FOR LATER", x, top + 5, GREEN, false);
        if (saved.isEmpty()) {
            renderEmpty(g, x, y + h / 2 - 24, w, "NOTHING SAVED YET", "TAP SAVE ON ANY PRODUCT TO WATCH IT FOR DEALS", "BROWSE SUPPLIES", () -> switchTab(0));
            return;
        }
        button(g, x + w - 76, top, 76, 18, "SHARE LIST", true, false, () -> screen.session.antmail.composeAntazonMail("Antazon wishlist", wishlistBody()));
        int listTop = top + 22;
        int listBottom = y + h - 12;
        int visible = Math.max(1, (listBottom - listTop) / ROW);
        int maximum = Math.max(0, saved.size() - visible);
        state.savedScroll = Math.max(0, Math.min(maximum, state.savedScroll));
        int rowWidth = w - 6;
        int buttonWidth = 46;
        for (int index = state.savedScroll; index < saved.size() && index < state.savedScroll + visible; index++) {
            String productId = saved.get(index);
            var product = AntazonClientState.product(productId);
            int rowY = listTop + (index - state.savedScroll) * ROW;
            int rowContentWidth = rowWidth - buttonWidth - 6;
            if (product == null) {
                g.drawString(font, screen.trimToWidth(prettyItem(productId), rowContentWidth - 30), x + 26, rowY + 4, MUTED, false);
                small(g, "NO LONGER AVAILABLE", x + 26, rowY + 15, rowContentWidth - 30, RED);
                button(g, x + rowWidth - buttonWidth, rowY + 4, buttonWidth, 18, "REMOVE", true, false, () -> toggleSaved(productId));
                g.fill(x, rowY + ROW - 1, x + rowWidth, rowY + ROW, DIVIDER);
                continue;
            }
            renderProductRow(g, product, x, rowY, rowContentWidth, screen.hovered(x, rowY, rowContentWidth, ROW - 1));
            g.fill(x + rowContentWidth, rowY + ROW - 1, x + rowWidth, rowY + ROW, DIVIDER);
            hit(x, rowY, rowContentWidth, ROW - 1, () -> openProduct(product.id(), "saved"));
            boolean buyable = blocker(product).isEmpty();
            button(g, x + rowWidth - buttonWidth, rowY + 4, buttonWidth, 18, "+ CART", buyable, true, () -> addToCart(product, bestOption(product, 1), 1));
        }
        scrollbar(g, x + w - 3, listTop, listBottom - listTop, visible, saved.size(), state.savedScroll, maximum, value -> state.savedScroll = value);
        scrollArea(x, listTop, w, listBottom - listTop, delta -> state.savedScroll = Math.max(0, Math.min(maximum, state.savedScroll + delta)));
        long onSale = saved.stream().map(AntazonClientState::product)
                .filter(product -> product != null && product.onSale()).count();
        state.footer = saved.size() + " SAVED" + (onSale > 0 ? " · " + onSale + " ON SALE NOW" : " · WE'LL FLAG ANY DEALS ON THE DESKTOP");
    }

    private Set<String> activeSavedDeals() {
        List<String> wishlist = AntazonClientState.wishlist();
        if (wishlist.isEmpty()) return Set.of();
        Set<String> saved = new HashSet<>(wishlist);
        Set<String> deals = new HashSet<>();
        for (var product : AntazonClientState.products()) {
            if (product.onSale() && saved.contains(product.id())) deals.add(product.id() + "@" + product.dealKey());
        }
        return deals;
    }

    boolean hasUnseenDeals() {
        return !state.seenDeals.containsAll(activeSavedDeals());
    }

    private void markDealsSeen() {
        state.seenDeals.addAll(activeSavedDeals());
    }

    private void renderSell(GuiGraphics g, int x, int y, int w, int h) {
        int top = y + 24;
        chip(g, x, top, 82, !state.priceGuide, "CONTAINER", () -> {
            state.priceGuide = false;
            ComputerNetworking.requestAntazonSellState();
        });
        chip(g, x + 86, top, 82, state.priceGuide, "PRICE GUIDE", () -> {
            state.priceGuide = true;
            ComputerNetworking.requestAntazonPrices();
        });
        button(g, x + w - 64, top, 64, 17, "REFRESH", true, false, () -> {
            state.sellConfirm = false;
            AntazonClientState.clearSellFeedback();
            ComputerNetworking.requestAntazonSellState();
            ComputerNetworking.requestAntazonPrices();
        });
        if (state.priceGuide) renderPriceGuide(g, x, top + 22, w, y + h - 12 - (top + 22));
        else renderChest(g, x, top + 22, w, y + h - 12 - (top + 22));
    }

    private void renderChest(GuiGraphics g, int x, int y, int w, int h) {
        List<AntazonClientState.SellRow> rows = AntazonClientState.sellRows();
        String status = AntazonClientState.status();
        String feedback = AntazonClientState.sellFeedback();
        boolean noChest = status.equals("CHEST_REQUIRED") || status.equals("CRATE_REQUIRED") || status.equals("CRATE_UNAVAILABLE");
        boolean launched = feedback.equals("SHIPMENT_LAUNCHED") || feedback.equals("SHIPMENT_ACCEPTED");
        boolean partialShipment = status.equals("PARTIAL_SHIPMENT");
        boolean ready = (status.isBlank() || partialShipment) && !rows.isEmpty()
                && rows.stream().allMatch(row -> row.sellable() && !row.locked())
                && rows.stream().anyMatch(row -> row.acceptedCount() > 0);
        int step = noChest ? 1 : launched || state.sellConfirm ? 3 : 2;
        String[] steps = {"1 PLACE CONTAINER", "2 REVIEW", "3 SHIP"};
        int stepX = x;
        for (int index = 0; index < steps.length; index++) {
            boolean current = index + 1 == step;
            boolean done = index + 1 < step;
            g.drawString(font, steps[index], stepX, y + 1, current ? GREEN : done ? PALE_GREEN : DIM, false);
            if (current) g.fill(stepX, y + 10, stepX + font.width(steps[index]), y + 11, GREEN);
            stepX += font.width(steps[index]) + 6;
            if (index < steps.length - 1) {
                g.drawString(font, ">", stepX, y + 1, DIM, false);
                stepX += font.width(">") + 6;
            }
        }
        String message;
        int messageColor = PALE_GREEN;
        if (feedback.equals("SHIPMENT_LAUNCHED")) {
            message = "FREIGHT LAUNCHED // " + number(AntazonClientState.sellAmount()) + " " + AntOSPlayerText.currencySymbol() + " ARRIVES WHEN IT LANDS";
            messageColor = (System.currentTimeMillis() / 250L) % 2L == 0L ? GREEN : PALE_GREEN;
        } else if (!feedback.isBlank()) {
            message = sellMessage(feedback);
            messageColor = feedback.contains("FAILED") || feedback.equals("CRATE_CHANGED") || feedback.equals("UNAUTHORIZED") ? RED : GREEN;
        } else if (status.equals("CHEST_REQUIRED") || status.equals("CRATE_REQUIRED")) {
            message = "PLACE A SHIPPING CONTAINER NEAR THIS COMPUTER, THEN FILL IT WITH ITEMS TO SELL";
        } else if (status.equals("CRATE_UNAVAILABLE")) {
            message = "YOUR SHIPPING CONTAINER IS OUT OF RANGE // VISIT IT OR PLACE A NEW ONE";
        } else if (status.equals("CRATE_EMPTY")) {
            message = "THE CONTAINER IS EMPTY // ADD ITEMS OR USE THE PRICE GUIDE";
        } else if (status.equals("UNSUPPORTED_ITEMS")) {
            message = "REMOVE THE ITEMS ANTAZON DOES NOT BUY BEFORE SHIPPING";
            messageColor = RED;
        } else if (status.equals("SELL_LIMIT_REACHED") || status.equals("SELL_LIMIT_EXCEEDED")) {
            message = "SELL LIMIT REACHED // WAIT FOR THE RESET BEFORE SHIPPING THESE ITEMS";
            messageColor = RED;
        } else if (partialShipment) {
            message = state.sellConfirm ? "CONFIRM PARTIAL SALE // ALLOWED ITEMS SHIP; EXTRA ITEMS DROP HERE"
                    : "PARTIAL SALE // ALLOWED ITEMS SHIP; EXTRA ITEMS DROP HERE";
            messageColor = state.sellConfirm ? GREEN : PALE_GREEN;
        } else if (status.equals("TASK_LOCKED")) {
            message = "LOCKED // FINISH THE REQUIRED TASKS TO SELL THESE ITEMS";
            messageColor = RED;
        } else if (status.equals("UNAUTHORIZED")) {
            message = failure("unauthorized");
            messageColor = RED;
        } else if (state.sellConfirm) {
            message = "THE CONTAINER AND EVERYTHING IN IT WILL BE SHIPPED. CONFIRM TO LAUNCH";
            messageColor = GREEN;
        } else if (ready) {
            message = "READY TO SHIP // CHECK THE MANIFEST BELOW";
        } else {
            message = rows.isEmpty() ? "CHECKING YOUR CONTAINER" : sellMessage(status);
        }
        small(g, message, x, y + 15, w, messageColor);
        int listTop = y + 27;
        int footerTop = y + h - 24;
        if (rows.isEmpty()) {
            if (noChest) renderEmpty(g, x, listTop + 16, w, "NO SHIPPING CONTAINER", "SELL BY SHIPPING A CONTAINER OF ITEMS FOR " + AntOSPlayerText.currencyName().toUpperCase(Locale.ROOT), "OPEN PRICE GUIDE", () -> {
                state.priceGuide = true;
                ComputerNetworking.requestAntazonPrices();
            });
        } else {
            int rowHeight = 27;
            int visible = Math.max(1, (footerTop - listTop - 2) / rowHeight);
            int maximum = Math.max(0, rows.size() - visible);
            state.sellScroll = Math.max(0, Math.min(maximum, state.sellScroll));
            int rowWidth = w - 6;
            for (int index = state.sellScroll; index < rows.size() && index < state.sellScroll + visible; index++) {
                var row = rows.get(index);
                int rowY = listTop + (index - state.sellScroll) * rowHeight;
                if (!row.sellable() || row.overLimit() || row.locked()) g.fill(x, rowY, x + rowWidth, rowY + rowHeight - 1, 0xFF1E1010);
                renderAsset(g, new AntazonClientState.PreviewAsset(row.item(), ""), x + 9, rowY + 9, 16, row.greenTint(), row.renderMobFromSpawnEgg());
                String value = !row.sellable() ? "NOT ACCEPTED" : row.locked() ? "LOCKED"
                        : row.acceptedCount() == 0 && row.overLimit() ? "LIMIT REACHED" : "+" + number(row.value());
                boolean accepted = row.sellable() && !row.locked() && row.acceptedCount() > 0;
                int valueWidth = font.width(value) + (accepted ? 9 : 0);
                g.drawString(font, value, x + rowWidth - valueWidth - 2, rowY + 4, accepted ? GREEN : RED, false);
                if (accepted) coin(g, x + rowWidth - 9, rowY + 4);
                String itemCount = itemName(row.item()) + " x" + row.count()
                        + (row.returnedCount() > 0 && row.acceptedCount() > 0 ? " // SELL " + row.acceptedCount() : "");
                g.drawString(font, screen.trimToWidth(itemCount, rowWidth - valueWidth - 32), x + 22, rowY + 4, row.sellable() ? PALE_GREEN : MUTED, false);
                if (row.locked()) small(g, "LOCKED // " + taskTitles(row.unlockTasks()), x + 22, rowY + 15, rowWidth - 28, RED);
                else if (row.returnedCount() > 0) small(g, "DROP " + number(row.returnedCount()) + " HERE // " + number(row.remaining()) + " / " + number(row.limit()) + " LEFT " + resetLabel(row.limitReset()), x + 22, rowY + 15, rowWidth - 28, MUTED);
                else if (row.limit() > 0) small(g, number(row.remaining()) + " / " + number(row.limit()) + " LEFT " + resetLabel(row.limitReset()), x + 22, rowY + 15, rowWidth - 28, row.overLimit() ? RED : MUTED);
                g.fill(x, rowY + rowHeight - 1, x + rowWidth, rowY + rowHeight, DIVIDER);
            }
            scrollbar(g, x + w - 3, listTop, footerTop - listTop - 2, visible, rows.size(), state.sellScroll, maximum, value -> state.sellScroll = value);
            scrollArea(x, listTop, w, footerTop - listTop, delta -> state.sellScroll = Math.max(0, Math.min(maximum, state.sellScroll + delta)));
        }
        long total = rows.stream().filter(AntazonClientState.SellRow::sellable)
                .mapToLong(AntazonClientState.SellRow::value).sum();
        g.fill(x, footerTop, x + w, footerTop + 1, GREEN);
        small(g, status.equals("UNSUPPORTED_ITEMS") || partialShipment ? "PAYOUT FOR ACCEPTED ITEMS" : "PAYOUT", x, footerTop + 4, 140, MUTED);
        String payout = number(total);
        g.drawString(font, payout, x, footerTop + 13, total > 0 ? GREEN : DIM, false);
        coin(g, x + font.width(payout) + 2, footerTop + 13);
        String ship = launched ? "IN TRANSIT" : state.sellConfirm ? "CONFIRM SHIPMENT" : "SHIP CRATE";
        button(g, x + w - 112, footerTop + 4, 112, 18, ship, ready && !launched, true, () -> {
            if (state.sellConfirm) {
                ComputerNetworking.sellAntazon();
                state.sellConfirm = false;
                state.walletRefreshTicks = 100;
            } else {
                state.sellConfirm = true;
            }
        });
        if (state.sellConfirm) button(g, x + w - 172, footerTop + 4, 56, 18, "CANCEL", true, false, () -> state.sellConfirm = false);
    }

    private void renderPriceGuide(GuiGraphics g, int x, int y, int w, int h) {
        searchField(g, x, y, w, state.priceSearch, "SEARCH WHAT ANTAZON BUYS", 2, () -> {
            state.priceSearch = "";
            state.priceScroll = 0;
        });
        String query = state.priceSearch.trim().toLowerCase(Locale.ROOT);
        List<AntazonClientState.PriceRow> prices = AntazonClientState.prices().stream()
                .filter(price -> query.isBlank() || itemName(price.item()).toLowerCase(Locale.ROOT).contains(query) || price.item().contains(query)).toList();
        int listTop = y + 21;
        var selected = AntazonClientState.prices().stream()
                .filter(price -> price.item().equals(state.priceItem)).findFirst().orElse(null);
        int taskRows = selected != null && selected.locked() ? Math.max(1, selected.unlockTasks().size()) : 0;
        int panelHeight = selected != null && selected.locked() ? Math.min(h / 2, 34 + taskRows * 11) : 24;
        int panelTop = y + h - panelHeight;
        if (prices.isEmpty()) {
            renderEmpty(g, x, listTop + 20, w, AntazonClientState.prices().isEmpty() ? "NO PRICES LOADED" : "NO MATCHING ITEMS",
                    "ANTAZON ONLY BUYS ITEMS LISTED HERE", null, null);
        } else {
            int rowHeight = 27;
            int visible = Math.max(1, (panelTop - listTop - 2) / rowHeight);
            int maximum = Math.max(0, prices.size() - visible);
            state.priceScroll = Math.max(0, Math.min(maximum, state.priceScroll));
            int rowWidth = w - 6;
            for (int index = state.priceScroll; index < prices.size() && index < state.priceScroll + visible; index++) {
                var price = prices.get(index);
                int rowY = listTop + (index - state.priceScroll) * rowHeight;
                boolean isSelected = price.item().equals(state.priceItem);
                if (isSelected || screen.hovered(x, rowY, rowWidth, rowHeight - 1)) g.fill(x, rowY, x + rowWidth, rowY + rowHeight - 1, HOVER_FILL);
                if (isSelected) g.fill(x, rowY, x + 2, rowY + rowHeight - 1, GREEN);
                renderAsset(g, new AntazonClientState.PreviewAsset(price.item(), ""), x + 11, rowY + 8, 14, price.greenTint(), price.renderMobFromSpawnEgg());
                String each = price.locked() ? "LOCKED" : number(price.value());
                int eachWidth = font.width(each) + (price.locked() ? 0 : 9 + smallWidth(" / EA"));
                g.drawString(font, each, x + rowWidth - eachWidth - 2, rowY + 5, price.locked() ? RED : GREEN, false);
                if (!price.locked()) {
                    coin(g, x + rowWidth - eachWidth + font.width(each), rowY + 5);
                    small(g, " / EA", x + rowWidth - smallWidth(" / EA") - 2, rowY + 6, 30, MUTED);
                }
                int owned = inventoryCount(price.item());
                String have = owned > 0 ? "YOU HAVE " + owned : "";
                int haveWidth = have.isEmpty() ? 0 : smallWidth(have) + 8;
                g.drawString(font, screen.trimToWidth(itemName(price.item()), rowWidth - eachWidth - haveWidth - 30), x + 22, rowY + 3, PALE_GREEN, false);
                if (price.locked()) small(g, "LOCKED // " + taskTitles(price.unlockTasks()), x + 22, rowY + 14, rowWidth - 28, RED);
                else if (price.limit() > 0) small(g, number(price.remaining()) + " / " + number(price.limit()) + " LEFT " + resetLabel(price.limitReset()), x + 22, rowY + 14,
                        rowWidth - eachWidth - haveWidth - 30, MUTED);
                if (!have.isEmpty()) small(g, have, x + rowWidth - eachWidth - haveWidth - 2, rowY + 6, haveWidth, MUTED);
                hit(x, rowY, rowWidth, rowHeight - 1, () -> {
                    state.priceItem = price.item();
                    state.priceQuantity = Math.max(1, Math.min(64, Math.min(state.priceQuantity, Math.max(1, inventoryCount(price.item())))));
                });
            }
            scrollbar(g, x + w - 3, listTop, panelTop - listTop - 2, visible, prices.size(), state.priceScroll, maximum, value -> state.priceScroll = value);
            scrollArea(x, listTop, w, panelTop - listTop, delta -> state.priceScroll = Math.max(0, Math.min(maximum, state.priceScroll + delta)));
        }
        g.fill(x, panelTop, x + w, panelTop + 1, GREEN);
        if (selected == null) {
            small(g, "PICK AN ITEM TO MOVE IT FROM YOUR INVENTORY INTO THE SHIPPING CONTAINER", x, panelTop + 9, w, MUTED);
            return;
        }
        if (selected.locked()) {
            small(g, "LOCKED // " + number(selected.value()) + " " + AntOSPlayerText.currencyName().toUpperCase(Locale.ROOT) + " EACH // "
                    + (selected.unlockMode().equals("any") ? "COMPLETE ANY TASK:" : "COMPLETE THESE TASKS:"), x, panelTop + 5, w, RED);
            int line = panelTop + 16;
            for (String taskId : selected.unlockTasks()) {
                var task = ComputerTasksClientState.get().stream().filter(row -> row.id().equals(taskId)).findFirst().orElse(null);
                boolean known = task != null && task.visible();
                String title = known ? AntOSPlayerText.apply(Component.translatable(task.title()).getString()) : taskId;
                line = screen.wrap(g, (task != null && task.complete() ? "[X] " : "[ ] ") + title, x + 2, line, w - 4, known ? PALE_GREEN : MUTED) + 1;
            }
            return;
        }
        int owned = inventoryCount(selected.item());
        state.priceQuantity = Math.max(1, Math.min(64, state.priceQuantity));
        int quantity = state.priceQuantity;
        int buttonWidth = 96;
        int stepperX = x + w - buttonWidth - 4 - 98;
        g.drawString(font, screen.trimToWidth(itemName(selected.item()), stepperX - x - 6), x, panelTop + 5, PALE_GREEN, false);
        String value = "= " + number((long) selected.value() * quantity);
        small(g, value, x, panelTop + 15, stepperX - x - 12, GREEN);
        coin(g, x + smallWidth(value) + 2, panelTop + 15);
        button(g, stepperX, panelTop + 5, 16, 16, "-", quantity > 1, false, () -> state.priceQuantity = Math.max(1, state.priceQuantity - 1));
        g.drawCenteredString(font, Integer.toString(quantity), stepperX + 28, panelTop + 9, GREEN);
        button(g, stepperX + 40, panelTop + 5, 16, 16, "+", quantity < 64, false, () -> state.priceQuantity = Math.min(64, state.priceQuantity + 1));
        button(g, stepperX + 60, panelTop + 5, 34, 16, "MAX", owned > 0, false, () -> state.priceQuantity = Math.max(1, Math.min(64, inventoryCount(selected.item()))));
        button(g, x + w - buttonWidth, panelTop + 4, buttonWidth, 18, owned >= quantity ? "ADD TO CHEST" : "NOT ENOUGH", owned >= quantity, true, () -> {
            try {
                ComputerNetworking.prepareAntazonShipment(ResourceLocation.parse(selected.item()), state.priceQuantity);
            } catch (RuntimeException ignored) { }
        });
    }

    private void renderOnboarding(GuiGraphics g, int x, int y, int w, int h) {
        String[][] pages = {
                {"WELCOME TO ANTAZON", "Supplies for the colony, delivered by air.", "Orders land at your linked shipping container, so keep it nearby when collecting deliveries."},
                {"SHOP", "Search, filter for deals and compare prices.", "Pick how you pay: " + AntOSPlayerText.currencyName() + " or items. Save products to get flagged when they go on sale."},
                {"CART + ORDERS", "Your cart follows your Anternet account to any computer.", "Check out in one go and track every delivery in Orders."},
                {"SELL", "Fill a shipping container with items Antazon buys and ship it for " + AntOSPlayerText.currencyName() + ".", "The price guide shows what everything is worth."}
        };
        int page = Math.max(0, Math.min(pages.length - 1, state.onboardingPage));
        state.onboardingPage = page;
        int cardX = x + 16;
        int cardY = y + 12;
        int cardWidth = w - 32;
        int cardBottom = y + h - 8;
        g.fill(cardX, cardY, cardX + cardWidth, cardBottom, PANEL);
        screen.box(g, cardX, cardY, cardX + cardWidth, cardBottom, GREEN);
        small(g, "STEP " + (page + 1) + " OF " + pages.length, cardX + 14, cardY + 12, 80, MUTED);
        g.pose().pushPose();
        try {
            g.pose().translate(cardX + 14, cardY + 24, 0.0F);
            g.pose().scale(1.5F, 1.5F, 1.0F);
            g.drawString(font, pages[page][0], 0, 0, GREEN, false);
        } finally {
            g.pose().popPose();
        }
        int line = screen.wrap(g, pages[page][1], cardX + 14, cardY + 46, cardWidth - 28, PALE_GREEN) + 4;
        screen.wrap(g, pages[page][2], cardX + 14, line, cardWidth - 28, MUTED);
        int dotsX = cardX + cardWidth / 2 - pages.length * 5;
        for (int index = 0; index < pages.length; index++) g.fill(dotsX + index * 10, cardBottom - 34, dotsX + index * 10 + 6, cardBottom - 31, index == page ? GREEN : DIVIDER);
        int buttonY = cardBottom - 26;
        button(g, cardX + 12, buttonY, 56, 18, "SKIP", true, false, ComputerNetworking::completeAntazonOnboarding);
        if (page > 0) button(g, cardX + cardWidth - 150, buttonY, 56, 18, "BACK", true, false, () -> state.onboardingPage--);
        boolean last = page == pages.length - 1;
        button(g, cardX + cardWidth - 88, buttonY, 76, 18, last ? "START" : "NEXT", true, true, () -> {
            if (last) {
                state.onboardingPage = 0;
                ComputerNetworking.completeAntazonOnboarding();
            } else {
                state.onboardingPage++;
            }
        });
    }

    boolean type(char codePoint) {
        if (state.focus == 0 || codePoint < 32) return false;
        switch (state.focus) {
            case 1 -> {
                if (state.search.length() < 48) state.search += codePoint;
                state.scroll = 0;
            }
            case 2 -> {
                if (state.priceSearch.length() < 32) state.priceSearch += codePoint;
                state.priceScroll = 0;
            }
            case 3 -> {
                if (state.reviewTitle.length() < 80) state.reviewTitle += codePoint;
            }
            case 4 -> {
                if (state.reviewBody.length() < 512) state.reviewBody += codePoint;
            }
            default -> { return false; }
        }
        return true;
    }

    boolean key(int keyCode) {
        if (state.focus == 0) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            state.focus = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_TAB) {
            if (state.focus == 3) state.focus = 4;
            else if (state.focus == 4) {
                if (keyCode == GLFW.GLFW_KEY_TAB) state.focus = 3;
            } else state.focus = 0;
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_BACKSPACE) return false;
        switch (state.focus) {
            case 1 -> {
                if (!state.search.isEmpty()) state.search = state.search.substring(0, state.search.length() - 1);
                state.scroll = 0;
            }
            case 2 -> {
                if (!state.priceSearch.isEmpty()) state.priceSearch = state.priceSearch.substring(0, state.priceSearch.length() - 1);
                state.priceScroll = 0;
            }
            case 3 -> {
                if (!state.reviewTitle.isEmpty()) state.reviewTitle = state.reviewTitle.substring(0, state.reviewTitle.length() - 1);
            }
            case 4 -> {
                if (!state.reviewBody.isEmpty()) state.reviewBody = state.reviewBody.substring(0, state.reviewBody.length() - 1);
            }
            default -> { return false; }
        }
        return true;
    }

    private String wishlistBody() {
        StringBuilder body = new StringBuilder("ANTAZON WISHLIST\n");
        for (String product : AntazonClientState.wishlist()) body.append("antazon://product/").append(product).append('\n');
        return body.toString().trim();
    }

    private String prettyItem(String item) {
        int separator = item.indexOf(':');
        String resource = separator >= 0 ? item.substring(separator + 1) : item;
        StringBuilder result = new StringBuilder();
        for (String part : resource.split("_")) {
            if (part.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private boolean onCooldown(AntazonClientState.ProductRow product) {
        return product.cooldownEnds() > gameTime();
    }

    private long gameTime() {
        return Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
    }

    private String cooldown(AntazonClientState.ProductRow product) {
        long remaining = Math.max(0L, product.cooldownEnds() - gameTime());
        long days = remaining / 24000L;
        long hours = remaining % 24000L / 1000L;
        long minutes = remaining % 1000L * 60L / 1000L;
        if (days > 0L) return days + "D " + hours + "H";
        if (hours > 0L) return hours + "H " + minutes + "M";
        return Math.max(1L, minutes) + "M";
    }

    private String sellMessage(String status) {
        return switch (status) {
            case "SHIPMENT_ACCEPTED" -> Component.translatable("computer.antos.status.shipment_accepted").getString();
            case "CRATE_CHANGED" -> Component.translatable("computer.antos.status.crate_changed").getString();
            case "UNAUTHORIZED" -> Component.translatable("computer.antos.status.shipping_unavailable").getString();
            case "INVALID_REQUEST", "SHIPMENT_FAILED" -> Component.translatable("computer.antos.status.shipment_failed").getString();
            default -> status.replace('_', ' ');
        };
    }

    private String resetLabel(String reset) {
        return switch (reset) {
            case "minecraft_day" -> "TODAY";
            case "minecraft_week" -> "THIS WEEK";
            default -> "EVER";
        };
    }

    private String taskTitles(List<String> tasks) {
        return String.join(", ", tasks.stream().map(taskId -> {
            var task = ComputerTasksClientState.get().stream().filter(row -> row.id().equals(taskId)).findFirst().orElse(null);
            return task != null && task.visible() ? AntOSPlayerText.apply(Component.translatable(task.title()).getString()) : taskId;
        }).toList());
    }

    void refresh() {
        ComputerNetworking.linkAntazonCrate();
        ComputerNetworking.requestAntazon();
        ComputerNetworking.requestAntazonWallet();
        ComputerNetworking.requestAntazonSellState();
        ComputerNetworking.requestAntazonWishlist();
        ComputerNetworking.requestAntazonOrders();
        ComputerNetworking.requestAntazonPrices();
        ComputerNetworking.requestAntazonOnboarding();
        ComputerNetworking.requestAntazonCart();
        state.refreshTicks = 200;
    }

    private static final class State {
        String mode = "catalog";
        String returnMode = "catalog";
        String footer = "";
        int focus;
        int scroll;
        int categoryScroll;
        int detailScroll;
        int cartScroll;
        int savedScroll;
        int ordersScroll;
        int sellScroll;
        int priceScroll;
        int orderSelection = -1;
        int priceQuantity = 1;
        String priceItem = "";
        String priceSearch = "";
        boolean priceGuide;
        String productId = "";
        String selectedVariant = "";
        int quantity = 1;
        int paymentOption;
        int galleryIndex = -1;
        String recipeItem = "";
        String search = "";
        String category = "ALL";
        boolean filterDeals;
        boolean filterStock;
        boolean filterAfford;
        long buyConfirmUntil;
        long checkoutConfirmUntil;
        boolean sellConfirm;
        int onboardingPage;
        int reviewRating;
        String reviewTitle = "";
        String reviewBody = "";
        boolean prefetched;
        int refreshTicks;
        final Set<String> seenDeals = new HashSet<>();
        int walletRefreshTicks;
    }
}
