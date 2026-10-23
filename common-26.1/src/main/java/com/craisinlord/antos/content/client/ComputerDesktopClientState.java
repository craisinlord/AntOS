package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerDesktopClientState {
    private static final String STATE_PREFIX = ComputerAccessPayload.DESKTOP_STATE + "\0\0";
    private static final String WALLPAPER_PREFIX = ComputerAccessPayload.DESKTOP_WALLPAPER + "\0\0";
    private static final Map<String, String> PENDING = new ConcurrentHashMap<>();

    private ComputerDesktopClientState() {
    }

    public static void clearAll() {
        PENDING.clear();
    }

    public static void update(ComputerAccessResultPayload result) {
        String data = result.data();
        if (data.startsWith(STATE_PREFIX)) {
            PENDING.put(ComputerWorkspaceClientKey.of(), data.substring(STATE_PREFIX.length()));
        } else if (data.startsWith(WALLPAPER_PREFIX)) {
            PENDING.put(ComputerWorkspaceClientKey.of(), data.substring(WALLPAPER_PREFIX.length()));
        }
    }

    public static String take() {
        return PENDING.remove(ComputerWorkspaceClientKey.of());
    }
}
