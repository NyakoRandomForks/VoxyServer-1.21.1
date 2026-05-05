package com.dripps.voxyserver.client;

import com.dripps.voxyserver.VoxyServer;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoxyServerClient implements ClientModInitializer {
    private static RemoteIngestService ingestService = null;

    public static RemoteIngestService getIngestService() {
        if (ingestService == null) {
            VoxyServer.LOGGER.info("RemoteIngestService initializing...");
            var instance = VoxyCommon.getInstance();
            if (instance == null)
                throw new IllegalStateException("Voxy have not been initialized");

            ingestService = new RemoteIngestService(instance.getServiceManager());
        }
        return ingestService;
    }

    @Override
    public void onInitializeClient() {
        VoxyServer.LOGGER.info("VoxyServerClient initializing...");

        ClientLodReceiver.register();
    }

}
