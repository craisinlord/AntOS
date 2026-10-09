package com.craisinlord.antos.content.network;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ComputerNetworking {
    private static Consumer<CustomPacketPayload> sender = payload -> {
    };

    private ComputerNetworking() {
    }

    public static void setSender(Consumer<CustomPacketPayload> sender) {
        ComputerNetworking.sender = sender;
    }

    private static void send(int action, String value) {
        if (profileActionAllowed(action)) sender.accept(new AnternetComputerPayload(action, value));
    }

    private static boolean profileActionAllowed(int action) {
        return switch (action) {
            case ComputerAccessPayload.OPEN, ComputerAccessPayload.CLOSE, ComputerAccessPayload.LOGOUT, ComputerAccessPayload.EJECT,
                    ComputerAccessPayload.FILE_LIST, ComputerAccessPayload.FILE_OPEN, ComputerAccessPayload.FILE_CREATE,
                    ComputerAccessPayload.FILE_SAVE, ComputerAccessPayload.FILE_DELETE, ComputerAccessPayload.FILE_MOVE,
                    ComputerAccessPayload.TERMINAL_COMMAND, ComputerAccessPayload.DESKTOP_STATE,
                    ComputerAccessPayload.DESKTOP_WALLPAPER, ComputerAccessPayload.TASK_STATE,
                    ComputerAccessPayload.ARCHIVE_VIEWED, ComputerAccessPayload.LOCATE_STRUCTURE,
                    ComputerAccessPayload.BLOCKLE_STATE,
                    ComputerAccessPayload.BLOCKLE_GUESS, ComputerAccessPayload.ANTAZON_STATE,
                    ComputerAccessPayload.ANTAZON_PURCHASE, ComputerAccessPayload.ANTAZON_WISHLIST,
                    ComputerAccessPayload.ANTAZON_ORDERS, ComputerAccessPayload.ANTAZON_REVIEW,
                    ComputerAccessPayload.ANTAZON_WALLET, ComputerAccessPayload.ANTAZON_SELL,
                    ComputerAccessPayload.ANTAZON_SELL_STATE, ComputerAccessPayload.ANTAZON_PREPARE,
                    ComputerAccessPayload.ANTAZON_PRICES, ComputerAccessPayload.ANTAZON_CRATE_LINK,
                    ComputerAccessPayload.ANTAZON_ONBOARDING, ComputerAccessPayload.ANTAZON_ONBOARDING_COMPLETE,
                    ComputerAccessPayload.ANTAZON_ONBOARDING_RESET,
                    ComputerAccessPayload.ANTAZON_CART, ComputerAccessPayload.ANTAZON_CHECKOUT,
                    ComputerAccessPayload.TASK_CLAIM_REWARD, ComputerAccessPayload.TEAM_CREATE,
                    ComputerAccessPayload.TEAM_INVITE, ComputerAccessPayload.TEAM_ACCEPT_INVITE,
                    ComputerAccessPayload.TEAM_DECLINE_INVITE, ComputerAccessPayload.TEAM_LEAVE,
                    ComputerAccessPayload.TEAM_DISBAND -> true;
            default -> false;
        };
    }

    public static void open() {
        send(ComputerAccessPayload.OPEN, "");
    }

    public static void logout() {
        send(ComputerAccessPayload.LOGOUT, "");
    }

    public static void close() {
        send(ComputerAccessPayload.CLOSE, "");
    }

    public static void eject(ResourceLocation diskId) {
        send(ComputerAccessPayload.EJECT, diskId.toString());
    }

    public static void listFiles() {
        send(ComputerAccessPayload.FILE_LIST, "");
    }

    public static void openFile(String path) {
        send(ComputerAccessPayload.FILE_OPEN, path);
    }

    public static void createFile(String path, String contents) {
        send(ComputerAccessPayload.FILE_CREATE, path + "\0" + contents);
    }

    public static void saveFile(String path, String contents) {
        send(ComputerAccessPayload.FILE_SAVE, path + "\0" + contents);
    }

    public static void deleteFile(String path) {
        send(ComputerAccessPayload.FILE_DELETE, path);
    }

    public static void moveFile(String source, String destination) {
        send(ComputerAccessPayload.FILE_MOVE, source + "\0" + destination);
    }

    public static void terminalCommand(String directory, String command) {
        send(ComputerAccessPayload.TERMINAL_COMMAND, directory + "\0" + command);
    }

    public static void requestDesktopState() {
        send(ComputerAccessPayload.DESKTOP_STATE, "");
    }

    public static void selectWallpaper(ResourceLocation id) {
        send(ComputerAccessPayload.DESKTOP_WALLPAPER, id.toString());
    }

    public static void locateStructure(ResourceLocation entryId) {
        send(ComputerAccessPayload.LOCATE_STRUCTURE, entryId.toString());
    }

    public static void requestTasks() {
        requestTasks("");
    }

    public static void requestTasks(String known) {
        send(ComputerAccessPayload.TASK_STATE, known == null ? "" : known);
    }

    public static void claimTaskReward(ResourceLocation taskId, java.util.Map<Integer, String> choices) {
        com.google.gson.JsonObject request = new com.google.gson.JsonObject();
        request.addProperty("task", taskId.toString());
        com.google.gson.JsonObject selected = new com.google.gson.JsonObject();
        choices.forEach((index, path) -> selected.addProperty(Integer.toString(index), path));
        request.add("choices", selected);
        send(ComputerAccessPayload.TASK_CLAIM_REWARD, request.toString());
    }

    public static void createTeam() { send(ComputerAccessPayload.TEAM_CREATE, ""); }
    public static void inviteToTeam(String username) { send(ComputerAccessPayload.TEAM_INVITE, username); }
    public static void acceptTeamInvite(String inviteId) { send(ComputerAccessPayload.TEAM_ACCEPT_INVITE, inviteId); }
    public static void declineTeamInvite(String inviteId) { send(ComputerAccessPayload.TEAM_DECLINE_INVITE, inviteId); }
    public static void leaveTeam() { send(ComputerAccessPayload.TEAM_LEAVE, ""); }
    public static void disbandTeam() { send(ComputerAccessPayload.TEAM_DISBAND, ""); }

    public static void recordArchiveViewed(ResourceLocation entryId) {
        send(ComputerAccessPayload.ARCHIVE_VIEWED, entryId.toString());
    }

    public static void requestBlockleState() {
        send(ComputerAccessPayload.BLOCKLE_STATE, "");
    }

    public static void submitBlockleGuess(String guess) {
        send(ComputerAccessPayload.BLOCKLE_GUESS, guess);
    }

    public static void requestAntazon() {
        send(ComputerAccessPayload.ANTAZON_STATE, com.craisinlord.antos.content.client.AntazonClientState.catalogVersion());
    }

    public static void requestAntazonWallet() {
        send(ComputerAccessPayload.ANTAZON_WALLET, "");
    }

    public static void sellAntazon() {
        send(ComputerAccessPayload.ANTAZON_SELL, "");
    }

    public static void requestAntazonSellState() {
        send(ComputerAccessPayload.ANTAZON_SELL_STATE, "");
    }

    public static void linkAntazonCrate() {
        send(ComputerAccessPayload.ANTAZON_CRATE_LINK, "");
    }

    public static void requestAntazonPrices() {
        send(ComputerAccessPayload.ANTAZON_PRICES, "");
    }

    public static void prepareAntazonShipment(ResourceLocation itemId, int amount) {
        send(ComputerAccessPayload.ANTAZON_PREPARE, itemId + "\0" + amount);
    }

    public static void requestAntazonOnboarding() {
        send(ComputerAccessPayload.ANTAZON_ONBOARDING, "");
    }

    public static void completeAntazonOnboarding() {
        send(ComputerAccessPayload.ANTAZON_ONBOARDING_COMPLETE, "");
    }

    public static void resetAntazonOnboarding() {
        send(ComputerAccessPayload.ANTAZON_ONBOARDING_RESET, "");
    }

    public static void purchaseAntazon(ResourceLocation productId, String optionId, int units) {
        purchaseAntazon(productId, optionId, "", units);
    }

    public static void purchaseAntazon(ResourceLocation productId, String optionId, String variantId, int units) {
        send(ComputerAccessPayload.ANTAZON_PURCHASE, productId + "\0" + optionId + "\0" + variantId + "\0" + units);
    }

    public static void toggleAntazonWishlist(ResourceLocation productId) {
        send(ComputerAccessPayload.ANTAZON_WISHLIST, productId.toString());
    }

    public static void requestAntazonWishlist() {
        send(ComputerAccessPayload.ANTAZON_WISHLIST, "");
    }

    public static void requestAntazonOrders() {
        send(ComputerAccessPayload.ANTAZON_ORDERS, "");
    }

    public static void requestAntazonCart() {
        send(ComputerAccessPayload.ANTAZON_CART, "");
    }

    public static void saveAntazonCart(String encodedCart) {
        send(ComputerAccessPayload.ANTAZON_CART, encodedCart.isBlank() ? "[]" : encodedCart);
    }

    public static void checkoutAntazon() {
        send(ComputerAccessPayload.ANTAZON_CHECKOUT, "");
    }

    public static void reviewAntazonProduct(ResourceLocation productId, int rating, String title, String body) {
        send(ComputerAccessPayload.ANTAZON_REVIEW, productId + "\0" + rating + "\0" + title + "\0" + body);
    }
}


