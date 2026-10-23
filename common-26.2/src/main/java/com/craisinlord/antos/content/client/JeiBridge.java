package com.craisinlord.antos.content.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;

public final class JeiBridge {
    public interface Handler {
        Boolean usesFor(KeyEvent event);
        boolean has(ItemStack stack, boolean uses);
        boolean show(ItemStack stack, boolean uses);
        boolean recipesScreen(Screen screen);
        Screen parentOf(Screen screen);
    }

    private static volatile Handler handler;

    private JeiBridge() { }

    public static void set(Handler value) { handler = value; }

    public static boolean available() { return handler != null; }

    public static Boolean usesFor(KeyEvent event) {
        Handler current = handler;
        if (current == null) return null;
        try { return current.usesFor(event); }
        catch (RuntimeException ignored) { return null; }
    }

    public static boolean has(ItemStack stack, boolean uses) {
        Handler current = handler;
        if (current == null || stack == null || stack.isEmpty()) return false;
        try { return current.has(stack, uses); }
        catch (RuntimeException ignored) { return false; }
    }

    public static boolean show(ItemStack stack, boolean uses) {
        Handler current = handler;
        if (current == null || stack == null || stack.isEmpty()) return false;
        try { return current.show(stack, uses); }
        catch (RuntimeException ignored) { return false; }
    }

    public static boolean recipesScreen(Screen screen) {
        Handler current = handler;
        return current != null && screen != null && current.recipesScreen(screen);
    }

    public static Screen parentOf(Screen screen) {
        Handler current = handler;
        if (current == null || screen == null || !current.recipesScreen(screen)) return null;
        try { return current.parentOf(screen); }
        catch (RuntimeException ignored) { return null; }
    }
}
