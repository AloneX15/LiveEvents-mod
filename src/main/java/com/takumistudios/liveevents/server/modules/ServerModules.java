package com.takumistudios.liveevents.server.modules;

import com.takumistudios.liveevents.api.module.ServerModule;
import com.takumistudios.liveevents.core.definition.ModuleSpec;
import com.takumistudios.liveevents.core.module.ModuleTypeRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Fábricas de comportamiento de servidor por tipo de módulo (oficiales y de addons). */
public final class ServerModules {
    private static final Map<String, Function<ModuleSpec, ServerModule>> FACTORIES = new HashMap<>();
    /** Módulos sin comportamiento de servidor (la parte de servidor de un módulo BOTH que solo es visual). */
    private static final ServerModule NOOP = new ServerModule() {
    };

    static {
        register("chat_broadcast", s -> new GameModules.ChatBroadcast());
        register("title", s -> new GameModules.Title());
        register("boss_bar", s -> new GameModules.BossBar());
        register("damage_area", s -> new GameModules.DamageArea());
        register("status_effect", s -> new GameModules.StatusEffect());
        register("ignite", s -> new GameModules.Ignite());
        register("knockback", s -> new GameModules.Knockback());
        register("gravity_pull", s -> new GameModules.GravityPull());
        register("gaze_check", s -> new GameModules.GazeCheck());
        register("lightning_storm", s -> new GameModules.LightningStorm());
        register("weather", s -> new GameModules.Weather());
        register("artificial_day", s -> new GameModules.ArtificialDay());
        register("time_override", s -> NOOP);
        register("block_transform", s -> new BlockModules.Transform(null));
        register("melt", s -> new BlockModules.Transform("liveevents:melt"));
        register("crater", s -> new BlockModules.Crater());
        register("fire_spread", s -> new BlockModules.FireSpread());
        register("crack", s -> new BlockModules.Crack());
        register("meteor_impacts", s -> new CompositeModules.MeteorImpacts());
        register("seismic_wave", s -> new CompositeModules.SeismicWave());
    }

    private ServerModules() {
    }

    private static void register(String shortId, Function<ModuleSpec, ServerModule> factory) {
        FACTORIES.put("liveevents:" + shortId, factory);
    }

    /** Registro desde addons. */
    public static synchronized void registerAddon(String typeId, Function<ModuleSpec, ServerModule> factory) {
        String id = ModuleTypeRegistry.normalize(typeId);
        if (FACTORIES.containsKey(id)) {
            throw new IllegalArgumentException("Ya hay un módulo de servidor para " + id);
        }
        FACTORIES.put(id, factory);
    }

    public static synchronized ServerModule create(ModuleSpec spec) {
        Function<ModuleSpec, ServerModule> f = FACTORIES.get(spec.type());
        return f == null ? NOOP : f.apply(spec);
    }
}
