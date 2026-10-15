package com.craisinlord.antos.compat.ftbquests;

import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import de.marhali.json5.Json5Object;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class AntOSTaskReward extends Reward {
    private Identifier taskId = Identifier.fromNamespaceAndPath("antos", "unset");

    public AntOSTaskReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FTBQuestsCompat.ANTOS_TASK_REWARD_TYPE;
    }

    @Override
    public void writeData(Json5Object data, HolderLookup.Provider provider) {
        super.writeData(data, provider);
        data.addProperty("antos_task", taskId.toString());
    }

    @Override
    public void readData(Json5Object data, HolderLookup.Provider provider) {
        super.readData(data, provider);
        Identifier parsed = Identifier.tryParse(data.has("antos_task") ? data.getAsJson5Primitive("antos_task").getAsString() : "");
        if (parsed != null) taskId = parsed;
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeIdentifier(taskId);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        taskId = buffer.readIdentifier();
    }

    @Override
    public void fillConfigGroup(EditableConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("antos_task", taskId.toString(), value -> {
            Identifier parsed = Identifier.tryParse(value);
            if (parsed != null) taskId = parsed;
        }, "antos:unset").setNameKey("ftbquests.reward.antos.grant_task");
    }

    @Override
    public boolean claim(ServerPlayer player, boolean notify) {
        AntOSFTBBridge.applyOrQueueGrant(player, "grant_task", taskId);
        return true;
    }
}
