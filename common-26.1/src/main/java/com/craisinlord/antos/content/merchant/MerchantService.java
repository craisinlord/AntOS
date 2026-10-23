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
import java.lang.reflect.Field;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;

public final class MerchantService {
    private static final Map<MinecraftServer, MerchantStore> STORES = new WeakHashMap<>();
    private static final int PAGE = 12;
    private static final String RECEIPT_SEPARATOR = "\u001F";
    private static Field playerData;

    private MerchantService() { }

    public static String report(MinecraftServer server) throws IOException {
        JsonObject root = store(server).snapshot();
        return "MERCHANT: " + root.getAsJsonArray("listings").size() + " listings, " + root.getAsJsonArray("trades").size() + " trades, " + root.getAsJsonArray("shipments").size()
                + " shipments. Recovery: " + (root.has("pending") ? text(root.getAsJsonObject("pending"), "player") : "none")
                + ", deferred: " + (root.has("deferred") ? root.getAsJsonArray("deferred").size() : 0);
    }

    public static int discardRecovery(MinecraftServer server, UUID player) throws IOException {
        MerchantStore storage = store(server);
        JsonObject root = storage.snapshot();
        int discarded = 0;
        if (root.has("pending") && text(root.getAsJsonObject("pending"), "player").equals(player.toString())) {
            settleWallets(server, root.getAsJsonObject("pending"));
            root.remove("pending");
            discarded++;
        }
        if (root.has("deferred")) {
            JsonArray deferred = root.getAsJsonArray("deferred");
            int before = deferred.size();
            deferred.asList().removeIf(element -> text(element.getAsJsonObject(), "player").equals(player.toString()));
            discarded += before - deferred.size();
            if (deferred.isEmpty()) root.remove("deferred");
        }
        if (discarded > 0) {
            AntOS.LOGGER.warn("MERCHANT recovery for {} was discarded by an administrator", player);
            storage.commit(root);
        }
        return discarded;
    }

    public static void resume(ServerPlayer player) throws IOException {
        recover(player, store(player.level().getServer()));
    }

    public static void returnListing(MinecraftServer server, String id) throws IOException {
        MerchantStore storage = store(server);
        JsonObject root = storage.snapshot();
        require(!root.has("pending"), "recovery_pending");
        JsonObject listing = MerchantStore.find(root, "listings", id);
        require(integer(listing, "lots") > 0, "listing_unavailable");
        shipment(root, text(listing, "owner"), listing, Math.toIntExact(MerchantStore.total(integer(listing, "quantity"), integer(listing, "lots"))), "return", UUID.randomUUID().toString());
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
            var account = AnternetAccountHandler.session(player);
            require(account != null, "unauthorized");
            MerchantStore storage = store(player.level().getServer());
            String action = text(request, "action");
            String restriction = MerchantAccess.failure(action, player.isAlive(), player.isSpectator());
            require(restriction.isEmpty(), restriction);
            if (!action.equals("state")) recover(player, storage);
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
            MerchantStore storage = store(player.level().getServer());
            if (storage.hasPending() || storage.hasDeferred(player.getUUID().toString())) recover(player, storage);
        } catch (Exception exception) {
            AntOS.LOGGER.error("Could not recover interrupted MERCHANT transaction for {}", player.getUUID(), exception);
        }
    }

    private static void publish(ServerPlayer player, MerchantStore storage, JsonObject root, JsonObject request, String owner, String seller, String requestId) throws IOException {
        int slot = integer(request, "slot");
        require(slot >= 0 && slot < 36, "invalid_item");
        ItemStack offered = player.getInventory().getItem(slot).copy();
        require(!offered.isEmpty(), "invalid_item");
        require(encode(player, offered).equals(request.getAsJsonObject("expected")), "inventory_changed");
        require(MerchantStore.activeListings(root, owner) < MerchantStore.MAX_ACTIVE_LISTINGS, "listing_limit");
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
        Plan plan = plan(player, offered, (int) total);
        offered.setCount(1);
        JsonObject listing = new JsonObject();
        listing.addProperty("id", UUID.randomUUID().toString());
        listing.addProperty("owner", owner);
        listing.addProperty("seller", seller);
        putStack(player, listing, offered);
        listing.addProperty("name", offered.getHoverName().getString());
        listing.addProperty("item", BuiltInRegistries.ITEM.getKey(offered.getItem()).toString());
        listing.addProperty("quantity", amount);
        listing.addProperty("lots", lots);
        listing.addProperty("revision", 0);
        listing.addProperty("created", System.currentTimeMillis());
        listing.add("payments", accepted);
        root.getAsJsonArray("listings").add(listing);
        transact(player, storage, root, plan, owner, requestId, new JsonArray(), request);
    }

    private static void buy(ServerPlayer player, MerchantStore storage, JsonObject root, JsonObject request, String owner, String buyer, String requestId) throws IOException {
        JsonObject listing = MerchantStore.find(root, "listings", text(request, "id"));
        ItemStack goodsPrototype = stackOf(player, listing);
        require(!goodsPrototype.isEmpty(), "invalid_item");
        int lots = integer(request, "lots");
        int option = integer(request, "option");
        MerchantStore.Purchase quote = MerchantStore.quote(listing, owner, integer(request, "revision"), lots, option);
        JsonArray alternatives = listing.getAsJsonArray("payments");
        JsonObject payment = alternatives.get(option).getAsJsonObject();
        long price = quote.price();
        long count = quote.quantity();
        Plan plan = null;
        JsonArray wallets = new JsonArray();
        String seller = text(listing, "owner");
        if (text(payment, "item").equals("antcoins")) {
            AntazonServerData coins = AntazonServerData.access(player.level().getServer());
            long buyerBalance = coins.wallet(UUID.fromString(owner));
            long sellerBalance = coins.wallet(UUID.fromString(seller));
            require(buyerBalance >= price, "insufficient_payment");
            require(sellerBalance <= Long.MAX_VALUE - price, "wallet_full");
            wallets.add(wallet(owner, buyerBalance, buyerBalance - price));
            wallets.add(wallet(seller, sellerBalance, sellerBalance + price));
        } else {
            ItemStack currency = plain(text(payment, "item"));
            plan = plan(player, currency, (int) price);
            JsonObject source = new JsonObject();
            putStack(player, source, currency);
            shipment(root, seller, source, (int) price, "payment", requestId);
        }
        if (plan == null) plan = unchanged(player);
        JsonObject goods = shipment(root, owner, listing, (int) count, "purchase", requestId);
        listing.addProperty("lots", quote.remaining());
        listing.addProperty("revision", quote.revision());
        JsonObject trade = new JsonObject();
        trade.addProperty("id", requestId);
        trade.addProperty("buyer", owner);
        trade.addProperty("buyerName", buyer);
        trade.addProperty("seller", seller);
        trade.addProperty("sellerName", text(listing, "seller"));
        trade.addProperty("created", System.currentTimeMillis());
        copyStack(listing, trade);
        trade.addProperty("quantity", count);
        trade.add("payment", payment.deepCopy());
        trade.addProperty("paid", price);
        root.getAsJsonArray("trades").add(trade);
        transact(player, storage, root, plan, owner, requestId, wallets, request);
        receipt(player.level().getServer(), text(listing, "seller"), "sold", Long.toString(count), text(listing, "name"), buyer);
        receipt(player.level().getServer(), buyer, "bought", Long.toString(count), text(listing, "name"));
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
        shipment(root, owner, listing, quantity, "return", requestId);
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
        var level = player.level();
        var location = player.blockPosition();
        require(level.getWorldBorder().isWithinBounds(location) && level.hasChunkAt(location) && location.getY() > level.getMinY() + 2, "unsafe_delivery");
        require(!player.isInLava() && !player.isUnderWater(), "unsafe_delivery");
        ItemStack prototype = stackOf(player, shipment);
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
        drop.setRewards(stacks, player);
        drop.prepareDelivery(location);
        JsonObject remainder = batch.remaining() > 0 ? shipment(root, text(shipment, "owner"), shipment, batch.remaining(), text(shipment, "kind"), text(shipment, "trade")) : null;
        shipment.addProperty("quantity", batch.sent());
        shipment.addProperty("status", "dispatched");
        shipment.addProperty("dimension", level.dimension().identifier().toString());
        shipment.addProperty("location", location.getX() + ", " + location.getY() + ", " + location.getZ());
        storage.commit(root);
        try {
            require(level.addFreshEntity(drop), "unsafe_delivery");
            level.getChunkSource().save(false);
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

    private static JsonObject shipment(JsonObject root, String owner, JsonObject source, int quantity, String kind, String trade) {
        JsonObject row = new JsonObject();
        row.addProperty("id", UUID.randomUUID().toString());
        row.addProperty("owner", owner);
        copyStack(source, row);
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

    private static void transact(ServerPlayer player, MerchantStore storage, JsonObject next, Plan plan, String owner, String requestId, JsonArray wallets, JsonObject request) throws IOException {
        JsonObject pending = new JsonObject();
        pending.addProperty("player", player.getUUID().toString());
        pending.add("before", plan.before());
        pending.add("after", plan.after());
        pending.add("removals", plan.removals());
        pending.add("wallets", wallets);
        next.add("pending", pending);
        recordRequest(next, owner, requestId, request);
        storage.commit(next);
        recover(player, storage);
    }

    private static void recover(ServerPlayer player, MerchantStore storage) throws IOException {
        if (storage.hasPending()) {
            JsonObject root = storage.snapshot();
            if (root.has("pending")) {
                JsonObject pending = root.getAsJsonObject("pending");
                root.remove("pending");
                settleWallets(player.level().getServer(), pending);
                pending.add("wallets", new JsonArray());
                ServerPlayer actor = player.level().getServer().getPlayerList().getPlayer(UUID.fromString(text(pending, "player")));
                if (actor == null || !settleInventory(actor, pending)) {
                    if (!root.has("deferred")) root.add("deferred", new JsonArray());
                    root.getAsJsonArray("deferred").add(pending);
                }
                storage.commit(root);
            }
        }
        String id = player.getUUID().toString();
        if (!storage.hasDeferred(id)) return;
        JsonObject root = storage.snapshot();
        JsonArray deferred = root.getAsJsonArray("deferred");
        boolean settled = true;
        for (int index = 0; index < deferred.size(); index++) {
            JsonObject entry = deferred.get(index).getAsJsonObject();
            if (!text(entry, "player").equals(id)) continue;
            if (!settleInventory(player, entry)) {
                settled = false;
                break;
            }
            deferred.remove(index--);
        }
        if (deferred.isEmpty()) root.remove("deferred");
        storage.commit(root);
        require(settled, "recovery_conflict");
    }

    private static void settleWallets(MinecraftServer server, JsonObject pending) {
        AntazonServerData coins = AntazonServerData.access(server);
        boolean changed = false;
        for (JsonElement element : pending.getAsJsonArray("wallets")) {
            JsonObject row = element.getAsJsonObject();
            UUID owner = UUID.fromString(text(row, "owner"));
            long balance = coins.wallet(owner);
            if (balance == row.get("before").getAsLong()) {
                coins.setWallet(owner, row.get("after").getAsLong());
                changed = true;
            } else if (balance != row.get("after").getAsLong()) {
                AntOS.LOGGER.warn("MERCHANT left wallet {} unchanged during recovery: expected {} or {}, found {}", owner, row.get("before"), row.get("after"), balance);
            }
        }
        if (changed) server.overworld().getDataStorage().saveAndJoin();
    }

    private static boolean settleInventory(ServerPlayer player, JsonObject entry) {
        JsonArray before = entry.getAsJsonArray("before");
        JsonArray after = entry.getAsJsonArray("after");
        if (before.equals(after)) return true;
        JsonArray current = inventory(player);
        if (current.equals(after)) return true;
        if (!current.equals(before)) return false;
        var inventory = player.getInventory();
        if (entry.has("removals")) {
            for (JsonElement element : entry.getAsJsonArray("removals")) {
                JsonObject removal = element.getAsJsonObject();
                inventory.getItem(integer(removal, "slot")).shrink(integer(removal, "count"));
            }
        } else {
            for (int slot = 0; slot < 36; slot++) {
                if (before.get(slot).equals(after.get(slot))) continue;
                inventory.setItem(slot, after.get(slot).isJsonNull() ? ItemStack.EMPTY : decode(player, after.get(slot).getAsJsonObject()));
            }
        }
        inventory.setChanged();
        player.containerMenu.broadcastChanges();
        savePlayer(player);
        return true;
    }

    private static void savePlayer(ServerPlayer player) {
        try {
            if (playerData == null) {
                for (Field field : MinecraftServer.class.getDeclaredFields()) {
                    if (field.getType() == PlayerDataStorage.class) {
                        field.setAccessible(true);
                        playerData = field;
                    }
                }
            }
            ((PlayerDataStorage) playerData.get(player.level().getServer())).save(player);
        } catch (RuntimeException | ReflectiveOperationException | LinkageError exception) {
            player.level().getServer().getPlayerList().saveAll();
        }
    }

    private static JsonArray inventory(ServerPlayer player) {
        JsonArray result = new JsonArray();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            result.add(item.isEmpty() ? com.google.gson.JsonNull.INSTANCE : encode(player, item));
        }
        return result;
    }

    private record Plan(JsonArray before, JsonArray after, JsonArray removals) { }

    private static Plan unchanged(ServerPlayer player) {
        JsonArray before = inventory(player);
        return new Plan(before, before.deepCopy(), new JsonArray());
    }

    private static Plan plan(ServerPlayer player, ItemStack prototype, int quantity) {
        List<ItemStack> live = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) live.add(player.getInventory().getItem(slot));
        List<ItemStack> remaining = MerchantInventory.take(live, prototype, quantity);
        JsonArray before = inventory(player);
        JsonArray after = new JsonArray();
        JsonArray removals = new JsonArray();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = remaining.get(slot);
            after.add(item.isEmpty() ? com.google.gson.JsonNull.INSTANCE : encode(player, item));
            int taken = live.get(slot).getCount() - item.getCount();
            if (taken > 0) {
                JsonObject removal = new JsonObject();
                removal.addProperty("slot", slot);
                removal.addProperty("count", taken);
                removals.add(removal);
            }
        }
        return new Plan(before, after, removals);
    }

    private static void putStack(ServerPlayer player, JsonObject row, ItemStack stack) {
        row.add("stack", encode(player, stack));
        try {
            row.addProperty("nbt", ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, player.level().getServer().registryAccess()), stack).getOrThrow().toString());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid_item");
        }
    }

    private static void copyStack(JsonObject source, JsonObject row) {
        row.add("stack", source.get("stack").deepCopy());
        if (source.has("nbt")) row.add("nbt", source.get("nbt").deepCopy());
    }

    private static ItemStack stackOf(ServerPlayer player, JsonObject row) {
        if (!row.has("nbt")) return decode(player, row.getAsJsonObject("stack"));
        try {
            return ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, player.level().getServer().registryAccess()), TagParser.parseCompoundFully(text(row, "nbt"))).getOrThrow();
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid_item");
        }
    }

    public static JsonObject encode(ServerPlayer player, ItemStack stack) {
        try {
            return ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, player.level().getServer().registryAccess()), stack).getOrThrow().getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid_item");
        }
    }

    private static ItemStack decode(ServerPlayer player, JsonObject value) {
        try {
            return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, player.level().getServer().registryAccess()), value).getOrThrow();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid_item");
        }
    }

    private static ItemStack plain(String id) {
        Identifier key = Identifier.tryParse(id);
        require(key != null && BuiltInRegistries.ITEM.containsKey(key), "invalid_currency");
        return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
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
            result.addProperty("wallet", AntazonServerData.access(player.level().getServer()).wallet(UUID.fromString(owner)));
            result.addProperty("pending", root.getAsJsonArray("shipments").asList().stream().map(JsonElement::getAsJsonObject).filter(row -> text(row, "owner").equals(owner) && text(row, "status").equals("pending")).count());
            return result;
        }
        AntmailServerData mail = AntmailServerData.access(player.level().getServer());
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
        int size = request.has("size") ? integer(request, "size") : PAGE;
        int page = request.has("page") ? Math.max(0, integer(request, "page")) : 0;
        page = Math.min(page, Math.max(0, (rows.size() - 1) / size));
        JsonArray pageRows = new JsonArray();
        for (int index = page * size; index < Math.min(rows.size(), (page + 1) * size); index++) pageRows.add(rows.get(index).deepCopy());
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
        result.addProperty("size", size);
        result.addProperty("owner", owner);
        result.addProperty("view", view);
        result.addProperty("wallet", AntazonServerData.access(player.level().getServer()).wallet(UUID.fromString(owner)));
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
        available.put("antcoins", AntazonServerData.access(player.level().getServer()).wallet(AnternetAccountHandler.session(player).accountId()));
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
            if (ItemStack.isSameItemSameComponents(item, new ItemStack(item.getItem()))) available.merge(id, (long) item.getCount(), Long::sum);
        }
        return available;
    }

    private static void receipt(MinecraftServer server, String username, String kind, String... values) {
        String key = "computer.antos.merchant.receipt." + kind;
        String body = key + "_body" + RECEIPT_SEPARATOR + String.join(RECEIPT_SEPARATOR, values);
        try {
            AntmailServerData.access(server).deliver(server, AntmailMessage.create(AntmailAddress.ofUsername("merchant"), AntmailAddress.ofUsername(username), key + "_subject", body + "\n\nmerchant://deliveries", server.overworld().getGameTime(), List.of()));
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
