package com.craisinlord.antos.content.network;

import com.craisinlord.antos.content.computer.ComputerWorkspace;
import com.craisinlord.antos.content.computer.ComputerFileSystem;
import com.craisinlord.antos.content.computer.terminal.TerminalCommandService;
import com.craisinlord.antos.content.computer.terminal.TerminalFileSystem;
import com.craisinlord.antos.content.computer.terminal.TerminalResult;
import com.craisinlord.antos.content.antmail.AntmailWire;
import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.computer.blockle.BlockleAnswers;
import com.craisinlord.antos.content.computer.blockle.BlockleDictionary;
import com.craisinlord.antos.content.computer.blockle.BlockleGame;
import com.craisinlord.antos.content.computer.blockle.BlockleSavedData;
import com.craisinlord.antos.content.antazon.AntazonData;
import com.craisinlord.antos.content.antazon.AntazonService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.function.BiConsumer;

public final class ComputerAccessHandler {
    private static BiConsumer<ServerPlayer, AnternetComputerResultPayload> anternetResultSender = (player, result) -> {
    };
    private ComputerAccessHandler() {
    }

    public static void setAnternetResultSender(BiConsumer<ServerPlayer, AnternetComputerResultPayload> sender) {
        anternetResultSender = sender;
    }

    public static void sendAntazonShipmentStatus(ServerPlayer player, java.util.UUID accountId, String status, long amount) {
        ComputerWorkspaceData.AccountInfo account = AnternetAccountHandler.session(player);
        if (account == null || !account.accountId().equals(accountId)) return;
        anternetResultSender.accept(player, new AnternetComputerResultPayload(ComputerAccessResultPayload.SUCCESS,
                true, true, ComputerAccessPayload.ANTAZON_SELL + "\0" + status + "\0" + amount));
    }

    public static void handleAnternet(ServerPlayer player, AnternetComputerPayload payload) {
        handle(player, new ComputerAccessPayload(payload.action(), payload.value()));
    }

    private static void sendResult(ServerPlayer player, ComputerAccessResultPayload result) {
        anternetResultSender.accept(player, new AnternetComputerResultPayload(result.result(), result.hasPassword(), result.authenticated(), result.data()));
    }

    private static void handle(ServerPlayer player, ComputerAccessPayload payload) {
        if (payload.value().length() > 65536 || player.level().isClientSide) {
            return;
        }
        ComputerWorkspaceData.AccountInfo account = AnternetAccountHandler.session(player);
        if (account == null || !remoteActionAllowed(payload.action())) {
            send(player, payload, ComputerAccessResultPayload.INVALID);
            return;
        }
        ComputerWorkspace computer = new com.craisinlord.antos.content.computer.AccountWorkspace(player, account);
        switch (payload.action()) {
            case ComputerAccessPayload.OPEN -> open(player, computer, payload);
            case ComputerAccessPayload.LOGOUT -> {
                computer.logout();
                send(player, payload, ComputerAccessResultPayload.READY);
            }
            case ComputerAccessPayload.CLOSE -> {
                computer.releaseUser(player);
                send(player, payload, computer.isAuthenticated() ? ComputerAccessResultPayload.SUCCESS : ComputerAccessResultPayload.READY);
            }
            case ComputerAccessPayload.EJECT -> eject(player, computer, payload);
            case ComputerAccessPayload.FILE_LIST -> fileList(player, computer, payload);
            case ComputerAccessPayload.FILE_OPEN -> fileOpen(player, computer, payload);
            case ComputerAccessPayload.FILE_CREATE -> fileCreate(player, computer, payload);
            case ComputerAccessPayload.FILE_SAVE -> fileSave(player, computer, payload);
            case ComputerAccessPayload.FILE_DELETE -> fileDelete(player, computer, payload);
            case ComputerAccessPayload.FILE_MOVE -> fileMove(player, computer, payload);
            case ComputerAccessPayload.TERMINAL_COMMAND -> terminalCommand(player, computer, payload);
            case ComputerAccessPayload.DESKTOP_STATE -> desktopState(player, computer, payload);
            case ComputerAccessPayload.DESKTOP_WALLPAPER -> desktopWallpaper(player, computer, payload);
            case ComputerAccessPayload.LOCATE_STRUCTURE -> locateStructure(player, computer, payload);
            case ComputerAccessPayload.TASK_STATE -> taskState(player, computer, payload);
            case ComputerAccessPayload.TASK_CLAIM_REWARD -> claimTaskReward(player, computer, payload);
            case ComputerAccessPayload.TEAM_CREATE, ComputerAccessPayload.TEAM_INVITE,
                    ComputerAccessPayload.TEAM_ACCEPT_INVITE, ComputerAccessPayload.TEAM_DECLINE_INVITE,
                    ComputerAccessPayload.TEAM_LEAVE, ComputerAccessPayload.TEAM_DISBAND -> teamAction(player, computer, payload);
            case ComputerAccessPayload.ARCHIVE_VIEWED -> archiveViewed(player, computer, payload);
            case ComputerAccessPayload.BLOCKLE_STATE -> blockle(player, computer, payload, false);
            case ComputerAccessPayload.BLOCKLE_GUESS -> blockle(player, computer, payload, true);
            case ComputerAccessPayload.ANTAZON_STATE -> antazonState(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_PURCHASE -> antazonPurchase(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_WISHLIST -> antazonWishlist(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_ORDERS -> antazonOrders(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_REVIEW -> antazonReview(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_WALLET -> antazonWallet(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_SELL -> antazonSell(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_SELL_STATE -> antazonSellState(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_PRICES -> antazonPrices(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_PREPARE -> antazonPrepare(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_CRATE_LINK -> antazonCrateLink(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_ONBOARDING -> antazonOnboarding(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_ONBOARDING_COMPLETE -> antazonOnboardingComplete(player, computer, payload, true);
            case ComputerAccessPayload.ANTAZON_ONBOARDING_RESET -> antazonOnboardingComplete(player, computer, payload, false);
            case ComputerAccessPayload.ANTAZON_CART -> antazonCart(player, computer, payload);
            case ComputerAccessPayload.ANTAZON_CHECKOUT -> antazonCheckout(player, computer, payload);
            default -> send(player, payload, ComputerAccessResultPayload.INVALID);
        }
        if (computer.isAuthenticatedBy(player)) computer.saveAccountWorkspace();
    }

    private static boolean remoteActionAllowed(int action) {
        return switch (action) {
            case ComputerAccessPayload.OPEN, ComputerAccessPayload.CLOSE, ComputerAccessPayload.LOGOUT, ComputerAccessPayload.EJECT,
                    ComputerAccessPayload.FILE_LIST, ComputerAccessPayload.FILE_OPEN, ComputerAccessPayload.FILE_CREATE,
                    ComputerAccessPayload.FILE_SAVE, ComputerAccessPayload.FILE_DELETE, ComputerAccessPayload.FILE_MOVE,
                    ComputerAccessPayload.TERMINAL_COMMAND, ComputerAccessPayload.DESKTOP_STATE,
                    ComputerAccessPayload.DESKTOP_WALLPAPER, ComputerAccessPayload.TASK_STATE,
                    ComputerAccessPayload.ARCHIVE_VIEWED, ComputerAccessPayload.LOCATE_STRUCTURE,
                    ComputerAccessPayload.ANTAZON_STATE,
                    ComputerAccessPayload.BLOCKLE_STATE, ComputerAccessPayload.BLOCKLE_GUESS,
                    ComputerAccessPayload.ANTAZON_PURCHASE, ComputerAccessPayload.ANTAZON_WISHLIST,
                    ComputerAccessPayload.ANTAZON_ORDERS, ComputerAccessPayload.ANTAZON_REVIEW,
                    ComputerAccessPayload.ANTAZON_WALLET,
                    ComputerAccessPayload.ANTAZON_SELL, ComputerAccessPayload.ANTAZON_SELL_STATE,
                    ComputerAccessPayload.ANTAZON_PREPARE, ComputerAccessPayload.ANTAZON_CRATE_LINK,
                    ComputerAccessPayload.ANTAZON_PRICES, ComputerAccessPayload.ANTAZON_ONBOARDING,
                    ComputerAccessPayload.ANTAZON_ONBOARDING_COMPLETE, ComputerAccessPayload.ANTAZON_ONBOARDING_RESET,
                    ComputerAccessPayload.ANTAZON_CART, ComputerAccessPayload.ANTAZON_CHECKOUT,
                    ComputerAccessPayload.TASK_CLAIM_REWARD, ComputerAccessPayload.TEAM_CREATE, ComputerAccessPayload.TEAM_INVITE,
                    ComputerAccessPayload.TEAM_ACCEPT_INVITE, ComputerAccessPayload.TEAM_DECLINE_INVITE,
                    ComputerAccessPayload.TEAM_LEAVE, ComputerAccessPayload.TEAM_DISBAND -> true;
            default -> false;
        };
    }

    private static void blockle(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload, boolean submit) {
        if (!computer.canUseFileSystem(player)) {
            sendBlockle(player, payload, false, "unauthorized", "");
            return;
        }
        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            sendBlockle(player, payload, false, "overworld_unavailable", "");
            return;
        }
        long day = overworld.getDayTime() / 24000L;
        String key = "workspace:" + computer.workspaceId();
        BlockleSavedData.State state = BlockleSavedData.get(player.server, key);
        if (state.day != day) {
            state.day = day;
            state.guesses.clear();
            BlockleSavedData.changed(player.server);
        }
        BlockleAnswers.Answer answer = BlockleAnswers.answerForDay(day);
        if (answer == null) {
            sendBlockle(player, payload, false, "answers_unavailable", "");
            return;
        }
        if (submit) {
            String guess = payload.value().toLowerCase(java.util.Locale.ROOT);
            if (guess.length() != 5 || !guess.chars().allMatch(value -> value >= 'a' && value <= 'z') || !BlockleDictionary.contains(guess)) {
                sendBlockle(player, payload, false, "invalid_word", "");
                return;
            }
            if (state.guesses.size() >= 6 || state.guesses.contains(answer.word())) {
                sendBlockle(player, payload, false, "game_over", "");
                return;
            }
            state.guesses.add(guess);
            BlockleSavedData.changed(player.server);
        }
        StringBuilder encoded = new StringBuilder(Long.toString(day));
        boolean solved = false;
        for (String guess : state.guesses) {
            encoded.append('|').append(guess).append(',').append(BlockleGame.evaluate(answer.word(), guess));
            if (guess.equals(answer.word())) solved = true;
        }
        if (solved || state.guesses.size() >= 6) encoded.append('|').append(solved ? "SOLVED" : "FAILED").append('|').append(answer.word()).append('|').append(answer.itemId());
        else encoded.append('|').append("PLAYING");
        sendBlockle(player, payload, true, "", encoded.toString());
    }

    private static void antazonState(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendAntazon(player, payload, false, "unauthorized", "");
            return;
        }
        // The static catalogue only travels when the client's cached copy is from another reload; every refresh
        // otherwise carries just the per-player fields (stock, limits, prices, ownership, player reviews).
        long gameTime = player.server.overworld().getGameTime();
        long day = gameTime / 24000L;
        String catalogVersion = AntazonData.catalogVersion(day);
        boolean sendCatalog = !catalogVersion.equals(payload.value());
        com.google.gson.JsonArray catalog = new com.google.gson.JsonArray();
        com.google.gson.JsonArray states = new com.google.gson.JsonArray();
        var antazonData = com.craisinlord.antos.content.antazon.AntazonServerData.access(player.server);
        java.util.UUID profile = AntazonService.accountOwner(computer, player);
        ComputerWorkspaceData workspaceData = ComputerWorkspaceData.access(player.server);
        for (AntazonData.Product product : AntazonData.products()) {
            if (!product.enabled() || !AntazonData.availableOn(product, day)) continue;
            if (sendCatalog) catalog.add(AntazonData.catalogRow(product.id()));
            com.google.gson.JsonObject row = new com.google.gson.JsonObject();
            row.addProperty("id", product.id().toString());
            AntazonData.Availability availability = product.availability();
            var stock = AntazonService.currentStock(antazonData, product, day);
            row.addProperty("remaining", availability.serverStock() > 0 ? stock.remaining() : -1);
            row.addProperty("restock_in_ms", AntazonService.restockInMillis(product, stock, gameTime));
            row.addProperty("restock_real", availability.restockRealHours() > 0L);
            row.addProperty("limit_used", availability.playerLimit() > 0 ? AntazonService.limitUsed(antazonData, profile, product, day) : 0);
            row.addProperty("deal_active", com.craisinlord.antos.content.antazon.AntazonService.activeDeal(product, day).enabled());
            row.addProperty("locked", !AntazonService.unlocked(player, computer, product));
            row.addProperty("rotates_in_ms", AntazonData.rotatesInMillis(product, gameTime));
            row.addProperty("rotates_real", product.itemPool() != null && product.itemPool().rotation() != null
                    && product.itemPool().rotation().everyRealHours() > 0L);
            var playerState = antazonData.playerState(profile, AntazonData.limitKey(product));
            long cooldownEnds = playerState.lastPurchase() == Long.MIN_VALUE || availability.cooldownMinecraftDays() < 1L
                    ? 0L : playerState.lastPurchase() + availability.cooldownMinecraftDays() * 24000L;
            row.addProperty("cooldown_ends", cooldownEnds);
            com.google.gson.JsonArray payments = new com.google.gson.JsonArray();
            for (AntazonData.Payment payment : product.payments()) {
                com.google.gson.JsonObject paymentRow = new com.google.gson.JsonObject();
                paymentRow.addProperty("price", AntazonService.unitPrice(product, payment, day));
                paymentRow.addProperty("owned", AntazonService.owned(player, antazonData, profile, payment));
                payments.add(paymentRow);
            }
            row.add("payments", payments);
            row.addProperty("purchased", antazonData.hasPurchased(profile, product.id()));
            row.addProperty("reviewed", antazonData.hasReviewed(profile, product.id()));
            com.google.gson.JsonArray reviews = new com.google.gson.JsonArray();
            for (var review : antazonData.reviews(product.id())) {
                com.google.gson.JsonObject reviewRow = new com.google.gson.JsonObject();
                ComputerWorkspaceData.AccountInfo author = workspaceData.account(review.player());
                reviewRow.addProperty("author", author == null ? "Anternet User" : author.displayUsername());
                reviewRow.addProperty("title", review.title());
                reviewRow.addProperty("body", review.body());
                reviewRow.addProperty("rating", review.rating());
                reviewRow.addProperty("badge", "VERIFIED PURCHASE");
                reviews.add(reviewRow);
            }
            row.add("player_reviews", reviews);
            states.add(row);
        }
        com.google.gson.JsonObject response = new com.google.gson.JsonObject();
        response.addProperty("catalog_version", catalogVersion);
        if (sendCatalog) response.add("catalog", catalog);
        response.add("state", states);
        sendAntazon(player, payload, true, "", response.toString());
    }

    private static void antazonPurchase(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendAntazon(player, payload, false, "unauthorized", "");
            return;
        }
        String[] request = payload.value().split("\u0000", 3);
        if (request.length != 3) {
            sendAntazon(player, payload, false, "invalid_request", "");
            return;
        }
        try {
            ResourceLocation productId = ResourceLocation.parse(request[0]);
            int units = Integer.parseInt(request[2]);
            AntazonService.PurchaseResult result = AntazonService.purchase(player, computer, productId, request[1], units);
            sendAntazon(player, payload, result.success(), result.status(), request[0] + "\0" + units + "\0" + result.receipt());
        } catch (RuntimeException exception) {
            sendAntazon(player, payload, false, "invalid_request", request[0] + "\0" + request[2] + "\0");
        }
    }

    private static void antazonWallet(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendAntazon(player, payload, false, "unauthorized", "");
            return;
        }
        java.util.UUID owner = computer.workspaceOwner() == null ? player.getUUID() : computer.workspaceOwner();
        sendAntazon(player, payload, true, "", Long.toString(com.craisinlord.antos.content.antazon.AntazonServerData.access(player.server).wallet(owner)));
    }

    private static void antazonSell(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendAntazon(player, payload, false, "unauthorized", "");
            return;
        }
        try {
            AntazonService.SellResult result = AntazonService.sell(player, computer);
            sendAntazon(player, payload, result.success(), result.status(), Long.toString(result.amount()));
        } catch (RuntimeException exception) {
            sendAntazon(player, payload, false, "invalid_request", "");
        }
    }

    private static void antazonSellState(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendAntazon(player, payload, false, "unauthorized", "");
            return;
        }
        var crate = AntazonService.resolveCrate(player, computer);
        com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
        String status = crate == null ? (computer.isRemoteWorkspace() ? "crate_unavailable" : "chest_required") : "";
        if (crate != null) {
            var manifest = AntazonService.manifest(computer, crate);
            status = manifest.status();
            for (var entry : manifest.entries()) {
                com.google.gson.JsonObject row = new com.google.gson.JsonObject();
                row.addProperty("item", entry.item().toString());
                row.addProperty("count", entry.count());
                row.addProperty("value", entry.value());
                row.addProperty("sellable", entry.sellable());
                row.addProperty("green_tint", entry.greenTint());
                row.addProperty("render_mob_from_spawn_egg", entry.renderMobFromSpawnEgg());
                rows.add(row);
            }
        }
        sendAntazon(player, payload, true, status, rows.toString());
    }

    private static void antazonPrices(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) { sendAntazon(player, payload, false, "unauthorized", ""); return; }
        com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
        for (var rule : com.craisinlord.antos.content.antazon.AntazonService.priceList()) {
            com.google.gson.JsonObject row = new com.google.gson.JsonObject();
            row.addProperty("item", rule.item().toString());
            row.addProperty("value", rule.value());
            row.addProperty("green_tint", rule.greenTint());
            row.addProperty("render_mob_from_spawn_egg", rule.renderMobFromSpawnEgg());
            rows.add(row);
        }
        sendAntazon(player, payload, true, "", rows.toString());
    }

    private static void antazonPrepare(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) { sendAntazon(player, payload, false, "unauthorized", ""); return; }
        try {
            int separator = payload.value().indexOf('\0');
            if (separator < 1) throw new IllegalArgumentException("invalid_request");
            var result = com.craisinlord.antos.content.antazon.AntazonService.prepareShipment(player, computer,
                    net.minecraft.resources.ResourceLocation.parse(payload.value().substring(0, separator)),
                    Integer.parseInt(payload.value().substring(separator + 1)));
            sendAntazon(player, payload, result.success(), result.status(), result.amount() + "\\0" + result.value());
        } catch (RuntimeException exception) { sendAntazon(player, payload, false, "invalid_request", ""); }
    }

    private static void antazonCrateLink(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        var account = AnternetAccountHandler.session(player);
        boolean linked = account != null && AntazonService.linkNearbyCrate(player, account.accountId());
        send(player, payload, linked ? ComputerAccessResultPayload.SUCCESS : ComputerAccessResultPayload.INVALID);
    }

    private static void antazonOnboarding(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) { sendAntazon(player, payload, false, "unauthorized", ""); return; }
        sendAntazon(player, payload, true, "", Boolean.toString(com.craisinlord.antos.content.antazon.AntazonServerData.access(player.server).onboardingComplete(antazonComputerKey(player, computer))));
    }

    private static void antazonOnboardingComplete(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload, boolean complete) {
        if (!computer.canUseFileSystem(player)) { sendAntazon(player, payload, false, "unauthorized", ""); return; }
        com.craisinlord.antos.content.antazon.AntazonServerData.access(player.server).setOnboardingComplete(antazonComputerKey(player, computer), complete);
        sendAntazon(player, payload, true, "", Boolean.toString(complete));
    }

    private static String antazonComputerKey(ServerPlayer player, ComputerWorkspace computer) {
        return computer.workspaceId().toString();
    }

    private static void sendAntazon(ServerPlayer player, ComputerAccessPayload payload, boolean success, String error, String data) {
        sendResult(player, new ComputerAccessResultPayload(success ? ComputerAccessResultPayload.SUCCESS : ComputerAccessResultPayload.INVALID,
                true, true, payload.action() + "\0" + error + "\0" + data));
    }

    private static void antazonWishlist(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendAntazon(player, payload, false, "unauthorized", "");
            return;
        }
        try {
            if (!payload.value().isBlank()) AntazonService.toggleWishlist(player, ResourceLocation.parse(payload.value()));
            com.google.gson.JsonArray ids = new com.google.gson.JsonArray();
            java.util.UUID profile = computer.workspaceOwner() == null ? player.getUUID() : computer.workspaceOwner();
            for (ResourceLocation id : com.craisinlord.antos.content.antazon.AntazonServerData.access(player.server).wishlist(profile)) ids.add(id.toString());
            sendAntazon(player, payload, true, "", ids.toString());
        } catch (RuntimeException exception) {
            sendAntazon(player, payload, false, "invalid_request", "");
        }
    }

    private static void antazonCart(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendAntazon(player, payload, false, "unauthorized", "");
            return;
        }
        var data = com.craisinlord.antos.content.antazon.AntazonServerData.access(player.server);
        java.util.UUID owner = AntazonService.accountOwner(computer, player);
        long day = player.server.overworld().getGameTime() / 24000L;
        try {
            if (!payload.value().isBlank()) {
                java.util.List<com.craisinlord.antos.content.antazon.AntazonServerData.CartLine> lines = new java.util.ArrayList<>();
                for (var element : com.google.gson.JsonParser.parseString(payload.value()).getAsJsonArray()) {
                    var row = element.getAsJsonObject();
                    lines.add(new com.craisinlord.antos.content.antazon.AntazonServerData.CartLine(ResourceLocation.parse(row.get("product").getAsString()),
                            row.get("option").getAsInt(), row.get("units").getAsInt()));
                }
                data.setCart(owner, AntazonService.sanitizeCart(lines, day));
            } else {
                var current = data.cart(owner);
                var available = AntazonService.sanitizeCart(current, day);
                if (!available.equals(current)) data.setCart(owner, available);
            }
            sendAntazon(player, payload, true, "", encodeCart(data.cart(owner)).toString());
        } catch (RuntimeException exception) {
            sendAntazon(player, payload, false, "invalid_request", encodeCart(data.cart(owner)).toString());
        }
    }

    private static void antazonCheckout(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendAntazon(player, payload, false, "unauthorized", "");
            return;
        }
        AntazonService.CheckoutResult result = AntazonService.checkout(player, computer);
        com.google.gson.JsonObject response = new com.google.gson.JsonObject();
        com.google.gson.JsonArray lines = new com.google.gson.JsonArray();
        for (AntazonService.CheckoutLine line : result.lines()) {
            com.google.gson.JsonObject row = new com.google.gson.JsonObject();
            row.addProperty("product", line.line().product().toString());
            row.addProperty("option", line.line().option());
            row.addProperty("units", line.line().units());
            row.addProperty("status", line.status());
            row.addProperty("receipt", line.receipt());
            lines.add(row);
        }
        response.add("results", lines);
        response.add("cart", encodeCart(result.remaining()));
        sendAntazon(player, payload, true, "", response.toString());
    }

    private static com.google.gson.JsonArray encodeCart(java.util.List<com.craisinlord.antos.content.antazon.AntazonServerData.CartLine> lines) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (var line : lines) {
            com.google.gson.JsonObject row = new com.google.gson.JsonObject();
            row.addProperty("product", line.product().toString());
            row.addProperty("option", line.option());
            row.addProperty("units", line.units());
            array.add(row);
        }
        return array;
    }

    private static void antazonOrders(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendAntazon(player, payload, false, "unauthorized", "");
            return;
        }
        com.google.gson.JsonArray orders = new com.google.gson.JsonArray();
        java.util.UUID profile = computer.workspaceOwner() == null ? player.getUUID() : computer.workspaceOwner();
        for (var order : com.craisinlord.antos.content.antazon.AntazonServerData.access(player.server).orders(profile)) {
            com.google.gson.JsonObject row = new com.google.gson.JsonObject();
            row.addProperty("product", order.product().toString());
            row.addProperty("option", order.option());
            row.addProperty("units", order.units());
            row.addProperty("game_time", order.gameTime());
            row.addProperty("status", order.status());
            orders.add(row);
        }
        sendAntazon(player, payload, true, "", orders.toString());
    }

    private static void antazonReview(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendAntazon(player, payload, false, "unauthorized", "");
            return;
        }
        String[] request = payload.value().split("\u0000", 4);
        if (request.length != 4) {
            sendAntazon(player, payload, false, "invalid_request", "");
            return;
        }
        try {
            var result = AntazonService.submitReview(player, ResourceLocation.parse(request[0]), Integer.parseInt(request[1]), request[2], request[3]);
            sendAntazon(player, payload, result.success(), result.status(), "");
        } catch (RuntimeException exception) {
            sendAntazon(player, payload, false, "invalid_request", "");
        }
    }

    private static void sendBlockle(ServerPlayer player, ComputerAccessPayload payload, boolean success, String error, String data) {
        sendResult(player, new ComputerAccessResultPayload(success ? ComputerAccessResultPayload.SUCCESS : ComputerAccessResultPayload.INVALID,
                true, true, payload.action() + "\0" + error + "\0" + data));
    }

    private static void taskState(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            send(player, payload, ComputerAccessResultPayload.INVALID_PASSWORD);
            return;
        }
        java.util.Set<ResourceLocation> archivesBefore = computer.taskProgress().unlockedArchiveEntries();
        String state = com.craisinlord.antos.content.computer.ComputerTasks.updateAndEncode(player, computer, payload.value());
        sendResult(player, new ComputerAccessResultPayload(ComputerAccessResultPayload.SUCCESS,
                computer.hasPassword(), computer.isAuthenticated(), ComputerAccessPayload.TASK_STATE + "\0\0" + state));
        if (!archivesBefore.equals(computer.taskProgress().unlockedArchiveEntries())) sendArchive(player, computer);
    }

    private static void claimTaskReward(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            send(player, payload, ComputerAccessResultPayload.INVALID_PASSWORD);
            return;
        }
        try {
            ResourceLocation taskId = ResourceLocation.parse(payload.value());
            boolean claimed = com.craisinlord.antos.content.computer.ComputerTasks.claimTaskRewards(player, computer, taskId);
            teamOperationResult(player, payload.action(), claimed, claimed ? "REWARD CLAIMED" : "REWARD NOT AVAILABLE");
            refreshTaskState(player, computer);
            if (claimed) sendArchive(player, computer);
        } catch (RuntimeException exception) {
            teamOperationResult(player, payload.action(), false, "INVALID TASK");
        }
    }

    private static void teamAction(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            send(player, payload, ComputerAccessResultPayload.INVALID_PASSWORD);
            return;
        }
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        ComputerWorkspaceData.AccountInfo account = AnternetAccountHandler.session(player);
        boolean success = false;
        String message = "REQUEST REJECTED";
        try {
            switch (payload.action()) {
                case ComputerAccessPayload.TEAM_CREATE -> {
                    success = data.createTeam(account.accountId());
                    message = success ? "TEAM CREATED" : "ALREADY IN A TEAM";
                }
                case ComputerAccessPayload.TEAM_INVITE -> {
                    if (payload.value().length() > 16 || !payload.value().matches("[A-Za-z0-9_]{3,16}")) {
                        message = "ENTER A VALID ANTOS USERNAME";
                        break;
                    }
                    ComputerWorkspaceData.AccountInfo recipient = data.accountByUsername(payload.value());
                    var invite = data.inviteToTeam(account.accountId(), payload.value(), player.serverLevel().getGameTime());
                    if (invite == null || recipient == null) {
                        message = recipient == null ? "ACCOUNT NOT FOUND OR CANNOT BE INVITED" : "INVITE COULD NOT BE CREATED";
                        break;
                    }
                    AntmailMessage mail = AntmailMessage.create(AntmailAddress.ofUsername(account.username()),
                            AntmailAddress.ofUsername(recipient.username()), "ANTOS TEAM INVITATION",
                            invite.inviterName() + " invited you to join " + invite.ownerName() + "'s AntOS team. "
                                    + "Open the Tasks app to accept or decline.",
                            player.serverLevel().getGameTime(), java.util.List.of());
                    var delivery = AntmailServerData.access(player.server).deliver(player.server, mail);
                    success = delivery.status() == com.craisinlord.antos.content.antmail.AntmailDeliveryResult.Status.DELIVERED;
                    message = success ? "INVITATION SENT BY ANTMAIL" : "INVITATION SAVED; ANTMAIL DELIVERY FAILED";
                }
                case ComputerAccessPayload.TEAM_ACCEPT_INVITE -> {
                    success = data.acceptTeamInvite(account.accountId(), java.util.UUID.fromString(payload.value()));
                    message = success ? "JOINED TEAM // PROGRESS MERGED" : "INVITATION EXPIRED OR UNAVAILABLE";
                }
                case ComputerAccessPayload.TEAM_DECLINE_INVITE -> {
                    success = data.declineTeamInvite(account.accountId(), java.util.UUID.fromString(payload.value()));
                    message = success ? "INVITATION DECLINED" : "INVITATION UNAVAILABLE";
                }
                case ComputerAccessPayload.TEAM_LEAVE -> {
                    success = data.leaveTeam(account.accountId());
                    message = success ? "LEFT TEAM // PROGRESS KEPT" : "ONLY THE OWNER CAN DISBAND THIS TEAM";
                }
                case ComputerAccessPayload.TEAM_DISBAND -> {
                    success = data.disbandTeam(account.accountId());
                    message = success ? "TEAM DISBANDED // PROGRESS KEPT" : "ONLY THE OWNER CAN DISBAND THIS TEAM";
                }
            }
        } catch (RuntimeException exception) {
            success = false;
            message = "INVALID TEAM REQUEST";
        }
        teamOperationResult(player, payload.action(), success, message);
        refreshTaskState(player, computer);
        sendArchive(player, computer);
    }

    private static void refreshTaskState(ServerPlayer player, ComputerWorkspace computer) {
        String state = com.craisinlord.antos.content.computer.ComputerTasks.updateAndEncode(player, computer);
        sendResult(player, new ComputerAccessResultPayload(ComputerAccessResultPayload.SUCCESS,
                computer.hasPassword(), computer.isAuthenticated(), ComputerAccessPayload.TASK_STATE + "\0\0" + state));
    }

    private static void teamOperationResult(ServerPlayer player, int action, boolean success, String message) {
        sendResult(player, new ComputerAccessResultPayload(success ? ComputerAccessResultPayload.SUCCESS : ComputerAccessResultPayload.INVALID,
                true, true, ComputerAccessPayload.TEAM_RESULT + "\0" + action + "\0" + (success ? "1" : "0") + "\0" + message));
    }

    private static void archiveViewed(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) return;
        try {
            ResourceLocation entryId = ResourceLocation.parse(payload.value());
            boolean unlocked = com.craisinlord.antos.content.guide.ComputerGuideData.entriesFor(computer.diskIds(), computer.taskProgress().unlockedArchiveEntries()).stream()
                    .anyMatch(entry -> entry.id().equals(entryId));
            if (unlocked) com.craisinlord.antos.content.computer.ComputerTasks.recordEvent(player, computer, "archive_viewed", entryId);
            if (unlocked) sendArchive(player, computer);
        } catch (RuntimeException ignored) { }
    }

    private static void open(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        ComputerWorkspaceData.AccountInfo account = AnternetAccountHandler.session(player);
        if (account != null && computer.authenticateAccount(player, account)) {
            com.craisinlord.antos.content.computer.ComputerTasks.synchronizeSharedProgress(player, computer);
            send(player, payload, ComputerAccessResultPayload.SUCCESS);
        } else {
            send(player, payload, ComputerAccessResultPayload.INVALID);
        }
        sendArchive(player, computer);
    }

    public static void sendArchive(ServerPlayer player, ComputerWorkspace computer) {
        com.google.gson.JsonObject snapshot = com.google.gson.JsonParser.parseString(
                com.craisinlord.antos.content.guide.ComputerGuideData.encodeNetworkSnapshot(computer.taskProgress().unlockedArchiveEntries())).getAsJsonObject();
        com.google.gson.JsonArray disks = new com.google.gson.JsonArray();
        for (ResourceLocation diskId : computer.diskIds()) {
            if (!diskId.equals(ResourceLocation.fromNamespaceAndPath("antos", "introduction"))) disks.add(diskId.toString());
        }
        snapshot.add("installed_disks", disks);
        anternetResultSender.accept(player, new AnternetComputerResultPayload(ComputerAccessResultPayload.SUCCESS,
                true, true, ComputerAccessPayload.ARCHIVE_STATE + "\0\0" + snapshot));
    }

    private static void eject(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (AnternetAccountHandler.session(player) == null || !computer.isAuthenticatedBy(player)) {
            send(player, payload, ComputerAccessResultPayload.INVALID);
            return;
        }
        try {
            ResourceLocation diskId = ResourceLocation.parse(payload.value());
            boolean ejected = computer.ejectOne(player, diskId);
            send(player, payload, ejected ? ComputerAccessResultPayload.SUCCESS : ComputerAccessResultPayload.INVALID);
            if (ejected) sendArchive(player, computer);
        } catch (Exception ignored) {
            send(player, payload, ComputerAccessResultPayload.INVALID);
        }
    }

    private static void fileList(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendFile(player, payload, false, "", "unauthorized");
            return;
        }
        StringBuilder data = new StringBuilder();
        for (ComputerFileSystem.ComputerFile file : computer.fileSystem().list()) {
            if (!data.isEmpty()) data.append('\n');
            data.append(file.type().name()).append('\t').append(file.path()).append('\t').append(file.contents().length());
        }
        sendFile(player, payload, true, data.toString(), "");
    }

    private static void fileOpen(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendFile(player, payload, false, "", "unauthorized");
            return;
        }
        ComputerFileSystem.ComputerFile file = computer.fileSystem().get(payload.value());
        if (file == null) {
            sendFile(player, payload, false, "", "not_found");
        } else if (file.type() != ComputerFileSystem.ComputerFile.Type.TEXT && file.type() != ComputerFileSystem.ComputerFile.Type.IMAGE) {
            sendFile(player, payload, false, "", "not_file");
        } else {
            sendFile(player, payload, true, file.path() + "\0" + file.contents(), "");
        }
    }

    private static void fileCreate(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendFile(player, payload, false, "", "unauthorized");
            return;
        }
        String[] request = splitFileRequest(payload.value());
        if (request == null) {
            sendFile(player, payload, false, "", "invalid_request");
            return;
        }
        ComputerFileSystem.Result result = computer.fileSystem().createTextFile(request[0], request[1]);
        if (result.successful()) computer.setChanged();
        sendFile(player, payload, result.successful(), request[0], result.error());
    }

    private static void fileSave(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendFile(player, payload, false, "", "unauthorized");
            return;
        }
        String[] request = splitFileRequest(payload.value());
        if (request == null) {
            sendFile(player, payload, false, "", "invalid_request");
            return;
        }
        ComputerFileSystem.Result result = computer.fileSystem().writeTextFile(request[0], request[1]);
        if (result.successful()) computer.setChanged();
        sendFile(player, payload, result.successful(), request[0], result.error());
    }

    private static void fileDelete(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendFile(player, payload, false, "", "unauthorized");
            return;
        }
        ComputerFileSystem.Result result = computer.fileSystem().delete(payload.value());
        if (result.successful()) computer.setChanged();
        sendFile(player, payload, result.successful(), payload.value(), result.error());
    }

    private static void fileMove(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendFile(player, payload, false, "", "unauthorized");
            return;
        }
        String[] request = splitFileRequest(payload.value());
        if (request == null) {
            sendFile(player, payload, false, "", "invalid_request");
            return;
        }
        ComputerFileSystem.Result result = computer.fileSystem().move(request[0], request[1]);
        if (result.successful()) computer.setChanged();
        sendFile(player, payload, result.successful(), request[1], result.error());
    }

    private static void terminalCommand(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        String[] request = splitFileRequest(payload.value());
        if (request == null) {
            sendTerminal(player, payload, TerminalResult.error("/", "ERROR: INVALID REQUEST"));
            return;
        }
        TerminalResult result = new TerminalCommandService().execute(new ComputerTerminalFileSystem(computer.fileSystem()), request[0], request[1]);
        if (result.status() != TerminalResult.Status.SUCCESS || !result.lines().isEmpty() || result.clearOutput() || result.openPath() != null) computer.setChanged();
        sendTerminal(player, payload, result);
    }

    private static void sendTerminal(ServerPlayer player, ComputerAccessPayload payload, TerminalResult result) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Directory", result.workingDirectory());
        tag.putBoolean("Clear", result.clearOutput());
        tag.putString("Open", result.openPath() == null ? "" : result.openPath());
        net.minecraft.nbt.ListTag lines = new net.minecraft.nbt.ListTag();
        for (String line : result.lines()) lines.add(net.minecraft.nbt.StringTag.valueOf(line));
        tag.put("Lines", lines);
        sendResult(player, new ComputerAccessResultPayload(result.status() == TerminalResult.Status.ERROR ? ComputerAccessResultPayload.INVALID : ComputerAccessResultPayload.SUCCESS,
                true, true, payload.action() + "\0" + AntmailWire.encodeTag(tag)));
    }

    private static final class ComputerTerminalFileSystem implements TerminalFileSystem {
        private final ComputerFileSystem fileSystem;

        private ComputerTerminalFileSystem(ComputerFileSystem fileSystem) { this.fileSystem = fileSystem; }

        @Override public java.util.Optional<Entry> find(String path, String workingDirectory) {
            ComputerFileSystem.ComputerFile file = fileSystem.get(resolve(path, workingDirectory));
            return file == null ? java.util.Optional.empty() : java.util.Optional.of(new Entry(file.path(), file.path(), file.type() == ComputerFileSystem.ComputerFile.Type.DIRECTORY ? EntryType.DIRECTORY : EntryType.FILE));
        }
        @Override public java.util.List<Entry> list(String path, String workingDirectory) {
            String directory = resolve(path, workingDirectory);
            return fileSystem.list().stream().filter(file -> {
                String parent = file.path().lastIndexOf('/') <= 0 ? "/" : file.path().substring(0, file.path().lastIndexOf('/'));
                return parent.equals(directory) && !file.path().equals(directory);
            }).map(file -> new Entry(file.path().substring(file.path().lastIndexOf('/') + 1), file.path(), file.type() == ComputerFileSystem.ComputerFile.Type.DIRECTORY ? EntryType.DIRECTORY : EntryType.FILE)).toList();
        }
        @Override public boolean createDirectory(String path, String workingDirectory) { return fileSystem.createDirectory(resolve(path, workingDirectory)).successful(); }
        @Override public boolean createFile(String path, String workingDirectory) { return fileSystem.createTextFile(resolve(path, workingDirectory), "").successful(); }
        @Override public java.util.Optional<String> readText(String path, String workingDirectory) { ComputerFileSystem.ComputerFile file = fileSystem.get(resolve(path, workingDirectory)); return file != null && file.type() == ComputerFileSystem.ComputerFile.Type.TEXT ? java.util.Optional.of(file.contents()) : java.util.Optional.empty(); }
        @Override public boolean writeText(String path, String workingDirectory, String contents) { return fileSystem.createOrWriteTextFile(resolve(path, workingDirectory), contents).successful(); }
        @Override public boolean deleteFile(String path, String workingDirectory) { return fileSystem.delete(resolve(path, workingDirectory)).successful(); }
        @Override public boolean deleteEmptyDirectory(String path, String workingDirectory) { return fileSystem.delete(resolve(path, workingDirectory)).successful(); }
        @Override public boolean move(String source, String destination, String workingDirectory) { return fileSystem.move(resolve(source, workingDirectory), resolve(destination, workingDirectory)).successful(); }
        @Override public String normalize(String path, String workingDirectory) { return resolve(path, workingDirectory); }

        private String resolve(String path, String workingDirectory) {
            if (path == null || path.isBlank()) return ComputerFileSystem.normalize(workingDirectory);
            return ComputerFileSystem.normalize(path.startsWith("/") ? path : ComputerFileSystem.normalize(workingDirectory) + "/" + path);
        }
    }

    private static void desktopState(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendDesktop(player, payload, false, "unauthorized");
            return;
        }
        sendDesktop(player, payload, true, AntmailWire.encodeTag(computer.desktopState().save()));
    }

    private static void desktopWallpaper(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        if (!computer.canUseFileSystem(player)) {
            sendDesktop(player, payload, false, "unauthorized");
            return;
        }
        try {
            ResourceLocation id = ResourceLocation.parse(payload.value());
            boolean known = com.craisinlord.antos.content.guide.ComputerGuideData.wallpaper(id) != null;
            boolean changed = known && computer.selectWallpaper(id);
            String state = AntmailWire.encodeTag(computer.desktopState().save());
            sendDesktop(player, payload, known && (changed || computer.desktopState().selectedWallpaper().equals(id)), state);
        } catch (RuntimeException exception) {
            sendDesktop(player, payload, false, AntmailWire.encodeTag(computer.desktopState().save()));
        }
    }

    private static void locateStructure(ServerPlayer player, ComputerWorkspace computer, ComputerAccessPayload payload) {
        ResourceLocation entryId;
        try {
            entryId = ResourceLocation.parse(payload.value());
        } catch (RuntimeException exception) {
            sendFile(player, payload, false, "", "unauthorized");
            return;
        }
        var entry = com.craisinlord.antos.content.guide.ComputerGuideData.entry(entryId);
        boolean unlocked = com.craisinlord.antos.content.guide.ComputerGuideData.entriesFor(computer.diskIds(), computer.taskProgress().unlockedArchiveEntries()).stream()
                .anyMatch(candidate -> candidate.id().equals(entryId));
        if (computer.canUseFileSystem(player) && unlocked && entry != null && !entry.locatorId().isBlank()) {
            runArchiveLocator(player, payload, entry);
            return;
        }
        if (!computer.canUseFileSystem(player) || !unlocked || entry == null || entry.structureId().isBlank()
                || entry.dimensionId().isBlank()) {
            sendFile(player, payload, false, "", "unauthorized");
            return;
        }

        ResourceLocation dimensionId;
        ResourceLocation structureTagId;
        ResourceLocation structureId;
        try {
            dimensionId = ResourceLocation.parse(entry.dimensionId());
            structureTagId = entry.structureTagId().isBlank() ? null : ResourceLocation.parse(entry.structureTagId());
            structureId = ResourceLocation.parse(entry.structureId());
        } catch (RuntimeException exception) {
            sendFile(player, payload, false, "", "dimension_unavailable");
            return;
        }
        ServerLevel targetLevel = player.server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (targetLevel == null) {
            sendFile(player, payload, false, "", "dimension_unavailable");
            return;
        }

        // Search around the player when they are already in the target dimension, otherwise around world spawn.
        net.minecraft.core.BlockPos origin = player.level() == targetLevel ? player.blockPosition() : targetLevel.getSharedSpawnPos();
        int radius = entry.searchRadius() > 0 ? entry.searchRadius() : 100;
        net.minecraft.core.BlockPos nearest;
        if (structureTagId != null) {
            nearest = targetLevel.findNearestMapStructure(TagKey.create(Registries.STRUCTURE, structureTagId), origin, radius, false);
        } else {
            var structure = targetLevel.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .getHolder(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, structureId)).orElse(null);
            var found = structure == null ? null : targetLevel.getChunkSource().getGenerator().findNearestMapStructure(
                    targetLevel, net.minecraft.core.HolderSet.direct(structure), origin, radius, false);
            nearest = found == null ? null : found.getFirst();
        }
        if (nearest == null) {
            sendFile(player, payload, false, "", "not_found");
            return;
        }

        sendFile(player, payload, true, nearest.getX() + ", " + nearest.getY() + ", " + nearest.getZ(), "");
    }

    private static void runArchiveLocator(ServerPlayer player, ComputerAccessPayload payload, com.craisinlord.antos.content.guide.ComputerGuideData.Entry entry) {
        com.craisinlord.antos.api.archive.ArchiveLocatorRegistry.Locator locator;
        ServerLevel targetLevel;
        try {
            locator = com.craisinlord.antos.api.archive.ArchiveLocatorRegistry.get(ResourceLocation.parse(entry.locatorId()));
            targetLevel = entry.dimensionId().isBlank() ? null
                    : player.server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.dimensionId())));
        } catch (RuntimeException exception) {
            sendFile(player, payload, false, "", "dimension_unavailable");
            return;
        }
        if (locator == null || targetLevel == null) {
            sendFile(player, payload, false, "", locator == null ? "unauthorized" : "dimension_unavailable");
            return;
        }

        net.minecraft.core.BlockPos nearest = locator.locate(targetLevel,
                player.level() == targetLevel ? player.blockPosition() : targetLevel.getSharedSpawnPos());
        if (nearest == null) {
            sendFile(player, payload, false, "", "not_found");
            return;
        }
        sendFile(player, payload, true, nearest.getX() + ", " + nearest.getY() + ", " + nearest.getZ(), "");
    }

    private static void sendDesktop(ServerPlayer player, ComputerAccessPayload payload, boolean success, String data) {
        sendResult(player, new ComputerAccessResultPayload(success ? ComputerAccessResultPayload.SUCCESS : ComputerAccessResultPayload.INVALID,
                true, true, payload.action() + "\0\0" + data));
    }

    private static String[] splitFileRequest(String value) {
        if (value == null) return null;
        int separator = value.indexOf('\0');
        if (separator < 0) return null;
        return new String[]{value.substring(0, separator), value.substring(separator + 1)};
    }

    private static void send(ServerPlayer player, ComputerAccessPayload payload, int result) {
        sendResult(player, new ComputerAccessResultPayload(result, true, true));
    }

    private static void sendFile(ServerPlayer player, ComputerAccessPayload payload, boolean success, String data, String error) {
        sendResult(player, new ComputerAccessResultPayload(success ? ComputerAccessResultPayload.SUCCESS : ComputerAccessResultPayload.INVALID,
                true, true, payload.action() + "\0" + error + "\0" + data));
    }
}
