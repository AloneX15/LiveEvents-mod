package com.takumistudios.liveevents.client.mixin;

import com.takumistudios.liveevents.client.visual.CameraShake;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Único mixin de LiveEvents (ver MIXINS.md): suma el temblor de los eventos a la rotación de la cámara al final de
 * {@code Camera.update}. Es aditivo (no reemplaza nada) y {@code require = 0}: si el método cambia en otra versión, el
 * temblor se desactiva sin romper el juego.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique
    private final float[] liveevents$offset = new float[2];

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float yRot();

    @Shadow
    public abstract float xRot();

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void liveevents$shake(DeltaTracker deltaTracker, CallbackInfo ci) {
        CameraShake.capture((Camera) (Object) this);
        float[] offset = CameraShake.offset(liveevents$offset);
        if (offset != null) {
            setRotation(yRot() + offset[0], Math.max(-90f, Math.min(90f, xRot() + offset[1])));
        }
    }
}
