package com.dripps.voxyserver.client.mixin;

import com.dripps.voxyserver.VoxyServer;
import com.dripps.voxyserver.client.service.IVoxyServerIngestAccess;
import com.dripps.voxyserver.client.service.RemoteIngestService;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VoxyInstance.class)
public abstract class VoxyInstanceMixin implements IVoxyServerIngestAccess {
    @Unique
    private RemoteIngestService voxyserver$remoteIngestService;
    @Unique
    private boolean voxyserver$usingRemoteIngest = false;

    @Shadow
    public abstract ServiceManager getServiceManager();

    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void voxyserver$init(CallbackInfo ci) {
        VoxyServer.LOGGER.info("Initializing remote ingest service...");
        voxyserver$remoteIngestService = new RemoteIngestService(this.getServiceManager());
    }

    @Inject(method = "shutdown()V", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/StampedLock;writeLock()J"))
    private void voxyserver$shutdown(CallbackInfo ci) {
        VoxyServer.LOGGER.info("Shutting down remote ingest service...");
        try {
            this.voxyserver$remoteIngestService.shutdown();
        } catch (Exception e) {
            VoxyServer.LOGGER.error("An error occurred while shutting down remote ingest service", e);
        }
    }

    @Inject(method= "isIngestEnabled(Lme/cortex/voxy/commonImpl/WorldIdentifier;)Z", at = @At("HEAD"), cancellable = true)
    private void voxyserver$disableClientsideIngest(WorldIdentifier worldId, CallbackInfoReturnable<Boolean> cir){
        if (voxyserver$usingRemoteIngest) {
            cir.setReturnValue(false);
        }
    }

    public RemoteIngestService voxyserver$getRemoteIngestService() {
        return voxyserver$remoteIngestService;
    }

    @Override
    public void voxyserver$setUsingRemoteIngest(boolean remoteIngest) {
        voxyserver$usingRemoteIngest = remoteIngest;
    }
}
