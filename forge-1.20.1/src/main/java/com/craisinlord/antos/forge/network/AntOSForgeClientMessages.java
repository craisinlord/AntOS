package com.craisinlord.antos.forge.network;

import com.craisinlord.antos.content.network.AnternetAccountResultPayload;
import com.craisinlord.antos.content.network.AnternetComputerResultPayload;
import com.craisinlord.antos.content.network.AntmailAnternetResultPayload;
import com.craisinlord.antos.content.network.FloppyTextureSyncPayload;
import com.craisinlord.antos.content.network.JeiSyncPayload;

public final class AntOSForgeClientMessages {
    private AntOSForgeClientMessages() { }

    public static void handle(Object message) {
        if (message instanceof AnternetComputerResultPayload payload)
            com.craisinlord.antos.content.client.ComputerAccessClientState.updateAnternet(payload);
        else if (message instanceof AnternetAccountResultPayload payload)
            com.craisinlord.antos.content.client.AnternetAccountClientState.update(payload);
        else if (message instanceof AntmailAnternetResultPayload payload)
            com.craisinlord.antos.content.client.AntmailClientState.update(payload);
        else if (message instanceof FloppyTextureSyncPayload payload)
            com.craisinlord.antos.content.client.FloppyTextureClientState.update(payload);
        else if (message instanceof JeiSyncPayload payload)
            com.craisinlord.antos.content.client.JeiClientState.update(payload);
        else if (message instanceof com.craisinlord.antos.content.network.DisplaySyncPayload payload)
            com.craisinlord.antos.content.client.DisplaySyncClientState.update(payload);
    }
}
