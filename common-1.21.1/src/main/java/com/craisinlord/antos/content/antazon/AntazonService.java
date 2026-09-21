package com.craisinlord.antos.content.antazon;

import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import com.craisinlord.antos.content.computer.ComputerTasks;
import com.craisinlord.antos.content.entity.RewardDropEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AntazonService {
    private static final long MINECRAFT_DAY = 24000L;

    private AntazonService() { }

    public static PurchaseResult purchase(ServerPlayer player, ComputerBlockEntity computer,
                                          ResourceLocation productId, String optionId, int units) {
        if (player == null || computer == null || productId == null || optionId == null || units < 1 || units > 64)
            return PurchaseResult.failed("invalid_request");
        AntazonData.Product product = AntazonData.product(productId);
        if (product == null || !product.enabled()) return PurchaseResult.failed("product_unavailable");
        if (!unlocked(player, computer, product)) return PurchaseResult.failed("task_locked");
        if (!optionId.equals("default")) return PurchaseResult.failed("invalid_request");
        long gameTime = player.serverLevel().getGameTime();
        long day = gameTime / MINECRAFT_DAY;
        AntazonServerData data = AntazonServerData.access(player.server);
        synchronized (data) {
            AntazonData.Availability availability = product.availability();
            AntazonServerData.Stock current = data.stock(productId);
            if (current == null) current = new AntazonServerData.Stock(availability.serverStock(), day + availability.restockMinecraftDays());
            if (availability.restockMinecraftDays() > 0 && day >= current.nextRestockDay())
                current = new AntazonServerData.Stock(availability.serverStock(), day + availability.restockMinecraftDays());
            int requiredUnits = Math.multiplyExact(product.quantity(), units);
            if (availability.serverStock() > 0 && current.remaining() < requiredUnits) return PurchaseResult.failed("out_of_stock");
            AntazonServerData.PlayerState playerState = data.playerState(player.getUUID(), productId);
            long reset = resetMarker(availability.playerLimitReset(), day);
            int playerQuantity = playerState.reset() == reset ? playerState.quantity() : 0;
            if (availability.playerLimit() > 0 && playerQuantity + requiredUnits > availability.playerLimit()) return PurchaseResult.failed("player_limit");
            if (availability.cooldownMinecraftDays() > 0 && playerState.lastPurchase() != Long.MIN_VALUE
                    && gameTime < playerState.lastPurchase() + availability.cooldownMinecraftDays() * MINECRAFT_DAY)
                return PurchaseResult.failed("cooldown");
            AntazonData.Payment payment = null;
            for (AntazonData.Payment candidate : product.payments()) {
                int unitPayment = product.deal().active(day)
                        ? Math.max(1, candidate.amount() * (100 - product.deal().discountPercent()) / 100)
                        : candidate.amount();
                int totalPayment = Math.multiplyExact(unitPayment, units);
                if (pay(player, candidate, totalPayment)) {
                    payment = candidate;
                    break;
                }
            }
            if (payment == null) return PurchaseResult.failed("payment_unavailable");
            List<ItemStack> rewards = rewards(product.rewards(), requiredUnits);
            if (rewards.isEmpty()) return PurchaseResult.failed("delivery_unavailable");
            if (availability.serverStock() > 0) current = new AntazonServerData.Stock(current.remaining() - requiredUnits, current.nextRestockDay());
            data.setStock(productId, current);
            data.setPlayerState(player.getUUID(), productId, new AntazonServerData.PlayerState(playerQuantity + requiredUnits, reset, gameTime));
            UUID orderId = UUID.randomUUID();
            data.addOrder(new AntazonServerData.Order(orderId, player.getUUID(), productId, "standard", requiredUnits, gameTime, "DELIVERED"));
            BlockPos landing = player.blockPosition().relative(player.getDirection(), 1);
            RewardDropEntity drop = new RewardDropEntity(AntOSObjects.REWARD_DROP_ENTITY.get(), player.serverLevel());
            drop.setPos(landing.getX() + 0.5D, RewardDropEntity.spawnHeight(player.serverLevel(), landing), landing.getZ() + 0.5D);
            drop.setRewards(rewards, player);
            RewardDropEntity.announceIncoming(player);
            player.serverLevel().addFreshEntity(drop);
            return PurchaseResult.accepted(orderId, requiredUnits);
        }
    }

    public static boolean toggleWishlist(ServerPlayer player, ResourceLocation productId) {
        if (player == null || productId == null || AntazonData.product(productId) == null) return false;
        return AntazonServerData.access(player.server).toggleWishlist(player.getUUID(), productId);
    }

    public static ReviewResult submitReview(ServerPlayer player, ResourceLocation productId, int rating, String title, String body) {
        if (player == null || productId == null || AntazonData.product(productId) == null) return ReviewResult.failed("product_unavailable");
        if (rating < 1 || rating > 5 || title == null || title.isBlank() || title.length() > 80 || body == null || body.isBlank() || body.length() > 512)
            return ReviewResult.failed("invalid_review");
        AntazonServerData data = AntazonServerData.access(player.server);
        if (data.hasReviewed(player.getUUID(), productId)) return ReviewResult.failed("already_reviewed");
        boolean purchased = data.orders(player.getUUID()).stream().anyMatch(order -> order.product().equals(productId) && order.status().equals("DELIVERED"));
        if (!purchased) return ReviewResult.failed("purchase_required");
        data.addReview(new AntazonServerData.PlayerReview(player.getUUID(), productId, rating, title.trim(), body.trim(), player.serverLevel().getGameTime()));
        return ReviewResult.accepted();
    }

    private static boolean unlocked(ServerPlayer player, ComputerBlockEntity computer, AntazonData.Product product) {
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

    private static boolean pay(ServerPlayer player, AntazonData.Payment payment, int amount) {
        if (!payment.type().equals("item")) return false;
        ResourceLocation id;
        try { id = ResourceLocation.parse(payment.resource()); }
        catch (RuntimeException exception) { return false; }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == net.minecraft.world.item.Items.AIR || player.getInventory().countItem(item) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
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

    public record PurchaseResult(boolean success, String status, UUID orderId, int units) {
        static PurchaseResult accepted(UUID orderId, int units) { return new PurchaseResult(true, "accepted", orderId, units); }
        static PurchaseResult failed(String status) { return new PurchaseResult(false, status, null, 0); }
    }

    public record ReviewResult(boolean success, String status) {
        static ReviewResult accepted() { return new ReviewResult(true, "review_accepted"); }
        static ReviewResult failed(String status) { return new ReviewResult(false, status); }
    }
}
