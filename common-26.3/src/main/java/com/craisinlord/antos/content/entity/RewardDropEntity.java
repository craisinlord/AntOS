package com.craisinlord.antos.content.entity;

import com.craisinlord.antos.content.antazon.AntazonServerData;
import com.craisinlord.antos.content.network.ComputerAccessHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RewardDropEntity extends Entity {
    private static final double SPAWN_HEIGHT = 64.0D;
    private static final EntityDataAccessor<Boolean> DELIVERY_HIDDEN = SynchedEntityData.defineId(RewardDropEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PORTAL_TICKS = SynchedEntityData.defineId(RewardDropEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> PORTAL_Y = SynchedEntityData.defineId(RewardDropEntity.class, EntityDataSerializers.FLOAT);
    private BlockPos deliveryTarget;
    private double deliveryFloorY;
    private int deliveryFlightTicks;
    private boolean deliveryWaitingNotified;
    private boolean followRecipient;
    private int deliveryWaitTicks;
    public static final int DELIVERY_FALLBACK_TICKS = 600;
    private static final String REWARDS_TAG = "Rewards";
    private static final String RECIPIENT_TAG = "Recipient";
    public static final int MAX_REWARD_STACKS = 108;
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

    public static double spawnHeight(Level level, BlockPos landing) {
        return Math.min(landing.getY() + SPAWN_HEIGHT, level.getMaxY() - 2.0D);
    }

    public boolean deliveryHidden() {
        return entityData.get(DELIVERY_HIDDEN);
    }

    public float deliveryScale(float partialTick) {
        int ticks = entityData.get(PORTAL_TICKS);
        return DeliveryArrival.packageScale(ticks == 0 ? 0.0F : ticks + partialTick);
    }

    public float portalRadius(float partialTick) {
        int ticks = entityData.get(PORTAL_TICKS);
        return (float) DeliveryArrival.PORTAL_RADIUS * DeliveryArrival.portalSize(ticks == 0 ? 0.0F : ticks + partialTick);
    }

    public float portalOffset(float partialTick) {
        return (float) (entityData.get(PORTAL_Y) - Mth.lerp(partialTick, yOld, getY()));
    }

    public AABB portalBounds() {
        double y = entityData.get(PORTAL_Y);
        double radius = DeliveryArrival.PORTAL_RADIUS;
        return getBoundingBox().minmax(new AABB(getX() - radius, y - 0.1D, getZ() - radius,
                getX() + radius, y + 0.1D, getZ() + radius));
    }

    public void prepareDelivery(BlockPos destination) {
        deliveryTarget = destination.immutable();
        entityData.set(DELIVERY_HIDDEN, true);
        setInvisible(true);
        setPos(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
        tryDelivery();
    }

    public void followRecipient() {
        followRecipient = true;
    }

    private ServerPlayer recipientPlayer() {
        if (recipient == null || !(level() instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getServer().getPlayerList().getPlayer(recipient);
    }

    private void retargetDelivery() {
        if (!followRecipient) return;
        ServerPlayer player = recipientPlayer();
        if (player == null || player.level() != level()) return;
        deliveryTarget = player.blockPosition();
        setPos(deliveryTarget.getX() + 0.5D, deliveryTarget.getY(), deliveryTarget.getZ() + 0.5D);
    }

    private boolean handOverDelivery() {
        ServerPlayer player = recipientPlayer();
        if (player == null) return false;
        for (ItemStack stack : rewards) {
            if (stack.isEmpty()) continue;
            ItemStack remaining = stack.copy();
            player.getInventory().add(remaining);
            if (!remaining.isEmpty()) player.drop(remaining, false);
        }
        rewards.clear();
        player.sendSystemMessage(Component.literal("No landing space turned up, so Antazon handed your delivery to you directly.").withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.6F, 1.0F);
        discard();
        return true;
    }

    private DeliveryArrival.Space deliverySpace() {
        return new DeliveryArrival.Space() {
            @Override
            public double floor(int x, int y, int z) {
                var search = new AABB(x + 0.2D, y - 1.25D, z + 0.2D,
                        x + 0.8D, y + 0.75D, z + 0.8D);
                if (!loaded(search)) return Double.NaN;
                double floor = Double.NaN;
                for (var shape : level().getBlockCollisions(RewardDropEntity.this, search)) {
                    double top = shape.max(Direction.Axis.Y);
                    if (top <= y + 0.75D && (!Double.isFinite(floor) || top > floor)) floor = top;
                }
                if (!Double.isFinite(floor)) return Double.NaN;
                var support = level().getBlockState(BlockPos.containing(x + 0.5D, floor - 0.01D, z + 0.5D));
                var landing = level().getBlockState(BlockPos.containing(x + 0.5D, floor + 0.01D, z + 0.5D));
                if (landing.is(BlockTags.FIRE) || support.is(BlockTags.FIRE) || support.is(BlockTags.CAMPFIRES)
                        || support.is(Blocks.MAGMA_BLOCK)
                        || support.is(Blocks.CACTUS)) return Double.NaN;
                return floor;
            }

            @Override
            public double ceiling(double x, double y, double z, double top) {
                var search = new AABB(x - 0.45D, y, z - 0.45D, x + 0.45D, top, z + 0.45D);
                double ceiling = Double.NaN;
                for (var shape : level().getBlockCollisions(RewardDropEntity.this, search)) {
                    double bottom = shape.min(Direction.Axis.Y);
                    if (!Double.isFinite(ceiling) || bottom < ceiling) ceiling = bottom;
                }
                return ceiling;
            }

            @Override
            public boolean clear(double x, double y, double z, double radius, double height) {
                var bounds = new AABB(x - radius, y + 0.001D, z - radius,
                        x + radius, y + height, z + radius);
                return loaded(bounds) && level().getWorldBorder().isWithinBounds(bounds)
                        && !level().getBlockCollisions(RewardDropEntity.this, bounds).iterator().hasNext()
                        && !level().containsAnyLiquid(bounds);
            }
        };
    }

    private boolean loaded(AABB bounds) {
        return bounds.minY >= level().getMinY() && bounds.maxY < level().getMaxY()
                && level().hasChunksAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ));
    }

    private void tryDelivery() {
        var plan = DeliveryArrival.find(deliveryTarget.getX(), deliveryTarget.getY(), deliveryTarget.getZ(),
                level().getMaxY() - 1.0D, deliverySpace());
        if (plan == null) {
            if (!deliveryWaitingNotified && level() instanceof ServerLevel serverLevel && recipient != null) {
                var player = serverLevel.getServer().getPlayerList().getPlayer(recipient);
                if (player != null) player.sendSystemMessage(Component.literal("Antazon delivery waiting for a clear landing space nearby.").withStyle(ChatFormatting.GOLD));
                deliveryWaitingNotified = true;
            }
            return;
        }
        deliveryFloorY = plan.floorY();
        deliveryFlightTicks = 0;
        setPos(plan.x(), plan.spawnY(), plan.z());
        entityData.set(PORTAL_Y, (float) (plan.spawnY() + 0.85D));
        entityData.set(PORTAL_TICKS, plan.portal() ? 1 : 0);
        entityData.set(DELIVERY_HIDDEN, plan.portal());
        setInvisible(plan.portal());
        setDeltaMovement(0.0D, plan.portal() ? 0.0D : -0.12D, 0.0D);
        if (plan.portal()) ((ServerLevel) level()).playSound(null, blockPosition(), SoundEvents.PORTAL_TRIGGER,
                SoundSource.NEUTRAL, 0.35F, 1.6F);
    }

    private void waitForDelivery() {
        entityData.set(PORTAL_TICKS, 0);
        entityData.set(DELIVERY_HIDDEN, true);
        setInvisible(true);
        setDeltaMovement(Vec3.ZERO);
        setPos(deliveryTarget.getX() + 0.5D, deliveryTarget.getY(), deliveryTarget.getZ() + 0.5D);
    }

    private boolean tickDelivery() {
        int ticks = entityData.get(PORTAL_TICKS);
        if (ticks > 0) {
            if (ticks == DeliveryArrival.OPEN_TICKS) {
                var space = deliverySpace();
                double floor = space.floor(Mth.floor(getX()), deliveryTarget.getY(), Mth.floor(getZ()));
                if (!Double.isFinite(floor) || Math.abs(floor - deliveryFloorY) > 0.01D
                        || !space.clear(getX(), deliveryFloorY, getZ(), 0.45D, getY() - deliveryFloorY + 0.85D)
                        || !space.clear(getX(), entityData.get(PORTAL_Y) - 0.08D, getZ(), DeliveryArrival.PORTAL_RADIUS, 0.1D)) {
                    waitForDelivery();
                    return true;
                }
                entityData.set(DELIVERY_HIDDEN, false);
                setInvisible(false);
                setDeltaMovement(0.0D, -0.12D, 0.0D);
                ((ServerLevel) level()).playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.NEUTRAL, 0.55F, 1.25F);
            }
            entityData.set(PORTAL_TICKS, ticks >= DeliveryArrival.OPEN_TICKS + DeliveryArrival.CLOSE_TICKS ? 0 : ticks + 1);
            if (ticks < DeliveryArrival.OPEN_TICKS) return true;
        }
        if (deliveryHidden()) {
            if (++deliveryWaitTicks >= DELIVERY_FALLBACK_TICKS && handOverDelivery()) return true;
            if (tickCount % 40 == 0) {
                retargetDelivery();
                tryDelivery();
            }
            return true;
        }
        deliveryFlightTicks++;
        if (deliveryFlightTicks > 200 || getY() < deliveryFloorY - 0.75D) {
            waitForDelivery();
            return true;
        }
        return false;
    }

    private void portalParticles() {
        int ticks = entityData.get(PORTAL_TICKS);
        if (ticks == 0) return;
        if (tickCount % 2 != 0) return;
        double size = DeliveryArrival.portalSize(ticks);
        double y = entityData.get(PORTAL_Y);
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8.0D + ticks * 0.22D;
            double radius = DeliveryArrival.PORTAL_RADIUS * size;
            level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(angle) * radius, y,
                    getZ() + Math.sin(angle) * radius, 0.0D, 0.0D, 0.0D);
        }
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
        shippingTargetY = Math.min(level().getMaxY() - 8.0D, getY() + 64.0D);
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
                    player.sendSystemMessage(Component.translatable("computer.antos.currency.shipment_accepted", payoutAmount, Component.translatable("computer.antos.currency.name")).withStyle(ChatFormatting.GOLD));
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DELIVERY_HIDDEN, false);
        builder.define(PORTAL_TICKS, 0);
        builder.define(PORTAL_Y, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) return;
        if (level().isClientSide()) {
            portalParticles();
            return;
        }
        if (impacted) {
            int portalTicks = entityData.get(PORTAL_TICKS);
            if (portalTicks > 0 && portalTicks < DeliveryArrival.OPEN_TICKS + DeliveryArrival.CLOSE_TICKS) {
                entityData.set(PORTAL_TICKS, portalTicks + 1);
            } else discard();
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
        if (deliveryTarget != null && tickDelivery()) return;
        Vec3 velocity = getDeltaMovement();
        setDeltaMovement(velocity.x * 0.98D, Math.max(-0.9D, velocity.y - 0.04D), velocity.z * 0.98D);
        move(MoverType.SELF, getDeltaMovement());
        if (!onGround() && tickCount % 8 == 0 && getDeltaMovement().y < 0.0D) {
            ((ServerLevel) level()).playSound(null, blockPosition(), SoundEvents.ELYTRA_FLYING,
                    SoundSource.NEUTRAL, 0.75F, 0.9F + random.nextFloat() * 0.2F);
        }
        if (deliveryTarget != null) {
            if (onGround()) {
                double floor = deliverySpace().floor(Mth.floor(getX()), deliveryTarget.getY(), Mth.floor(getZ()));
                if (!Double.isFinite(floor) || Math.abs(getY() - deliveryFloorY) > 0.75D
                        || !deliverySpace().clear(getX(), getY(), getZ(), 0.45D, 0.85D)) waitForDelivery();
                else impactServer();
            }
        } else if (onGround() || tickCount > 200) impactServer();
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
        if (entityData.get(PORTAL_TICKS) > 0) {
            entityData.set(DELIVERY_HIDDEN, true);
            setInvisible(true);
        } else discard();
    }

    private void impactParticles() {
        if (impacted || !level().isClientSide()) return;
        impacted = true;
        var state = Blocks.CHEST.defaultBlockState();
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
    protected void addAdditionalSaveData(ValueOutput output) {
        var list = output.list(REWARDS_TAG, ItemStack.CODEC);
        for (ItemStack stack : rewards) list.add(stack);
        output.storeNullable(RECIPIENT_TAG, UUIDUtil.CODEC, recipient);
        output.storeNullable("PayoutAccount", UUIDUtil.CODEC, payoutAccount);
        output.storeNullable("PayoutPlayer", UUIDUtil.CODEC, payoutPlayer);
        output.putLong("PayoutAmount", payoutAmount);
        output.putDouble("ShippingTargetY", shippingTargetY);
        output.putBoolean("Impacted", impacted);
        output.putBoolean("Shipping", shipping);
        if (deliveryTarget != null) output.putLong("DeliveryTarget", deliveryTarget.asLong());
        output.putDouble("DeliveryFloorY", deliveryFloorY);
        output.putInt("DeliveryFlightTicks", deliveryFlightTicks);
        output.putBoolean("DeliveryWaitingNotified", deliveryWaitingNotified);
        output.putBoolean("FollowRecipient", followRecipient);
        output.putInt("DeliveryWaitTicks", deliveryWaitTicks);
        output.putBoolean("DeliveryHidden", deliveryHidden());
        output.putInt("PortalTicks", entityData.get(PORTAL_TICKS));
        output.putFloat("PortalY", entityData.get(PORTAL_Y));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        rewards.clear();
        for (ItemStack stack : input.listOrEmpty(REWARDS_TAG, ItemStack.CODEC)) {
            if (rewards.size() < MAX_REWARD_STACKS && !stack.isEmpty()) rewards.add(stack);
        }
        recipient = input.read(RECIPIENT_TAG, UUIDUtil.CODEC).orElse(null);
        payoutAccount = input.read("PayoutAccount", UUIDUtil.CODEC).orElse(null);
        payoutPlayer = input.read("PayoutPlayer", UUIDUtil.CODEC).orElse(null);
        payoutAmount = input.getLongOr("PayoutAmount", 0L);
        shippingTargetY = input.getDoubleOr("ShippingTargetY", getY() + 64.0D);
        impacted = input.getBooleanOr("Impacted", false);
        shipping = input.getBooleanOr("Shipping", false);
        deliveryTarget = input.getLong("DeliveryTarget").isPresent() ? BlockPos.of(input.getLongOr("DeliveryTarget", 0L)) : null;
        deliveryFloorY = input.getDoubleOr("DeliveryFloorY", getY());
        deliveryFlightTicks = input.getIntOr("DeliveryFlightTicks", 0);
        deliveryWaitingNotified = input.getBooleanOr("DeliveryWaitingNotified", false);
        followRecipient = input.getBooleanOr("FollowRecipient", false);
        deliveryWaitTicks = input.getIntOr("DeliveryWaitTicks", 0);
        entityData.set(DELIVERY_HIDDEN, input.getBooleanOr("DeliveryHidden", false));
        entityData.set(PORTAL_TICKS, Math.max(0, Math.min(DeliveryArrival.OPEN_TICKS + DeliveryArrival.CLOSE_TICKS, input.getIntOr("PortalTicks", 0))));
        entityData.set(PORTAL_Y, input.getFloatOr("PortalY", 0.0F));
        setInvisible(deliveryHidden());
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }
}
