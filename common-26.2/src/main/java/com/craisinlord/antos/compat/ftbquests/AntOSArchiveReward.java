package com.craisinlord.antos.compat.ftbquests;

import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import de.marhali.json5.Json5Object;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class AntOSArchiveReward extends Reward {
    private Identifier entryId = Identifier.fromNamespaceAndPath("antos", "unset");

    public AntOSArchiveReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FTBQuestsCompat.ANTOS_ARCHIVE_REWARD_TYPE;
    }

    @Override
    public void writeData(Json5Object data, HolderLookup.Provider provider) {
        super.writeData(data, provider);
        data.addProperty("antos_entry", entryId.toString());
    }

    @Override
    public void readData(Json5Object data, HolderLookup.Provider provider) {
        super.readData(data, provider);
        Identifier parsed = Identifier.tryParse(data.has("antos_entry") ? data.getAsJson5Primitive("antos_entry").getAsString() : "");
        if (parsed != null) entryId = parsed;
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeIdentifier(entryId);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        entryId = buffer.readIdentifier();
    }

    @Override
    public void fillConfigGroup(EditableConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("antos_entry", entryId.toString(), value -> {
            Identifier parsed = Identifier.tryParse(value);
            if (parsed != null) entryId = parsed;
        }, "antos:unset").setNameKey("ftbquests.reward.antos.unlock_archive");
    }

    @Override
    public boolean claim(ServerPlayer player, boolean notify) {
        AntOSFTBBridge.applyOrQueueGrant(player, "unlock_archive", entryId);
        return true;
    }
}
