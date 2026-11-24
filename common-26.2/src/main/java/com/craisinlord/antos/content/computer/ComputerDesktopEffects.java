package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailEventData;
import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.content.antmail.AntmailTakeover;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import com.craisinlord.antos.content.network.ComputerAccessHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class ComputerDesktopEffects {
    public static final String TAKEOVER_CONTENT_TAG = "TakeoverContent";
    public static final String TAKEOVER_SEEN = "takeover_seen";

    private ComputerDesktopEffects() { }

    public record WallpaperChange(Identifier id, boolean force, boolean restore, boolean unlock) {
        public static WallpaperChange parse(Object source, JsonElement element) {
            if (element == null || !element.isJsonObject()) return null;
            JsonObject object = element.getAsJsonObject();
            String raw = object.has("id") && object.get("id").isJsonPrimitive() ? object.get("id").getAsString() : "";
            Identifier id = raw.isBlank() ? null : Identifier.tryParse(raw);
            boolean restore = flag(object, "restore");
            boolean force = flag(object, "force");
            boolean unlock = flag(object, "unlock");
            if (!raw.isBlank() && id == null) {
                AntOS.LOGGER.warn("Ignoring wallpaper change on {}: '{}' is not a valid ID", source, raw);
                return null;
            }
            if (!restore && (id == null || !force && !unlock)) {
                AntOS.LOGGER.warn("Ignoring wallpaper change on {}: it needs an id with force or unlock, or restore", source);
                return null;
            }
            return new WallpaperChange(id, force, restore, unlock);
        }

        private static boolean flag(JsonObject object, String key) {
            return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isBoolean() && object.get(key).getAsBoolean();
        }

        public String flags() {
            return String.join(",", java.util.stream.Stream.of(force ? "force" : "", restore ? "restore" : "", unlock ? "unlock" : "")
                    .filter(flag -> !flag.isEmpty()).toList());
        }

        public static WallpaperChange fromFlags(Identifier id, String flags) {
            java.util.List<String> values = flags == null ? java.util.List.of() : java.util.Arrays.asList(flags.split(","));
            return new WallpaperChange(id, values.contains("force"), values.contains("restore"), values.contains("unlock"));
        }

        public JsonObject encode() {
            JsonObject object = new JsonObject();
            if (id != null) object.addProperty("id", id.toString());
            if (force) object.addProperty("force", true);
            if (restore) object.addProperty("restore", true);
            if (unlock) object.addProperty("unlock", true);
            return object;
        }
    }

    public static boolean apply(ComputerDesktopState state, WallpaperChange change) {
        if (state == null || change == null) return false;
        boolean changed = false;
        if (change.restore()) changed = state.restoreWallpaper();
        if (change.id() == null) return changed;
        if (ComputerGuideData.wallpaper(change.id()) == null) {
            AntOS.LOGGER.warn("Wallpaper change refers to missing wallpaper {}", change.id());
            return changed;
        }
        if (change.unlock()) changed |= state.unlockWallpaper(change.id());
        if (change.force()) changed |= state.forceWallpaper(change.id());
        return changed;
    }

    public static boolean applyToAccount(MinecraftServer server, UUID accountId, WallpaperChange change, Identifier takeover) {
        if (server == null || accountId == null || change == null && takeover == null) return false;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(server);
        ComputerWorkspaceData.AccountInfo account = data.account(accountId);
        if (account == null) return false;
        CompoundTag section = data.workspaceSection(account.workspaceId(), "Desktop");
        if (section == null) return false;
        ComputerDesktopState state = new ComputerDesktopState();
        state.load(section);
        boolean changed = apply(state, change);
        if (takeover != null) changed |= state.queueTakeover(takeover);
        if (!changed) return false;
        if (!data.saveWorkspaceSection(account.workspaceId(), "Desktop", state.save())) return false;
        push(server, accountId, state);
        return true;
    }

    public static void push(MinecraftServer server, UUID accountId, ComputerDesktopState state) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ComputerWorkspaceData.AccountInfo session = AnternetAccountHandler.session(player);
            if (session != null && session.accountId().equals(accountId)) ComputerAccessHandler.pushDesktop(player, clientTag(state, false));
        }
    }

    public static CompoundTag clientTag(ComputerDesktopState state, boolean includeTakeover) {
        CompoundTag tag = state.save();
        if (!includeTakeover || state.pendingTakeover() == null) return tag;
        AntmailTakeover takeover = AntmailEventData.takeover(state.pendingTakeover());
        if (takeover != null) tag.put(TAKEOVER_CONTENT_TAG, takeover.toTag());
        return tag;
    }

    public static UUID accountFor(ServerPlayer player) {
        ComputerWorkspaceData.AccountInfo session = AnternetAccountHandler.session(player);
        if (session != null) return session.accountId();
        AntmailServerData mail = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = mail.deliveryAddressFor(player);
        if (address != null) {
            UUID account = mail.accountIdForAddress(player.level().getServer(), address);
            if (account != null) return account;
        }
        ComputerWorkspaceData.AccountInfo faceId = ComputerWorkspaceData.access(player.level().getServer()).faceIdAccount(player.getUUID());
        return faceId == null ? null : faceId.accountId();
    }
}
