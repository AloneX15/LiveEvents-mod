package com.takumistudios.liveevents.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Test-only workaround for FabricMC/fabric-api#5466 on the 26.1 branch. */
public final class ScreenshotWorkaround {
    public static volatile boolean preparing;
    private ScreenshotWorkaround() { }
    public static void capture(ClientGameTestContext context,String name) {
        preparing=true;
        try { context.takeScreenshot(name); }
        finally { preparing=false; }
    }
}
