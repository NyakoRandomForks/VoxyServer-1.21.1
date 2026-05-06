package com.dripps.voxyserver.client.service;

import com.dripps.voxyserver.VoxyServer;
import com.dripps.voxyserver.network.LODBulkPayload;
import com.dripps.voxyserver.network.LODSectionPayload;
import me.cortex.voxy.common.thread.Service;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldUpdater;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

public class RemoteIngestService {

    private record IngestSection(WorldEngine engine, ClientLevel level, LODBulkPayload bulk) {
    }

    private final VoxyInstance voxyInstance;
    private final Service service;
    private final ConcurrentLinkedDeque<IngestSection> ingestQueue = new ConcurrentLinkedDeque<>();

    public RemoteIngestService(VoxyInstance instance, ServiceManager pool) {
        this.voxyInstance = instance;
        this.service = pool.createServiceNoCleanup(() -> {
            return this::processJob;
        }, 5000L, "VoxyServer-RemoteIngestService");
    }

    private void processJob() {
        IngestSection task = (IngestSection) this.ingestQueue.pop();
        task.engine.markActive();
        for (LODSectionPayload section : task.bulk.sections()) {
            Mapper mapper = task.engine.getMapper();

            long[] remappedLut = remapLut(section.lutBlockStateIds(), section.lutBiomeIds(),
                    section.lutLight(), mapper, task.level);

            int secX = WorldEngine.getX(section.sectionKey());
            int secY = WorldEngine.getY(section.sectionKey());
            int secZ = WorldEngine.getZ(section.sectionKey());

            short[] indexArray = section.indexArray();

            // split 32x32x32 world section into 8 VoxelizedSections (16x16x16 each)
            for (int oy = 0; oy < 2; oy++) {
                for (int oz = 0; oz < 2; oz++) {
                    for (int ox = 0; ox < 2; ox++) {
                        VoxelizedSection vs = VoxelizedSection.createEmpty();
                        vs.setPosition(secX * 2 + ox, secY * 2 + oy, secZ * 2 + oz);

                        int nonAirCount = 0;
                        for (int vy = 0; vy < 16; vy++) {
                            for (int vz = 0; vz < 16; vz++) {
                                for (int vx = 0; vx < 16; vx++) {
                                    // world section index: (y<<10)|(z<<5)|x
                                    int wsIdx = ((oy * 16 + vy) << 10) | ((oz * 16 + vz) << 5) | (ox * 16 + vx);
                                    // voxelized section level 0 index: (y<<8)|(z<<4)|x
                                    int vsIdx = (vy << 8) | (vz << 4) | vx;
                                    long id = remappedLut[indexArray[wsIdx] & 0xFFFF];
                                    vs.section[vsIdx] = id;
                                    if (!Mapper.isAir(id)) nonAirCount++;
                                }
                            }
                        }
                        vs.lvl0NonAirCount = nonAirCount;

                        WorldVoxilizedSectionMipper.mipSection(vs, mapper);
                        WorldUpdater.insertUpdate(task.engine, vs);
                    }
                }
            }
        }
    }

    private static long[] remapLut(int[] blockStateIds, int[] biomeIds, byte[] light, Mapper mapper, ClientLevel level) {
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        long[] remapped = new long[blockStateIds.length];

        for (int i = 0; i < blockStateIds.length; i++) {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(blockStateIds[i]);
            int clientBlockId = (state != null) ? mapper.getIdForBlockState(state) : 0;

            Optional<Holder.Reference<Biome>> biomeHolder = biomeRegistry.getHolder(biomeIds[i]);
            int clientBiomeId = biomeHolder.map(mapper::getIdForBiome).orElse(0);

            remapped[i] = Mapper.composeMappingId(light[i], clientBlockId, clientBiomeId);
        }

        return remapped;
    }

    public void enqueueIngest(WorldIdentifier worldId, ClientLevel level, LODBulkPayload bulk) {
        if (!voxyInstance.isRunning()) {
            VoxyServer.LOGGER.warn("Tried enqueue ingest to voxy instance that not running");
            return;
        }
        WorldEngine engine = voxyInstance.getOrCreate(worldId);
        if (this.service.isLive()) {
            if (!engine.isLive()) {
                VoxyServer.LOGGER.warn("Tried inserting chunk into WorldEngine that was not alive");
                return;
            }

            engine.markActive();
            this.ingestQueue.add(new IngestSection(engine, level, bulk));

            try {
                this.service.execute();
            } catch (Exception ex) {
                VoxyServer.LOGGER.error("Exception caught while enqueuing remote ingest", ex);
            }
        }
    }

    public void shutdown() {
        this.service.shutdown();
    }
}
