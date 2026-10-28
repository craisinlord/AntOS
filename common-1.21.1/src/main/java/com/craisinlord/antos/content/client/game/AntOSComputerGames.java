package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.api.client.game.ComputerGame;
import com.craisinlord.antos.api.client.game.ComputerGameRegistry;
import com.craisinlord.antos.api.client.game.GameIcon;
import net.minecraft.resources.ResourceLocation;

public final class AntOSComputerGames {
    private AntOSComputerGames() {}

    public static void register() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("antos", "blockle");
        if (ComputerGameRegistry.get(id) == null) ComputerGameRegistry.register(new ComputerGame() {
            @Override public ResourceLocation id() { return id; }
            @Override public ResourceLocation diskId() { return null; }
            @Override public boolean includedByDefault() { return true; }
            @Override public String title() { return "BLOCKLE"; }
            @Override public GameIcon icon() { return GameIcons.BLOCKLE; }
            @Override public Session create() {
                BlockleProgram program = new BlockleProgram();
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
        registerBuiltIn("chess", "CHESS", GameIcons.CHESS, ChessSession::new);
        registerBuiltIn("minesweeper", "MINESWEEPER", GameIcons.MINESWEEPER, MinesweeperSession::new);
        registerBuiltIn("cake_stack", "CAKE STACK", GameIcons.CAKE_STACK, CakeStackProgram::new);
        registerBuiltIn("nether_breakout", "NETHER BREAKOUT", GameIcons.NETHER_BREAKOUT, NetherBreakoutSession::new);
        registerBuiltIn("fliptris", "FLIPTRIS", GameIcons.FLIPTRIS, FliptrisProgram::new);
        registerBuiltIn("flappy_ghast", "FLAPPY GHAST", GameIcons.FLAPPY_GHAST, FlappyGhastProgram::new);
        registerBuiltIn("ore_2048", "ORE 2048", GameIcons.ORE_2048, Ore2048Session::new);
        registerBuiltIn("piston_push", "PISTON PUSH", GameIcons.PISTON_PUSH, PistonPushSession::new);
    }

    private static void registerBuiltIn(String name, String title, GameIcon icon, java.util.function.Supplier<ComputerGame.Session> factory) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("antos", name);
        if (ComputerGameRegistry.get(id) != null) return;
        ComputerGameRegistry.register(new ComputerGame() {
            @Override public ResourceLocation id() { return id; }
            @Override public ResourceLocation diskId() { return id; }
            @Override public boolean includedByDefault() { return com.craisinlord.antos.config.AntOSSettings.gameIncluded(id.toString(), true); }
            @Override public String title() { return title; }
            @Override public GameIcon icon() { return icon; }
            @Override public Session create() { return factory.get(); }
        });
    }
}
