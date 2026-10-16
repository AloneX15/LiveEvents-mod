package com.takumistudios.liveevents.server.integration;

import com.takumistudios.liveevents.LiveEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.lang.reflect.Method;

/** Optional Common Protection API bridge; an incompatible installed provider fails closed. */
public final class ProtectionCompat {
    private static boolean checked, installed, failed;
    private static Method protectedAt;
    private ProtectionCompat() { }
    public static boolean protectedAt(ServerLevel level,BlockPos pos) {
        if (!checked) {
            checked=true;
            installed=FabricLoader.getInstance().isModLoaded("common-protection-api");
            if (installed) try {
                protectedAt=Class.forName("eu.pb4.common.protection.api.CommonProtection").getMethod("isProtected",Level.class,BlockPos.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                failed=true; LiveEvents.LOGGER.warn("Common Protection API unavailable; protected operations denied",e);
            }
        }
        if (!installed) return false;
        if (failed) return true;
        try { return (boolean)protectedAt.invoke(null,level,pos); }
        catch (ReflectiveOperationException | RuntimeException e) {
            failed=true; LiveEvents.LOGGER.warn("Common Protection API failed; protected operations denied",e); return true;
        }
    }
}
