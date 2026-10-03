package com.takumistudios.liveevents.test;

import com.takumistudios.liveevents.client.screen.ConfigScreen;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.instance.EventInstance;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * Test de cliente con capturas (§23.1): abre un mundo, lanza los eventos y fotografía los momentos clave de cada
 * guion en los distintos perfiles. Las capturas quedan en versions/&lt;v&gt;/build/run/clientGameTest/screenshots/ y se
 * usan en la wiki. Ejecutar con ./gradlew :26.3:runClientGameTest.
 */
public class LiveEventsClientGameTest implements FabricClientGameTest {
    /** La ancla de la supernova está a 160° de acimut y 55° de altura: mirar hacia allí (yaw -20, pitch -45). */
    private static final String LOOK_AT_SUPERNOVA = "execute as @p at @s run tp @s ~ ~ ~ -20 -45";

    private static void waitForChunks(TestSingleplayerContext world) {
        //? if >=26.2 {
        world.getConnection().waitForChunksRender();
        //?} else {
        /*world.getClientLevel().waitForChunksRender();
        *///?}
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        // Sin chat en las capturas
        context.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            waitForChunks(world);
            TestServerContext server = world.getServer();
            server.runCommand("time set 18000");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            server.runCommand("execute as @p at @s run tp @s ~ ~12 ~ -20 -45");
            context.waitTicks(40);
            context.takeScreenshot("liveevents_00_night_sky");

            // Supernova en cinematic: un fotograma por segmento del guion
            int id = start(context, server, "le start supernova profile=cinematic");
            double[] moments = {4.0, 7.5, 8.3, 10.1, 15.0, 27.0, 40.0, 52.0};
            String[] names = {"supernova_01_agony", "supernova_02_collapse", "supernova_03_breakout", "supernova_04_glitch",
                    "supernova_05_shattered_sky", "supernova_06_demon_sun", "supernova_07_plasma", "supernova_08_scar"};
            for (int i = 0; i < moments.length; i++) {
                seekAndShoot(context, server, id, moments[i], names[i]);
            }
            stop(context, server, id);

            // El mismo momento (pico del whiteout) en los tres perfiles
            for (String profile : new String[]{"cinematic", "gameplay", "safe"}) {
                int p = start(context, server, "le start supernova profile=" + profile);
                seekAndShoot(context, server, p, 20.6, "supernova_profile_" + profile);
                stop(context, server, p);
            }

            // Secuela persistente: la nebulosa y el púlsar se quedan en el cielo tras el evento
            int last = start(context, server, "le start supernova profile=gameplay");
            server.runCommand("le seek " + last + " 59.5");
            context.waitTicks(80);
            context.takeScreenshot("supernova_09_aftermath");

            server.runCommand("le scars clear all");

            // Eventos de las fases 2–4 del plan
            shootEvent(context, server, "le start aurora_borealis", 50, "execute as @p at @s run tp @s ~ ~ ~ 180 -25", "aurora_borealis");
            shootEvent(context, server, "le start total_eclipse", 40, "execute as @p at @s run tp @s ~ ~ ~ 0 -45", "total_eclipse_totality");
            shootEvent(context, server, "le start solar_storm", 30, "execute as @p at @s run tp @s ~ ~ ~ -90 -30", "solar_storm");
            shootEvent(context, server, "le start black_hole", 35, "execute as @p at @s run tp @s ~ ~ ~ 180 -60", "black_hole");
            shootEvent(context, server, "le start blizzard", 40, "execute as @p at @s run tp @s ~ ~ ~ 0 0", "blizzard");
            int meteor = start(context, server, "le start great_meteor at ~ ~ ~28");
            server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 30");
            server.runCommand("le seek " + meteor + " 12.5");
            context.waitTicks(110); // el impacto es a los ~15 s: esperar a que se abra el cráter
            context.takeScreenshot("great_meteor_crater");
            stop(context, server, meteor);

            // Lluvia de meteoritos: mirar al horizonte desde una altura
            server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 -10");
            int meteors = start(context, server, "le start meteor_shower radius=40 count=40");
            server.runCommand("le seek " + meteors + " 28");
            context.waitTicks(45);
            context.takeScreenshot("meteor_shower_01_rain");
            context.waitTicks(40);
            context.takeScreenshot("meteor_shower_02_impacts");
            stop(context, server, meteors);

            // Terremoto con el overlay de depuración
            server.runCommand("execute as @p run le debug timeline");
            int quake = start(context, server, "le start earthquake");
            server.runCommand("le seek " + quake + " 14");
            context.waitTicks(30);
            context.takeScreenshot("earthquake_01_s_wave_debug");
            server.runCommand("execute as @p run le debug timeline");
            stop(context, server, quake);

            // Panel de administración con un evento activo
            int panel = start(context, server, "le start meteor_shower");
            server.runCommand("execute as @p run le gui");
            context.waitTicks(20);
            context.takeScreenshot("admin_panel");
            context.runOnClient(mc -> mc.setScreenAndShow(null));
            stop(context, server, panel);

            // Opciones del cliente
            context.runOnClient(mc -> mc.setScreenAndShow(new ConfigScreen(null)));
            context.waitTicks(10);
            context.takeScreenshot("client_options");
            context.runOnClient(mc -> mc.setScreenAndShow(null));
        }
    }

    /** Lanza con un comando de consola y devuelve el número de la instancia creada. */
    private static int start(ClientGameTestContext context, TestServerContext server, String command) {
        server.runOnServer(s -> LiveEventsServer.get().director().clearCooldowns());
        server.runCommand(command);
        context.waitTicks(5);
        return server.computeOnServer(s -> LiveEventsServer.get().director().active().stream()
                .mapToInt(EventInstance::instanceId).max().orElseThrow(() -> new AssertionError("No se lanzó: " + command)));
    }

    private static void stop(ClientGameTestContext context, TestServerContext server, int id) {
        server.runCommand("le stop " + id);
        context.waitTicks(40);
    }

    /** Lanza un evento, salta a un segundo, mira hacia donde ocurre y hace la captura. */
    private static void shootEvent(ClientGameTestContext context, TestServerContext server, String command, double second,
                                   String look, String name) {
        int id = start(context, server, command);
        server.runCommand(look);
        server.runCommand("le seek " + id + " " + second);
        context.waitTicks(30);
        context.takeScreenshot(name);
        stop(context, server, id);
    }

    private static void seekAndShoot(ClientGameTestContext context, TestServerContext server, int id, double second, String name) {
        server.runCommand(LOOK_AT_SUPERNOVA);
        server.runCommand("le seek " + id + " " + second);
        context.waitTicks(4);
        server.runCommand("le pause " + id);
        context.waitTicks(8);
        context.takeScreenshot(name);
        server.runCommand("le resume " + id);
    }
}
