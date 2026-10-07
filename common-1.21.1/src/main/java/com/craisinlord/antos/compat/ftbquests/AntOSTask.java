package com.craisinlord.antos.compat.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class AntOSTask extends AbstractBooleanTask {
    private ResourceLocation antosTaskId = ResourceLocation.fromNamespaceAndPath("antos", "unset");

    public AntOSTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FTBQuestsCompat.ANTOS_TASK_TYPE;
    }

    public ResourceLocation antosTaskId() {
        return antosTaskId;
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("antos_task", antosTaskId.toString());
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        ResourceLocation parsed = ResourceLocation.tryParse(nbt.getString("antos_task"));
        if (parsed != null) antosTaskId = parsed;
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeResourceLocation(antosTaskId);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        antosTaskId = buffer.readResourceLocation();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("antos_task", antosTaskId.toString(), value -> {
            ResourceLocation parsed = ResourceLocation.tryParse(value);
            if (parsed != null) antosTaskId = parsed;
        }, "antos:unset").setNameKey("ftbquests.task.antos.task");
    }

    @Override
    public boolean canSubmit(TeamData teamData, ServerPlayer player) {
        return false;
    }
}
