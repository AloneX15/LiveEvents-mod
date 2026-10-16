package com.takumistudios.liveevents.test.mixin;
import com.takumistudios.liveevents.test.ScreenshotWorkaround;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Present only in the test mod; never packaged in the production jar. */
@Mixin(GameRenderer.class)
public abstract class ScreenshotMixin {
    @Inject(method="extract",at=@At("HEAD"),require=0)
    private void prepareScreenshot(DeltaTracker delta,boolean renderLevel,CallbackInfo ci) {
        //? if <26.2 {
        /*if(ScreenshotWorkaround.preparing) ((GameRenderer)(Object)this).update(delta,renderLevel);
        *///?}
    }
}
