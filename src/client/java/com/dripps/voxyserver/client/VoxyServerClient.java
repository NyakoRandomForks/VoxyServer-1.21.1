package com.dripps.voxyserver.client;

import com.dripps.voxyserver.client.config.ClientLodSettings;
import com.dripps.voxyserver.client.service.IVoxyServerIngestAccess;
import com.dripps.voxyserver.network.*;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;

public class VoxyServerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register(this::onGameJoin);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onGameLeave);

        ClientPlayNetworking.registerGlobalReceiver(LODServerSettingsPayload.TYPE, this::onHandshake);

        ClientPlayNetworking.registerGlobalReceiver(PreSerializedLodPayload.TYPE, this::onLodReceived);

        ClientPlayNetworking.registerGlobalReceiver(LODClearPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> handleClear(payload));
        });
    }

    private void onGameJoin(ClientPacketListener handler, PacketSender sender, Minecraft client) {
        ClientLodSettings.prepareForCurrentConnection();
        ClientPlayNetworking.send(new LODReadyPayload());
    }

    private void onGameLeave(ClientPacketListener handler, Minecraft client) {
        ClientLodSettings.reset();
    }

    private void onHandshake(LODServerSettingsPayload payload, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            ClientLodSettings.applyServerSettings(payload.maxLodStreamRadius(), payload.maxSectionsPerTick());

            VoxyInstance instance = VoxyCommon.getInstance();
            if (instance == null)
                return;
            ((IVoxyServerIngestAccess) instance).voxyserver$setUsingRemoteIngest(true);
        });
    }

    private void onLodReceived(PreSerializedLodPayload payload, ClientPlayNetworking.Context context) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
            return;

        VoxyInstance instance = VoxyCommon.getInstance();
        if (instance == null || !instance.isRunning())
            return;

        WorldIdentifier worldId = WorldIdentifier.of(level);
        if (worldId == null)
            return;

        WorldEngine engine = instance.getOrCreate(worldId);
        LODBulkPayload bulk = payload.decodeBulk(level.registryAccess());

        ((IVoxyServerIngestAccess) instance).voxyserver$getRemoteIngestService().enqueueIngest(engine, level, bulk);
    }


    private static void handleClear(LODClearPayload payload) {
        // dimension change clear is handled by voxy itself when the world changes
        // this is a signal from the server to reset any cached state
    }
}
