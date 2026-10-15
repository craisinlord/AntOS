package com.craisinlord.antos.compat.ftbquests;

import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import de.marhali.json5.Json5Object;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class AntOSTask extends AbstractBooleanTask {
    private Identifier antosTaskId = Identifier.fromNamespaceAndPath("antos", "unset");

    public AntOSTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FTBQuestsCompat.ANTOS_TASK_TYPE;
    }

    public Identifier antosTaskId() {
        return antosTaskId;
    }

    @Override
    public void writeData(Json5Object data, HolderLookup.Provider provider) {
        super.writeData(data, provider);
        data.addProperty("antos_task", antosTaskId.toString());
    }

    @Override
    public void readData(Json5Object data, HolderLookup.Provider provider) {
        super.readData(data, provider);
        Identifier parsed = Identifier.tryParse(data.has("antos_task") ? data.getAsJson5Primitive("antos_task").getAsString() : "");
        if (parsed != null) antosTaskId = parsed;
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeIdentifier(antosTaskId);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        antosTaskId = buffer.readIdentifier();
    }

    @Override
    public void fillConfigGroup(EditableConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("antos_task", antosTaskId.toString(), value -> {
            Identifier parsed = Identifier.tryParse(value);
            if (parsed != null) antosTaskId = parsed;
        }, "antos:unset").setNameKey("ftbquests.task.antos.task");
    }

    @Override
    public boolean canSubmit(TeamData teamData, ServerPlayer player) {
        return false;
    }
}
