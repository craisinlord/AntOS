package com.craisinlord.antos.content.entity;

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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RewardDropEntity extends Entity {
    private static final String REWARDS_TAG = "Rewards";
    private static final String RECIPIENT_TAG = "Recipient";
    private static final int MAX_REWARD_STACKS = 108;
    private final List<ItemStack> rewards = new ArrayList<>();
    private UUID recipient;
    private boolean impacted;

    public RewardDropEntity(EntityType<? extends RewardDropEntity> type, Level level) {
        super(type, level);
    }

    public void setRewards(List<ItemStack> stacks, ServerPlayer recipient) {
        rewards.clear();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && rewards.size() < MAX_REWARD_STACKS) rewards.add(stack.copy());
        }
        this.recipient = recipient.getUUID();
        setDeltaMovement(0.0D, -0.12D, 0.0D);
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
        Vec3 velocity = getDeltaMovement();
        setDeltaMovement(velocity.x * 0.98D, Math.max(-0.9D, velocity.y - 0.04D), velocity.z * 0.98D);
        move(MoverType.SELF, getDeltaMovement());
        if (onGround() || tickCount > 200) impactServer();
    }

    private void impactServer() {
        if (impacted || !(level() instanceof ServerLevel serverLevel)) return;
        impacted = true;
        serverLevel.broadcastEntityEvent(this, (byte) 60);
        serverLevel.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.WOOD_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 0.85F + random.nextFloat() * 0.25F);
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
        tag.putBoolean("Impacted", impacted);
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
        impacted = tag.getBoolean("Impacted");
    }
}
