package com.dripps.voxyserver.client;

import com.dripps.voxyserver.network.LODBulkPayload;
import com.dripps.voxyserver.network.LODClearPayload;
import com.dripps.voxyserver.network.LODReadyPayload;
import com.dripps.voxyserver.network.LODSectionPayload;
import com.dripps.voxyserver.network.LODServerSettingsPayload;
import com.dripps.voxyserver.network.PreSerializedLodPayload;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldUpdater;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public class ClientLodReceiver {

    public static void register() {
        // send ready handshake when joining a server
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientLodSettings.prepareForCurrentConnection();
            VoxyServerClient.getIngestService();
            ClientPlayNetworking.send(new LODReadyPayload());
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientLodSettings.reset();
        });

        ClientPlayNetworking.registerGlobalReceiver(LODServerSettingsPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> ClientLodSettings.applyServerSettings(
                    payload.maxLodStreamRadius(), payload.maxSectionsPerTick()));
        });

        ClientPlayNetworking.registerGlobalReceiver(PreSerializedLodPayload.TYPE, (payload, context) -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;

            var instance = VoxyCommon.getInstance();
            if (instance == null) return;

            WorldIdentifier worldId = WorldIdentifier.of(level);
            if (worldId == null) return;

            WorldEngine engine = instance.getOrCreate(worldId);
            LODBulkPayload bulk = payload.decodeBulk(level.registryAccess());

            RemoteIngestService.ingestSections(engine, level, bulk);

        });

        ClientPlayNetworking.registerGlobalReceiver(LODClearPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> handleClear(payload));
        });
    }



    private static void handleClear(LODClearPayload payload) {
        // dimension change clear is handled by voxy itself when the world changes
        // this is a signal from the server to reset any cached state
    }
}
