package com.craisinlord.antos.compat.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class AntOSArchiveReward extends Reward {
    private ResourceLocation entryId = ResourceLocation.fromNamespaceAndPath("antos", "unset");

    public AntOSArchiveReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FTBQuestsCompat.ANTOS_ARCHIVE_REWARD_TYPE;
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("antos_entry", entryId.toString());
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        ResourceLocation parsed = ResourceLocation.tryParse(nbt.getString("antos_entry"));
        if (parsed != null) entryId = parsed;
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeResourceLocation(entryId);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        entryId = buffer.readResourceLocation();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("antos_entry", entryId.toString(), value -> {
            ResourceLocation parsed = ResourceLocation.tryParse(value);
            if (parsed != null) entryId = parsed;
        }, "antos:unset").setNameKey("ftbquests.reward.antos.unlock_archive");
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        AntOSFTBBridge.applyOrQueueGrant(player, "unlock_archive", entryId);
    }
}
