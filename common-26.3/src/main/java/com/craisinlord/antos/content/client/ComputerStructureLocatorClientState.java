package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerStructureLocatorClientState {
    private static final Map<String, String> RESULTS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> CUSTOM_LOCATOR = new ConcurrentHashMap<>();
    private static final String LOCATE_PREFIX = ComputerAccessPayload.LOCATE_STRUCTURE + "\0";

    private ComputerStructureLocatorClientState() {
    }

    public static void clearAll() { RESULTS.clear(); CUSTOM_LOCATOR.clear(); }

    public static void searching(String dimensionId) {
        searching(dimensionId, false);
    }

    public static void searching(String dimensionId, boolean customLocator) {
        CUSTOM_LOCATOR.put(ComputerWorkspaceClientKey.of(), customLocator);
        String dimension = dimensionId == null || dimensionId.isBlank() ? "TARGET DIMENSION" : dimensionId.toUpperCase(java.util.Locale.ROOT);
        RESULTS.put(ComputerWorkspaceClientKey.of(), "SEARCHING IN " + dimension + "...");
    }

    public static void update(ComputerAccessResultPayload payload) {
        if (!payload.data().startsWith(LOCATE_PREFIX)) return;
        String[] envelope = payload.data().split("\u0000", 3);
        if (envelope.length != 3) return;

        boolean customLocator = CUSTOM_LOCATOR.getOrDefault(ComputerWorkspaceClientKey.of(), false);
        String message = switch (envelope[1]) {
            case "not_found" -> customLocator ? "NOTHING FOUND WITHIN RANGE" : "NO STRUCTURE FOUND WITHIN RANGE";
            case "dimension_unavailable" -> "TARGET DIMENSION UNAVAILABLE";
            case "busy" -> "LOCATOR BUSY // TRY AGAIN IN A FEW SECONDS";
            default -> "LOCATOR UNAVAILABLE";
        };
        RESULTS.put(ComputerWorkspaceClientKey.of(), payload.result() == ComputerAccessResultPayload.SUCCESS
                ? (customLocator ? "LOCATION // " : "STRUCTURE START // ") + envelope[2]
                : message);
    }

    public static String get() {
        return RESULTS.getOrDefault(ComputerWorkspaceClientKey.of(), "COORDINATES // SEARCH WHEN SELECTED");
    }
}


