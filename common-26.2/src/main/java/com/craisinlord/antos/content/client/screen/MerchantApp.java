package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.AntOSPlayerText;
import com.craisinlord.antos.content.client.MerchantClientState;
import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.network.ComputerNetworking;
import com.craisinlord.antos.content.merchant.MerchantProtocol;
import com.craisinlord.antos.content.merchant.MerchantIdentity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

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
    private static final int TUTORIAL_STEPS = 6;
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
    private int page;
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
                            error = false;
                        }
                        selected = fresh.deepCopy();
                    }
                }
            }
            if (!success) {
                notice = tr("error." + string(result, "error"));
                error = true;
                if (mutation) {
                    if (!List.of("storage_error", "recovery_pending", "recovery_conflict", "invalid_response", "result_too_large").contains(string(result, "error"))) pending = null;
                }
                if (mutation || !current && pending != null) waiting = 0;
                if (mutation && pending == null) { confirm = false; query(); }
            } else if (mutation && result.has("notice")) {
                notice = tr("success." + string(result, "notice"));
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
                query();
            }
        }
        if (waiting > 0) waiting--;
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
        request.addProperty("query", queryId);
        request.addProperty("showDispatched", showDispatched);
        if (mode.equals("detail") && selected != null && !view.equals("history")) request.addProperty("selected", string(selected, "id"));
        return request;
    }

    private void mutate(JsonObject request) {
        if (pending != null) return;
        queryId = UUID.randomUUID().toString();
        JsonObject query = queryFields();
        for (var entry : query.entrySet()) request.add(entry.getKey(), entry.getValue());
        request.addProperty("request", UUID.randomUUID().toString());
        pending = request;
        waiting = 120;
        ComputerNetworking.merchant(request.toString());
    }

    void render(ThemedGraphics g, int x, int y, int w, int h) {
        kit.begin();
        maximumScroll = 0;
        String workspace = com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of();
        if (pending == null && !workspace.equals("workspace:unbound") && !workspace.equals(tutorialWorkspace)) {
            tutorialWorkspace = workspace;
            if (!com.craisinlord.antos.config.AntOSSettings.hasCompletedMerchantTour(workspace)) openTutorial(true);
        }
        if (tutorial) {
            renderTutorial(g, x, y, w, h);
            kit.drawFocus(g);
            kit.drawTooltip(g, x, y, x + w, y + h);
            return;
        }
        int tabW = Math.max(38, (w - 74) / 3);
        String[] views = {"browse", "mine", "deliveries"};
        for (int index = 0; index < views.length; index++) {
            String target = views[index];
            String label = tr(target);
            if (target.equals("deliveries") && data.has("pending") && data.get("pending").getAsInt() > 0) label += " " + data.get("pending").getAsInt();
            kit.button(g, x + index * tabW, y, tabW - 2, 18, label, view.equals(target) || target.equals("mine") && view.equals("history"), pending == null, () -> {
                view = target; mode = "list"; picking = false; confirm = false; page = 0; scroll = 0; focus = -1; query();
            });
        }
        kit.button(g, x + w - 70, y, 70, 18, tr("list_item"), mode.equals("create"), pending == null, () -> {
            listScroll = scroll; mode = "create"; stage = offered == null ? 0 : stage; scroll = 0; focus = -1; picking = false;
        });
        int top = y + 23;
        int height = h - 46;
        if (picking) renderPicker(g, x, top, w, height);
        else if (mode.equals("create")) renderCreate(g, x, top, w, height);
        else if (mode.equals("detail") && selected != null) renderDetail(g, x, top, w, height);
        else renderList(g, x, top, w, height);
        String status = pending != null ? waiting > 0 ? tr("working") : tr("retry_hint") : notice;
        String balance = data.has("wallet") ? AntOSPlayerText.currencySymbol() + " " + data.get("wallet").getAsLong() : "";
        int helpWidth = pending == null ? 66 : 0;
        int balanceWidth = pending == null && !balance.isEmpty() ? Math.min(Math.max(0, w / 3), font.width(balance) + 8) : 0;
        label(g, status, x, y + h - 15, pending != null ? w - 58 : w - balanceWidth - helpWidth, error ? Ui.ERROR : Ui.TEXT_PLACEHOLDER);
        if (balanceWidth > 0) label(g, balance, x + w - balanceWidth - helpWidth + 4, y + h - 15, balanceWidth - 4, Ui.ACCENT);
        if (pending == null) kit.button(g, x + w - 64, y + h - 19, 64, 18, tr("tutorial.open"), false, true, () -> openTutorial(false));
        if (pending != null) kit.begin();
        if (pending != null && waiting == 0) kit.button(g, x + w - 54, y + h - 19, 54, 18, tr("retry"), false, true, () -> {
            waiting = 120; ComputerNetworking.merchant(pending.toString());
        });
        if (!status.isEmpty() && kit.hovered(x, y + h - 19, w - balanceWidth - helpWidth, 18)) kit.tooltip(status);
        kit.drawFocus(g);
        kit.drawTooltip(g, x, y, x + w, y + h);
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

    private void tutorialNext() {
        if (tutorialStep + 1 == TUTORIAL_STEPS) finishTutorial();
        else { tutorialStep++; tutorialScroll = 0; kit.begin(); }
    }

    private void tutorialBack() {
        tutorialStep = Math.max(0, tutorialStep - 1);
        tutorialScroll = 0;
        kit.begin();
    }

    private void renderTutorial(ThemedGraphics g, int x, int y, int w, int h) {
        label(g, tr("tutorial.header"), x, y + 4, w - 42, Ui.TEXT_STRONG);
        label(g, (tutorialStep + 1) + " / " + TUTORIAL_STEPS, x + w - 36, y + 4, 36, Ui.ACCENT);
        for (int index = 0; index < TUTORIAL_STEPS; index++) {
            int left = x + index * w / TUTORIAL_STEPS;
            int right = x + (index + 1) * w / TUTORIAL_STEPS - 3;
            g.fill(left, y + 19, right, y + 22, index <= tutorialStep ? Ui.ACCENT : Ui.BORDER_INACTIVE);
        }
        int top = y + 29;
        int bottom = y + h - 27;
        g.fill(x, top, x + w, bottom, Ui.PANEL);
        kit.outline(g, x, top, w, bottom - top, Ui.BORDER_INACTIVE);
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(font.split(Component.literal(tr("tutorial." + tutorialStep + ".title")), Math.max(1, w - 28)));
        int titleLines = lines.size();
        for (int paragraph = 0; paragraph < 3; paragraph++) {
            lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            lines.addAll(font.split(Component.literal(tr("tutorial." + tutorialStep + ".text." + paragraph)), Math.max(1, w - 28)));
        }
        int visible = Math.max(1, (bottom - top - 16) / 11);
        maximumScroll = Math.max(0, lines.size() - visible);
        tutorialScroll = Math.min(tutorialScroll, maximumScroll);
        for (int row = 0; row < visible && row + tutorialScroll < lines.size(); row++) {
            int line = row + tutorialScroll;
            g.drawString(font, lines.get(line), x + 9, top + 8 + row * 11, line < titleLines ? Ui.ACCENT : Ui.TEXT, false);
        }
        kit.scrollbar(g, x + w - 5, top + 5, bottom - top - 10, visible, lines.size(), tutorialScroll, maximumScroll, value -> tutorialScroll = value);
        int buttonWidth = (w - 8) / 3;
        kit.button(g, x, y + h - 20, buttonWidth, 18, tr(firstTutorial ? "tutorial.skip" : "tutorial.close"), false, true, this::finishTutorial);
        kit.button(g, x + buttonWidth + 4, y + h - 20, buttonWidth, 18, tr("back"), false, tutorialStep > 0, this::tutorialBack);
        kit.button(g, x + (buttonWidth + 4) * 2, y + h - 20, w - (buttonWidth + 4) * 2, 18,
                tr(tutorialStep + 1 == TUTORIAL_STEPS ? "tutorial.done" : "next"), false, true, this::tutorialNext);
    }

    private void renderList(ThemedGraphics g, int x, int y, int w, int h) {
        boolean history = view.equals("history");
        boolean listings = view.equals("browse") || view.equals("mine");
        int top = y;
        if (listings) {
            field(g, x, top, w - 64, 0, tr("search"));
            kit.button(g, x + w - 60, top, 60, 18, tr("search_button"), false, true, () -> { page = 0; scroll = 0; query(); });
            top += 22;
            if (view.equals("browse")) {
                kit.button(g, x, top, w / 2 - 2, 18, currency.isEmpty() ? tr("any_payment") : itemName(currency), false, true, () -> {
                    filterPicker = true; picking = true; fields[4] = ""; pickerScroll = scroll; scroll = 0; focus = 4; cursor = 0; selectedText = false;
                });
                kit.button(g, x + w / 2, top, w / 4 - 2, 18, tr("can_pay"), canPay, true, () -> { canPay = !canPay; page = 0; query(); });
                kit.button(g, x + w * 3 / 4, top, w / 4, 18, tr("sort." + sort), false, true, () -> {
                    sort = sort.equals("newest") ? "name" : sort.equals("name") && !currency.isEmpty() ? "price" : "newest"; page = 0; query();
                });
            } else kit.button(g, x, top, 110, 18, tr("history"), false, true, () -> { view = "history"; page = 0; scroll = 0; query(); });
            top += 23;
        } else {
            label(g, tr(history ? "history" : "deliveries_hint"), x, top + 3, w - (history ? 0 : 106), Ui.TEXT);
            if (!history) kit.button(g, x + w - 102, top, 102, 18, tr(showDispatched ? "all_shipments" : "pending_only"), showDispatched, true, () -> { showDispatched = !showDispatched; page = 0; scroll = 0; query(); });
            top += 21;
        }
        JsonArray rows = data.has("rows") && view.equals(string(data, "view")) ? data.getAsJsonArray("rows") : new JsonArray();
        int rowHeight = listings || history ? 42 : 32;
        int rowStride = rowHeight + 2;
        visibleRows = Math.max(1, (h - (top - y) - 23) / rowStride);
        scroll = Math.min(scroll, Math.max(0, rows.size() - visibleRows));
        for (int index = scroll; index < Math.min(rows.size(), scroll + visibleRows); index++) {
            JsonObject row = rows.get(index).getAsJsonObject();
            int rowY = top + (index - scroll) * rowStride;
            g.fill(x, rowY, x + w - 6, rowY + rowHeight, kit.hovered(x, rowY, w - 6, rowHeight) ? Ui.HOVER : Ui.PANEL);
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
            kit.hit(x, rowY, w - 6, rowHeight, () -> { listScroll = scroll; selected = row.deepCopy(); mode = "detail"; option = 0; fields[5] = "1"; confirm = false; focus = -1; scroll = 0; });
        }
        if (rows.isEmpty()) {
            label(g, tr(loading ? "loading" : "empty." + view), x + 6, top + 10, w - 12, Ui.TEXT_PLACEHOLDER);
            if (!loading && view.equals("browse")) kit.button(g, x + 6, top + 31, 130, 18, tr("clear_filters"), false, true, () -> { fields[0] = ""; currency = ""; canPay = false; page = 0; query(); });
        }
        maximumScroll = Math.max(0, rows.size() - visibleRows);
        kit.scrollbar(g, x + w - 3, top, Math.max(20, h - (top - y) - 24), visibleRows, rows.size(), scroll, Math.max(0, rows.size() - visibleRows), value -> scroll = value);
        int currentPage = data.has("page") ? integer(data, "page") : 0;
        int total = data.has("total") ? integer(data, "total") : 0;
        kit.button(g, x, y + h - 20, 45, 18, "<", false, currentPage > 0 && pending == null, () -> { page = currentPage - 1; scroll = 0; query(); });
        label(g, (currentPage + 1) + " / " + Math.max(1, (total + 11) / 12) + " · " + total + " " + tr("results"), x + 52, y + h - 15, w - 103, Ui.TEXT_PLACEHOLDER);
        kit.button(g, x + w - 45, y + h - 20, 45, 18, ">", false, (currentPage + 1) * 12 < total && pending == null, () -> { page = currentPage + 1; scroll = 0; query(); });
    }

    private void renderDetail(ThemedGraphics g, int x, int y, int w, int h) {
        kit.button(g, x, y, 55, 18, tr(confirm ? "change" : "back"), false, pending == null, () -> {
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
            kit.button(g, x, y + h - 21, w, 20, tr("deliver_here"), false, pending == null && string(selected, "status").equals("pending"), () -> {
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
            identityCard(g, identity(string(selected, "seller")), tr("seller"), x, top, w);
            top += 37;
            if (!own) {
                label(g, tr("buy_lots"), x, top + 5, 106, Ui.TEXT);
                number(g, x + 110, top, 112, 5, integer(selected, "lots"));
                label(g, integer(selected, "quantity") + " / " + tr("lot"), x + 229, top + 5, w - 229, Ui.TEXT_PLACEHOLDER);
                top += 23;
            }
            visibleRows = Math.max(1, (y + h - 43 - top) / 25);
            maximumScroll = Math.max(0, alternatives.size() - visibleRows);
            scroll = Math.min(scroll, maximumScroll);
            for (int index = scroll; index < Math.min(alternatives.size(), scroll + visibleRows); index++) {
                JsonObject alternative = alternatives.get(index).getAsJsonObject();
                int target = index;
                int rowY = top + (index - scroll) * 25;
                kit.button(g, x, rowY, w - 8, 23, "", index == option, pending == null && !own, () -> { option = target; confirm = false; });
                label(g, index == option ? "●" : "○", x + 5, rowY + 7, 10, Ui.ACCENT);
                if (!string(alternative, "item").equals("antcoins")) drawItem(g, plain(string(alternative, "item")), x + 18, rowY + 3);
                label(g, paymentLabel(alternative, own ? 1 : positive(5)), x + 39, rowY + 7, w - (own ? 53 : 139), Ui.TEXT);
                if (!own) label(g, tr("owned") + " " + owned(string(alternative, "item")), x + w - 96, rowY + 7, 83, Ui.TEXT_PLACEHOLDER);
            }
            kit.scrollbar(g, x + w - 3, top, visibleRows * 25, visibleRows, alternatives.size(), scroll, maximumScroll, value -> scroll = value);
            if (!own && !enough) label(g, tr(validLots ? "error.insufficient_payment" : "error.invalid_quantity"), x, y + h - 37, w, Ui.TEXT_PLACEHOLDER);
        }
        kit.button(g, x, y + h - 21, w, 20, tr(own ? confirm ? "confirm_cancel" : "cancel_listing" : confirm ? "confirm_trade" : "review_trade"), confirm, pending == null && enough && integer(selected, "lots") > 0, () -> {
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

    private void drawIdentity(ThemedGraphics g, MerchantIdentity person, int x, int y, int w, int size) {
        ProfileAvatar.draw(this, g, person.displayName(), person.avatarItem(), "", x, y, size);
        label(g, person.displayName(), x + size + 5, y + (size - 8) / 2, w - size - 5, Ui.TEXT);
        if (kit.hovered(x, y, w, size)) kit.tooltip(person.displayName() + " · " + person.address());
    }

    private void identityCard(ThemedGraphics g, MerchantIdentity person, String role, int x, int y, int w) {
        g.fill(x, y, x + w, y + 32, Ui.PANEL);
        ProfileAvatar.draw(this, g, person.displayName(), person.avatarItem(), "", x + 4, y + 4, 24);
        label(g, role + " · " + person.displayName(), x + 34, y + 5, w - 39, Ui.TEXT_STRONG);
        label(g, person.address(), x + 34, y + 18, w - 39, Ui.TEXT_PLACEHOLDER);
        if (kit.hovered(x, y, w, 32)) kit.tooltip(role + " · " + person.displayName() + " · " + person.address());
    }

    private void summary(ThemedGraphics g, int x, int y, int w, String caption, String item, long quantity) {
        g.fill(x, y, x + w, y + 43, Ui.PANEL);
        label(g, caption, x + 8, y + 5, w - 16, Ui.TEXT_PLACEHOLDER);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(quantity + " × " + item), w - 16);
        for (int index = 0; index < Math.min(2, lines.size()); index++) g.drawString(font, lines.get(index), x + 8, y + 18 + index * 10, Ui.ACCENT, false);
        if (kit.hovered(x, y, w, 43)) kit.tooltip(quantity + " × " + item);
    }

    private void renderCreate(ThemedGraphics g, int x, int y, int w, int h) {
        label(g, tr("stage." + stage), x, y + 3, w - 58, Ui.TEXT_STRONG);
        kit.button(g, x + w - 54, y, 54, 18, tr("back"), false, pending == null, () -> { if (stage > 0) stage--; else mode = "list"; focus = -1; scroll = mode.equals("list") ? listScroll : 0; });
        int top = y + 24;
        if (stage == 0) {
            JsonArray inventory = data.has("inventory") ? data.getAsJsonArray("inventory") : new JsonArray();
            long availableStock = 0;
            int cell = Math.min(24, (w - 6) / 9);
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
                        if (ItemStack.isSameItemSameComponents(current, prototype)) availableStock += current.getCount();
                    }
                }
                label(g, prototype.getHoverName().getString(), x + cell * 9 + 5, top + 8, w - cell * 9 - 5, Ui.TEXT);
                label(g, tr("available") + " " + availableStock, x + cell * 9 + 5, top + 26, w - cell * 9 - 5, Ui.TEXT_PLACEHOLDER);
                label(g, tr("deposit") + " " + ((long) positive(1) * positive(2)), x + cell * 9 + 5, top + 44, w - cell * 9 - 5, Ui.ACCENT);
            }
            top += cell * 4 + 4;
            label(g, tr("per_lot"), x, top + 4, 60, Ui.TEXT);
            number(g, x + 62, top, 104, 1, (int) Math.min(Integer.MAX_VALUE, availableStock));
            label(g, tr("lots"), x + 176, top + 4, 42, Ui.TEXT);
            number(g, x + 220, top, w - 220, 2, (int) Math.min(Integer.MAX_VALUE, availableStock / positive(1)));
            kit.button(g, x, y + h - 21, w, 20, tr("next"), false, offered != null && validNumber(1) && validNumber(2) && (long) positive(1) * positive(2) <= Math.min(Integer.MAX_VALUE, availableStock), () -> { stage = 1; scroll = 0; focus = -1; });
        } else if (stage == 1) {
            int left = Math.max(140, w / 2);
            kit.button(g, x, top, left - 8, 20, itemName(paymentItem), false, true, () -> { picking = true; filterPicker = false; fields[4] = ""; pickerScroll = scroll; scroll = 0; focus = 4; cursor = 0; selectedText = false; });
            label(g, tr("amount"), x, top + 31, 65, Ui.TEXT);
            field(g, x + 70, top + 26, left - 78, 3, "1");
            boolean existingCurrency = payments.asList().stream().anyMatch(value -> string(value.getAsJsonObject(), "item").equals(paymentItem));
            kit.button(g, x, top + 52, left - 8, 20, tr(existingCurrency ? "update_payment" : "add_alternative"), false, validNumber(3), () -> {
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
                kit.button(g, x + w - 30, rowY, 20, 18, "×", false, true, () -> payments.remove(target));
            }
            kit.scrollbar(g, x + w - 3, top + 20, visibleRows * 22, visibleRows, payments.size(), scroll, maximumScroll, value -> scroll = value);
            kit.button(g, x, y + h - 21, w, 20, tr("review"), false, !payments.isEmpty(), () -> { stage = 2; scroll = 0; focus = -1; });
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
            kit.button(g, x, y + h - 21, w, 20, tr("publish"), false, pending == null, () -> {
                JsonObject request = new JsonObject(); request.addProperty("action", "publish"); request.addProperty("slot", slot);
                request.add("expected", offered.deepCopy()); request.addProperty("quantity", positive(1)); request.addProperty("lots", positive(2)); request.add("payments", payments.deepCopy()); mutate(request);
            });
        }
    }

    private void renderPicker(ThemedGraphics g, int x, int y, int w, int h) {
        field(g, x, y, w - 60, 4, tr("search_currency"));
        kit.button(g, x + w - 56, y, 56, 18, tr("back"), false, true, () -> { picking = false; scroll = pickerScroll; focus = -1; });
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
            kit.button(g, x, rowY, w - 8, 28, "", choice.id().equals(filterPicker ? currency : paymentItem), true, () -> {
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

    private void number(ThemedGraphics g, int x, int y, int w, int index, int maximum) {
        kit.button(g, x, y, 20, 18, "−", false, pending == null && positive(index) > 1, () -> { fields[index] = Integer.toString(positive(index) - 1); confirm = false; });
        field(g, x + 23, y, w - 46, index, "1");
        kit.button(g, x + w - 20, y, 20, 18, "+", false, pending == null && positive(index) < maximum, () -> { fields[index] = Integer.toString(positive(index) + 1); confirm = false; });
    }

    private void drawItem(ThemedGraphics g, ItemStack item, int x, int y) {
        g.renderItem(item, x, y);
        g.source().itemDecorations(font, item, x, y);
        if (kit.hovered(x, y, 18, 18)) {
            List<Component> lines = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(), item);
            kit.tooltip(String.join("\n", lines.stream().map(Component::getString).toList()));
        }
    }

    private void field(ThemedGraphics g, int x, int y, int w, int index, String placeholder) {
        kit.field(g, x, y, w, 18, fields[index], focus == index ? cursor : fields[index].length(), focus == index, placeholder);
        if (focus == index && selectedText && !fields[index].isEmpty()) {
            g.fill(x + 3, y + 3, x + w - 3, y + 15, Ui.HOVER);
            label(g, fields[index], x + 5, y + 5, w - 10, Ui.TEXT);
        }
        kit.hit(x, y, w, 18, () -> { focus = index; cursor = fields[index].length(); selectedText = index != 0 && index != 4; });
    }

    private void label(ThemedGraphics g, String value, int x, int y, int width, int color) {
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
            if (ItemStack.isSameItemSameComponents(item, prototype)) amount += item.getCount();
        }
        return amount;
    }

    private ItemStack stack(JsonObject object) {
        ItemStack cached = previews.get(object);
        if (cached != null) return cached;
        try {
            ItemStack decoded = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().level.registryAccess()), object).getOrThrow();
            if (previews.size() > 512) previews.clear();
            previews.put(object, decoded);
            return decoded;
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack plain(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(key));
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
            if (code == InputConstants.KEY_ESCAPE) finishTutorial();
            else if (code == InputConstants.KEY_TAB) kit.focusNext((com.craisinlord.antos.content.client.KeyboardState.isDown(InputConstants.KEY_LSHIFT) || com.craisinlord.antos.content.client.KeyboardState.isDown(InputConstants.KEY_RSHIFT)));
            else if (code == InputConstants.KEY_RETURN || code == InputConstants.KEY_NUMPADENTER || code == InputConstants.KEY_SPACE) {
                if (!kit.activateFocused()) tutorialNext();
            } else if (code == InputConstants.KEY_RIGHT) tutorialNext();
            else if (code == InputConstants.KEY_LEFT) tutorialBack();
            else if (code == InputConstants.KEY_DOWN || code == InputConstants.KEY_UP) tutorialScroll = Math.max(0, Math.min(maximumScroll, tutorialScroll + (code == InputConstants.KEY_DOWN ? 1 : -1)));
            return true;
        }
        if (pending != null) {
            if (waiting == 0 && (code == InputConstants.KEY_RETURN || code == InputConstants.KEY_NUMPADENTER)) { waiting = 120; ComputerNetworking.merchant(pending.toString()); }
            return true;
        }
        if (code == InputConstants.KEY_TAB) { focus = -1; kit.focusNext((com.craisinlord.antos.content.client.KeyboardState.isDown(InputConstants.KEY_LSHIFT) || com.craisinlord.antos.content.client.KeyboardState.isDown(InputConstants.KEY_RSHIFT))); return true; }
        if (code == InputConstants.KEY_ESCAPE) {
            if (picking) { picking = false; scroll = pickerScroll; } else if (confirm) confirm = false; else if (mode.equals("create") && stage > 0) { stage--; scroll = 0; } else { mode = "list"; scroll = listScroll; }
            focus = -1; return true;
        }
        if (code == InputConstants.KEY_RETURN || code == InputConstants.KEY_NUMPADENTER) {
            if (focus == 0) { page = 0; query(); } else kit.activateFocused();
            return true;
        }
        if (focus < 0) return false;
        boolean control = (com.craisinlord.antos.content.client.KeyboardState.isDown(InputConstants.KEY_LCONTROL) || com.craisinlord.antos.content.client.KeyboardState.isDown(InputConstants.KEY_RCONTROL));
        if (control && code == InputConstants.KEY_A) { selectedText = true; cursor = fields[focus].length(); return true; }
        if (control && code == InputConstants.KEY_V) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            clipboard.codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(this::type);
            return true;
        }
        if (selectedText && (code == InputConstants.KEY_BACKSPACE || code == InputConstants.KEY_DELETE)) { fields[focus] = ""; cursor = 0; selectedText = false; confirm = false; return true; }
        selectedText = false;
        String value = fields[focus];
        if (code == InputConstants.KEY_BACKSPACE && cursor > 0) { fields[focus] = value.substring(0, value.offsetByCodePoints(cursor, -1)) + value.substring(cursor); cursor = value.offsetByCodePoints(cursor, -1); }
        else if (code == InputConstants.KEY_DELETE && cursor < value.length()) fields[focus] = value.substring(0, cursor) + value.substring(value.offsetByCodePoints(cursor, 1));
        else if (code == InputConstants.KEY_LEFT) cursor = cursor > 0 ? value.offsetByCodePoints(cursor, -1) : 0;
        else if (code == InputConstants.KEY_RIGHT) cursor = cursor < value.length() ? value.offsetByCodePoints(cursor, 1) : value.length();
        else if (code == InputConstants.KEY_HOME) cursor = 0;
        else if (code == InputConstants.KEY_END) cursor = value.length();
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
