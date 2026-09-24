package com.craisinlord.antos.content.entity;

import com.craisinlord.antos.content.antazon.AntazonServerData;
import com.craisinlord.antos.content.network.ComputerAccessHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RewardDropEntity extends Entity {
    private static final double SPAWN_HEIGHT = 64.0D;
    private static final String REWARDS_TAG = "Rewards";
    private static final String RECIPIENT_TAG = "Recipient";
    private static final int MAX_REWARD_STACKS = 108;
    private final List<ItemStack> rewards = new ArrayList<>();
    private UUID recipient;
    private UUID payoutAccount;
    private UUID payoutPlayer;
    private long payoutAmount;
    private double shippingTargetY;
    private boolean impacted;
    private boolean shipping;

    public RewardDropEntity(EntityType<? extends RewardDropEntity> type, Level level) {
        super(type, level);
    }

    public static double spawnHeight(Level level, net.minecraft.core.BlockPos landing) {
        return Math.min(landing.getY() + SPAWN_HEIGHT, level.getMaxBuildHeight() - 2.0D);
    }

    public static void announceIncoming(ServerPlayer recipient) {
        recipient.sendSystemMessage(Component.literal("Antazon delivery incoming!").withStyle(ChatFormatting.GOLD));
    }

    public void setRewards(List<ItemStack> stacks, ServerPlayer recipient) {
        rewards.clear();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && rewards.size() < MAX_REWARD_STACKS) rewards.add(stack.copy());
        }
        this.recipient = recipient.getUUID();
        setDeltaMovement(0.0D, -0.12D, 0.0D);
    }

    public void setShipping(UUID account, long amount, UUID player) {
        shipping = true;
        payoutAccount = account;
        payoutAmount = amount;
        payoutPlayer = player;
        shippingTargetY = Math.min(level().getMaxBuildHeight() - 8.0D, getY() + 64.0D);
        setDeltaMovement(0.0D, 0.22D, 0.0D);
    }

    public static void announceShippingLaunched(ServerPlayer sender) {
        sender.sendSystemMessage(Component.literal("Antazon freight crate launched! Payment arrives when it reaches the sky.").withStyle(ChatFormatting.GOLD));
    }

    private void completeShipping(ServerLevel serverLevel) {
        if (payoutAmount > 0L && payoutAccount != null) {
            AntazonServerData.access(serverLevel.getServer()).credit(payoutAccount, payoutAmount);
            if (payoutPlayer != null) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(payoutPlayer);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("Antazon shipment accepted: " + payoutAmount + " AntCoins credited.").withStyle(ChatFormatting.GOLD));
                    ComputerAccessHandler.sendAntazonShipmentStatus(player, payoutAccount, "shipment_accepted", payoutAmount);
                }
            }
        }
        serverLevel.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.NEUTRAL, 0.9F, 1.0F + random.nextFloat() * 0.15F);
        serverLevel.playSound(null, blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.NEUTRAL, 0.7F, 1.2F);
        serverLevel.sendParticles(ParticleTypes.FIREWORK, getX(), getY(), getZ(), 16, 0.35D, 0.35D, 0.35D, 0.08D);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY(), getZ(), 8, 0.25D, 0.25D, 0.25D, 0.04D);
        discard();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved() || impacted) return;
        if (level().isClientSide) {
            if (onGround()) impactParticles();
            return;
        }
        if (shipping) {
            setDeltaMovement(0.0D, Math.min(0.9D, getDeltaMovement().y + 0.025D), 0.0D);
            move(MoverType.SELF, getDeltaMovement());
            if (tickCount % 2 == 0) ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD,
                    getX(), getY() - 0.35D, getZ(), 2, 0.08D, 0.08D, 0.08D, 0.01D);
            if (tickCount % 8 == 0) ((ServerLevel) level()).playSound(null, blockPosition(), SoundEvents.ELYTRA_FLYING,
                    SoundSource.NEUTRAL, 0.55F, 1.15F + random.nextFloat() * 0.2F);
            if (getY() >= shippingTargetY || tickCount > 100) completeShipping((ServerLevel) level());
            return;
        }
        Vec3 velocity = getDeltaMovement();
        setDeltaMovement(velocity.x * 0.98D, Math.max(-0.9D, velocity.y - 0.04D), velocity.z * 0.98D);
        move(MoverType.SELF, getDeltaMovement());
        if (!onGround() && tickCount % 8 == 0 && getDeltaMovement().y < 0.0D) {
            ((ServerLevel) level()).playSound(null, blockPosition(), SoundEvents.ELYTRA_FLYING,
                    SoundSource.NEUTRAL, 0.75F, 0.9F + random.nextFloat() * 0.2F);
        }
        if (onGround() || tickCount > 200) impactServer();
    }

    private void impactServer() {
        if (impacted || !(level() instanceof ServerLevel serverLevel)) return;
        impacted = true;
        serverLevel.broadcastEntityEvent(this, (byte) 60);
        serverLevel.playSound(null, blockPosition(), SoundEvents.CHEST_CLOSE,
                SoundSource.BLOCKS, 1.0F, 0.7F + random.nextFloat() * 0.15F);
        serverLevel.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.WOOD_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 0.85F + random.nextFloat() * 0.25F);
        serverLevel.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.NEUTRAL, 1.0F, 0.95F + random.nextFloat() * 0.15F);
        for (ItemStack stack : rewards) {
            if (stack.isEmpty()) continue;
            ItemEntity item = new ItemEntity(serverLevel, getX(), getY() + 0.15D, getZ(), stack.copy());
            if (recipient != null) item.setTarget(recipient);
            item.setPickUpDelay(10);
            item.setDeltaMovement((random.nextDouble() - 0.5D) * 0.18D, 0.08D + random.nextDouble() * 0.1D,
                    (random.nextDouble() - 0.5D) * 0.18D);
            serverLevel.addFreshEntity(item);
        }
        rewards.clear();
        discard();
    }

    private void impactParticles() {
        if (impacted || !level().isClientSide) return;
        impacted = true;
        var state = net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState();
        for (int i = 0; i < 28; i++) {
            double vx = (random.nextDouble() - 0.5D) * 0.28D;
            double vy = random.nextDouble() * 0.22D;
            double vz = (random.nextDouble() - 0.5D) * 0.28D;
            level().addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                    getX() + (random.nextDouble() - 0.5D) * 0.7D, getY() + random.nextDouble() * 0.45D,
                    getZ() + (random.nextDouble() - 0.5D) * 0.7D, vx, vy, vz);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 60) impactParticles();
        else super.handleEntityEvent(id);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ListTag list = new ListTag();
        for (ItemStack stack : rewards) list.add(stack.save(registryAccess()));
        tag.put(REWARDS_TAG, list);
        if (recipient != null) tag.putUUID(RECIPIENT_TAG, recipient);
        if (payoutAccount != null) tag.putUUID("PayoutAccount", payoutAccount);
        if (payoutPlayer != null) tag.putUUID("PayoutPlayer", payoutPlayer);
        tag.putLong("PayoutAmount", payoutAmount);
        tag.putDouble("ShippingTargetY", shippingTargetY);
        tag.putBoolean("Impacted", impacted);
        tag.putBoolean("Shipping", shipping);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        rewards.clear();
        ListTag list = tag.getList(REWARDS_TAG, 10);
        for (int i = 0; i < list.size() && rewards.size() < MAX_REWARD_STACKS; i++) {
            Optional<ItemStack> stack = ItemStack.parse(registryAccess(), list.get(i));
            stack.filter(value -> !value.isEmpty()).ifPresent(rewards::add);
        }
        recipient = tag.hasUUID(RECIPIENT_TAG) ? tag.getUUID(RECIPIENT_TAG) : null;
        payoutAccount = tag.hasUUID("PayoutAccount") ? tag.getUUID("PayoutAccount") : null;
        payoutPlayer = tag.hasUUID("PayoutPlayer") ? tag.getUUID("PayoutPlayer") : null;
        payoutAmount = tag.getLong("PayoutAmount");
        shippingTargetY = tag.contains("ShippingTargetY") ? tag.getDouble("ShippingTargetY") : getY() + 64.0D;
        impacted = tag.getBoolean("Impacted");
        shipping = tag.getBoolean("Shipping");
    }
}
