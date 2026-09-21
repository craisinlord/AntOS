package com.craisinlord.antos.content.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public final class ComputerNetworking {
    private static Consumer<ComputerAccessPayload> sender = payload -> {
    };

    private ComputerNetworking() {
    }

    public static void setSender(Consumer<ComputerAccessPayload> sender) {
        ComputerNetworking.sender = sender;
    }

    public static void open(BlockPos pos) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.OPEN));
    }

    public static void setup(BlockPos pos, String password, boolean restoreWorkspace) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.SETUP,
                (restoreWorkspace ? "restore" : "fresh") + "\0" + password));
    }

    public static void login(BlockPos pos, String password) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.LOGIN, password));
    }

    public static void logout(BlockPos pos) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.LOGOUT));
    }

    public static void close(BlockPos pos) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.CLOSE));
    }

    public static void changePassword(BlockPos pos, String password) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.CHANGE_PASSWORD, password));
    }

    public static void eject(BlockPos pos, ResourceLocation diskId) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.EJECT, diskId.toString()));
    }

    public static void listFiles(BlockPos pos) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.FILE_LIST));
    }

    public static void openFile(BlockPos pos, String path) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.FILE_OPEN, path));
    }

    public static void createFile(BlockPos pos, String path, String contents) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.FILE_CREATE, path + "\0" + contents));
    }

    public static void saveFile(BlockPos pos, String path, String contents) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.FILE_SAVE, path + "\0" + contents));
    }

    public static void deleteFile(BlockPos pos, String path) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.FILE_DELETE, path));
    }

    public static void moveFile(BlockPos pos, String source, String destination) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.FILE_MOVE, source + "\0" + destination));
    }

    public static void terminalCommand(BlockPos pos, String directory, String command) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.TERMINAL_COMMAND, directory + "\0" + command));
    }

    public static void requestDesktopState(BlockPos pos) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.DESKTOP_STATE));
    }

    public static void selectWallpaper(BlockPos pos, ResourceLocation id) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.DESKTOP_WALLPAPER, id.toString()));
    }

    public static void locateStructure(BlockPos pos, ResourceLocation entryId) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.LOCATE_STRUCTURE, entryId.toString()));
    }

    public static void requestTasks(BlockPos pos) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.TASK_STATE));
    }

    public static void recordArchiveViewed(BlockPos pos, ResourceLocation entryId) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.ARCHIVE_VIEWED, entryId.toString()));
    }

    public static void requestBlockleState(BlockPos pos) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.BLOCKLE_STATE));
    }

    public static void submitBlockleGuess(BlockPos pos, String guess) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.BLOCKLE_GUESS, guess));
    }

    public static void requestAntazon(BlockPos pos) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.ANTAZON_STATE));
    }

    public static void purchaseAntazon(BlockPos pos, ResourceLocation productId, String optionId, int units) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.ANTAZON_PURCHASE,
                productId + "\0" + optionId + "\0" + units));
    }

    public static void toggleAntazonWishlist(BlockPos pos, ResourceLocation productId) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.ANTAZON_WISHLIST, productId.toString()));
    }

    public static void requestAntazonWishlist(BlockPos pos) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.ANTAZON_WISHLIST, ""));
    }

    public static void requestAntazonOrders(BlockPos pos) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.ANTAZON_ORDERS));
    }

    public static void reviewAntazonProduct(BlockPos pos, ResourceLocation productId, int rating, String title, String body) {
        sender.accept(new ComputerAccessPayload(pos, ComputerAccessPayload.ANTAZON_REVIEW,
                productId + "\0" + rating + "\0" + title + "\0" + body));
    }
}


