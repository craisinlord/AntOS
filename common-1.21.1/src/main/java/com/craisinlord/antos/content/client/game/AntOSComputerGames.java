package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.api.client.game.ComputerGame;
import com.craisinlord.antos.api.client.game.ComputerGameRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class AntOSComputerGames {
    private AntOSComputerGames() {}

    public static void register() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("antos", "blockle");
        if (ComputerGameRegistry.get(id) != null) return;
        ComputerGameRegistry.register(new ComputerGame() {
            @Override public ResourceLocation id() { return id; }
            @Override public ResourceLocation diskId() { return null; }
            @Override public boolean includedByDefault() { return true; }
            @Override public String title() { return "BLOCKLE"; }
            @Override public Session create(BlockPos position) {
                BlockleProgram program = new BlockleProgram(position);
                program.setInstalled(true);
                program.requestState();
                return new Session() {
                    @Override public void tick() { program.tick(); }
                    @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, int x, int y, int width, int height) { program.render(graphics, font, x, y, width, height); }
                    @Override public boolean keyPressed(int keyCode) { return program.keyPressed(keyCode); }
                    @Override public boolean charTyped(char codePoint) { return program.charTyped(codePoint); }
                };
            }
        });
    }
}
