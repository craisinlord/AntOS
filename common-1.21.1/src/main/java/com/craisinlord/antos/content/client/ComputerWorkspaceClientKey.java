package com.craisinlord.antos.content.client;

public final class ComputerWorkspaceClientKey {
    private static java.util.UUID cachedWorkspace;
    private static String cachedKey = "workspace:unbound";

    private ComputerWorkspaceClientKey() { }

    public static String of() {
        var account = AnternetAccountClientState.latest();
        if (account != null && account.result() == com.craisinlord.antos.content.network.AnternetAccountResultPayload.SUCCESS) {
            java.util.UUID workspace = account.workspaceId();
            String key = cachedKey;
            if (workspace == null || !workspace.equals(cachedWorkspace)) {
                key = "workspace:" + workspace;
                if (workspace != null) {
                    cachedWorkspace = workspace;
                    cachedKey = key;
                }
            }
            return key;
        }
        return "workspace:unbound";
    }
}
