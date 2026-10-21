package com.craisinlord.antos.content.merchant;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.antazon.AntazonServerData;
import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.entity.RewardDropEntity;
import net.minecraft.network.chat.Component;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

public final class MerchantService {
    private static final Map<MinecraftServer, MerchantStore> STORES = new WeakHashMap<>();
    private static final int PAGE = 12;

    private MerchantService() { }

    public static String report(MinecraftServer server) throws IOException {
        JsonObject root = store(server).snapshot();
        return "MERCHANT: " + root.getAsJsonArray("listings").size() + " listings, " + root.getAsJsonArray("trades").size() + " trades, " + root.getAsJsonArray("shipments").size()
                + " shipments. Recovery: " + (root.has("pending") ? text(root.getAsJsonObject("pending"), "player") : "none");
    }

    public static void resume(ServerPlayer player) throws IOException {
        recover(player, store(player.server));
    }

    public static void returnListing(MinecraftServer server, String id) throws IOException {
        MerchantStore storage = store(server);
        JsonObject root = storage.snapshot();
        require(!root.has("pending"), "recovery_pending");
        JsonObject listing = MerchantStore.find(root, "listings", id);
        require(integer(listing, "lots") > 0, "listing_unavailable");
        shipment(root, text(listing, "owner"), listing.getAsJsonObject("stack"), Math.toIntExact(MerchantStore.total(integer(listing, "quantity"), integer(listing, "lots"))), "return", UUID.randomUUID().toString());
        listing.addProperty("lots", 0);
        listing.addProperty("revision", integer(listing, "revision") + 1);
        storage.commit(root);
    }

    private static synchronized MerchantStore store(MinecraftServer server) throws IOException {
        MerchantStore value = STORES.get(server);
        if (value == null) {
            value = new MerchantStore(server.getWorldPath(LevelResource.ROOT).resolve("data/antos_merchant.json"));
            STORES.put(server, value);
        }
        return value;
    }

    public static JsonObject handle(ServerPlayer player, String encoded) {
        JsonObject result = MerchantProtocol.failure(encoded, "invalid_request");
        try {
            JsonObject request = MerchantProtocol.parse(encoded);
            result = MerchantProtocol.envelope(request);
            require(AntOSSettings.appEnabled("MERCHANT"), "disabled");
            require(player.isAlive() && !player.isSpectator(), "unavailable");
            var account = AnternetAccountHandler.session(player);
            require(account != null, "unauthorized");
            MerchantStore storage = store(player.server);
            recover(player, storage);
            String action = text(request, "action");
            String owner = account.accountId().toString();
            if (!action.equals("state")) {
                String requestId = UUID.fromString(text(request, "request")).toString();
                JsonObject root = storage.snapshot();
                MerchantStore.validateReplay(root, owner, requestId, MerchantProtocol.operation(request));
                boolean recorded = MerchantStore.requested(root, owner, requestId);
                if (recorded && action.equals("deliver")) {
                    JsonObject shipment = MerchantStore.find(root, "shipments", text(request, "id"));
                    require(text(shipment, "owner").equals(owner), "unauthorized");
                    if (text(shipment, "status").equals("pending")) dispatch(player, storage, root, shipment);
                }
                if (!recorded) {
                    switch (action) {
                        case "publish" -> publish(player, storage, root, request, owner, account.username(), requestId);
                        case "buy" -> buy(player, storage, root, request, owner, account.username(), requestId);
                        case "cancel" -> cancel(player, storage, root, request, owner, requestId);
                        case "deliver" -> deliver(player, storage, root, request, owner, requestId);
                        default -> throw new IllegalArgumentException("invalid_request");
                    }
                }
                String notice = action;
                if (action.equals("buy")) {
                    for (JsonElement element : storage.snapshot().getAsJsonArray("shipments")) {
                        JsonObject row = element.getAsJsonObject();
                        if (text(row, "trade").equals(requestId) && text(row, "owner").equals(owner) && text(row, "kind").equals("purchase") && text(row, "status").equals("pending")) notice = "buy_queued";
                    }
                }
                result.addProperty("notice", notice);
                result.addProperty("request", requestId);
            }
            try {
                result.add("state", storage.read(root -> state(player, root, request, owner)));
            } catch (RuntimeException exception) {
                if (action.equals("state")) throw exception;
                AntOS.LOGGER.warn("MERCHANT settled a request but could not refresh its view", exception);
            }
            result.addProperty("ok", true);
        } catch (IllegalArgumentException exception) {
            result.addProperty("ok", false);
            result.addProperty("error", exception.getMessage() == null ? "invalid_request" : exception.getMessage());
        } catch (Exception exception) {
            AntOS.LOGGER.error("MERCHANT request failed for {}", player.getUUID(), exception);
            result.addProperty("ok", false);
            result.addProperty("error", "storage_error");
        }
        return result;
    }

    public static void recoverOnLogin(ServerPlayer player) {
        try {
            MerchantStore storage = store(player.server);
            if (!storage.hasPending()) return;
            JsonObject root = storage.snapshot();
            if (root.has("pending") && text(root.getAsJsonObject("pending"), "player").equals(player.getUUID().toString())) recover(player, storage);
        } catch (Exception exception) {
            AntOS.LOGGER.error("Could not recover interrupted MERCHANT transaction for {}", player.getUUID(), exception);
        }
    }

    private static void publish(ServerPlayer player, MerchantStore storage, JsonObject root, JsonObject request, String owner, String seller, String requestId) throws IOException {
        int slot = integer(request, "slot");
        require(slot >= 0 && slot < 36, "invalid_item");
        ItemStack offered = player.getInventory().getItem(slot).copy();
        require(!offered.isEmpty(), "invalid_item");
        require(ItemStack.isSameItemSameComponents(offered, decode(player, request.getAsJsonObject("expected"))), "inventory_changed");
        int amount = integer(request, "quantity");
        int lots = integer(request, "lots");
        long total = MerchantStore.total(amount, lots);
        require(total <= Integer.MAX_VALUE, "invalid_quantity");
        JsonArray payments = request.getAsJsonArray("payments");
        require(payments != null && !payments.isEmpty(), "payment_required");
        JsonArray accepted = new JsonArray();
        for (JsonElement element : payments) {
            JsonObject payment = element.getAsJsonObject();
            String currency = text(payment, "item");
            int quantity = integer(payment, "quantity");
            require(quantity > 0, "invalid_quantity");
            if (!currency.equals("antcoins")) {
                ItemStack item = plain(currency);
                require(!item.isEmpty(), "invalid_currency");
            }
            JsonObject option = new JsonObject();
            option.addProperty("item", currency);
            option.addProperty("quantity", quantity);
            require(!accepted.contains(option), "duplicate_payment");
            accepted.add(option);
        }
        JsonArray before = inventory(player);
        JsonArray after = remove(player, before, offered, (int) total);
        offered.setCount(1);
        JsonObject listing = new JsonObject();
        listing.addProperty("id", UUID.randomUUID().toString());
        listing.addProperty("owner", owner);
        listing.addProperty("seller", seller);
        listing.add("stack", encode(player, offered));
        listing.addProperty("name", offered.getHoverName().getString());
        listing.addProperty("item", BuiltInRegistries.ITEM.getKey(offered.getItem()).toString());
        listing.addProperty("quantity", amount);
        listing.addProperty("lots", lots);
        listing.addProperty("revision", 0);
        listing.addProperty("created", System.currentTimeMillis());
        listing.add("payments", accepted);
        root.getAsJsonArray("listings").add(listing);
        transact(player, storage, root, before, after, owner, requestId, new JsonArray(), request);
    }

    private static void buy(ServerPlayer player, MerchantStore storage, JsonObject root, JsonObject request, String owner, String buyer, String requestId) throws IOException {
        JsonObject listing = MerchantStore.find(root, "listings", text(request, "id"));
        ItemStack goodsPrototype = decode(player, listing.getAsJsonObject("stack"));
        require(!goodsPrototype.isEmpty(), "invalid_item");
        int lots = integer(request, "lots");
        int option = integer(request, "option");
        MerchantStore.Purchase quote = MerchantStore.quote(listing, owner, integer(request, "revision"), lots, option);
        JsonArray alternatives = listing.getAsJsonArray("payments");
        JsonObject payment = alternatives.get(option).getAsJsonObject();
        long price = quote.price();
        long count = quote.quantity();
        JsonArray before = inventory(player);
        JsonArray after = before.deepCopy();
        JsonArray wallets = new JsonArray();
        String seller = text(listing, "owner");
        if (text(payment, "item").equals("antcoins")) {
            AntazonServerData coins = AntazonServerData.access(player.server);
            long buyerBalance = coins.wallet(UUID.fromString(owner));
            long sellerBalance = coins.wallet(UUID.fromString(seller));
            require(buyerBalance >= price, "insufficient_payment");
            require(sellerBalance <= Long.MAX_VALUE - price, "wallet_full");
            wallets.add(wallet(owner, buyerBalance, buyerBalance - price));
            wallets.add(wallet(seller, sellerBalance, sellerBalance + price));
        } else {
            ItemStack currency = plain(text(payment, "item"));
            after = remove(player, before, currency, (int) price);
            shipment(root, seller, encode(player, currency), (int) price, "payment", requestId);
        }
        JsonObject goods = shipment(root, owner, listing.getAsJsonObject("stack"), (int) count, "purchase", requestId);
        listing.addProperty("lots", quote.remaining());
        listing.addProperty("revision", quote.revision());
        JsonObject trade = new JsonObject();
        trade.addProperty("id", requestId);
        trade.addProperty("buyer", owner);
        trade.addProperty("buyerName", buyer);
        trade.addProperty("seller", seller);
        trade.addProperty("sellerName", text(listing, "seller"));
        trade.addProperty("created", System.currentTimeMillis());
        trade.add("stack", listing.get("stack").deepCopy());
        trade.addProperty("quantity", count);
        trade.add("payment", payment.deepCopy());
        trade.addProperty("paid", price);
        root.getAsJsonArray("trades").add(trade);
        transact(player, storage, root, before, after, owner, requestId, wallets, request);
        receipt(player.server, text(listing, "seller"), "Items sold", count + " " + text(listing, "name") + " sold to " + buyer + ". Open MERCHANT to view your payment.");
        receipt(player.server, buyer, "Trade complete", "Purchased " + count + " " + text(listing, "name") + ". Open MERCHANT to view your delivery.");
        JsonObject current = storage.snapshot();
        try {
            dispatch(player, storage, current, MerchantStore.find(current, "shipments", text(goods, "id")));
        } catch (IllegalArgumentException | IOException exception) {
            AntOS.LOGGER.warn("MERCHANT purchase remains queued: {}", exception.getMessage());
        }
    }

    private static JsonObject wallet(String owner, long before, long after) {
        JsonObject value = new JsonObject();
        value.addProperty("owner", owner);
        value.addProperty("before", before);
        value.addProperty("after", after);
        return value;
    }

    private static void cancel(ServerPlayer player, MerchantStore storage, JsonObject root, JsonObject request, String owner, String requestId) throws IOException {
        JsonObject listing = MerchantStore.find(root, "listings", text(request, "id"));
        require(text(listing, "owner").equals(owner), "unauthorized");
        require(integer(listing, "lots") > 0, "listing_unavailable");
        require(integer(listing, "revision") == integer(request, "revision"), "listing_changed");
        int quantity = Math.toIntExact(MerchantStore.total(integer(listing, "quantity"), integer(listing, "lots")));
        shipment(root, owner, listing.getAsJsonObject("stack"), quantity, "return", requestId);
        listing.addProperty("lots", 0);
        listing.addProperty("revision", integer(listing, "revision") + 1);
        recordRequest(root, owner, requestId, request);
        storage.commit(root);
    }

    private static void deliver(ServerPlayer player, MerchantStore storage, JsonObject root, JsonObject request, String owner, String requestId) throws IOException {
        JsonObject shipment = MerchantStore.find(root, "shipments", text(request, "id"));
        require(text(shipment, "owner").equals(owner), "unauthorized");
        require(text(shipment, "status").equals("pending"), "already_dispatched");
        recordRequest(root, owner, requestId, request);
        dispatch(player, storage, root, shipment);
    }

    private static void dispatch(ServerPlayer player, MerchantStore storage, JsonObject root, JsonObject shipment) throws IOException {
        require(player.isAlive() && !player.isSpectator() && !player.isChangingDimension(), "unsafe_delivery");
        var level = player.serverLevel();
        var location = player.blockPosition();
        require(level.getWorldBorder().isWithinBounds(location) && level.hasChunkAt(location) && location.getY() > level.getMinBuildHeight() + 2, "unsafe_delivery");
        require(!player.isInLava() && !player.isUnderWater(), "unsafe_delivery");
        ItemStack prototype = decode(player, shipment.getAsJsonObject("stack"));
        require(!prototype.isEmpty(), "invalid_item");
        int originalQuantity = integer(shipment, "quantity");
        MerchantStore.Batch batch = MerchantStore.deliveryBatch(originalQuantity, prototype.getMaxStackSize(), RewardDropEntity.MAX_REWARD_STACKS);
        int amount = batch.sent();
        List<ItemStack> stacks = new ArrayList<>();
        while (amount > 0) {
            int count = Math.min(amount, prototype.getMaxStackSize());
            ItemStack stack = prototype.copy();
            stack.setCount(count);
            stacks.add(stack);
            amount -= count;
        }
        RewardDropEntity drop = new RewardDropEntity(AntOSObjects.REWARD_DROP_ENTITY.get(), level);
        drop.setPos(location.getX() + 0.5D, RewardDropEntity.spawnHeight(level, location), location.getZ() + 0.5D);
        drop.setRewards(stacks, player);
        JsonObject remainder = batch.remaining() > 0 ? shipment(root, text(shipment, "owner"), shipment.getAsJsonObject("stack"), batch.remaining(), text(shipment, "kind"), text(shipment, "trade")) : null;
        shipment.addProperty("quantity", batch.sent());
        shipment.addProperty("status", "dispatched");
        shipment.addProperty("dimension", level.dimension().location().toString());
        shipment.addProperty("location", location.getX() + ", " + location.getY() + ", " + location.getZ());
        storage.commit(root);
        try {
            require(level.addFreshEntity(drop), "unsafe_delivery");
        } catch (RuntimeException exception) {
            if (level.getEntity(drop.getUUID()) == null) {
                if (remainder != null) root.getAsJsonArray("shipments").remove(remainder);
                shipment.addProperty("quantity", originalQuantity);
                shipment.addProperty("status", "pending");
                storage.commit(root);
                throw new IllegalArgumentException("unsafe_delivery");
            }
            AntOS.LOGGER.warn("MERCHANT shipment was added despite a delivery hook failure", exception);
        }
        player.sendSystemMessage(Component.translatable("computer.antos.merchant.incoming"));
    }

    private static JsonObject shipment(JsonObject root, String owner, JsonObject stack, int quantity, String kind, String trade) {
        JsonObject row = new JsonObject();
        row.addProperty("id", UUID.randomUUID().toString());
        row.addProperty("owner", owner);
        row.add("stack", stack.deepCopy());
        row.addProperty("quantity", quantity);
        row.addProperty("kind", kind);
        row.addProperty("trade", trade);
        row.addProperty("created", System.currentTimeMillis());
        row.addProperty("status", "pending");
        root.getAsJsonArray("shipments").add(row);
        return row;
    }

    private static void recordRequest(JsonObject root, String owner, String id, JsonObject request) {
        JsonObject value = new JsonObject();
        value.addProperty("account", owner);
        value.addProperty("id", id);
        value.add("operation", MerchantProtocol.operation(request));
        root.getAsJsonArray("requests").add(value);
    }

    private static void transact(ServerPlayer player, MerchantStore storage, JsonObject next, JsonArray before, JsonArray after, String owner, String requestId, JsonArray wallets, JsonObject request) throws IOException {
        JsonObject pending = new JsonObject();
        pending.addProperty("player", player.getUUID().toString());
        pending.add("before", before);
        pending.add("after", after);
        pending.add("wallets", wallets);
        next.add("pending", pending);
        recordRequest(next, owner, requestId, request);
        storage.commit(next);
        recover(player, storage);
    }

    private static void recover(ServerPlayer player, MerchantStore storage) throws IOException {
        if (!storage.hasPending()) return;
        JsonObject root = storage.snapshot();
        if (!root.has("pending")) return;
        JsonObject pending = root.getAsJsonObject("pending");
        if (!text(pending, "player").equals(player.getUUID().toString())) {
            ServerPlayer actor = player.server.getPlayerList().getPlayer(UUID.fromString(text(pending, "player")));
            require(actor != null, "recovery_pending");
            recover(actor, storage);
            return;
        }
        JsonArray current = inventory(player);
        JsonArray before = pending.getAsJsonArray("before");
        JsonArray after = pending.getAsJsonArray("after");
        boolean inventoryChanged = !before.equals(after);
        require(!inventoryChanged || current.equals(before) || current.equals(after), "recovery_conflict");
        AntazonServerData coins = AntazonServerData.access(player.server);
        for (JsonElement element : pending.getAsJsonArray("wallets")) {
            JsonObject row = element.getAsJsonObject();
            long balance = coins.wallet(UUID.fromString(text(row, "owner")));
            require(balance == row.get("before").getAsLong() || balance == row.get("after").getAsLong(), "recovery_conflict");
        }
        if (inventoryChanged && current.equals(before)) {
            for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, after.get(slot).isJsonNull() ? ItemStack.EMPTY : decode(player, after.get(slot).getAsJsonObject()));
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        for (JsonElement element : pending.getAsJsonArray("wallets")) {
            JsonObject row = element.getAsJsonObject();
            coins.setWallet(UUID.fromString(text(row, "owner")), row.get("after").getAsLong());
        }
        if (inventoryChanged) player.server.getPlayerList().saveAll();
        if (!pending.getAsJsonArray("wallets").isEmpty()) player.server.overworld().getDataStorage().save();
        root.remove("pending");
        storage.commit(root);
    }

    private static JsonArray inventory(ServerPlayer player) {
        JsonArray result = new JsonArray();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            result.add(item.isEmpty() ? com.google.gson.JsonNull.INSTANCE : encode(player, item));
        }
        return result;
    }

    private static JsonArray remove(ServerPlayer player, JsonArray before, ItemStack prototype, int quantity) {
        List<ItemStack> inventory = new ArrayList<>();
        for (JsonElement element : before) inventory.add(element.isJsonNull() ? ItemStack.EMPTY : decode(player, element.getAsJsonObject()));
        JsonArray after = new JsonArray();
        for (ItemStack item : MerchantInventory.take(inventory, prototype, quantity)) after.add(item.isEmpty() ? com.google.gson.JsonNull.INSTANCE : encode(player, item));
        return after;
    }

    public static JsonObject encode(ServerPlayer player, ItemStack stack) {
        return ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, player.server.registryAccess()), stack).getOrThrow().getAsJsonObject();
    }

    private static ItemStack decode(ServerPlayer player, JsonObject value) {
        return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, player.server.registryAccess()), value).getOrThrow();
    }

    private static ItemStack plain(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        require(key != null && BuiltInRegistries.ITEM.containsKey(key), "invalid_currency");
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    private static JsonObject state(ServerPlayer player, JsonObject root, JsonObject request, String owner) {
        JsonObject result = new JsonObject();
        String view = request.has("view") ? text(request, "view") : "browse";
        String search = request.has("search") ? text(request, "search").toLowerCase(Locale.ROOT).trim() : "";
        String currency = request.has("currency") ? text(request, "currency") : "";
        boolean affordable = request.has("affordable") && request.get("affordable").getAsBoolean();
        String sort = request.has("sort") ? text(request, "sort") : "newest";
        if (view.equals("summary")) {
            result.addProperty("view", "summary");
            result.addProperty("wallet", AntazonServerData.access(player.server).wallet(UUID.fromString(owner)));
            result.addProperty("pending", root.getAsJsonArray("shipments").asList().stream().map(JsonElement::getAsJsonObject).filter(row -> text(row, "owner").equals(owner) && text(row, "status").equals("pending")).count());
            return result;
        }
        AntmailServerData mail = AntmailServerData.access(player.server);
        Map<String, Long> available = affordable ? availablePayments(player) : Map.of();
        List<JsonObject> rows = new ArrayList<>();
        String collection = view.equals("deliveries") ? "shipments" : view.equals("history") ? "trades" : "listings";
        for (JsonElement element : root.getAsJsonArray(collection)) {
            JsonObject row = element.getAsJsonObject();
            if (collection.equals("listings")) {
                if (integer(row, "lots") < 1) continue;
                if (view.equals("mine") && !text(row, "owner").equals(owner)) continue;
                String haystack = text(row, "name") + " " + text(row, "seller") + " " + text(row, "item");
                if (!haystack.toLowerCase(Locale.ROOT).contains(search)
                        && !identity(mail, text(row, "seller")).displayName().toLowerCase(Locale.ROOT).contains(search)) continue;
                if (!currency.isEmpty() && paymentPrice(row, currency) < 0) continue;
                if (affordable && (text(row, "owner").equals(owner) || !MerchantStore.affordable(row, currency, available))) continue;
            } else if (collection.equals("shipments")) {
                if (!text(row, "owner").equals(owner)) continue;
                if (request.has("showDispatched") && !request.get("showDispatched").getAsBoolean() && !text(row, "status").equals("pending")) continue;
            } else if (!text(row, "buyer").equals(owner) && !text(row, "seller").equals(owner)) continue;
            rows.add(row);
        }
        Comparator<JsonObject> order = Comparator.comparingLong(row -> row.get("created").getAsLong());
        order = order.reversed();
        if (collection.equals("listings") && sort.equals("name")) order = Comparator.comparing(row -> text(row, "name"));
        if (collection.equals("listings") && sort.equals("price") && !currency.isEmpty()) order = Comparator.comparingLong(row -> paymentPrice(row, currency));
        rows.sort(order);
        int page = request.has("page") ? Math.max(0, integer(request, "page")) : 0;
        page = Math.min(page, Math.max(0, (rows.size() - 1) / PAGE));
        JsonArray pageRows = new JsonArray();
        for (int index = page * PAGE; index < Math.min(rows.size(), (page + 1) * PAGE); index++) pageRows.add(rows.get(index).deepCopy());
        if (request.has("selected")) {
            for (JsonElement element : root.getAsJsonArray(collection)) {
                JsonObject row = element.getAsJsonObject();
                if (text(row, "id").equals(text(request, "selected")) && (collection.equals("listings") || collection.equals("shipments") && text(row, "owner").equals(owner))) {
                    result.add("selection", row.deepCopy());
                    break;
                }
            }
        }
        JsonObject profiles = new JsonObject();
        for (JsonElement element : pageRows) addProfiles(mail, profiles, element.getAsJsonObject(), collection);
        if (result.has("selection")) addProfiles(mail, profiles, result.getAsJsonObject("selection"), collection);
        result.add("profiles", profiles);
        result.add("rows", pageRows);
        result.addProperty("total", rows.size());
        result.addProperty("page", page);
        result.addProperty("owner", owner);
        result.addProperty("view", view);
        result.addProperty("wallet", AntazonServerData.access(player.server).wallet(UUID.fromString(owner)));
        result.addProperty("pending", root.getAsJsonArray("shipments").asList().stream().map(JsonElement::getAsJsonObject).filter(row -> text(row, "owner").equals(owner) && text(row, "status").equals("pending")).count());
        result.add("inventory", inventory(player));
        return result;
    }

    private static MerchantIdentity identity(AntmailServerData mail, String username) {
        return MerchantIdentity.of(username, AntmailAddress.isValidUsername(username) ? mail.profile(AntmailAddress.ofUsername(username)) : null);
    }

    private static void addProfiles(AntmailServerData mail, JsonObject profiles, JsonObject row, String collection) {
        List<String> names = collection.equals("listings") ? List.of(text(row, "seller"))
                : collection.equals("trades") ? List.of(text(row, "buyerName"), text(row, "sellerName")) : List.of();
        for (String name : names) if (!name.isBlank() && !profiles.has(name)) profiles.add(name, identity(mail, name).encode());
    }

    private static long paymentPrice(JsonObject listing, String currency) {
        return MerchantStore.paymentPrice(listing, currency);
    }

    private static Map<String, Long> availablePayments(ServerPlayer player) {
        Map<String, Long> available = new java.util.HashMap<>();
        available.put("antcoins", AntazonServerData.access(player.server).wallet(AnternetAccountHandler.session(player).accountId()));
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
            if (ItemStack.isSameItemSameComponents(item, new ItemStack(item.getItem()))) available.merge(id, (long) item.getCount(), Long::sum);
        }
        return available;
    }

    private static void receipt(MinecraftServer server, String username, String subject, String body) {
        try {
            AntmailServerData.access(server).deliver(server, AntmailMessage.create(AntmailAddress.ofUsername("merchant"), AntmailAddress.ofUsername(username), subject, body + "\n\nmerchant://deliveries", server.overworld().getGameTime(), List.of()));
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Could not send MERCHANT receipt", exception);
        }
    }

    private static String text(JsonObject object, String key) {
        return object.get(key).getAsString();
    }

    private static int integer(JsonObject object, String key) {
        return MerchantProtocol.integer(object, key);
    }

    private static void require(boolean condition, String error) {
        if (!condition) throw new IllegalArgumentException(error);
    }
}
