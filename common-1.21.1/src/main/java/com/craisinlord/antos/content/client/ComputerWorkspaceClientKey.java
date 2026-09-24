package com.craisinlord.antos.content.client;

public final class ComputerWorkspaceClientKey {
    private ComputerWorkspaceClientKey() { }

    public static String of() {
        var account = AnternetAccountClientState.latest();
        if (account != null && account.result() == com.craisinlord.antos.content.network.AnternetAccountResultPayload.SUCCESS) {
            return "workspace:" + account.workspaceId();
        }
        return "workspace:unbound";
    }
}
