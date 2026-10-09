package com.craisinlord.antos.content.antazon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class AntazonServerData extends SavedData {
    private static final String DATA_ID = "antos_antazon";
    public static final int MAX_CART_LINES = 32;
    private static final int MAX_ORDERS = 1024;
    private static final int MAX_REVIEWS = 2048;
    private final Map<ResourceLocation, Stock> stock = new LinkedHashMap<>();
    private final Map<UUID, Long> wallets = new LinkedHashMap<>();
    private final Map<UUID, Map<ResourceLocation, PlayerState>> players = new LinkedHashMap<>();
    private final Map<UUID, Map<String, SellLimitState>> sellLimits = new LinkedHashMap<>();
    private final Map<UUID, Set<ResourceLocation>> wishlists = new LinkedHashMap<>();
    private final Map<UUID, Set<ResourceLocation>> reviewed = new LinkedHashMap<>();
    private final Set<String> onboardingComputers = new LinkedHashSet<>();
    private final Map<UUID, CrateLocation> shippingCrates = new LinkedHashMap<>();
    private final List<PlayerReview> reviews = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final Map<UUID, List<CartLine>> carts = new LinkedHashMap<>();
    private final Map<UUID, Set<ResourceLocation>> purchased = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<PlayerReview>> reviewsByProduct = new LinkedHashMap<>();

    public static AntazonServerData access(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AntazonServerData::new, AntazonServerData::load, null), DATA_ID);
    }

    private static AntazonServerData load(CompoundTag tag, HolderLookup.Provider registries) {
        AntazonServerData data = new AntazonServerData();
        ListTag onboardingTags = tag.getList("Onboarding", 8);
        for (int index = 0; index < onboardingTags.size(); index++) {
            data.onboardingComputers.add(onboardingTags.getString(index));
        }
        ListTag crateTags = tag.getList("ShippingCrates", 10);
        for (int index = 0; index < crateTags.size(); index++) {
            CompoundTag row = crateTags.getCompound(index);
            try {
                data.shippingCrates.put(UUID.fromString(row.getString("Owner")),
                        new CrateLocation(row.getString("Dimension"), row.getLong("Position")));
            } catch (RuntimeException ignored) { }
        }
        ListTag walletTags = tag.getList("Wallets", 10);
        for (int index = 0; index < walletTags.size(); index++) {
            CompoundTag row = walletTags.getCompound(index);
            try {
                data.wallets.put(UUID.fromString(row.getString("Owner")), Math.max(0L, row.getLong("Balance")));
            } catch (RuntimeException ignored) { }
        }
        ListTag stockTags = tag.getList("Stock", 10);
        for (int index = 0; index < stockTags.size(); index++) {
            CompoundTag row = stockTags.getCompound(index);
            ResourceLocation id = parse(row.getString("Id"));
            if (id != null) data.stock.put(id, new Stock(Math.max(0, row.getInt("Remaining")), row.getLong("NextRestockDay")));
        }
        ListTag playerTags = tag.getList("Players", 10);
        for (int index = 0; index < playerTags.size(); index++) {
            CompoundTag playerTag = playerTags.getCompound(index);
            try {
                UUID player = UUID.fromString(playerTag.getString("Id"));
                Map<ResourceLocation, PlayerState> states = new LinkedHashMap<>();
                ListTag statesTag = playerTag.getList("Products", 10);
                for (int stateIndex = 0; stateIndex < statesTag.size(); stateIndex++) {
                    CompoundTag stateTag = statesTag.getCompound(stateIndex);
                    ResourceLocation product = parse(stateTag.getString("Product"));
                    if (product != null) states.put(product, new PlayerState(Math.max(0, stateTag.getInt("Quantity")), stateTag.getLong("Reset"), stateTag.getLong("LastPurchase")));
                }
                data.players.put(player, states);
            } catch (RuntimeException ignored) {
            }
        }
        ListTag sellLimitTags = tag.getList("SellLimits", 10);
        for (int index = 0; index < sellLimitTags.size(); index++) {
            CompoundTag row = sellLimitTags.getCompound(index);
            try {
                UUID owner = UUID.fromString(row.getString("Owner"));
                String key = row.getString("Key");
                if (!key.isBlank()) data.sellLimits.computeIfAbsent(owner, ignored -> new LinkedHashMap<>())
                        .put(key, new SellLimitState(Math.max(0, row.getInt("Quantity")), row.getLong("Reset")));
            } catch (RuntimeException ignored) { }
        }
        ListTag orderTags = tag.getList("Orders", 10);
        for (int index = 0; index < orderTags.size(); index++) {
            CompoundTag row = orderTags.getCompound(index);
            try {
                data.orders.add(new Order(UUID.fromString(row.getString("Id")), UUID.fromString(row.getString("Player")),
                        ResourceLocation.parse(row.getString("Product")), row.getString("Option"), row.getInt("Units"),
                        row.getLong("GameTime"), row.getString("Status"), row.getInt("PaidAmount"), row.getInt("DealDiscount"), row.getString("DealPool")));
            } catch (RuntimeException ignored) {
            }
        }
        ListTag wishlistTags = tag.getList("Wishlists", 10);
        for (int index = 0; index < wishlistTags.size(); index++) {
            CompoundTag row = wishlistTags.getCompound(index);
            try {
                UUID player = UUID.fromString(row.getString("Player"));
                Set<ResourceLocation> ids = new LinkedHashSet<>();
                ListTag idsTag = row.getList("Products", 8);
                for (int idIndex = 0; idIndex < idsTag.size(); idIndex++) {
                    ResourceLocation id = parse(idsTag.getString(idIndex));
                    if (id != null) ids.add(id);
                }
                data.wishlists.put(player, ids);
            } catch (RuntimeException ignored) { }
        }
        ListTag reviewedTags = tag.getList("Reviewed", 10);
        for (int index = 0; index < reviewedTags.size(); index++) {
            CompoundTag row = reviewedTags.getCompound(index);
            try {
                UUID player = UUID.fromString(row.getString("Player"));
                ResourceLocation product = parse(row.getString("Product"));
                if (product != null) data.reviewed.computeIfAbsent(player, ignored -> new LinkedHashSet<>()).add(product);
            } catch (RuntimeException ignored) { }
        }
        ListTag reviewTags = tag.getList("PlayerReviews", 10);
        for (int index = 0; index < reviewTags.size(); index++) {
            CompoundTag row = reviewTags.getCompound(index);
            try {
                data.reviews.add(new PlayerReview(UUID.fromString(row.getString("Player")), ResourceLocation.parse(row.getString("Product")),
                        row.getInt("Rating"), row.getString("Title"), row.getString("Body"), row.getLong("GameTime")));
            } catch (RuntimeException ignored) { }
        }
        ListTag purchasedTags = tag.getList("Purchased", 10);
        for (int index = 0; index < purchasedTags.size(); index++) {
            CompoundTag row = purchasedTags.getCompound(index);
            try {
                UUID player = UUID.fromString(row.getString("Player"));
                ListTag idsTag = row.getList("Products", 8);
                for (int idIndex = 0; idIndex < idsTag.size(); idIndex++) {
                    ResourceLocation id = parse(idsTag.getString(idIndex));
                    if (id != null) data.purchased.computeIfAbsent(player, ignored -> new LinkedHashSet<>()).add(id);
                }
            } catch (RuntimeException ignored) { }
        }
        // Saves from before the purchased index only have the order history to go on.
        for (Order order : data.orders) data.recordPurchase(order);
        data.trimHistory();
        data.rebuildReviewIndex();
        ListTag cartTags = tag.getList("Carts", 10);
        for (int index = 0; index < cartTags.size(); index++) {
            CompoundTag row = cartTags.getCompound(index);
            try {
                UUID owner = UUID.fromString(row.getString("Owner"));
                List<CartLine> lines = new ArrayList<>();
                ListTag lineTags = row.getList("Lines", 10);
                for (int lineIndex = 0; lineIndex < lineTags.size(); lineIndex++) {
                    CompoundTag line = lineTags.getCompound(lineIndex);
                    ResourceLocation product = parse(line.getString("Product"));
                    if (product != null) lines.add(new CartLine(product, Math.max(0, line.getInt("Option")), Math.max(1, Math.min(64, line.getInt("Units"))), line.getString("Variant")));
                }
                if (!lines.isEmpty()) data.carts.put(owner, lines);
            } catch (RuntimeException ignored) { }
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag stockTags = new ListTag();
        for (Map.Entry<ResourceLocation, Stock> entry : stock.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Id", entry.getKey().toString());
            row.putInt("Remaining", entry.getValue().remaining());
            row.putLong("NextRestockDay", entry.getValue().nextRestockDay());
            stockTags.add(row);
        }
        tag.put("Stock", stockTags);
        ListTag walletTags = new ListTag();
        for (Map.Entry<UUID, Long> entry : wallets.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Owner", entry.getKey().toString());
            row.putLong("Balance", Math.max(0L, entry.getValue()));
            walletTags.add(row);
        }
        tag.put("Wallets", walletTags);
        ListTag playerTags = new ListTag();
        for (Map.Entry<UUID, Map<ResourceLocation, PlayerState>> playerEntry : players.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putString("Id", playerEntry.getKey().toString());
            ListTag states = new ListTag();
            for (Map.Entry<ResourceLocation, PlayerState> stateEntry : playerEntry.getValue().entrySet()) {
                CompoundTag row = new CompoundTag();
                row.putString("Product", stateEntry.getKey().toString());
                row.putInt("Quantity", stateEntry.getValue().quantity());
                row.putLong("Reset", stateEntry.getValue().reset());
                row.putLong("LastPurchase", stateEntry.getValue().lastPurchase());
                states.add(row);
            }
            playerTag.put("Products", states);
            playerTags.add(playerTag);
        }
        tag.put("Players", playerTags);
        ListTag sellLimitTags = new ListTag();
        for (Map.Entry<UUID, Map<String, SellLimitState>> ownerEntry : sellLimits.entrySet()) {
            for (Map.Entry<String, SellLimitState> stateEntry : ownerEntry.getValue().entrySet()) {
                CompoundTag row = new CompoundTag();
                row.putString("Owner", ownerEntry.getKey().toString());
                row.putString("Key", stateEntry.getKey());
                row.putInt("Quantity", stateEntry.getValue().quantity());
                row.putLong("Reset", stateEntry.getValue().reset());
                sellLimitTags.add(row);
            }
        }
        tag.put("SellLimits", sellLimitTags);
        ListTag orderTags = new ListTag();
        for (Order order : orders) {
            CompoundTag row = new CompoundTag();
            row.putString("Id", order.id().toString());
            row.putString("Player", order.player().toString());
            row.putString("Product", order.product().toString());
            row.putString("Option", order.option());
            row.putInt("Units", order.units());
            row.putLong("GameTime", order.gameTime());
            row.putString("Status", order.status());
            row.putInt("PaidAmount", order.paidAmount());
            row.putInt("DealDiscount", order.dealDiscount());
            row.putString("DealPool", order.dealPool());
            orderTags.add(row);
        }
        tag.put("Orders", orderTags);
        ListTag wishlistTags = new ListTag();
        for (Map.Entry<UUID, Set<ResourceLocation>> entry : wishlists.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Player", entry.getKey().toString());
            ListTag ids = new ListTag();
            for (ResourceLocation id : entry.getValue()) ids.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
            row.put("Products", ids);
            wishlistTags.add(row);
        }
        tag.put("Wishlists", wishlistTags);
        ListTag reviewedTags = new ListTag();
        for (Map.Entry<UUID, Set<ResourceLocation>> entry : reviewed.entrySet()) {
            for (ResourceLocation product : entry.getValue()) {
                CompoundTag row = new CompoundTag();
                row.putString("Player", entry.getKey().toString());
                row.putString("Product", product.toString());
                reviewedTags.add(row);
            }
        }
        tag.put("Reviewed", reviewedTags);
        ListTag reviewTags = new ListTag();
        for (PlayerReview review : reviews) {
            CompoundTag row = new CompoundTag();
            row.putString("Player", review.player().toString());
            row.putString("Product", review.product().toString());
            row.putInt("Rating", review.rating());
            row.putString("Title", review.title());
            row.putString("Body", review.body());
            row.putLong("GameTime", review.gameTime());
            reviewTags.add(row);
        }
        tag.put("PlayerReviews", reviewTags);
        ListTag onboardingTags = new ListTag();
        for (String computer : onboardingComputers) onboardingTags.add(net.minecraft.nbt.StringTag.valueOf(computer));
        tag.put("Onboarding", onboardingTags);
        ListTag crateTags = new ListTag();
        for (Map.Entry<UUID, CrateLocation> entry : shippingCrates.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Owner", entry.getKey().toString());
            row.putString("Dimension", entry.getValue().dimension());
            row.putLong("Position", entry.getValue().position());
            crateTags.add(row);
        }
        tag.put("ShippingCrates", crateTags);
        ListTag cartTags = new ListTag();
        for (Map.Entry<UUID, List<CartLine>> entry : carts.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag row = new CompoundTag();
            row.putString("Owner", entry.getKey().toString());
            ListTag lines = new ListTag();
            for (CartLine line : entry.getValue()) {
                CompoundTag lineTag = new CompoundTag();
                lineTag.putString("Product", line.product().toString());
                lineTag.putInt("Option", line.option());
                lineTag.putInt("Units", line.units());
                lineTag.putString("Variant", line.variant());
                lines.add(lineTag);
            }
            row.put("Lines", lines);
            cartTags.add(row);
        }
        tag.put("Carts", cartTags);
        ListTag purchasedTags = new ListTag();
        for (Map.Entry<UUID, Set<ResourceLocation>> entry : purchased.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag row = new CompoundTag();
            row.putString("Player", entry.getKey().toString());
            ListTag ids = new ListTag();
            for (ResourceLocation id : entry.getValue()) ids.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
            row.put("Products", ids);
            purchasedTags.add(row);
        }
        tag.put("Purchased", purchasedTags);
        return tag;
    }

    public synchronized CrateLocation shippingCrate(UUID owner) {
        return owner == null ? null : shippingCrates.get(owner);
    }

    public synchronized void setShippingCrate(UUID owner, ResourceLocation dimension, net.minecraft.core.BlockPos position) {
        if (owner == null || dimension == null || position == null) return;
        CrateLocation location = new CrateLocation(dimension.toString(), position.asLong());
        if (location.equals(shippingCrates.put(owner, location))) return;
        setDirty();
    }

    public synchronized void clearShippingCrate(UUID owner) {
        if (owner != null && shippingCrates.remove(owner) != null) setDirty();
    }

    public record CrateLocation(String dimension, long position) { }

    public synchronized boolean onboardingComplete(String computer) { return onboardingComputers.contains(computer); }

    public synchronized void setOnboardingComplete(String computer, boolean complete) {
        if (complete) onboardingComputers.add(computer); else onboardingComputers.remove(computer);
        setDirty();
    }

    public synchronized Stock stock(ResourceLocation productId) {
        return stock.get(productId);
    }

    public synchronized long wallet(UUID owner) {
        return owner == null ? 0L : Math.max(0L, wallets.getOrDefault(owner, 0L));
    }

    public synchronized void migratePlayerToProfile(UUID playerId, UUID profileId) {
        if (playerId == null || profileId == null || playerId.equals(profileId)) return;
        boolean changed = false;
        CrateLocation oldCrate = shippingCrates.remove(playerId);
        if (oldCrate != null) {
            shippingCrates.putIfAbsent(profileId, oldCrate);
            changed = true;
        }
        Long oldWallet = wallets.remove(playerId);
        if (oldWallet != null && oldWallet > 0L) {
            wallets.put(profileId, saturatedAdd(wallet(profileId), oldWallet));
            changed = true;
        }
        Map<ResourceLocation, PlayerState> oldPlayerStates = players.remove(playerId);
        if (oldPlayerStates != null) {
            Map<ResourceLocation, PlayerState> profileStates = players.computeIfAbsent(profileId, ignored -> new LinkedHashMap<>());
            for (Map.Entry<ResourceLocation, PlayerState> entry : oldPlayerStates.entrySet()) {
                PlayerState old = entry.getValue();
                PlayerState current = profileStates.get(entry.getKey());
                if (current == null) profileStates.put(entry.getKey(), old);
                else {
                    long reset = Math.max(current.reset(), old.reset());
                    int quantity = current.reset() == old.reset()
                            ? (int) Math.min(Integer.MAX_VALUE, (long) current.quantity() + old.quantity())
                            : current.reset() > old.reset() ? current.quantity() : old.quantity();
                    profileStates.put(entry.getKey(), new PlayerState(quantity, reset, Math.max(current.lastPurchase(), old.lastPurchase())));
                }
            }
            changed = true;
        }
        Map<String, SellLimitState> oldSellLimits = sellLimits.remove(playerId);
        if (oldSellLimits != null) {
            Map<String, SellLimitState> profileLimits = sellLimits.computeIfAbsent(profileId, ignored -> new LinkedHashMap<>());
            for (Map.Entry<String, SellLimitState> entry : oldSellLimits.entrySet()) {
                SellLimitState current = profileLimits.get(entry.getKey());
                SellLimitState old = entry.getValue();
                if (current == null) profileLimits.put(entry.getKey(), old);
                else if (current.reset() == old.reset()) profileLimits.put(entry.getKey(), new SellLimitState(
                        (int) Math.min(Integer.MAX_VALUE, (long) current.quantity() + old.quantity()), current.reset()));
                else profileLimits.put(entry.getKey(), current.reset() > old.reset() ? current : old);
            }
            changed = true;
        }
        Set<ResourceLocation> oldWishlist = wishlists.remove(playerId);
        if (oldWishlist != null) {
            wishlists.computeIfAbsent(profileId, ignored -> new LinkedHashSet<>()).addAll(oldWishlist);
            changed = true;
        }
        Set<ResourceLocation> oldReviews = reviewed.remove(playerId);
        if (oldReviews != null) {
            reviewed.computeIfAbsent(profileId, ignored -> new LinkedHashSet<>()).addAll(oldReviews);
            changed = true;
        }
        for (int index = 0; index < orders.size(); index++) {
            Order order = orders.get(index);
            if (order.player().equals(playerId)) {
                orders.set(index, new Order(order.id(), profileId, order.product(), order.option(), order.units(), order.gameTime(), order.status(), order.paidAmount(), order.dealDiscount(), order.dealPool()));
                changed = true;
            }
        }
        List<CartLine> oldCart = carts.remove(playerId);
        if (oldCart != null && !oldCart.isEmpty()) {
            List<CartLine> merged = new ArrayList<>(carts.getOrDefault(profileId, List.of()));
            for (CartLine line : oldCart) if (merged.size() < MAX_CART_LINES) merged.add(line);
            carts.put(profileId, List.copyOf(merged));
            changed = true;
        }
        Set<ResourceLocation> oldPurchased = purchased.remove(playerId);
        if (oldPurchased != null) {
            purchased.computeIfAbsent(profileId, ignored -> new LinkedHashSet<>()).addAll(oldPurchased);
            changed = true;
        }
        boolean reviewsChanged = false;
        for (int index = 0; index < reviews.size(); index++) {
            PlayerReview review = reviews.get(index);
            if (review.player().equals(playerId)) {
                reviews.set(index, new PlayerReview(profileId, review.product(), review.rating(), review.title(), review.body(), review.gameTime()));
                reviewsChanged = true;
            }
        }
        if (reviewsChanged) {
            rebuildReviewIndex();
            changed = true;
        }
        if (changed) setDirty();
    }

    private static long saturatedAdd(long first, long second) {
        if (second > 0L && first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        if (second < 0L && first < Long.MIN_VALUE - second) return Long.MIN_VALUE;
        return first + second;
    }

    public synchronized void credit(UUID owner, long amount) {
        if (owner == null || amount < 1L) return;
        wallets.put(owner, saturatedAdd(wallet(owner), amount));
        setDirty();
    }

    public synchronized void setWallet(UUID owner, long amount) {
        if (owner == null) return;
        wallets.put(owner, Math.max(0L, amount));
        setDirty();
    }

    public synchronized boolean debit(UUID owner, long amount) {
        if (owner == null || amount < 1L || wallet(owner) < amount) return false;
        wallets.put(owner, wallet(owner) - amount);
        setDirty();
        return true;
    }

    public synchronized void setStock(ResourceLocation productId, Stock value) {
        stock.put(productId, value);
        setDirty();
    }

    public synchronized PlayerState playerState(UUID player, ResourceLocation productId) {
        return players.getOrDefault(player, Map.of()).getOrDefault(productId, new PlayerState(0, Long.MIN_VALUE, Long.MIN_VALUE));
    }

    public synchronized void setPlayerState(UUID player, ResourceLocation productId, PlayerState state) {
        players.computeIfAbsent(player, ignored -> new LinkedHashMap<>()).put(productId, state);
        setDirty();
    }

    public synchronized SellLimitState sellLimitState(UUID owner, String key) {
        return sellLimits.getOrDefault(owner, Map.of()).getOrDefault(key, new SellLimitState(0, Long.MIN_VALUE));
    }

    public synchronized void setSellLimitState(UUID owner, String key, SellLimitState state) {
        if (owner == null || key == null || key.isBlank() || state == null) return;
        sellLimits.computeIfAbsent(owner, ignored -> new LinkedHashMap<>()).put(key, state);
        setDirty();
    }

    public synchronized void addOrder(Order order) {
        orders.add(order);
        recordPurchase(order);
        trimHistory();
        setDirty();
    }

    private void recordPurchase(Order order) {
        if (order.status().equals("DELIVERED")) purchased.computeIfAbsent(order.player(), ignored -> new LinkedHashSet<>()).add(order.product());
    }

    private void trimHistory() {
        if (orders.size() > MAX_ORDERS) orders.subList(0, orders.size() - MAX_ORDERS).clear();
        while (reviews.size() > MAX_REVIEWS) {
            PlayerReview oldest = reviews.remove(0);
            List<PlayerReview> productReviews = reviewsByProduct.get(oldest.product());
            if (productReviews != null && !productReviews.isEmpty()) {
                productReviews.remove(0);
                if (productReviews.isEmpty()) reviewsByProduct.remove(oldest.product());
            }
        }
    }

    private void rebuildReviewIndex() {
        reviewsByProduct.clear();
        for (PlayerReview review : reviews) reviewsByProduct.computeIfAbsent(review.product(), ignored -> new ArrayList<>()).add(review);
    }

    public synchronized List<Order> orders(UUID player) {
        return orders.stream().filter(order -> order.player().equals(player)).toList();
    }

    public synchronized Map<String, DealMetrics> dealMetrics() {
        Map<String, DealMetrics> metrics = new LinkedHashMap<>();
        for (Order order : orders) {
            if (!order.status().equals("DELIVERED")) continue;
            String key = order.product().toString() + (order.dealPool().isBlank() ? "" : " [" + order.dealPool() + "]");
            boolean deal = order.dealDiscount() > 0;
            DealMetrics previous = metrics.getOrDefault(key, new DealMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
            metrics.put(key, new DealMetrics(previous.orders() + 1, previous.units() + order.units(),
                    previous.revenue() + order.paidAmount(), previous.dealOrders() + (deal ? 1 : 0),
                    previous.discountPercentTotal() + order.dealDiscount(), previous.regularOrders() + (deal ? 0 : 1),
                    previous.dealUnits() + (deal ? order.units() : 0), previous.regularUnits() + (deal ? 0 : order.units()),
                    previous.dealRevenue() + (deal ? order.paidAmount() : 0), previous.regularRevenue() + (deal ? 0 : order.paidAmount())));
        }
        return Map.copyOf(metrics);
    }

    public synchronized List<ResourceLocation> wishlist(UUID player) {
        return List.copyOf(wishlists.getOrDefault(player, Set.of()));
    }

    public synchronized List<CartLine> cart(UUID owner) {
        return owner == null ? List.of() : List.copyOf(carts.getOrDefault(owner, List.of()));
    }

    public synchronized void setCart(UUID owner, List<CartLine> lines) {
        if (owner == null) return;
        if (lines == null || lines.isEmpty()) carts.remove(owner);
        else carts.put(owner, List.copyOf(lines.subList(0, Math.min(MAX_CART_LINES, lines.size()))));
        setDirty();
    }

    public synchronized boolean hasPurchased(UUID player, ResourceLocation product) {
        return purchased.getOrDefault(player, Set.of()).contains(product);
    }

    public synchronized boolean toggleWishlist(UUID player, ResourceLocation product) {
        Set<ResourceLocation> ids = wishlists.computeIfAbsent(player, ignored -> new LinkedHashSet<>());
        boolean added = ids.add(product);
        if (!added) ids.remove(product);
        setDirty();
        return added;
    }

    public synchronized boolean hasReviewed(UUID player, ResourceLocation product) {
        return reviewed.getOrDefault(player, Set.of()).contains(product);
    }

    public synchronized void addReview(PlayerReview review) {
        reviews.add(review);
        reviewsByProduct.computeIfAbsent(review.product(), ignored -> new ArrayList<>()).add(review);
        reviewed.computeIfAbsent(review.player(), ignored -> new LinkedHashSet<>()).add(review.product());
        trimHistory();
        setDirty();
    }

    public synchronized List<PlayerReview> reviews(ResourceLocation product) {
        return List.copyOf(reviewsByProduct.getOrDefault(product, List.of()));
    }

    private static ResourceLocation parse(String value) {
        try { return ResourceLocation.parse(value); }
        catch (RuntimeException exception) { return null; }
    }

    public record Stock(int remaining, long nextRestockDay) { }
    public record PlayerState(int quantity, long reset, long lastPurchase) { }
    public record SellLimitState(int quantity, long reset) { }
    public record Order(UUID id, UUID player, ResourceLocation product, String option, int units, long gameTime, String status,
                        int paidAmount, int dealDiscount, String dealPool) { }
    public record DealMetrics(int orders, int units, long revenue, int dealOrders, long discountPercentTotal,
                              int regularOrders, int dealUnits, int regularUnits, long dealRevenue, long regularRevenue) { }
    public record CartLine(ResourceLocation product, int option, int units, String variant) {
        public CartLine(ResourceLocation product, int option, int units) { this(product, option, units, ""); }
    }
    public record PlayerReview(UUID player, ResourceLocation product, int rating, String title, String body, long gameTime) { }
}
