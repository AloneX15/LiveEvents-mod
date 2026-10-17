package com.takumistudios.liveevents.server;

import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.config.ServerConfig;
import com.takumistudios.liveevents.core.safety.RateLimiter;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler;
import com.takumistudios.liveevents.server.instance.EventDirector;
import com.takumistudios.liveevents.server.journal.WorldJournal;
import com.takumistudios.liveevents.server.schedule.ScheduleService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Servicios de LiveEvents de un servidor en marcha (dedicado o integrado). Se crea en SERVER_STARTING y se cierra en
 * SERVER_STOPPING; en un jugador se crea uno nuevo cada vez que se abre un mundo.
 */
public final class LiveEventsServer {
    private static volatile LiveEventsServer current;

    private final MinecraftServer server;
    private final ExecutorService compute = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "LiveEvents-Geometry"); t.setDaemon(true); return t;
    });
    public ExecutorService compute() { return compute; }
    private final ExecutorService io;
    private final Path configDir;
    private final Path worldDir;
    private ServerConfig config;
    private final EventRegistry registry;
    private final WorkScheduler scheduler;
    private final WorldJournal journal;
    private final com.takumistudios.liveevents.server.modules.MeteorCoolingRecovery cooling;
    private final AuditLog audit;
    private final SkyScarManager scars;
    private final EventDirector director;
    private final ScheduleService schedule;
    private final DiscordNotifier discord;
    private RateLimiter startLimiter;

    private LiveEventsServer(MinecraftServer server) {
        this.server = server;
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LiveEvents-IO");
            t.setDaemon(true);
            return t;
        });
        this.configDir = FabricLoader.getInstance().getConfigDir().resolve("liveevents");
        this.worldDir = server.getWorldPath(LevelResource.ROOT).resolve("liveevents");
        this.config = ServerConfig.load(configDir.resolve("server.json"));
        this.registry = new EventRegistry(configDir);
        this.scheduler = new WorkScheduler(config.tick_budget_ms, config.max_block_changes_per_tick, System::nanoTime);
        this.journal = new WorldJournal(worldDir.resolve("journal"), io);
        this.cooling = new com.takumistudios.liveevents.server.modules.MeteorCoolingRecovery(this,worldDir.resolve("meteor-cooling.json"));
        this.audit = new AuditLog(FabricLoader.getInstance().getGameDir().resolve("logs"), io, config.audit_log);
        this.scars = new SkyScarManager(worldDir.resolve("scars.json"), io);
        this.discord = new DiscordNotifier(this);
        this.startLimiter = RateLimiter.parse(config.start_rate_limit, new RateLimiter(5, 60_000));
        this.director = new EventDirector(server, this);
        this.schedule = new ScheduleService(server, this, worldDir.resolve("schedule.json"), io);
    }

    public static LiveEventsServer start(MinecraftServer server) {
        LiveEventsServer s = new LiveEventsServer(server);
        current = s;
        s.journal.expire(s.config.journal_expiry_days);
        s.audit.purge(s.config.audit_retention_days);
        return s;
    }

    /** El servidor activo. Solo válido mientras hay un servidor en marcha. */
    public static LiveEventsServer get() {
        LiveEventsServer s = current;
        if (s == null) {
            throw new IllegalStateException("LiveEvents no está activo (no hay servidor en marcha)");
        }
        return s;
    }

    public static boolean active() {
        return current != null;
    }

    /** Cierra los servicios esperando a que terminen las escrituras pendientes. */
    public void stop() {
        try {
            director.shutdown();
            schedule.save();
            journal.flushAll();
        } catch (RuntimeException e) {
            LiveEvents.LOGGER.error("Error while stopping LiveEvents", e);
        }
        compute.shutdownNow();
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                LiveEvents.LOGGER.warn("LiveEvents-IO did not finish writing in 10 s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LethalGuard.clear();
        if (current == this) {
            current = null;
        }
    }

    /** /le reload: vuelve a leer server.json y las definiciones. */
    public void reloadConfig() {
        config = ServerConfig.load(configDir.resolve("server.json"));
        scheduler.configure(config.tick_budget_ms, config.max_block_changes_per_tick);
        audit.setEnabled(config.audit_log);
        startLimiter = RateLimiter.parse(config.start_rate_limit, new RateLimiter(5, 60_000));
    }

    public MinecraftServer server() {
        return server;
    }

    public ServerConfig config() {
        return config;
    }

    public EventRegistry registry() {
        return registry;
    }

    public WorkScheduler scheduler() {
        return scheduler;
    }

    public com.takumistudios.liveevents.server.modules.MeteorCoolingRecovery cooling() { return cooling; }

    public WorldJournal journal() {
        return journal;
    }

    public AuditLog audit() {
        return audit;
    }

    public SkyScarManager scars() {
        return scars;
    }

    public EventDirector director() {
        return director;
    }

    public ScheduleService schedule() {
        return schedule;
    }

    public DiscordNotifier discord() {
        return discord;
    }

    public RateLimiter startLimiter() {
        return startLimiter;
    }

    public ExecutorService io() {
        return io;
    }

    public Path worldDir() {
        return worldDir;
    }

    public Path configDir() {
        return configDir;
    }
}
