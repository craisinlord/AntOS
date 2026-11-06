package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.ArchiveLookup;
import com.craisinlord.antos.content.client.TaskTrackerHud;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** The pinned task tracker on the inventory screen, where a click opens that task in AntOS. */
public final class TrackedTasksButton extends AbstractButton {
    private static final int WIDEST_INVENTORY = 195;
    private String hoveredTask;

    public TrackedTasksButton(int x, int y) {
        super(x, y, 0, 0, Component.translatable("button.antos.tracked_tasks"));
    }

    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        String task = hoveredTask != null ? hoveredTask : TaskTrackerHud.firstPanelTask();
        if (task != null) ArchiveLookup.openTarget(new ArchiveLookup.Target("task", task));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Ui.refresh();
        int room = (Minecraft.getInstance().getWindow().getGuiScaledWidth() - WIDEST_INVENTORY) / 2 - getX() - 4;
        int[] size = TaskTrackerHud.panelSize(room);
        hoveredTask = null;
        width = size == null ? 0 : size[0];
        height = size == null ? 0 : size[1];
        active = size != null && AntOSSettings.appEnabled("TASKS");
        if (size != null) hoveredTask = TaskTrackerHud.renderPanel(graphics, getX(), getY(), mouseX, mouseY);
    }
}
