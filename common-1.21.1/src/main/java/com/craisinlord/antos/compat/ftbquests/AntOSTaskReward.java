package com.craisinlord.antos.compat.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class AntOSTaskReward extends Reward {
    private ResourceLocation taskId = ResourceLocation.fromNamespaceAndPath("antos", "unset");

    public AntOSTaskReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FTBQuestsCompat.ANTOS_TASK_REWARD_TYPE;
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("antos_task", taskId.toString());
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        ResourceLocation parsed = ResourceLocation.tryParse(nbt.getString("antos_task"));
        if (parsed != null) taskId = parsed;
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeResourceLocation(taskId);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        taskId = buffer.readResourceLocation();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("antos_task", taskId.toString(), value -> {
            ResourceLocation parsed = ResourceLocation.tryParse(value);
            if (parsed != null) taskId = parsed;
        }, "antos:unset").setNameKey("ftbquests.reward.antos.grant_task");
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        AntOSFTBBridge.applyOrQueueGrant(player, "grant_task", taskId);
    }
}
