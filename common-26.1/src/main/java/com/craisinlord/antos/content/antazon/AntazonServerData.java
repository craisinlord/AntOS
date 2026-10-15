package com.craisinlord.antos.content.antazon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
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
    private static final net.minecraft.world.level.saveddata.SavedDataType<AntazonServerData> FACTORY = com.craisinlord.antos.content.AntOSSavedDataType.create(DATA_ID, AntazonServerData::new, tag -> load(tag, null), (data, tag) -> data.save(tag, null));
    private static volatile Cached cached;
    private final Map<Identifier, Stock> stock = new LinkedHashMap<>();
    private final Map<UUID, Long> wallets = new LinkedHashMap<>();
    private final Map<UUID, Map<Identifier, PlayerState>> players = new LinkedHashMap<>();
    private final Map<UUID, Map<String, SellLimitState>> sellLimits = new LinkedHashMap<>();
    private final Map<UUID, Map<String, DealUsage>> dealUsage = new LinkedHashMap<>();
    private final Map<UUID, Set<Identifier>> wishlists = new LinkedHashMap<>();
    private final Map<UUID, Set<Identifier>> reviewed = new LinkedHashMap<>();
    private final Set<String> onboardingComputers = new LinkedHashSet<>();
    private final Map<UUID, CrateLocation> shippingCrates = new LinkedHashMap<>();
    private final List<PlayerReview> reviews = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final Map<UUID, List<CartLine>> carts = new LinkedHashMap<>();
    private final Map<UUID, Set<Identifier>> purchased = new LinkedHashMap<>();
    private final Map<Identifier, List<PlayerReview>> reviewsByProduct = new LinkedHashMap<>();
    private final Map<Identifier, Long> reviewRevisions = new LinkedHashMap<>();
    private final long reviewRevisionBase = System.currentTimeMillis();
    private final Map<UUID, Set<String>> unlockMailSent = new LinkedHashMap<>();
    private boolean legacyUnlockMailImported;

    public static AntazonServerData access(MinecraftServer server) {
        Cached current = cached;
        if (current != null && current.server().get() == server) {
            AntazonServerData data = current.data().get();
            if (data != null) return data;
        }
        com.craisinlord.antos.content.AntOSSavedDataType.migrateLegacyFile(server, DATA_ID);
        AntazonServerData data = server.overworld().getDataStorage().computeIfAbsent(FACTORY);
        if (current == null || current.server().get() != server) AntazonDeals.clear();
        cached = new Cached(new java.lang.ref.WeakReference<>(server), new java.lang.ref.WeakReference<>(data));
        return data;
    }

    private record Cached(java.lang.ref.WeakReference<MinecraftServer> server, java.lang.ref.WeakReference<AntazonServerData> data) { }

    private static AntazonServerData load(CompoundTag tag, HolderLookup.Provider registries) {
        AntazonServerData data = new AntazonServerData();
        ListTag onboardingTags = tag.getListOrEmpty("Onboarding");
        for (int index = 0; index < onboardingTags.size(); index++) {
            data.onboardingComputers.add(onboardingTags.getStringOr(index, ""));
        }
        ListTag unlockMailTags = tag.getListOrEmpty("UnlockMailSent");
        for (int index = 0; index < unlockMailTags.size(); index++) {
            CompoundTag row = unlockMailTags.getCompoundOrEmpty(index);
            try {
                UUID account = UUID.fromString(row.getStringOr("Account", ""));
                ListTag listings = row.getListOrEmpty("Listings");
                Set<String> sent = data.unlockMailSent.computeIfAbsent(account, ignored -> new LinkedHashSet<>());
                for (int listingIndex = 0; listingIndex < listings.size(); listingIndex++) sent.add(listings.getStringOr(listingIndex, ""));
            } catch (RuntimeException ignored) { }
        }
        data.legacyUnlockMailImported = tag.getBooleanOr("LegacyUnlockMailImported", false);
        ListTag crateTags = tag.getListOrEmpty("ShippingCrates");
        for (int index = 0; index < crateTags.size(); index++) {
            CompoundTag row = crateTags.getCompoundOrEmpty(index);
            try {
                data.shippingCrates.put(UUID.fromString(row.getStringOr("Owner", "")),
                        new CrateLocation(row.getStringOr("Dimension", ""), row.getLongOr("Position", 0L)));
            } catch (RuntimeException ignored) { }
        }
        ListTag walletTags = tag.getListOrEmpty("Wallets");
        for (int index = 0; index < walletTags.size(); index++) {
            CompoundTag row = walletTags.getCompoundOrEmpty(index);
            try {
                data.wallets.put(UUID.fromString(row.getStringOr("Owner", "")), Math.max(0L, row.getLongOr("Balance", 0L)));
            } catch (RuntimeException ignored) { }
        }
        ListTag stockTags = tag.getListOrEmpty("Stock");
        for (int index = 0; index < stockTags.size(); index++) {
            CompoundTag row = stockTags.getCompoundOrEmpty(index);
            Identifier id = parse(row.getStringOr("Id", ""));
            if (id != null) data.stock.put(id, new Stock(Math.max(0, row.getIntOr("Remaining", 0)), row.getLongOr("NextRestockDay", 0L)));
        }
        ListTag playerTags = tag.getListOrEmpty("Players");
        for (int index = 0; index < playerTags.size(); index++) {
            CompoundTag playerTag = playerTags.getCompoundOrEmpty(index);
            try {
                UUID player = UUID.fromString(playerTag.getStringOr("Id", ""));
                Map<Identifier, PlayerState> states = new LinkedHashMap<>();
                ListTag statesTag = playerTag.getListOrEmpty("Products");
                for (int stateIndex = 0; stateIndex < statesTag.size(); stateIndex++) {
                    CompoundTag stateTag = statesTag.getCompoundOrEmpty(stateIndex);
                    Identifier product = parse(stateTag.getStringOr("Product", ""));
                    if (product != null) states.put(product, new PlayerState(Math.max(0, stateTag.getIntOr("Quantity", 0)), stateTag.getLongOr("Reset", 0L), stateTag.getLongOr("LastPurchase", 0L)));
                }
                data.players.put(player, states);
            } catch (RuntimeException ignored) {
            }
        }
        ListTag sellLimitTags = tag.getListOrEmpty("SellLimits");
        for (int index = 0; index < sellLimitTags.size(); index++) {
            CompoundTag row = sellLimitTags.getCompoundOrEmpty(index);
            try {
                UUID owner = UUID.fromString(row.getStringOr("Owner", ""));
                String key = row.getStringOr("Key", "");
                if (!key.isBlank()) data.sellLimits.computeIfAbsent(owner, ignored -> new LinkedHashMap<>())
                        .put(key, new SellLimitState(Math.max(0, row.getIntOr("Quantity", 0)), row.getLongOr("Reset", 0L)));
            } catch (RuntimeException ignored) { }
        }
        ListTag dealUsageTags = tag.getListOrEmpty("DealUsage");
        for (int index = 0; index < dealUsageTags.size(); index++) {
            CompoundTag row = dealUsageTags.getCompoundOrEmpty(index);
            try {
                UUID owner = UUID.fromString(row.getStringOr("Owner", ""));
                String product = row.getStringOr("Product", "");
                if (!product.isBlank()) data.dealUsage.computeIfAbsent(owner, ignored -> new LinkedHashMap<>())
                        .put(product, new DealUsage(row.getStringOr("Deal", ""), Math.max(0, row.getIntOr("Units", 0))));
            } catch (RuntimeException ignored) { }
        }
        ListTag orderTags = tag.getListOrEmpty("Orders");
        for (int index = 0; index < orderTags.size(); index++) {
            CompoundTag row = orderTags.getCompoundOrEmpty(index);
            try {
                data.orders.add(new Order(UUID.fromString(row.getStringOr("Id", "")), UUID.fromString(row.getStringOr("Player", "")),
                        Identifier.parse(row.getStringOr("Product", "")), row.getStringOr("Option", ""), row.getIntOr("Units", 0),
                        row.getLongOr("GameTime", 0L), row.getStringOr("Status", ""), row.getIntOr("PaidAmount", 0), row.getIntOr("DealDiscount", 0), row.getStringOr("DealCampaign", ""), row.getIntOr("DealUnits", 0)));
            } catch (RuntimeException ignored) {
            }
        }
        ListTag wishlistTags = tag.getListOrEmpty("Wishlists");
        for (int index = 0; index < wishlistTags.size(); index++) {
            CompoundTag row = wishlistTags.getCompoundOrEmpty(index);
            try {
                UUID player = UUID.fromString(row.getStringOr("Player", ""));
                Set<Identifier> ids = new LinkedHashSet<>();
                ListTag idsTag = row.getListOrEmpty("Products");
                for (int idIndex = 0; idIndex < idsTag.size(); idIndex++) {
                    Identifier id = parse(idsTag.getStringOr(idIndex, ""));
                    if (id != null) ids.add(id);
                }
                data.wishlists.put(player, ids);
            } catch (RuntimeException ignored) { }
        }
        ListTag reviewedTags = tag.getListOrEmpty("Reviewed");
        for (int index = 0; index < reviewedTags.size(); index++) {
            CompoundTag row = reviewedTags.getCompoundOrEmpty(index);
            try {
                UUID player = UUID.fromString(row.getStringOr("Player", ""));
                Identifier product = parse(row.getStringOr("Product", ""));
                if (product != null) data.reviewed.computeIfAbsent(player, ignored -> new LinkedHashSet<>()).add(product);
            } catch (RuntimeException ignored) { }
        }
        ListTag reviewTags = tag.getListOrEmpty("PlayerReviews");
        for (int index = 0; index < reviewTags.size(); index++) {
            CompoundTag row = reviewTags.getCompoundOrEmpty(index);
            try {
                data.reviews.add(new PlayerReview(UUID.fromString(row.getStringOr("Player", "")), Identifier.parse(row.getStringOr("Product", "")),
                        row.getIntOr("Rating", 0), row.getStringOr("Title", ""), row.getStringOr("Body", ""), row.getLongOr("GameTime", 0L)));
            } catch (RuntimeException ignored) { }
        }
        ListTag purchasedTags = tag.getListOrEmpty("Purchased");
        for (int index = 0; index < purchasedTags.size(); index++) {
            CompoundTag row = purchasedTags.getCompoundOrEmpty(index);
            try {
                UUID player = UUID.fromString(row.getStringOr("Player", ""));
                ListTag idsTag = row.getListOrEmpty("Products");
                for (int idIndex = 0; idIndex < idsTag.size(); idIndex++) {
                    Identifier id = parse(idsTag.getStringOr(idIndex, ""));
                    if (id != null) data.purchased.computeIfAbsent(player, ignored -> new LinkedHashSet<>()).add(id);
                }
            } catch (RuntimeException ignored) { }
        }
        // Saves from before the purchased index only have the order history to go on.
        for (Order order : data.orders) data.recordPurchase(order);
        data.trimHistory();
        data.rebuildReviewIndex();
        ListTag cartTags = tag.getListOrEmpty("Carts");
        for (int index = 0; index < cartTags.size(); index++) {
            CompoundTag row = cartTags.getCompoundOrEmpty(index);
            try {
                UUID owner = UUID.fromString(row.getStringOr("Owner", ""));
                List<CartLine> lines = new ArrayList<>();
                ListTag lineTags = row.getListOrEmpty("Lines");
                for (int lineIndex = 0; lineIndex < lineTags.size(); lineIndex++) {
                    CompoundTag line = lineTags.getCompoundOrEmpty(lineIndex);
                    Identifier product = parse(line.getStringOr("Product", ""));
                    if (product != null) lines.add(new CartLine(product, Math.max(0, line.getIntOr("Option", 0)), Math.max(1, Math.min(64, line.getIntOr("Units", 0))), line.getStringOr("Variant", "")));
                }
                if (!lines.isEmpty()) data.carts.put(owner, lines);
            } catch (RuntimeException ignored) { }
        }
        return data;
    }
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag stockTags = new ListTag();
        for (Map.Entry<Identifier, Stock> entry : stock.entrySet()) {
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
        for (Map.Entry<UUID, Map<Identifier, PlayerState>> playerEntry : players.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putString("Id", playerEntry.getKey().toString());
            ListTag states = new ListTag();
            for (Map.Entry<Identifier, PlayerState> stateEntry : playerEntry.getValue().entrySet()) {
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
        ListTag dealUsageTags = new ListTag();
        for (Map.Entry<UUID, Map<String, DealUsage>> ownerEntry : dealUsage.entrySet()) {
            for (Map.Entry<String, DealUsage> usageEntry : ownerEntry.getValue().entrySet()) {
                CompoundTag row = new CompoundTag();
                row.putString("Owner", ownerEntry.getKey().toString());
                row.putString("Product", usageEntry.getKey());
                row.putString("Deal", usageEntry.getValue().deal());
                row.putInt("Units", usageEntry.getValue().units());
                dealUsageTags.add(row);
            }
        }
        tag.put("DealUsage", dealUsageTags);
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
            row.putString("DealCampaign", order.dealCampaign());
            row.putInt("DealUnits", order.dealUnits());
            orderTags.add(row);
        }
        tag.put("Orders", orderTags);
        ListTag wishlistTags = new ListTag();
        for (Map.Entry<UUID, Set<Identifier>> entry : wishlists.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Player", entry.getKey().toString());
            ListTag ids = new ListTag();
            for (Identifier id : entry.getValue()) ids.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
            row.put("Products", ids);
            wishlistTags.add(row);
        }
        tag.put("Wishlists", wishlistTags);
        ListTag reviewedTags = new ListTag();
        for (Map.Entry<UUID, Set<Identifier>> entry : reviewed.entrySet()) {
            for (Identifier product : entry.getValue()) {
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
        ListTag unlockMailTags = new ListTag();
        for (Map.Entry<UUID, Set<String>> entry : unlockMailSent.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag row = new CompoundTag();
            row.putString("Account", entry.getKey().toString());
            ListTag listings = new ListTag();
            for (String listing : entry.getValue()) listings.add(net.minecraft.nbt.StringTag.valueOf(listing));
            row.put("Listings", listings);
            unlockMailTags.add(row);
        }
        tag.put("UnlockMailSent", unlockMailTags);
        tag.putBoolean("LegacyUnlockMailImported", legacyUnlockMailImported);
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
        for (Map.Entry<UUID, Set<Identifier>> entry : purchased.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag row = new CompoundTag();
            row.putString("Player", entry.getKey().toString());
            ListTag ids = new ListTag();
            for (Identifier id : entry.getValue()) ids.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
            row.put("Products", ids);
            purchasedTags.add(row);
        }
        tag.put("Purchased", purchasedTags);
        return tag;
    }

    public synchronized CrateLocation shippingCrate(UUID owner) {
        return owner == null ? null : shippingCrates.get(owner);
    }

    public synchronized void setShippingCrate(UUID owner, Identifier dimension, net.minecraft.core.BlockPos position) {
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
        boolean changed = complete ? onboardingComputers.add(computer) : onboardingComputers.remove(computer);
        if (changed) setDirty();
    }

    public synchronized boolean unlockMailSent(UUID account, String listing) {
        return account != null && unlockMailSent.getOrDefault(account, Set.of()).contains(listing);
    }

    public synchronized void markUnlockMailSent(UUID account, Set<String> listings) {
        if (account == null || listings == null || listings.isEmpty()) return;
        if (unlockMailSent.computeIfAbsent(account, ignored -> new LinkedHashSet<>()).addAll(listings)) setDirty();
    }

    public synchronized void importLegacyUnlockMail(Set<String> entries, java.util.function.Predicate<UUID> knownAccount) {
        if (legacyUnlockMailImported) return;
        legacyUnlockMailImported = true;
        for (String entry : entries) {
            int separator = entry.indexOf(':');
            if (separator <= 0 || separator == entry.length() - 1) continue;
            try {
                UUID account = UUID.fromString(entry.substring(0, separator));
                if (knownAccount.test(account)) unlockMailSent.computeIfAbsent(account, ignored -> new LinkedHashSet<>()).add(entry.substring(separator + 1));
            } catch (RuntimeException ignored) { }
        }
        setDirty();
    }

    public synchronized boolean legacyUnlockMailImported() {
        return legacyUnlockMailImported;
    }

    public synchronized Stock stock(Identifier productId) {
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
        Map<Identifier, PlayerState> oldPlayerStates = players.remove(playerId);
        if (oldPlayerStates != null) {
            Map<Identifier, PlayerState> profileStates = players.computeIfAbsent(profileId, ignored -> new LinkedHashMap<>());
            for (Map.Entry<Identifier, PlayerState> entry : oldPlayerStates.entrySet()) {
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
        Map<String, DealUsage> oldDealUsage = dealUsage.remove(playerId);
        if (oldDealUsage != null) {
            Map<String, DealUsage> profileUsage = dealUsage.computeIfAbsent(profileId, ignored -> new LinkedHashMap<>());
            for (Map.Entry<String, DealUsage> entry : oldDealUsage.entrySet()) {
                DealUsage current = profileUsage.get(entry.getKey());
                DealUsage old = entry.getValue();
                if (current == null || !current.deal().equals(old.deal())) profileUsage.putIfAbsent(entry.getKey(), old);
                else profileUsage.put(entry.getKey(), new DealUsage(current.deal(), (int) Math.min(Integer.MAX_VALUE, (long) current.units() + old.units())));
            }
            changed = true;
        }
        Set<Identifier> oldWishlist = wishlists.remove(playerId);
        if (oldWishlist != null) {
            wishlists.computeIfAbsent(profileId, ignored -> new LinkedHashSet<>()).addAll(oldWishlist);
            changed = true;
        }
        Set<Identifier> oldReviews = reviewed.remove(playerId);
        if (oldReviews != null) {
            reviewed.computeIfAbsent(profileId, ignored -> new LinkedHashSet<>()).addAll(oldReviews);
            changed = true;
        }
        for (int index = 0; index < orders.size(); index++) {
            Order order = orders.get(index);
            if (order.player().equals(playerId)) {
                orders.set(index, new Order(order.id(), profileId, order.product(), order.option(), order.units(), order.gameTime(), order.status(), order.paidAmount(), order.dealDiscount(), order.dealCampaign(), order.dealUnits()));
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
        Set<Identifier> oldPurchased = purchased.remove(playerId);
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

    public synchronized void setStock(Identifier productId, Stock value) {
        stock.put(productId, value);
        setDirty();
    }

    public synchronized PlayerState playerState(UUID player, Identifier productId) {
        return players.getOrDefault(player, Map.of()).getOrDefault(productId, new PlayerState(0, Long.MIN_VALUE, Long.MIN_VALUE));
    }

    public synchronized void setPlayerState(UUID player, Identifier productId, PlayerState state) {
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
            bumpReviewRevision(oldest.product());
            List<PlayerReview> productReviews = reviewsByProduct.get(oldest.product());
            if (productReviews != null && !productReviews.isEmpty()) {
                productReviews.remove(0);
                if (productReviews.isEmpty()) reviewsByProduct.remove(oldest.product());
            }
        }
    }

    private void rebuildReviewIndex() {
        reviewsByProduct.keySet().forEach(this::bumpReviewRevision);
        reviewsByProduct.clear();
        for (PlayerReview review : reviews) reviewsByProduct.computeIfAbsent(review.product(), ignored -> new ArrayList<>()).add(review);
        reviewsByProduct.keySet().forEach(this::bumpReviewRevision);
    }

    private void bumpReviewRevision(Identifier product) {
        reviewRevisions.merge(product, 1L, Long::sum);
    }

    public synchronized List<Order> orders(UUID player) {
        return orders.stream().filter(order -> order.player().equals(player)).toList();
    }

    public synchronized Map<String, DealMetrics> dealMetrics() {
        Map<String, DealMetrics> metrics = new LinkedHashMap<>();
        for (Order order : orders) {
            if (!order.status().equals("DELIVERED")) continue;
            String key = order.product().toString();
            boolean deal = order.dealDiscount() > 0 && order.dealUnits() > 0;
            DealMetrics previous = metrics.getOrDefault(key, new DealMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
            metrics.put(key, new DealMetrics(previous.orders() + 1, previous.units() + order.units(),
                    previous.revenue() + order.paidAmount(), previous.dealOrders() + (deal ? 1 : 0),
                    previous.discountPercentTotal() + (deal ? order.dealDiscount() : 0), previous.regularOrders() + (deal ? 0 : 1),
                    previous.dealUnits() + (deal ? order.dealUnits() : 0), previous.regularUnits() + order.units() - (deal ? order.dealUnits() : 0),
                    previous.dealRevenue() + (deal ? order.paidAmount() : 0), previous.regularRevenue() + (deal ? 0 : order.paidAmount())));
        }
        return Map.copyOf(metrics);
    }

    public synchronized Map<String, CampaignMetrics> campaignMetrics() {
        Map<String, CampaignMetrics> metrics = new LinkedHashMap<>();
        for (Order order : orders) {
            if (!order.status().equals("DELIVERED") || order.dealCampaign().isBlank() || order.dealUnits() <= 0) continue;
            CampaignMetrics previous = metrics.getOrDefault(order.dealCampaign(), new CampaignMetrics(0, 0, 0, 0));
            metrics.put(order.dealCampaign(), new CampaignMetrics(previous.orders() + 1, previous.dealUnits() + order.dealUnits(),
                    previous.revenue() + order.paidAmount(), previous.discountPercentTotal() + order.dealDiscount()));
        }
        return Map.copyOf(metrics);
    }

    public synchronized int dealUnitsUsed(UUID owner, String deal, Identifier product) {
        if (owner == null) return 0;
        DealUsage usage = dealUsage.getOrDefault(owner, Map.of()).get(product.toString());
        return usage != null && usage.deal().equals(deal) ? usage.units() : 0;
    }

    public synchronized void addDealUnits(UUID owner, String deal, Identifier product, int units) {
        if (owner == null || units <= 0) return;
        Map<String, DealUsage> usage = dealUsage.computeIfAbsent(owner, ignored -> new LinkedHashMap<>());
        DealUsage current = usage.get(product.toString());
        int previous = current != null && current.deal().equals(deal) ? current.units() : 0;
        usage.put(product.toString(), new DealUsage(deal, (int) Math.min(Integer.MAX_VALUE, (long) previous + units)));
        setDirty();
    }

    public synchronized List<Identifier> wishlist(UUID player) {
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

    public synchronized boolean hasPurchased(UUID player, Identifier product) {
        return purchased.getOrDefault(player, Set.of()).contains(product);
    }

    public synchronized boolean toggleWishlist(UUID player, Identifier product) {
        Set<Identifier> ids = wishlists.computeIfAbsent(player, ignored -> new LinkedHashSet<>());
        boolean added = ids.add(product);
        if (!added) ids.remove(product);
        setDirty();
        return added;
    }

    public synchronized boolean hasReviewed(UUID player, Identifier product) {
        return reviewed.getOrDefault(player, Set.of()).contains(product);
    }

    public synchronized void addReview(PlayerReview review) {
        reviews.add(review);
        reviewsByProduct.computeIfAbsent(review.product(), ignored -> new ArrayList<>()).add(review);
        bumpReviewRevision(review.product());
        reviewed.computeIfAbsent(review.player(), ignored -> new LinkedHashSet<>()).add(review.product());
        trimHistory();
        setDirty();
    }

    public synchronized List<PlayerReview> reviews(Identifier product) {
        return List.copyOf(reviewsByProduct.getOrDefault(product, List.of()));
    }

    public synchronized List<PlayerReview> reviews(Identifier product, int offset, int limit) {
        List<PlayerReview> productReviews = reviewsByProduct.getOrDefault(product, List.of());
        int start = Math.max(0, Math.min(offset, productReviews.size()));
        int end = (int) Math.min(productReviews.size(), (long) start + Math.max(0, limit));
        return List.copyOf(productReviews.subList(start, end));
    }

    public synchronized int reviewCount(Identifier product) {
        return reviewsByProduct.getOrDefault(product, List.of()).size();
    }

    public synchronized long reviewRevision(Identifier product) {
        return reviewRevisionBase + reviewRevisions.getOrDefault(product, 0L);
    }

    private static Identifier parse(String value) {
        try { return Identifier.parse(value); }
        catch (RuntimeException exception) { return null; }
    }

    public record Stock(int remaining, long nextRestockDay) { }
    public record PlayerState(int quantity, long reset, long lastPurchase) { }
    public record SellLimitState(int quantity, long reset) { }
    public record Order(UUID id, UUID player, Identifier product, String option, int units, long gameTime, String status,
                        int paidAmount, int dealDiscount, String dealCampaign, int dealUnits) { }
    public record DealUsage(String deal, int units) { }
    public record CampaignMetrics(int orders, int dealUnits, long revenue, long discountPercentTotal) { }
    public record DealMetrics(int orders, int units, long revenue, int dealOrders, long discountPercentTotal,
                              int regularOrders, int dealUnits, int regularUnits, long dealRevenue, long regularRevenue) { }
    public record CartLine(Identifier product, int option, int units, String variant) {
        public CartLine(Identifier product, int option, int units) { this(product, option, units, ""); }
    }
    public record PlayerReview(UUID player, Identifier product, int rating, String title, String body, long gameTime) { }
}
