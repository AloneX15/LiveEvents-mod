package com.takumistudios.liveevents.content;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Contenido registrado: efecto de radiación, sonidos, tipos de daño (datos) y tags de bloques. */
public final class LEContent {
    public static Holder<MobEffect> RADIATION_BURN;

    public static final ResourceKey<DamageType> STELLAR_BURN = damage("stellar_burn");
    public static final ResourceKey<DamageType> METEOR_IMPACT = damage("meteor_impact");
    public static final ResourceKey<DamageType> SEISMIC = damage("seismic");
    public static final ResourceKey<DamageType> RADIATION = damage("radiation");

    /** Bloques que los eventos no tocan nunca (los modpacks pueden ampliarlo). */
    public static final TagKey<Block> IMMUNE = TagKey.create(Registries.BLOCK, id("immune"));

    /** Sonidos del mod; las claves de subtítulo están en sounds.json y en lang. */
    public static final List<String> SOUNDS = List.of(
            "supernova.heartbeat", "supernova.groan", "supernova.collapse", "supernova.shatter", "supernova.xray",
            "supernova.crackle", "supernova.roar", "supernova.blast", "supernova.tinnitus", "supernova.plasma",
            "supernova.whistler", "supernova.wind", "supernova.pulsar",
            "meteor.rumble", "meteor.whistle", "meteor.impact_near", "meteor.impact_far", "meteor.sizzle",
            "earthquake.hum", "earthquake.thump", "earthquake.rumble", "earthquake.crack",
            "ui.warning");
    public static final Map<String, SoundEvent> SOUND_EVENTS = new LinkedHashMap<>();

    private LEContent() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("liveevents", path);
    }

    private static ResourceKey<DamageType> damage(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, id(path));
    }

    public static void register() {
        RADIATION_BURN = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id("radiation_burn"), new RadiationBurnEffect());
        for (String name : SOUNDS) {
            Identifier soundId = id(name);
            SOUND_EVENTS.put(name, Registry.register(BuiltInRegistries.SOUND_EVENT, soundId, SoundEvent.createVariableRangeEvent(soundId)));
        }
    }

    /** Fuente de daño de un tipo (vanilla, del mod o de un datapack). Vacío si el tipo no existe. */
    public static Optional<DamageSource> damageSource(ServerLevel level, Identifier type) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, type);
        return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(key).map(DamageSource::new);
    }
}
