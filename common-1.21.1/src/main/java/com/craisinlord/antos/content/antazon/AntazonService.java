package com.craisinlord.antos.content.antazon;

import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.computer.ComputerWorkspace;
import com.craisinlord.antos.content.computer.ComputerTasks;
import com.craisinlord.antos.content.entity.RewardDropEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AntazonService {
    private static final long MINECRAFT_DAY = 24000L;

    private AntazonService() { }

    public static PurchaseResult purchase(ServerPlayer player, ComputerWorkspace computer,
                                          ResourceLocation productId, String optionId, int units) {
        if (player == null || computer == null || productId == null || optionId == null || units < 1 || units > 64)
            return PurchaseResult.failed("invalid_request");
        CrateRef deliveryCrate = resolveCrate(player, computer);
        List<ItemStack> deliveries = new ArrayList<>();
        List<ItemStack> directDeliveries = new ArrayList<>();
        List<LocationDelivery> locationDeliveries = new ArrayList<>();
        PurchaseResult result = purchase(player, computer, deliveryCrate, productId, optionId, units, deliveries, directDeliveries, locationDeliveries);
        if (result.success()) {
            launchDelivery(player, deliveryCrate, deliveries);
            deliverDirect(player, directDeliveries);
            launchLocationDeliveries(player, locationDeliveries);
        }
        return result;
    }

    /**
     * Charges for and records one purchase, adding its reward stacks to {@code deliveries} instead of launching them,
     * so checkout can resolve the crate once and send every line in a single drop.
     */
    private static PurchaseResult purchase(ServerPlayer player, ComputerWorkspace computer, CrateRef deliveryCrate,
                                           ResourceLocation productId, String optionId, int units, List<ItemStack> deliveries,
                                           List<ItemStack> directDeliveries, List<LocationDelivery> locationDeliveries) {
        if (player == null || computer == null || productId == null || optionId == null || units < 1 || units > 64)
            return PurchaseResult.failed("invalid_request");
        AntazonData.Product product = AntazonData.product(productId);
        if (product == null || !product.enabled()) return PurchaseResult.failed("product_unavailable");
        if (!unlocked(player, computer, product)) return PurchaseResult.failed("task_locked");
        int optionIndex = -1;
        if (!optionId.equals("default")) {
            try { optionIndex = Integer.parseInt(optionId); }
            catch (NumberFormatException exception) { return PurchaseResult.failed("invalid_request"); }
            if (optionIndex < 0 || optionIndex >= product.payments().size()) return PurchaseResult.failed("invalid_request");
        }
        if (product.delivery().equals("falling_chest") && deliveryCrate == null) return PurchaseResult.failed("crate_unavailable");
        BlockPos deliveryLocation = null;
        if (product.delivery().equals("falling_chest_location")) {
            AntazonData.DeliveryLocation coordinates = product.deliveryLocation();
            if (coordinates == null) return PurchaseResult.failed("delivery_unavailable");
            deliveryLocation = new BlockPos(coordinates.x(), coordinates.y(), coordinates.z());
            ServerLevel level = player.serverLevel();
            if (!level.isInWorldBounds(deliveryLocation) || !level.getWorldBorder().isWithinBounds(deliveryLocation)
                    || !level.hasChunkAt(deliveryLocation)) return PurchaseResult.failed("delivery_unavailable");
        }
        long gameTime = player.serverLevel().getGameTime();
        long day = gameTime / MINECRAFT_DAY;
        if (!AntazonData.availableOn(product, day)) return PurchaseResult.failed("product_unavailable");
        AntazonServerData data = AntazonServerData.access(player.server);
        synchronized (data) {
            AntazonData.Availability availability = product.availability();
            AntazonServerData.Stock current = currentStock(data, product, day);
            int requiredUnits = Math.multiplyExact(product.quantity(), units);
            if (availability.serverStock() > 0 && current.remaining() < requiredUnits) return PurchaseResult.failed("out_of_stock");
            UUID profile = accountOwner(computer, player);
            ResourceLocation limitKey = AntazonData.limitKey(product);
            AntazonServerData.PlayerState playerState = data.playerState(profile, limitKey);
            long reset = resetMarker(availability.playerLimitReset(), day);
            int playerQuantity = playerState.reset() == reset ? playerState.quantity() : 0;
            if (availability.playerLimit() > 0 && playerQuantity + requiredUnits > availability.playerLimit()) return PurchaseResult.failed("player_limit");
            if (availability.cooldownMinecraftDays() > 0 && playerState.lastPurchase() != Long.MIN_VALUE
                    && gameTime < playerState.lastPurchase() + availability.cooldownMinecraftDays() * MINECRAFT_DAY)
                return PurchaseResult.failed("cooldown");
            List<ItemStack> rewards = rewards(product.rewards(), requiredUnits);
            if (rewards.isEmpty()) return PurchaseResult.failed("delivery_unavailable");
            UUID account = accountOwner(computer, player);
            AntazonData.Payment payment = null;
            String paymentReceipt = "";
            List<AntazonData.Payment> candidates = optionIndex < 0 ? product.payments() : List.of(product.payments().get(optionIndex));
            for (AntazonData.Payment candidate : candidates) {
                int totalPayment = Math.multiplyExact(unitPrice(product, candidate, day), units);
                if (pay(player, data, account, candidate, totalPayment)) {
                    payment = candidate;
                    paymentReceipt = totalPayment + " " + (candidate.type().equals("antcoins") ? "antcoins" : candidate.resource());
                    break;
                }
            }
            if (payment == null) return PurchaseResult.failed(optionIndex < 0 ? "payment_unavailable" : "insufficient_payment");
            // Unlimited products have no stock to track, so they never touch the stock table.
            if (availability.serverStock() > 0) data.setStock(limitKey, new AntazonServerData.Stock(current.remaining() - requiredUnits, current.nextRestockDay()));
            data.setPlayerState(profile, limitKey, new AntazonServerData.PlayerState(playerQuantity + requiredUnits, reset, gameTime));
            UUID orderId = UUID.randomUUID();
            AntazonData.Deal deal = activeDeal(product, day);
            int paidAmount = Math.multiplyExact(unitPrice(product, payment, day), units);
            data.addOrder(new AntazonServerData.Order(orderId, profile, productId, paymentReceipt, requiredUnits, gameTime, "DELIVERED",
                    paidAmount, deal.enabled() ? deal.discountPercent() : 0, deal.enabled() ? deal.pool() : ""));
            if (product.delivery().equals("direct")) directDeliveries.addAll(rewards);
            else if (product.delivery().equals("falling_chest_location")) locationDeliveries.add(new LocationDelivery(deliveryLocation, rewards));
            else deliveries.addAll(rewards);
            return PurchaseResult.accepted(orderId, requiredUnits, paymentReceipt);
        }
    }

    /** Merges like stacks and drops them on the crate, splitting into several crates only past the per-drop stack cap. */
    private static void launchDelivery(ServerPlayer player, CrateRef crate, List<ItemStack> deliveries) {
        if (crate == null || deliveries.isEmpty()) return;
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : deliveries) {
            ItemStack remaining = stack.copy();
            for (ItemStack existing : merged) {
                if (remaining.isEmpty()) break;
                if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue;
                int moved = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(moved);
                remaining.shrink(moved);
            }
            while (!remaining.isEmpty()) merged.add(remaining.split(remaining.getMaxStackSize()));
        }
        BlockPos landing = crate.position();
        int perDrop = RewardDropEntity.MAX_REWARD_STACKS - 1;
        for (int start = 0; start < merged.size(); start += perDrop) {
            List<ItemStack> contents = new ArrayList<>(merged.subList(start, Math.min(merged.size(), start + perDrop)));
            contents.add(new ItemStack(Items.CHEST, 1));
            RewardDropEntity drop = new RewardDropEntity(AntOSObjects.REWARD_DROP_ENTITY.get(), crate.level());
            drop.setPos(landing.getX() + 0.5D, RewardDropEntity.spawnHeight(crate.level(), landing), landing.getZ() + 0.5D);
            drop.setRewards(contents, player);
            crate.level().addFreshEntity(drop);
        }
        RewardDropEntity.announceIncoming(player);
    }

    private static void deliverDirect(ServerPlayer player, List<ItemStack> deliveries) {
        for (ItemStack stack : deliveries) {
            ItemStack remaining = stack.copy();
            player.getInventory().add(remaining);
            if (!remaining.isEmpty()) player.drop(remaining, false);
        }
    }

    private static void launchLocationDeliveries(ServerPlayer player, List<LocationDelivery> deliveries) {
        ServerLevel level = player.serverLevel();
        for (LocationDelivery delivery : deliveries) {
            List<ItemStack> contents = new ArrayList<>();
            for (ItemStack stack : delivery.rewards()) {
                ItemStack remaining = stack.copy();
                while (!remaining.isEmpty()) contents.add(remaining.split(remaining.getMaxStackSize()));
            }
            contents.add(new ItemStack(Items.CHEST, 1));
            for (int start = 0; start < contents.size(); start += RewardDropEntity.MAX_REWARD_STACKS) {
                List<ItemStack> chunk = new ArrayList<>(contents.subList(start, Math.min(contents.size(), start + RewardDropEntity.MAX_REWARD_STACKS)));
                RewardDropEntity drop = new RewardDropEntity(AntOSObjects.REWARD_DROP_ENTITY.get(), level);
                BlockPos landing = delivery.position();
                drop.setPos(landing.getX() + 0.5D, RewardDropEntity.spawnHeight(level, landing), landing.getZ() + 0.5D);
                drop.setRewards(chunk, player);
                level.addFreshEntity(drop);
            }
            RewardDropEntity.announceIncoming(player);
        }
    }

    public static CheckoutResult checkout(ServerPlayer player, ComputerWorkspace computer) {
        if (player == null || computer == null || !computer.canUseFileSystem(player)) return new CheckoutResult(List.of(), List.of());
        AntazonServerData data = AntazonServerData.access(player.server);
        UUID owner = accountOwner(computer, player);
        List<AntazonServerData.CartLine> cart = data.cart(owner);
        boolean hasFallingChestOrder = cart.stream().anyMatch(line -> {
            AntazonData.Product product = AntazonData.product(line.product());
            return product != null && product.delivery().equals("falling_chest");
        });
        CrateRef crate = hasFallingChestOrder ? resolveCrate(player, computer) : null;
        List<ItemStack> deliveries = new ArrayList<>();
        List<ItemStack> directDeliveries = new ArrayList<>();
        List<LocationDelivery> locationDeliveries = new ArrayList<>();
        List<AntazonServerData.CartLine> remaining = new ArrayList<>();
        List<CheckoutLine> results = new ArrayList<>();
        for (AntazonServerData.CartLine line : cart) {
            PurchaseResult result;
            try {
                result = purchase(player, computer, crate, line.product(), Integer.toString(line.option()), line.units(), deliveries, directDeliveries, locationDeliveries);
            } catch (ArithmeticException exception) {
                result = PurchaseResult.failed("invalid_request");
            }
            results.add(new CheckoutLine(line, result.status(), result.receipt()));
            if (!result.success() && !result.status().equals("product_unavailable")) remaining.add(line);
        }
        launchDelivery(player, crate, deliveries);
        deliverDirect(player, directDeliveries);
        launchLocationDeliveries(player, locationDeliveries);
        if (!cart.equals(remaining)) data.setCart(owner, remaining);
        return new CheckoutResult(List.copyOf(results), List.copyOf(remaining));
    }

    public static List<AntazonServerData.CartLine> sanitizeCart(List<AntazonServerData.CartLine> lines, long day) {
        Map<String, AntazonServerData.CartLine> merged = new LinkedHashMap<>();
        for (AntazonServerData.CartLine line : lines) {
            AntazonData.Product product = AntazonData.product(line.product());
            if (product == null || !product.enabled() || !AntazonData.availableOn(product, day)
                    || line.option() < 0 || line.option() >= product.payments().size()) continue;
            String key = line.product() + "|" + line.option();
            AntazonServerData.CartLine previous = merged.get(key);
            if (previous == null && merged.size() >= AntazonServerData.MAX_CART_LINES) continue;
            int units = Math.max(1, Math.min(64, line.units() + (previous == null ? 0 : previous.units())));
            merged.put(key, new AntazonServerData.CartLine(line.product(), line.option(), units));
        }
        return List.copyOf(merged.values());
    }

    public static int unitPrice(AntazonData.Product product, AntazonData.Payment payment, long day) {
        AntazonData.Deal deal = activeDeal(product, day);
        return deal.enabled()
                ? Math.max(1, payment.amount() * (100 - deal.discountPercent()) / 100)
                : payment.amount();
    }

    public static AntazonData.Deal activeDeal(AntazonData.Product product, long day) {
        AntazonData.Deal deal = product.deal();
        if (!deal.enabled()) return deal;
        if (deal.pool().isBlank()) return deal.active(day) ? deal : inactiveDeal(deal);
        List<AntazonData.Product> pool = AntazonData.products().stream()
                .filter(candidate -> candidate.enabled() && candidate.deal().enabled() && candidate.deal().pool().equals(deal.pool())
                        && AntazonData.availableOn(candidate, day))
                .sorted(java.util.Comparator.comparing(candidate -> candidate.id().toString())).toList();
        if (pool.isEmpty()) return inactiveDeal(deal);
        int selected = Math.floorMod(day, pool.size());
        return pool.get(selected).id().equals(product.id()) ? deal : inactiveDeal(deal);
    }

    private static AntazonData.Deal inactiveDeal(AntazonData.Deal deal) {
        return new AntazonData.Deal(false, deal.label(), deal.discountPercent(), deal.cycleMinecraftDays(), deal.dayOffset(), deal.pool());
    }

    public static AntazonServerData.Stock currentStock(AntazonServerData data, AntazonData.Product product, long day) {
        AntazonData.Availability availability = product.availability();
        AntazonServerData.Stock current = data.stock(AntazonData.limitKey(product));
        boolean real = availability.restockRealHours() > 0L;
        long clock = real ? System.currentTimeMillis() / 60_000L : day;
        long interval = real ? availability.restockRealHours() * 60L : availability.restockMinecraftDays();
        if (current == null) current = new AntazonServerData.Stock(availability.serverStock(), clock + interval);
        if (interval > 0 && clock >= current.nextRestockDay())
            current = new AntazonServerData.Stock(availability.serverStock(), clock + interval);
        return current;
    }

    public static long restockInMillis(AntazonData.Product product, AntazonServerData.Stock stock, long gameTime) {
        AntazonData.Availability availability = product.availability();
        if (availability.serverStock() <= 0 || !availability.restocks() || stock.remaining() >= availability.serverStock()) return 0L;
        if (availability.restockRealHours() > 0L) return Math.max(0L, stock.nextRestockDay() * 60_000L - System.currentTimeMillis());
        return Math.max(0L, (stock.nextRestockDay() * MINECRAFT_DAY - gameTime) * 50L);
    }

    public static int limitUsed(AntazonServerData data, UUID profile, AntazonData.Product product, long day) {
        AntazonServerData.PlayerState state = data.playerState(profile, AntazonData.limitKey(product));
        return state.reset() == resetMarker(product.availability().playerLimitReset(), day) ? state.quantity() : 0;
    }

    public static long owned(ServerPlayer player, AntazonServerData data, UUID account, AntazonData.Payment payment) {
        if (payment.type().equals("antcoins")) return data.wallet(account);
        if (!payment.type().equals("item")) return 0L;
        java.util.function.Predicate<ItemStack> matches = AntazonTags.matcher(payment.resource());
        if (matches == null) return 0L;
        long count = 0L;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (matches.test(stack)) count += stack.getCount();
        }
        return count;
    }

    public static Manifest manifest(ServerPlayer player, ComputerWorkspace computer) {
        if (player == null || computer == null || !computer.canUseFileSystem(player)) return Manifest.failed("unauthorized");
        return manifest(computer, resolveCrate(player, computer));
    }

    /** The manifest for an already-resolved crate, so callers that hold one do not search for it again. */
    public static Manifest manifest(ComputerWorkspace computer, CrateRef crate) {
        if (crate == null) return Manifest.failed(computer.isRemoteWorkspace() ? "crate_unavailable" : "crate_required");
        ChestBlockEntity chest = crate.chest();
        Map<ResourceLocation, ManifestEntry> entries = new LinkedHashMap<>();
        boolean hasItems = false;
        boolean unsupported = false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) continue;
            hasItems = true;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            AntazonSellData.Rule rule = AntazonSellData.rule(itemId);
            long value = rule == null ? 0L : Math.multiplyExact((long) stack.getCount(), rule.value());
            ManifestEntry previous = entries.get(itemId);
            entries.put(itemId, new ManifestEntry(itemId,
                    (previous == null ? 0 : previous.count()) + stack.getCount(),
                    Math.addExact(previous == null ? 0L : previous.value(), value), rule != null, rule == null || rule.greenTint(), rule == null || rule.renderMobFromSpawnEgg()));
            unsupported |= rule == null;
        }
        long total = entries.values().stream().mapToLong(ManifestEntry::value).sum();
        String status = !hasItems ? "crate_empty" : unsupported ? "unsupported_items" : total < 1L ? "crate_empty" : "";
        return new Manifest(List.copyOf(entries.values()), total, status);
    }

    public static List<AntazonSellData.Rule> priceList() {
        return AntazonSellData.rules();
    }

    public static PrepareResult prepareShipment(ServerPlayer player, ComputerWorkspace computer, ResourceLocation itemId, int amount) {
        if (player == null || computer == null || itemId == null || amount < 1 || amount > 64) return PrepareResult.failed("invalid_request");
        if (!computer.canUseFileSystem(player)) return PrepareResult.failed("unauthorized");
        AntazonSellData.Rule rule = AntazonSellData.rule(itemId);
        CrateRef crate = resolveCrate(player, computer);
        if (rule == null) return PrepareResult.failed("unsupported_item");
        if (crate == null) return PrepareResult.failed(computer.isRemoteWorkspace() ? "crate_unavailable" : "crate_required");
        ChestBlockEntity chest = crate.chest();
        Item item = BuiltInRegistries.ITEM.get(itemId);
        int available = player.getInventory().countItem(item);
        if (available < amount) return PrepareResult.failed("insufficient_inventory");
        int remaining = amount;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) remaining -= Math.min(remaining, item.getDefaultMaxStackSize());
            else if (ItemStack.isSameItemSameComponents(stack, new ItemStack(item))) remaining -= Math.min(remaining, stack.getMaxStackSize() - stack.getCount());
            if (remaining <= 0) break;
        }
        if (remaining > 0) return PrepareResult.failed("crate_full");
        remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        ItemStack toInsert = new ItemStack(item, amount);
        for (int slot = 0; slot < chest.getContainerSize() && !toInsert.isEmpty(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, toInsert)) {
                int moved = Math.min(toInsert.getCount(), stack.getMaxStackSize() - stack.getCount());
                stack.grow(moved);
                toInsert.shrink(moved);
            }
        }
        for (int slot = 0; slot < chest.getContainerSize() && !toInsert.isEmpty(); slot++) {
            if (chest.getItem(slot).isEmpty()) {
                chest.setItem(slot, toInsert.split(Math.min(toInsert.getCount(), toInsert.getMaxStackSize())));
            }
        }
        chest.setChanged();
        return PrepareResult.accepted(amount, (long) amount * rule.value());
    }

    public static SellResult sell(ServerPlayer player, ComputerWorkspace computer) {
        if (player == null || computer == null || !computer.canUseFileSystem(player)) return SellResult.failed("unauthorized");
        CrateRef crate = resolveCrate(player, computer);
        if (crate == null) return SellResult.failed(computer.isRemoteWorkspace() ? "crate_unavailable" : "crate_required");
        BlockPos chestPos = crate.position();
        ChestBlockEntity chest = crate.chest();
        synchronized (AntazonServerData.access(player.server)) {
            Manifest manifest = manifest(computer, crate);
            if (!manifest.ready()) return SellResult.failed(manifest.status());
            List<ItemStack> shipmentContents = new ArrayList<>();
            List<ItemStack> originalContents = new ArrayList<>();
            long currentTotal = 0L;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                originalContents.add(stack.copy());
                if (stack.isEmpty()) continue;
                AntazonSellData.Rule rule = AntazonSellData.rule(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                if (rule == null) return SellResult.failed("crate_changed");
                currentTotal = Math.addExact(currentTotal, Math.multiplyExact((long) stack.getCount(), rule.value()));
                shipmentContents.add(stack.copy());
            }
            if (currentTotal != manifest.total()) return SellResult.failed("crate_changed");
            UUID account = accountOwner(computer, player);
            var chestState = crate.level().getBlockState(chestPos);
            crate.level().removeBlock(chestPos, false);
            RewardDropEntity shipment = new RewardDropEntity(AntOSObjects.REWARD_DROP_ENTITY.get(), crate.level());
            shipment.setPos(chestPos.getX() + 0.5D, chestPos.getY() + 0.75D, chestPos.getZ() + 0.5D);
            shipment.setRewards(shipmentContents, player);
            shipment.setShipping(account, manifest.total(), player.getUUID());
            if (!crate.level().addFreshEntity(shipment)) {
                crate.level().setBlock(chestPos, chestState, 3);
                if (crate.level().getBlockEntity(chestPos) instanceof ChestBlockEntity restored) {
                    for (int slot = 0; slot < originalContents.size(); slot++) restored.setItem(slot, originalContents.get(slot));
                }
                return SellResult.failed("shipment_failed");
            }
            crate.level().playSound(null, chestPos, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 1.0F);
            RewardDropEntity.announceShippingLaunched(player);
            return SellResult.accepted(manifest.total());
        }
    }

    public static boolean toggleWishlist(ServerPlayer player, ResourceLocation productId) {
        if (player == null || productId == null || AntazonData.product(productId) == null) return false;
        return AntazonServerData.access(player.server).toggleWishlist(profileId(player), productId);
    }

    public static ReviewResult submitReview(ServerPlayer player, ResourceLocation productId, int rating, String title, String body) {
        if (player == null || productId == null || AntazonData.product(productId) == null) return ReviewResult.failed("product_unavailable");
        if (rating < 1 || rating > 5 || title == null || title.isBlank() || title.length() > 80 || body == null || body.isBlank() || body.length() > 512)
            return ReviewResult.failed("invalid_review");
        AntazonServerData data = AntazonServerData.access(player.server);
        UUID profile = profileId(player);
        if (data.hasReviewed(profile, productId)) return ReviewResult.failed("already_reviewed");
        if (!data.hasPurchased(profile, productId)) return ReviewResult.failed("purchase_required");
        data.addReview(new AntazonServerData.PlayerReview(profile, productId, rating, title.trim(), body.trim(), player.serverLevel().getGameTime()));
        return ReviewResult.accepted();
    }

    public static boolean unlocked(ServerPlayer player, ComputerWorkspace computer, AntazonData.Product product) {
        if (product.unlockTasks().isEmpty()) return true;
        boolean any = product.unlockMode().equals("any");
        for (ResourceLocation task : product.unlockTasks()) {
            boolean complete = ComputerTasks.isTaskComplete(player, computer, task);
            if (any && complete) return true;
            if (!any && !complete) return false;
        }
        return !any;
    }

    private static long resetMarker(String reset, long day) {
        return switch (reset) {
            case "minecraft_day" -> day;
            case "minecraft_week" -> day / 7L;
            default -> Long.MIN_VALUE;
        };
    }

    private static boolean pay(ServerPlayer player, AntazonServerData data, UUID account, AntazonData.Payment payment, int amount) {
        if (payment.type().equals("antcoins")) return data.debit(account, amount);
        if (!payment.type().equals("item")) return false;
        java.util.function.Predicate<ItemStack> matches = AntazonTags.matcher(payment.resource());
        if (matches == null || owned(player, data, account, payment) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!matches.test(stack)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        player.containerMenu.broadcastChanges();
        return remaining == 0;
    }

    private static List<ItemStack> rewards(List<AntazonData.Reward> definitions, int units) {
        List<ItemStack> stacks = new ArrayList<>();
        for (AntazonData.Reward reward : definitions) {
            Item item = BuiltInRegistries.ITEM.get(reward.item());
            int remaining = reward.count() * units;
            while (remaining > 0) {
                int count = Math.min(item.getDefaultMaxStackSize(), remaining);
                stacks.add(new ItemStack(item, count));
                remaining -= count;
            }
        }
        return stacks;
    }

    public static UUID accountOwner(ComputerWorkspace computer, ServerPlayer player) {
        return computer.workspaceOwner() == null ? player.getUUID() : computer.workspaceOwner();
    }

    private static UUID profileId(ServerPlayer player) {
        var account = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
        return account == null ? player.getUUID() : account.accountId();
    }

    public static CrateRef resolveCrate(ServerPlayer player, ComputerWorkspace computer) {
        UUID owner = accountOwner(computer, player);
        AntazonServerData data = AntazonServerData.access(player.server);
        if (computer.isRemoteWorkspace() || computer.workspaceOwner() != null) {
            AntazonServerData.CrateLocation location = data.shippingCrate(owner);
            if (location == null) return null;
            ResourceLocation dimension;
            try { dimension = ResourceLocation.parse(location.dimension()); }
            catch (RuntimeException exception) { return null; }
            ServerLevel level = player.server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dimension));
            if (level == null) return null;
            BlockPos position = BlockPos.of(location.position());
            if (!level.hasChunkAt(position)) return null;
            if (!isShippingChest(level, position)) {
                data.clearShippingCrate(owner);
                return null;
            }
            return new CrateRef(level, position, (ChestBlockEntity) level.getBlockEntity(position));
        }
        BlockPos best = findNearbyChestPosition(player);
        if (best == null) return null;
        data.setShippingCrate(owner, player.serverLevel().dimension().location(), best);
        return new CrateRef(player.serverLevel(), best, (ChestBlockEntity) player.serverLevel().getBlockEntity(best));
    }

    public static boolean linkNearbyCrate(ServerPlayer player, UUID accountId) {
        if (player == null || accountId == null) return false;
        BlockPos position = findNearbyChestPosition(player);
        if (position == null) return false;
        AntazonServerData.access(player.server).setShippingCrate(accountId, player.serverLevel().dimension().location(), position);
        return true;
    }

    private static BlockPos findNearbyChestPosition(ServerPlayer player) {
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double bestDistance = 36.0D;
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-3, -2, -3), origin.offset(3, 2, 3))) {
            if (!player.serverLevel().getBlockState(candidate).is(Blocks.CHEST)
                    || player.serverLevel().getBlockState(candidate).getValue(ChestBlock.TYPE) != ChestType.SINGLE
                    || !(player.serverLevel().getBlockEntity(candidate) instanceof ChestBlockEntity)) continue;
            double distance = candidate.distToCenterSqr(player.position());
            if (distance <= bestDistance) { best = candidate.immutable(); bestDistance = distance; }
        }
        return best;
    }

    private static boolean isShippingChest(ServerLevel level, BlockPos position) {
        return level.getBlockState(position).is(Blocks.CHEST)
                && level.getBlockState(position).getValue(ChestBlock.TYPE) == ChestType.SINGLE
                && level.getBlockEntity(position) instanceof ChestBlockEntity;
    }

    public record CrateRef(ServerLevel level, BlockPos position, ChestBlockEntity chest) { }

    private record LocationDelivery(BlockPos position, List<ItemStack> rewards) { }

    public record PurchaseResult(boolean success, String status, UUID orderId, int units, String receipt) {
        static PurchaseResult accepted(UUID orderId, int units, String receipt) { return new PurchaseResult(true, "accepted", orderId, units, receipt); }
        static PurchaseResult failed(String status) { return new PurchaseResult(false, status, null, 0, ""); }
    }

    public record CheckoutLine(AntazonServerData.CartLine line, String status, String receipt) { }

    public record CheckoutResult(List<CheckoutLine> lines, List<AntazonServerData.CartLine> remaining) { }

    public record SellResult(boolean success, String status, long amount) {
        static SellResult accepted(long amount) { return new SellResult(true, "shipment_launched", amount); }
        static SellResult failed(String status) { return new SellResult(false, status, 0L); }
    }

    public record PrepareResult(boolean success, String status, int amount, long value) {
        public static PrepareResult accepted(int amount, long value) { return new PrepareResult(true, "prepared", amount, value); }
        public static PrepareResult failed(String status) { return new PrepareResult(false, status, 0, 0L); }
    }

    public record Manifest(List<ManifestEntry> entries, long total, String status) {
        static Manifest failed(String status) { return new Manifest(List.of(), 0L, status); }
        boolean ready() { return status.isBlank() && total > 0L; }
    }

    public record ManifestEntry(ResourceLocation item, int count, long value, boolean sellable, boolean greenTint, boolean renderMobFromSpawnEgg) { }

    public record ReviewResult(boolean success, String status) {
        static ReviewResult accepted() { return new ReviewResult(true, "review_accepted"); }
        static ReviewResult failed(String status) { return new ReviewResult(false, status); }
    }
}
