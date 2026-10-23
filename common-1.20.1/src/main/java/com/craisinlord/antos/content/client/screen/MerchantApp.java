package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.AntOSPlayerText;
import com.craisinlord.antos.content.client.AntOSUiSounds;
import com.craisinlord.antos.content.client.MerchantClientState;
import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.network.ComputerNetworking;
import com.craisinlord.antos.content.merchant.MerchantProtocol;
import com.craisinlord.antos.content.merchant.MerchantIdentity;
import com.craisinlord.antos.content.merchant.MerchantAccess;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

final class MerchantApp extends ComputerApp {
    final AppKit kit = new AppKit(this);
    private JsonObject data = new JsonObject();
    private String queryId = "";
    private boolean loading;
    private boolean showDispatched;
    private String tutorialWorkspace = "";
    private boolean tutorial;
    private boolean firstTutorial;
    private int tutorialStep;
    private int tutorialScroll;
    private static final int TUTORIAL_STEPS = 4;
    private int listScroll;
    private int maximumScroll;
    private int pickerScroll;
    private List<CurrencyChoice> currencyChoices = List.of();
    private List<CurrencyChoice> filteredCurrencies = List.of();
    private String currencySearch = null;
    private Object currencyLanguage;
    private Object currencyRegistry;
    private final java.util.Map<JsonObject, ItemStack> previews = new java.util.IdentityHashMap<>();
    private JsonObject selected;
    private JsonObject pending;
    private JsonObject offered;
    private final JsonArray payments = new JsonArray();
    private String view = "browse";
    private String mode = "list";
    private String currency = "";
    private String sort = "newest";
    private String paymentItem = "minecraft:diamond";
    private String notice = "";
    private boolean canPay;
    private boolean confirm;
    private boolean filterPicker;
    private boolean picking;
    private boolean error;
    private boolean queryError;
    private String errorCode = "";
    private int page;
    private int pageSize = 12;
    private int noticeTicks;
    private int scroll;
    private int option;
    private int slot;
    private int focus = -1;
    private int cursor;
    private boolean selectedText;
    private int refreshTicks;
    private int waiting;
    private int stage;
    private int summaryTicks;
    private int visibleRows = 1;
    private final String[] fields = {"", "1", "1", "1", "", "1"};

    void openDeliveries() { view = "deliveries"; mode = "list"; page = 0; scroll = 0; refresh(); }

    void refresh() {
        refreshTicks = 200;
        query();
    }

    void tick(boolean loggedIn, boolean open) {
        if (!loggedIn || !com.craisinlord.antos.config.AntOSSettings.appEnabled("MERCHANT")) return;
        for (JsonObject result : MerchantClientState.drain()) {
            boolean mutation = pending != null && MerchantProtocol.matches(result, "request", string(pending, "request"));
            boolean current = MerchantProtocol.matches(result, "query", queryId) || mutation;
            if (!current && (result.has("query") || result.has("request"))) continue;
            boolean success = result.has("ok") && result.get("ok").getAsBoolean();
            if (current) loading = false;
            var player = Minecraft.getInstance().player;
            if (current && success && !mutation && error && MerchantAccess.clearAfterRefresh(errorCode, queryError,
                    player != null && player.isAlive(), player != null && player.isSpectator())) {
                notice = "";
                error = false;
                errorCode = "";
                queryError = false;
            }
            if (current && result.has("state")) {
                JsonObject incoming = result.getAsJsonObject("state");
                if (string(incoming, "view").equals("summary")) {
                    data.add("pending", incoming.get("pending"));
                    data.add("wallet", incoming.get("wallet"));
                } else {
                    data = incoming;
                    previews.clear();
                    page = integer(data, "page");
                    if (mode.equals("detail") && data.has("selection")) {
                        JsonObject fresh = data.getAsJsonObject("selection");
                        if (selected.has("revision") && integer(selected, "revision") != integer(fresh, "revision") && !mutation) {
                            confirm = false;
                            notice = tr("stock_changed");
                            noticeTicks = 100;
                            error = false;
                        }
                        selected = fresh.deepCopy();
                    }
                }
            }
            if (!success) {
                errorCode = string(result, "error");
                queryError = !mutation;
                notice = tr("error." + errorCode);
                error = true;
                if (mutation) {
                    AntOSUiSounds.play(AntOSUiSounds.Cue.ERROR);
                    if (!List.of("storage_error", "recovery_pending", "recovery_conflict", "invalid_response", "result_too_large").contains(string(result, "error"))) pending = null;
                }
                if (mutation || !current && pending != null) waiting = 0;
                if (mutation && pending == null) { confirm = false; query(); }
            } else if (mutation && result.has("notice")) {
                notice = tr("success." + string(result, "notice"));
                noticeTicks = 100;
                error = false;
                pending = null;
                waiting = 0;
                mode = "list";
                scroll = listScroll;
                confirm = false;
                if (string(result, "notice").equals("publish")) {
                    payments.asList().clear();
                    offered = null;
                    view = "mine";
                    page = 0;
                    scroll = 0;
                }
                AntOSUiSounds.play(AntOSUiSounds.Cue.ORDER);
                query();
            }
        }
        if (waiting > 0) waiting--;
        if (noticeTicks > 0 && --noticeTicks == 0 && !error) notice = "";
        if (open && pending == null && --refreshTicks <= 0) refresh();
        if (!open && pending == null && --summaryTicks <= 0) {
            summaryTicks = 600;
            queryId = UUID.randomUUID().toString();
            JsonObject request = new JsonObject();
            request.addProperty("action", "state"); request.addProperty("view", "summary"); request.addProperty("query", queryId);
            ComputerNetworking.merchant(request.toString());
        }
    }

    boolean hasPending() { return data.has("pending") && data.get("pending").getAsInt() > 0; }

    private void query() {
        if (pending != null) return;
        queryId = UUID.randomUUID().toString();
        loading = true;
        JsonObject request = queryFields();
        request.addProperty("action", "state");
        ComputerNetworking.merchant(request.toString());
    }

    private JsonObject queryFields() {
        JsonObject request = new JsonObject();
        request.addProperty("view", view);
        request.addProperty("search", fields[0]);
        request.addProperty("currency", currency);
        request.addProperty("sort", sort);
        request.addProperty("affordable", canPay);
        request.addProperty("page", page);
        request.addProperty("size", pageSize);
        request.addProperty("query", queryId);
        request.addProperty("showDispatched", showDispatched);
        if (mode.equals("detail") && selected != null && !view.equals("history")) request.addProperty("selected", string(selected, "id"));
        return request;
    }

    private void mutate(JsonObject request) {
        if (pending != null) return;
        String restriction = tradeRestriction();
        if (!restriction.isEmpty()) {
            errorCode = restriction;
            queryError = false;
            notice = tr("error." + restriction);
            error = true;
            return;
        }
        queryId = UUID.randomUUID().toString();
        JsonObject query = queryFields();
        for (var entry : query.entrySet()) request.add(entry.getKey(), entry.getValue());
        request.addProperty("request", UUID.randomUUID().toString());
        pending = request;
        waiting = 120;
        ComputerNetworking.merchant(request.toString());
    }

    void render(GuiGraphics g, int x, int y, int w, int h) {
        kit.begin();
        maximumScroll = 0;
        String workspace = com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of();
        if (pending == null && !workspace.equals("workspace:unbound") && !workspace.equals(tutorialWorkspace)) {
            tutorialWorkspace = workspace;
            tutorial = false;
            if (!com.craisinlord.antos.config.AntOSSettings.hasCompletedMerchantTour(workspace)) openTutorial(true);
        }
        if (tutorial) {
            renderTutorial(g, x, y, w, h);
            kit.drawFocus(g);
            kit.drawTooltip(g, x, y, x + w, y + h);
            return;
        }
        String restriction = tradeRestriction();
        String status = pending != null ? waiting > 0 ? tr("working") : tr("retry_hint")
                : !notice.isEmpty() ? notice : !restriction.isEmpty() ? tr("browse_only") + " · " + tr("error." + restriction) : "";
        boolean listing = listing();
        renderHeader(g, x, y, w);
        int top = y + 26;
        int height = status.isEmpty() || listing ? h - 26 : h - 49;
        if (picking) renderPicker(g, x, top, w, height);
        else if (mode.equals("create")) renderCreate(g, x, top, w, height);
        else if (mode.equals("detail") && selected != null) renderDetail(g, x, top, w, height);
        else renderList(g, x, top, w, height, status.isEmpty());
        int actionWidth = pending != null && waiting == 0 ? 116 : listing ? 48 : 0;
        int left = listing ? x + 48 : x - 2;
        int stripTop = listing ? y + h - 20 : y + h - 15;
        int stripBottom = listing ? y + h - 2 : y + h;
        if (!status.isEmpty()) {
            if (!notice.isEmpty() || pending != null) g.fill(left, stripTop, x + w - actionWidth, stripBottom,
                    error ? Ui.NOTICE_ERROR : pending != null ? Ui.PANEL : Ui.NOTICE_SUCCESS);
            small(g, status, left + 4, stripBottom - 10 - (listing ? 3 : 0), x + w - actionWidth - left - 8, error ? Ui.ERROR : Ui.TEXT_PLACEHOLDER);
        }
        if (pending != null) kit.begin();
        if (pending != null && waiting == 0) button(g, x + w - 54, y + h - 19, 54, 18, tr("retry"), true, canTrade(), () -> {
            waiting = 120; ComputerNetworking.merchant(pending.toString());
        });
        if (pending != null && waiting == 0) button(g, x + w - 112, y + h - 19, 54, 18, tr("dismiss"), false, true, () -> {
            pending = null;
            notice = "";
            error = false;
            errorCode = "";
            confirm = false;
            query();
        });
        if (!status.isEmpty() && kit.hovered(left, stripTop, x + w - actionWidth - left, stripBottom - stripTop)) kit.tooltip(status);
        kit.drawFocus(g);
        kit.drawTooltip(g, x, y, x + w, y + h);
    }

    private String tradeRestriction() {
        var player = Minecraft.getInstance().player;
        return MerchantAccess.failure("buy", player != null && player.isAlive(), player != null && player.isSpectator());
    }

    private boolean canTrade() { return tradeRestriction().isEmpty(); }

    private void renderHeader(GuiGraphics g, int x, int y, int w) {
        String balance = data.has("wallet") ? Long.toString(data.get("wallet").getAsLong()) : "—";
        int walletWidth = Math.max(36, Math.min(86, font.width(balance) + 21));
        int listWidth = 54;
        int helpWidth = 18;
        int groupWidth = w - walletWidth - listWidth - helpWidth - 12;
        String[] views = {"browse", "mine", "deliveries"};
        for (int index = 0; index < views.length; index++) {
            String target = views[index];
            int left = x + index * groupWidth / views.length;
            int right = x + (index + 1) * groupWidth / views.length;
            boolean active = !mode.equals("create") && (view.equals(target) || target.equals("mine") && view.equals("history"));
            boolean hover = pending == null && kit.hovered(left, y, right - left, 20);
            g.fill(left, y, right, y + 20, active ? Ui.TEXT : hover ? Ui.HOVER : Ui.SURFACE);
            if (index > 0) g.fill(left, y + 2, left + 1, y + 18, Ui.LINE);
            String caption = tr(target);
            if (target.equals("deliveries") && hasPending()) caption += " " + integer(data, "pending");
            screen.drawTabLabel(g, caption, left, y, right - left, active ? Ui.BACKGROUND : Ui.ACCENT);
            if (pending == null) kit.hit(left, y, right - left, 20, () -> {
                view = target; mode = "list"; picking = false; confirm = false; page = 0; scroll = 0; focus = -1; query();
            });
        }
        kit.outline(g, x, y, groupWidth, 20, Ui.BORDER_STRONG);
        int walletX = x + groupWidth + 4;
        g.fill(walletX, y, walletX + walletWidth, y + 20, Ui.PANEL);
        kit.outline(g, walletX, y, walletWidth, 20, Ui.LINE);
        coin(g, walletX + 6, y + 7);
        label(g, balance, walletX + 15, y + 6, walletWidth - 21, Ui.TEXT);
        if (kit.hovered(walletX, y, walletWidth, 20)) kit.tooltip(AntOSPlayerText.currencyName() + " · " + balance);
        int helpX = walletX + walletWidth + 4;
        button(g, helpX, y, helpWidth, 20, "?", false, pending == null, () -> openTutorial(false));
        if (kit.hovered(helpX, y, helpWidth, 20)) kit.tooltip(tr("tutorial.open"));
        button(g, x + w - listWidth, y, listWidth, 20, tr("list_item"), true, pending == null && canTrade(), () -> {
            listScroll = scroll; mode = "create"; stage = offered == null ? 0 : stage; scroll = 0; focus = -1; picking = false;
        });
        if (!canTrade() && kit.hovered(x + w - listWidth, y, listWidth, 20)) kit.tooltip(tr("error." + tradeRestriction()));
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String caption, boolean primary, boolean enabled, Runnable action) {
        boolean hover = enabled && kit.hovered(x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? Ui.HOVER : primary && enabled ? Ui.SURFACE_DEEP : Ui.SURFACE);
        kit.outline(g, x, y, w, h, !enabled ? Ui.LINE : hover ? Ui.FOCUS : primary ? Ui.ACCENT : Ui.LINE);
        float scale = Math.min(1.0F, Math.max(0.1F, (w - 6.0F) / Math.max(1, font.width(caption))));
        g.pose().pushPose();
        try {
            g.pose().translate(x + w / 2.0F, y + (h - 8) / 2.0F + 1, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, caption, -font.width(caption) / 2, 0, !enabled ? Ui.TEXT_DIM : primary ? Ui.ACCENT : Ui.TEXT, false);
        } finally { g.pose().popPose(); }
        if (!enabled && !canTrade() && List.of("list_item", "publish", "deliver_here", "retry", "cancel_listing", "confirm_cancel", "review_trade", "confirm_trade").stream()
                .anyMatch(key -> tr(key).equals(caption)) && kit.hovered(x, y, w, h)) kit.tooltip(tr("error." + tradeRestriction()));
        if (enabled) kit.hit(x, y, w, h, () -> AntOSUiSounds.action(action));
    }

    private void filter(GuiGraphics g, int x, int y, int w, String caption, boolean selected, Runnable action) {
        screen.drawFilterButton(g, x, y, w, selected, caption);
        if (pending == null) kit.hit(x, y, w, 17, action);
    }

    private void coin(GuiGraphics g, int x, int y) {
        String[] pixels = {"..###..", ".#+++#.", "#++#++#", "#+###+#", "#++#++#", ".#+++#.", "..###.."};
        for (int row = 0; row < pixels.length; row++) {
            for (int column = 0; column < pixels[row].length(); column++) {
                char pixel = pixels[row].charAt(column);
                if (pixel == '#') g.fill(x + column, y + row, x + column + 1, y + row + 1, Ui.ACCENT);
                else if (pixel == '+') g.fill(x + column, y + row, x + column + 1, y + row + 1, Ui.BRAND_MARK);
            }
        }
    }

    private void small(GuiGraphics g, String text, int x, int y, int width, int color) {
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(0.85F, 0.85F, 1.0F);
            g.drawString(font, screen.trimToWidth(text, Math.max(1, (int) (width / 0.85F))), 0, 0, color, false);
        } finally { g.pose().popPose(); }
    }

    private void openTutorial(boolean first) {
        tutorial = true;
        firstTutorial = first;
        tutorialStep = 0;
        tutorialScroll = 0;
        focus = -1;
        kit.release();
        kit.begin();
    }

    private void finishTutorial() {
        com.craisinlord.antos.config.AntOSSettings.setMerchantTourCompleted(tutorialWorkspace, true);
        tutorial = false;
        focus = -1;
        kit.release();
        kit.begin();
    }

    void replayTour() {
        com.craisinlord.antos.config.AntOSSettings.setMerchantTourCompleted(com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of(), false);
        tutorialWorkspace = "";
    }

    private void tutorialNext() {
        if (tutorialStep + 1 == TUTORIAL_STEPS) finishTutorial();
        else { tutorialStep++; tutorialScroll = 0; kit.begin(); }
    }

    private void tutorialBack() {
        tutorialStep = Math.max(0, tutorialStep - 1);
        tutorialScroll = 0;
        kit.begin();
    }

    private void renderTutorial(GuiGraphics g, int x, int y, int w, int h) {
        int cardX = x + 16;
        int cardY = y + 12;
        int cardWidth = w - 32;
        int bottom = y + h - 8;
        g.fill(cardX, cardY, cardX + cardWidth, bottom, Ui.PANEL);
        kit.outline(g, cardX, cardY, cardWidth, bottom - cardY, Ui.BORDER_STRONG);
        small(g, Component.translatable("computer.antos.merchant.tutorial.step", tutorialStep + 1, TUTORIAL_STEPS).getString(),
                cardX + 14, cardY + 12, cardWidth - 28, Ui.TEXT_PLACEHOLDER);
        String title = tr("tutorial." + tutorialStep + ".title");
        float scale = Math.min(1.5F, (cardWidth - 28.0F) / Math.max(1, font.width(title)));
        g.pose().pushPose();
        try {
            g.pose().translate(cardX + 14, cardY + 24, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, title, 0, 0, Ui.TEXT_STRONG, false);
        } finally { g.pose().popPose(); }
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>(font.split(Component.literal(tr("tutorial." + tutorialStep + ".text.0")), Math.max(1, cardWidth - 34)));
        int primaryLines = lines.size();
        lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
        lines.addAll(font.split(Component.literal(tr("tutorial." + tutorialStep + ".text.1")), Math.max(1, cardWidth - 34)));
        int textY = cardY + 46;
        int visible = Math.max(1, (bottom - 42 - textY) / 11);
        maximumScroll = Math.max(0, lines.size() - visible);
        tutorialScroll = Math.min(tutorialScroll, maximumScroll);
        for (int index = 0; index < visible && index + tutorialScroll < lines.size(); index++) {
            int line = index + tutorialScroll;
            g.drawString(font, lines.get(line), cardX + 14, textY + index * 11, line < primaryLines ? Ui.TEXT : Ui.TEXT_PLACEHOLDER, false);
        }
        kit.scrollbar(g, cardX + cardWidth - 7, textY, Math.max(12, visible * 11), visible, lines.size(), tutorialScroll, maximumScroll, value -> tutorialScroll = value);
        int dotsX = cardX + cardWidth / 2 - TUTORIAL_STEPS * 5;
        for (int index = 0; index < TUTORIAL_STEPS; index++) g.fill(dotsX + index * 10, bottom - 34, dotsX + index * 10 + 6, bottom - 31, index == tutorialStep ? Ui.ACCENT : Ui.LINE);
        button(g, cardX + 12, bottom - 26, 56, 18, tr(firstTutorial ? "tutorial.skip" : "tutorial.close"), false, true, this::finishTutorial);
        if (tutorialStep > 0) button(g, cardX + cardWidth - 150, bottom - 26, 56, 18, tr("back"), false, true, this::tutorialBack);
        button(g, cardX + cardWidth - 88, bottom - 26, 76, 18, tr(tutorialStep + 1 == TUTORIAL_STEPS ? "tutorial.done" : "next"), true, true, this::tutorialNext);
    }

    private void renderList(GuiGraphics g, int x, int y, int w, int h, boolean pageLabel) {
        boolean history = view.equals("history");
        boolean listings = view.equals("browse") || view.equals("mine");
        int top = y;
        if (listings) {
            field(g, x, top, w, 0, tr("search"));
            top += 22;
            if (view.equals("browse")) {
                filter(g, x, top, w / 2 - 2, currency.isEmpty() ? tr("any_payment") : itemName(currency), !currency.isEmpty(), () -> {
                    filterPicker = true; picking = true; fields[4] = ""; pickerScroll = scroll; scroll = 0; focus = 4; cursor = 0; selectedText = false;
                });
                filter(g, x + w / 2, top, w / 4 - 2, tr("can_pay"), canPay, () -> { canPay = !canPay; page = 0; query(); });
                filter(g, x + w * 3 / 4, top, w / 4, tr("sort." + sort) + " ▼", !sort.equals("newest"), () -> {
                    sort = sort.equals("newest") ? "name" : sort.equals("name") && !currency.isEmpty() ? "price" : "newest"; page = 0; query();
                });
                if (kit.hovered(x + w * 3 / 4, top, w / 4, 17)) kit.tooltip(tr(currency.isEmpty() ? "sort_hint_currency" : "sort_hint"));
            } else button(g, x, top, 110, 18, tr("history"), false, true, () -> { view = "history"; page = 0; scroll = 0; query(); });
            top += 23;
        } else {
            label(g, tr(history ? "history" : "deliveries_hint"), x, top + 3, w - (history ? 0 : 106), Ui.TEXT);
            if (!history) filter(g, x + w - 102, top, 102, tr(showDispatched ? "all_shipments" : "pending_only"), showDispatched, () -> { showDispatched = !showDispatched; page = 0; scroll = 0; query(); });
            top += 21;
        }
        JsonArray rows = data.has("rows") && view.equals(string(data, "view")) ? data.getAsJsonArray("rows") : new JsonArray();
        int rowHeight = listings || history ? 42 : 32;
        int rowStride = rowHeight + 2;
        visibleRows = Math.max(1, Math.min(24, (h - (top - y) - 22) / rowStride));
        if (visibleRows != pageSize && pending == null) {
            page = page * pageSize / visibleRows;
            pageSize = visibleRows;
            query();
        }
        scroll = 0;
        for (int index = 0; index < Math.min(rows.size(), visibleRows); index++) {
            JsonObject row = rows.get(index).getAsJsonObject();
            int rowY = top + index * rowStride;
            boolean hovered = kit.hovered(x, rowY, w, rowHeight);
            g.fill(x, rowY, x + w, rowY + rowHeight, hovered ? Ui.HOVER : Ui.SURFACE);
            kit.outline(g, x, rowY, w, rowHeight, hovered ? Ui.FOCUS : Ui.LINE);
            ItemStack stack = stack(row.getAsJsonObject("stack"));
            drawItem(g, stack, x + 4, rowY + 7);
            long quantity = row.get("quantity").getAsLong();

            if (listings) {
                JsonArray alternatives = row.getAsJsonArray("payments");
                JsonObject displayed = alternatives.get(0).getAsJsonObject();
                for (JsonElement alternative : alternatives) if (string(alternative.getAsJsonObject(), "item").equals(currency)) { displayed = alternative.getAsJsonObject(); break; }
                for (JsonElement alternative : alternatives) {
                    JsonObject candidate = alternative.getAsJsonObject();
                    if (string(candidate, "item").equals(string(displayed, "item")) && integer(candidate, "quantity") < integer(displayed, "quantity")) displayed = candidate;
                }
                String price = paymentLabel(displayed, 1);
                int priceWidth = Math.min(w / 2, font.width(price) + 8);
                label(g, stack.getHoverName().getString() + " ×" + quantity, x + 26, rowY + 4, w - priceWidth - 35, Ui.TEXT_STRONG);
                label(g, price, x + w - priceWidth - 7, rowY + 4, priceWidth, Ui.ACCENT);
                String stock = integer(row, "lots") + " " + tr("lots") + (alternatives.size() > 1 ? " · +" + (alternatives.size() - 1) + " " + tr("alternatives") : "");
                int stockWidth = Math.min(w / 2, font.width(stock) + 6);
                label(g, stock, x + w - stockWidth - 7, rowY + 26, stockWidth, Ui.TEXT_PLACEHOLDER);
                MerchantIdentity seller = identity(string(row, "seller"));
                drawIdentity(g, seller, x + 26, rowY + 21, w - stockWidth - 35, 16);
            } else if (history) {
                boolean bought = string(row, "buyer").equals(string(data, "owner"));
                String price = tr(bought ? "bought" : "sold") + " · " + row.get("paid").getAsLong() + " " + itemName(string(row.getAsJsonObject("payment"), "item"));
                int priceWidth = Math.min(w / 2, font.width(price) + 6);
                label(g, stack.getHoverName().getString() + " ×" + quantity, x + 26, rowY + 4, w - 34, Ui.TEXT_STRONG);
                label(g, price, x + w - priceWidth - 7, rowY + 26, priceWidth, Ui.TEXT_PLACEHOLDER);
                drawIdentity(g, identity(string(row, bought ? "sellerName" : "buyerName")), x + 26, rowY + 21, w - priceWidth - 35, 16);
            } else {
                String subtitle = tr("kind." + string(row, "kind")) + " · " + tr("shipment." + string(row, "status"));
                label(g, stack.getHoverName().getString() + " ×" + quantity, x + 26, rowY + 4, w - 34, Ui.TEXT_STRONG);
                label(g, subtitle, x + 26, rowY + 18, w - 34, Ui.TEXT_PLACEHOLDER);
            }
            kit.hit(x, rowY, w, rowHeight, () -> { listScroll = scroll; selected = row.deepCopy(); mode = "detail"; option = 0; fields[5] = "1"; confirm = false; focus = -1; scroll = 0; });
        }
        if (rows.isEmpty()) {
            g.drawCenteredString(font, screen.trimToWidth(tr(loading ? "loading" : "empty." + view), w - 12), x + w / 2, top + 10, Ui.TEXT_STRONG);
            if (!loading && view.equals("browse")) button(g, x + (w - 130) / 2, top + 31, 130, 18, tr("clear_filters"), true, true, () -> { fields[0] = ""; currency = ""; canPay = false; page = 0; query(); });
        }
        int currentPage = data.has("page") ? integer(data, "page") : 0;
        int size = data.has("size") ? integer(data, "size") : pageSize;
        int total = data.has("total") ? integer(data, "total") : 0;
        button(g, x, y + h - 20, 45, 18, "<", false, currentPage > 0 && pending == null, () -> turnPage(-1));
        if (pageLabel) label(g, (currentPage + 1) + " / " + Math.max(1, (total + size - 1) / size) + " · " + total + " " + tr("results"), x + 52, y + h - 15, w - 103, Ui.TEXT_PLACEHOLDER);
        button(g, x + w - 45, y + h - 20, 45, 18, ">", false, (currentPage + 1) * size < total && pending == null, () -> turnPage(1));
    }

    private boolean listing() { return !picking && !mode.equals("create") && !(mode.equals("detail") && selected != null); }

    private void turnPage(int delta) {
        int target = (data.has("page") ? integer(data, "page") : 0) + delta;
        int size = data.has("size") ? integer(data, "size") : pageSize;
        if (pending != null || target < 0 || target * size >= (data.has("total") ? integer(data, "total") : 0)) return;
        page = target;
        query();
    }

    private void renderDetail(GuiGraphics g, int x, int y, int w, int h) {
        button(g, x, y, 55, 18, tr(confirm ? "change" : "back"), false, pending == null, () -> {
            if (confirm) confirm = false; else { mode = "list"; scroll = listScroll; }
            focus = -1;
        });
        ItemStack item = stack(selected.getAsJsonObject("stack"));
        drawItem(g, item, x + 65, y);
        label(g, item.getHoverName().getString() + " ×" + selected.get("quantity").getAsLong(), x + 86, y + 4, w - 87, Ui.TEXT_STRONG);
        int top = y + 24;
        if (view.equals("deliveries")) {
            label(g, tr("shipment." + string(selected, "status")), x, top, w, Ui.ACCENT);
            label(g, tr("loose_delivery"), x, top + 21, w, Ui.TEXT_PLACEHOLDER);
            if (selected.has("location")) {
                label(g, string(selected, "dimension"), x, top + 43, w, Ui.TEXT);
                label(g, string(selected, "location"), x, top + 58, w, Ui.TEXT_PLACEHOLDER);
            }
            button(g, x, y + h - 21, w, 20, tr("deliver_here"), true, pending == null && canTrade() && string(selected, "status").equals("pending"), () -> {
                JsonObject request = new JsonObject(); request.addProperty("action", "deliver"); request.addProperty("id", string(selected, "id")); mutate(request);
            });
            return;
        }
        if (view.equals("history")) {
            int half = (w - 6) / 2;
            identityCard(g, identity(string(selected, "buyerName")), tr("buyer"), x, top, half);
            identityCard(g, identity(string(selected, "sellerName")), tr("seller"), x + half + 6, top, w - half - 6);
            summary(g, x, top + 40, w, tr("paid"), itemName(string(selected.getAsJsonObject("payment"), "item")), selected.get("paid").getAsLong());
            return;
        }
        boolean own = string(selected, "owner").equals(string(data, "owner"));
        JsonArray alternatives = selected.getAsJsonArray("payments");
        option = Math.max(0, Math.min(option, alternatives.size() - 1));
        JsonObject payment = alternatives.get(option).getAsJsonObject();
        long required = (long) integer(payment, "quantity") * positive(5);
        boolean validLots = validNumber(5) && positive(5) <= integer(selected, "lots") && required <= Integer.MAX_VALUE;
        boolean enough = own || validLots && owned(string(payment, "item")) >= required;
        if (confirm) {
            summary(g, x, top, w, tr(own ? "return_stock" : "receive"), item.getHoverName().getString(), (long) integer(selected, "quantity") * (own ? integer(selected, "lots") : positive(5)));
            if (own) label(g, tr("cancel_review"), x, top + 54, w, Ui.TEXT_PLACEHOLDER);
            else summary(g, x, top + 49, w, tr("give"), itemName(string(payment, "item")), required);
            label(g, tr("loose_delivery"), x, y + h - 38, w, Ui.TEXT_PLACEHOLDER);
        } else {
            identityCard(g, identity(string(selected, "seller")), tr("seller") + " · " + integer(selected, "lots") + " " + tr("lots"), x, top, w);
            top += 37;
            if (!own) {
                int labelWidth = Math.min(110, (w - 8) / 3);
                int numberWidth = Math.max(66, Math.min(112, w - labelWidth - 60));
                label(g, tr("buy_lots"), x, top + 5, labelWidth - 4, Ui.TEXT);
                number(g, x + labelWidth, top, numberWidth, 5, integer(selected, "lots"));
                label(g, integer(selected, "quantity") + " / " + tr("lot"), x + labelWidth + numberWidth + 7, top + 5, w - labelWidth - numberWidth - 7, Ui.TEXT_PLACEHOLDER);
                top += 23;
            }
            visibleRows = Math.max(1, (y + h - 43 - top) / 25);
            maximumScroll = Math.max(0, alternatives.size() - visibleRows);
            scroll = Math.min(scroll, maximumScroll);
            for (int index = scroll; index < Math.min(alternatives.size(), scroll + visibleRows); index++) {
                JsonObject alternative = alternatives.get(index).getAsJsonObject();
                int target = index;
                int rowY = top + (index - scroll) * 25;
                button(g, x, rowY, w - 8, 23, "", index == option, pending == null && !own, () -> { option = target; confirm = false; });
                label(g, index == option ? "●" : "○", x + 5, rowY + 7, 10, Ui.ACCENT);
                if (!string(alternative, "item").equals("antcoins")) drawItem(g, plain(string(alternative, "item")), x + 18, rowY + 3);
                label(g, paymentLabel(alternative, own ? 1 : positive(5)), x + 39, rowY + 7, w - (own ? 53 : 139), Ui.TEXT);
                if (!own) label(g, tr("owned") + " " + owned(string(alternative, "item")), x + w - 96, rowY + 7, 83, Ui.TEXT_PLACEHOLDER);
            }
            kit.scrollbar(g, x + w - 3, top, visibleRows * 25, visibleRows, alternatives.size(), scroll, maximumScroll, value -> scroll = value);
            if (!own && !enough) label(g, tr(validLots ? "error.insufficient_payment" : "error.invalid_quantity"), x, y + h - 37, w, Ui.TEXT_PLACEHOLDER);
        }
        button(g, x, y + h - 21, w, 20, tr(own ? confirm ? "confirm_cancel" : "cancel_listing" : confirm ? "confirm_trade" : "review_trade"), true, pending == null && canTrade() && enough && integer(selected, "lots") > 0, () -> {
            if (!confirm) { confirm = true; focus = -1; return; }
            JsonObject request = new JsonObject();
            request.addProperty("action", own ? "cancel" : "buy"); request.addProperty("id", string(selected, "id"));
            request.addProperty("revision", integer(selected, "revision")); request.addProperty("option", option); request.addProperty("lots", positive(5));
            mutate(request);
        });
    }

    private MerchantIdentity identity(String username) {
        return MerchantIdentity.read(data.has("profiles") && data.get("profiles").isJsonObject() ? data.getAsJsonObject("profiles") : null, username);
    }

    private void drawIdentity(GuiGraphics g, MerchantIdentity person, int x, int y, int w, int size) {
        ProfileAvatar.draw(this, g, person.displayName(), person.avatarItem(), "", x, y, size);
        label(g, person.displayName(), x + size + 5, y + (size - 8) / 2, w - size - 5, Ui.TEXT);
        if (kit.hovered(x, y, w, size)) kit.tooltip(person.displayName() + " · " + person.address());
    }

    private void identityCard(GuiGraphics g, MerchantIdentity person, String role, int x, int y, int w) {
        g.fill(x, y, x + w, y + 32, Ui.PANEL);
        ProfileAvatar.draw(this, g, person.displayName(), person.avatarItem(), "", x + 4, y + 4, 24);
        label(g, role + " · " + person.displayName(), x + 34, y + 5, w - 39, Ui.TEXT_STRONG);
        label(g, person.address(), x + 34, y + 18, w - 39, Ui.TEXT_PLACEHOLDER);
        if (kit.hovered(x, y, w, 32)) kit.tooltip(role + " · " + person.displayName() + " · " + person.address());
    }

    private void summary(GuiGraphics g, int x, int y, int w, String caption, String item, long quantity) {
        g.fill(x, y, x + w, y + 43, Ui.SURFACE);
        kit.outline(g, x, y, w, 43, Ui.LINE);
        label(g, caption, x + 8, y + 5, w - 16, Ui.TEXT_PLACEHOLDER);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(quantity + " × " + item), w - 16);
        for (int index = 0; index < Math.min(2, lines.size()); index++) g.drawString(font, lines.get(index), x + 8, y + 18 + index * 10, Ui.ACCENT, false);
        if (kit.hovered(x, y, w, 43)) kit.tooltip(quantity + " × " + item);
    }

    private void renderCreate(GuiGraphics g, int x, int y, int w, int h) {
        label(g, tr("stage." + stage), x, y + 3, w - 58, Ui.TEXT_STRONG);
        button(g, x + w - 54, y, 54, 18, tr("back"), false, pending == null, () -> { if (stage > 0) stage--; else mode = "list"; focus = -1; scroll = mode.equals("list") ? listScroll : 0; });
        int top = y + 24;
        if (stage == 0) {
            JsonArray inventory = data.has("inventory") ? data.getAsJsonArray("inventory") : new JsonArray();
            long availableStock = 0;
            int cell = Math.max(18, Math.min(24, Math.min((w - 6) / 9, (h - 76) / 4)));
            boolean side = w - cell * 9 - 5 >= 70;
            if (!side) cell = Math.max(18, Math.min(cell, (h - 90) / 4));
            for (int index = 0; index < Math.min(36, inventory.size()); index++) {
                int sx = x + index % 9 * cell;
                int sy = top + index / 9 * cell;
                boolean chosen = offered != null && slot == index;
                g.fill(sx, sy, sx + cell - 1, sy + cell - 1, chosen ? Ui.HOVER : Ui.PANEL);
                if (!inventory.get(index).isJsonNull()) {
                    JsonObject value = inventory.get(index).getAsJsonObject();
                    ItemStack item = stack(value);
                    drawItem(g, item, sx + (cell - 16) / 2, sy + 2);
                    int target = index;
                    kit.hit(sx, sy, cell - 1, cell - 1, () -> { slot = target; offered = value.deepCopy(); fields[1] = Integer.toString(item.getCount()); });
                }
                if (chosen) kit.outline(g, sx, sy, cell - 1, cell - 1, Ui.ACCENT);
            }
            if (offered != null) {
                ItemStack prototype = stack(offered);
                for (JsonElement element : inventory) {
                    if (!element.isJsonNull()) {
                        ItemStack current = stack(element.getAsJsonObject());
                        if (ItemStack.isSameItemSameTags(current, prototype)) availableStock += current.getCount();
                    }
                }
                String name = prototype.getHoverName().getString();
                String available = tr("available") + " " + availableStock;
                String deposit = tr("deposit") + " " + ((long) positive(1) * positive(2));
                if (side) {
                    label(g, name, x + cell * 9 + 5, top + 8, w - cell * 9 - 5, Ui.TEXT);
                    label(g, available, x + cell * 9 + 5, top + 26, w - cell * 9 - 5, Ui.TEXT_PLACEHOLDER);
                    label(g, deposit, x + cell * 9 + 5, top + 44, w - cell * 9 - 5, Ui.ACCENT);
                } else {
                    int depositWidth = Math.min(w / 3, font.width(deposit) + 4);
                    label(g, name + " · " + available, x, top + cell * 4 + 4, w - depositWidth - 4, Ui.TEXT);
                    label(g, deposit, x + w - depositWidth, top + cell * 4 + 4, depositWidth, Ui.ACCENT);
                }
            }
            top += cell * 4 + 4 + (side ? 0 : 14);
            int half = (w - 6) / 2;
            int caption = Math.min(60, half * 2 / 5);
            label(g, tr("per_lot"), x, top + 4, caption - 2, Ui.TEXT);
            number(g, x + caption, top, half - caption, 1, (int) Math.min(Integer.MAX_VALUE, availableStock));
            label(g, tr("lots"), x + half + 6, top + 4, caption - 2, Ui.TEXT);
            number(g, x + half + 6 + caption, top, w - half - 6 - caption, 2, (int) Math.min(Integer.MAX_VALUE, availableStock / positive(1)));
            button(g, x, y + h - 21, w, 20, tr("next"), true, offered != null && validNumber(1) && validNumber(2) && (long) positive(1) * positive(2) <= Math.min(Integer.MAX_VALUE, availableStock), () -> { stage = 1; scroll = 0; focus = -1; });
        } else if (stage == 1) {
            int left = Math.max(140, w / 2);
            button(g, x, top, left - 8, 20, itemName(paymentItem), false, true, () -> { picking = true; filterPicker = false; fields[4] = ""; pickerScroll = scroll; scroll = 0; focus = 4; cursor = 0; selectedText = false; });
            label(g, tr("amount"), x, top + 31, 65, Ui.TEXT);
            field(g, x + 70, top + 26, left - 78, 3, "1");
            boolean existingCurrency = payments.asList().stream().anyMatch(value -> string(value.getAsJsonObject(), "item").equals(paymentItem));
            button(g, x, top + 52, left - 8, 20, tr(existingCurrency ? "update_payment" : "add_alternative"), false, validNumber(3), () -> {
                JsonObject payment = new JsonObject(); payment.addProperty("item", paymentItem); payment.addProperty("quantity", positive(3));
                boolean replaced = false;
                for (int index = 0; index < payments.size(); index++) if (string(payments.get(index).getAsJsonObject(), "item").equals(paymentItem)) {
                    payments.set(index, payment); replaced = true; break;
                }
                if (!replaced) payments.add(payment);
            });
            label(g, tr("choose_one"), x + left, top + 4, w - left, Ui.TEXT_PLACEHOLDER);
            visibleRows = Math.max(1, (h - 72) / 22);
            maximumScroll = Math.max(0, payments.size() - visibleRows);
            scroll = Math.min(scroll, maximumScroll);
            for (int index = scroll; index < Math.min(payments.size(), scroll + visibleRows); index++) {
                int target = index;
                int rowY = top + 20 + (index - scroll) * 22;
                label(g, paymentLabel(payments.get(index).getAsJsonObject(), 1), x + left, rowY + 4, w - left - 27, Ui.TEXT);
                kit.hit(x + left, rowY, w - left - 34, 18, () -> {
                    JsonObject payment = payments.get(target).getAsJsonObject(); paymentItem = string(payment, "item");
                    fields[3] = payment.get("quantity").getAsString(); focus = 3; cursor = fields[3].length(); selectedText = true;
                });
                button(g, x + w - 30, rowY, 20, 18, "×", false, true, () -> payments.remove(target));
            }
            kit.scrollbar(g, x + w - 3, top + 20, visibleRows * 22, visibleRows, payments.size(), scroll, maximumScroll, value -> scroll = value);
            button(g, x, y + h - 21, w, 20, tr("review"), true, !payments.isEmpty(), () -> { stage = 2; scroll = 0; focus = -1; });
        } else {
            ItemStack item = stack(offered);
            drawItem(g, item, x, top);
            label(g, item.getHoverName().getString() + " ×" + positive(1) + " / " + tr("lot"), x + 24, top + 3, w - 24, Ui.TEXT_STRONG);
            label(g, positive(2) + " " + tr("lots") + " · " + tr("deposit") + " " + ((long) positive(1) * positive(2)), x, top + 23, w, Ui.TEXT);
            label(g, tr("choose_one"), x, top + 42, w, Ui.TEXT_PLACEHOLDER);
            visibleRows = Math.max(1, (h - 112) / 18);
            scroll = Math.min(scroll, Math.max(0, payments.size() - visibleRows));
            for (int index = scroll; index < Math.min(payments.size(), scroll + visibleRows); index++) label(g, paymentLabel(payments.get(index).getAsJsonObject(), 1), x + 8, top + 58 + (index - scroll) * 18, w - 16, Ui.ACCENT);
            kit.scrollbar(g, x + w - 3, top + 58, visibleRows * 18, visibleRows, payments.size(), scroll, maximumScroll, value -> scroll = value);
            label(g, tr("deposit_hint"), x, y + h - 39, w, Ui.TEXT_PLACEHOLDER);
            button(g, x, y + h - 21, w, 20, tr("publish"), true, pending == null && canTrade(), () -> {
                JsonObject request = new JsonObject(); request.addProperty("action", "publish"); request.addProperty("slot", slot);
                request.add("expected", offered.deepCopy()); request.addProperty("quantity", positive(1)); request.addProperty("lots", positive(2)); request.add("payments", payments.deepCopy()); mutate(request);
            });
        }
    }

    private void renderPicker(GuiGraphics g, int x, int y, int w, int h) {
        field(g, x, y, w - 60, 4, tr("search_currency"));
        button(g, x + w - 56, y, 56, 18, tr("back"), false, true, () -> { picking = false; scroll = pickerScroll; focus = -1; });
        Object language = net.minecraft.locale.Language.getInstance();
        Object registry = Minecraft.getInstance().level.registryAccess();
        if (currencyLanguage != language || currencyRegistry != registry) {
            List<CurrencyChoice> choices = new ArrayList<>();
            for (var item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty()) continue;
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                String name = stack.getHoverName().getString();
                choices.add(new CurrencyChoice(id, name, (id + " " + name).toLowerCase(Locale.ROOT), stack));
            }
            choices.sort(java.util.Comparator.comparing(CurrencyChoice::name, String.CASE_INSENSITIVE_ORDER));
            currencyChoices = choices;
            currencyLanguage = language;
            currencyRegistry = registry;
            currencySearch = null;
        }
        String search = fields[4].toLowerCase(Locale.ROOT).trim();
        String cacheKey = filterPicker + ":" + search;
        if (!cacheKey.equals(currencySearch)) {
            List<CurrencyChoice> choices = new ArrayList<>();
            if (filterPicker && search.isEmpty()) choices.add(new CurrencyChoice("", tr("any_payment"), "", ItemStack.EMPTY));
            if ((AntOSPlayerText.currencyName() + " antcoins").toLowerCase(Locale.ROOT).contains(search)) choices.add(new CurrencyChoice("antcoins", AntOSPlayerText.currencyName(), "", ItemStack.EMPTY));
            for (CurrencyChoice choice : currencyChoices) if (choice.searchable().contains(search)) choices.add(choice);
            filteredCurrencies = choices;
            currencySearch = cacheKey;
        }
        visibleRows = Math.max(1, (h - 24) / 30);
        maximumScroll = Math.max(0, filteredCurrencies.size() - visibleRows);
        scroll = Math.min(scroll, maximumScroll);
        for (int index = scroll; index < Math.min(filteredCurrencies.size(), scroll + visibleRows); index++) {
            CurrencyChoice choice = filteredCurrencies.get(index);
            int rowY = y + 24 + (index - scroll) * 30;
            button(g, x, rowY, w - 8, 28, "", choice.id().equals(filterPicker ? currency : paymentItem), true, () -> {
                if (filterPicker) { currency = choice.id(); sort = "newest"; page = 0; query(); } else paymentItem = choice.id();
                picking = false; scroll = pickerScroll; focus = -1;
            });
            if (!choice.icon().isEmpty()) drawItem(g, choice.icon(), x + 5, rowY + 5);
            label(g, choice.name(), x + 28, rowY + 4, w - 41, Ui.TEXT);
            label(g, choice.id(), x + 28, rowY + 16, w - 41, Ui.TEXT_PLACEHOLDER);
        }
        if (filteredCurrencies.isEmpty()) label(g, tr("no_currencies"), x + 5, y + 36, w - 10, Ui.TEXT_PLACEHOLDER);
        kit.scrollbar(g, x + w - 3, y + 24, visibleRows * 30, visibleRows, filteredCurrencies.size(), scroll, maximumScroll, value -> scroll = value);
    }

    private record CurrencyChoice(String id, String name, String searchable, ItemStack icon) { }

    private void number(GuiGraphics g, int x, int y, int w, int index, int maximum) {
        button(g, x, y, 20, 18, "−", false, pending == null && positive(index) > 1, () -> { fields[index] = Integer.toString(positive(index) - 1); confirm = false; });
        field(g, x + 23, y, w - 46, index, "1");
        button(g, x + w - 20, y, 20, 18, "+", false, pending == null && positive(index) < maximum, () -> { fields[index] = Integer.toString(positive(index) + 1); confirm = false; });
    }

    private void drawItem(GuiGraphics g, ItemStack item, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, Ui.PANEL);
        g.renderItem(item, x, y);
        g.renderItemDecorations(font, item, x, y);
        if (kit.hovered(x, y, 18, 18)) {
            List<Component> lines = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(), item);
            kit.tooltip(String.join("\n", lines.stream().map(Component::getString).toList()));
        }
    }

    private void field(GuiGraphics g, int x, int y, int w, int index, String placeholder) {
        kit.field(g, x, y, w, 18, fields[index], focus == index ? cursor : fields[index].length(), focus == index, placeholder);
        if (kit.hovered(x, y, w, 18) && focus != index) kit.outline(g, x, y, w, 18, Ui.FOCUS);
        if (focus == index && selectedText && !fields[index].isEmpty()) {
            g.fill(x + 3, y + 3, x + w - 3, y + 15, Ui.HOVER);
            label(g, fields[index], x + 5, y + 5, w - 10, Ui.TEXT);
        }
        kit.hit(x, y, w, 18, () -> { focus = index; cursor = fields[index].length(); selectedText = index != 0 && index != 4; });
    }

    private void label(GuiGraphics g, String value, int x, int y, int width, int color) {
        String shown = screen.trimToWidth(value, Math.max(1, width));
        g.drawString(font, shown, x, y, color, false);
        if (!shown.equals(value) && kit.hovered(x, y - 1, Math.max(1, width), 11)) kit.tooltip(value);
    }

    private long owned(String id) {
        if (id.equals("antcoins")) return data.has("wallet") ? data.get("wallet").getAsLong() : 0;
        ItemStack prototype = plain(id);
        if (!data.has("inventory")) return 0;
        long amount = 0;
        for (JsonElement element : data.getAsJsonArray("inventory")) {
            if (element.isJsonNull()) continue;
            ItemStack item = stack(element.getAsJsonObject());
            if (ItemStack.isSameItemSameTags(item, prototype)) amount += item.getCount();
        }
        return amount;
    }

    private ItemStack stack(JsonObject object) {
        ItemStack cached = previews.get(object);
        if (cached != null) return cached;
        try {
            ItemStack decoded = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().level.registryAccess()), object).getOrThrow(false, message -> { });
            if (previews.size() > 512) previews.clear();
            previews.put(object, decoded);
            return decoded;
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack plain(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    private static String itemName(String id) {
        if (id.equals("antcoins")) return AntOSPlayerText.currencyName();
        ItemStack item = plain(id);
        return item.isEmpty() ? id : item.getHoverName().getString();
    }

    private static String paymentLabel(JsonObject payment, int lots) {
        return (long) integer(payment, "quantity") * lots + " " + itemName(string(payment, "item"));
    }

    private boolean validNumber(int index) {
        try { return Integer.parseInt(fields[index]) > 0; } catch (NumberFormatException exception) { return false; }
    }

    private int positive(int index) {
        return validNumber(index) ? Integer.parseInt(fields[index]) : 1;
    }

    private static int integer(JsonObject value, String key) { return value.get(key).getAsInt(); }
    private static String string(JsonObject value, String key) { return value.has(key) ? value.get(key).getAsString() : ""; }
    private static String tr(String key) { return Component.translatable("computer.antos.merchant." + key).getString(); }

    boolean click(double x, double y) {
        if (pending != null) {
            if (waiting == 0) kit.click(x, y, 0);
            return true;
        }
        focus = -1;
        return kit.click(x, y, 0);
    }

    boolean scroll(double x, double y, double amount) {
        if (pending != null) return true;
        if (tutorial) tutorialScroll = Math.max(0, Math.min(maximumScroll, tutorialScroll - (int) Math.signum(amount)));
        else if (listing()) turnPage(-(int) Math.signum(amount));
        else scroll = Math.max(0, Math.min(maximumScroll, scroll - (int) Math.signum(amount)));
        return true;
    }

    boolean drag(double y) { if (pending != null || !kit.draggingScrollbar()) return false; kit.drag(y); return true; }
    boolean release() { kit.release(); return true; }

    boolean typing() { return tutorial || focus >= 0; }

    boolean type(int codepoint) {
        if (tutorial) return true;
        if (focus < 0 || pending != null || Character.isISOControl(codepoint) || fields[focus].length() >= 128 && !selectedText) return false;
        if (focus != 0 && focus != 4 && !Character.isDigit(codepoint)) return true;
        if (selectedText) { fields[focus] = ""; cursor = 0; selectedText = false; }
        String inserted = new String(Character.toChars(codepoint));
        fields[focus] = fields[focus].substring(0, cursor) + inserted + fields[focus].substring(cursor);
        cursor += inserted.length();
        confirm = false;
        if (focus == 4) scroll = 0;
        return true;
    }

    boolean key(int code) {
        if (tutorial) {
            if (code == GLFW.GLFW_KEY_ESCAPE) finishTutorial();
            else if (code == GLFW.GLFW_KEY_TAB) kit.focusNext(net.minecraft.client.gui.screens.Screen.hasShiftDown());
            else if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER || code == GLFW.GLFW_KEY_SPACE) {
                if (!kit.activateFocused()) tutorialNext();
            } else if (code == GLFW.GLFW_KEY_RIGHT) tutorialNext();
            else if (code == GLFW.GLFW_KEY_LEFT) tutorialBack();
            else if (code == GLFW.GLFW_KEY_DOWN || code == GLFW.GLFW_KEY_UP) tutorialScroll = Math.max(0, Math.min(maximumScroll, tutorialScroll + (code == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
            return true;
        }
        if (pending != null) {
            if (waiting == 0 && canTrade() && (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER)) { waiting = 120; ComputerNetworking.merchant(pending.toString()); }
            return true;
        }
        if (code == GLFW.GLFW_KEY_TAB) { focus = -1; kit.focusNext(net.minecraft.client.gui.screens.Screen.hasShiftDown()); return true; }
        if (code == GLFW.GLFW_KEY_ESCAPE) {
            if (picking) { picking = false; scroll = pickerScroll; } else if (confirm) confirm = false; else if (mode.equals("create") && stage > 0) { stage--; scroll = 0; } else { mode = "list"; scroll = listScroll; }
            focus = -1; return true;
        }
        if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER) {
            if (focus == 0) { page = 0; query(); } else kit.activateFocused();
            return true;
        }
        if (focus < 0) return false;
        boolean control = net.minecraft.client.gui.screens.Screen.hasControlDown();
        if (control && code == GLFW.GLFW_KEY_A) { selectedText = true; cursor = fields[focus].length(); return true; }
        if (control && code == GLFW.GLFW_KEY_V) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            clipboard.codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(this::type);
            return true;
        }
        if (selectedText && (code == GLFW.GLFW_KEY_BACKSPACE || code == GLFW.GLFW_KEY_DELETE)) { fields[focus] = ""; cursor = 0; selectedText = false; confirm = false; return true; }
        selectedText = false;
        String value = fields[focus];
        if (code == GLFW.GLFW_KEY_BACKSPACE && cursor > 0) { fields[focus] = value.substring(0, value.offsetByCodePoints(cursor, -1)) + value.substring(cursor); cursor = value.offsetByCodePoints(cursor, -1); }
        else if (code == GLFW.GLFW_KEY_DELETE && cursor < value.length()) fields[focus] = value.substring(0, cursor) + value.substring(value.offsetByCodePoints(cursor, 1));
        else if (code == GLFW.GLFW_KEY_LEFT) cursor = cursor > 0 ? value.offsetByCodePoints(cursor, -1) : 0;
        else if (code == GLFW.GLFW_KEY_RIGHT) cursor = cursor < value.length() ? value.offsetByCodePoints(cursor, 1) : value.length();
        else if (code == GLFW.GLFW_KEY_HOME) cursor = 0;
        else if (code == GLFW.GLFW_KEY_END) cursor = value.length();
        confirm = false;
        if (focus == 4) scroll = 0;
        return true;
    }

    JsonObject saveResume() {
        JsonObject value = new JsonObject();
        value.addProperty("view", view); value.addProperty("search", fields[0]); value.addProperty("currency", currency); value.addProperty("sort", sort);
        value.addProperty("page", page); value.addProperty("canPay", canPay); value.addProperty("showDispatched", showDispatched);
        if (pending != null) value.add("pending", pending.deepCopy());
        if (offered != null) {
            value.add("offered", offered.deepCopy()); value.add("payments", payments.deepCopy()); value.addProperty("slot", slot);
            value.addProperty("quantity", fields[1]); value.addProperty("lots", fields[2]); value.addProperty("stage", stage);
        }
        return value;
    }

    void loadResume(JsonObject value) {
        if (value.has("view") && List.of("browse", "mine", "deliveries", "history").contains(string(value, "view"))) view = string(value, "view");
        if (value.has("search")) fields[0] = string(value, "search");
        if (value.has("currency")) currency = string(value, "currency");
        if (value.has("sort")) sort = string(value, "sort");
        if (value.has("page")) page = Math.max(0, integer(value, "page"));
        if (value.has("canPay")) canPay = value.get("canPay").getAsBoolean();
        if (value.has("showDispatched")) showDispatched = value.get("showDispatched").getAsBoolean();
        if (value.has("pending")) { pending = value.getAsJsonObject("pending").deepCopy(); waiting = 0; }
        if (value.has("offered")) {
            offered = value.getAsJsonObject("offered").deepCopy(); payments.asList().clear(); payments.addAll(value.getAsJsonArray("payments"));
            slot = integer(value, "slot"); fields[1] = string(value, "quantity"); fields[2] = string(value, "lots"); stage = Math.max(0, Math.min(2, integer(value, "stage")));
        }
    }
}
