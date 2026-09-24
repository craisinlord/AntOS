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
    private final Map<ResourceLocation, Stock> stock = new LinkedHashMap<>();
    private final Map<UUID, Long> wallets = new LinkedHashMap<>();
    private final Map<UUID, Map<ResourceLocation, PlayerState>> players = new LinkedHashMap<>();
    private final Map<UUID, Set<ResourceLocation>> wishlists = new LinkedHashMap<>();
    private final Map<UUID, Set<ResourceLocation>> reviewed = new LinkedHashMap<>();
    private final Set<String> onboardingComputers = new LinkedHashSet<>();
    private final Map<UUID, CrateLocation> shippingCrates = new LinkedHashMap<>();
    private final List<PlayerReview> reviews = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();

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
        ListTag orderTags = tag.getList("Orders", 10);
        for (int index = 0; index < orderTags.size(); index++) {
            CompoundTag row = orderTags.getCompound(index);
            try {
                data.orders.add(new Order(UUID.fromString(row.getString("Id")), UUID.fromString(row.getString("Player")),
                        ResourceLocation.parse(row.getString("Product")), row.getString("Option"), row.getInt("Units"),
                        row.getLong("GameTime"), row.getString("Status")));
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
        ListTag orderTags = new ListTag();
        int start = Math.max(0, orders.size() - 1024);
        for (int index = start; index < orders.size(); index++) {
            Order order = orders.get(index);
            CompoundTag row = new CompoundTag();
            row.putString("Id", order.id().toString());
            row.putString("Player", order.player().toString());
            row.putString("Product", order.product().toString());
            row.putString("Option", order.option());
            row.putInt("Units", order.units());
            row.putLong("GameTime", order.gameTime());
            row.putString("Status", order.status());
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
        int reviewStart = Math.max(0, reviews.size() - 2048);
        for (int index = reviewStart; index < reviews.size(); index++) {
            PlayerReview review = reviews.get(index);
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
        return tag;
    }

    public synchronized CrateLocation shippingCrate(UUID owner) {
        return owner == null ? null : shippingCrates.get(owner);
    }

    public synchronized void setShippingCrate(UUID owner, ResourceLocation dimension, net.minecraft.core.BlockPos position) {
        if (owner == null || dimension == null || position == null) return;
        shippingCrates.put(owner, new CrateLocation(dimension.toString(), position.asLong()));
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
                orders.set(index, new Order(order.id(), profileId, order.product(), order.option(), order.units(), order.gameTime(), order.status()));
                changed = true;
            }
        }
        for (int index = 0; index < reviews.size(); index++) {
            PlayerReview review = reviews.get(index);
            if (review.player().equals(playerId)) {
                reviews.set(index, new PlayerReview(profileId, review.product(), review.rating(), review.title(), review.body(), review.gameTime()));
                changed = true;
            }
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

    public synchronized void addOrder(Order order) {
        orders.add(order);
        setDirty();
    }

    public synchronized List<Order> orders(UUID player) {
        return orders.stream().filter(order -> order.player().equals(player)).toList();
    }

    public synchronized Set<ResourceLocation> wishlist(UUID player) {
        return Set.copyOf(wishlists.getOrDefault(player, Set.of()));
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
        reviewed.computeIfAbsent(review.player(), ignored -> new LinkedHashSet<>()).add(review.product());
        setDirty();
    }

    public synchronized List<PlayerReview> reviews(ResourceLocation product) {
        return reviews.stream().filter(review -> review.product().equals(product)).toList();
    }

    private static ResourceLocation parse(String value) {
        try { return ResourceLocation.parse(value); }
        catch (RuntimeException exception) { return null; }
    }

    public record Stock(int remaining, long nextRestockDay) { }
    public record PlayerState(int quantity, long reset, long lastPurchase) { }
    public record Order(UUID id, UUID player, ResourceLocation product, String option, int units, long gameTime, String status) { }
    public record PlayerReview(UUID player, ResourceLocation product, int rating, String title, String body, long gameTime) { }
}
