package com.takumistudios.liveevents.core.module;

import com.takumistudios.liveevents.core.definition.Side;

import java.util.ArrayList;
import java.util.List;

import static com.takumistudios.liveevents.core.module.ParamKind.BOOLEAN;
import static com.takumistudios.liveevents.core.module.ParamKind.COLOR;
import static com.takumistudios.liveevents.core.module.ParamKind.CUTS;
import static com.takumistudios.liveevents.core.module.ParamKind.DURATION;
import static com.takumistudios.liveevents.core.module.ParamKind.IDENTIFIER;
import static com.takumistudios.liveevents.core.module.ParamKind.LIST;
import static com.takumistudios.liveevents.core.module.ParamKind.MODULES;
import static com.takumistudios.liveevents.core.module.ParamKind.NUMBER;
import static com.takumistudios.liveevents.core.module.ParamKind.OBJECT;
import static com.takumistudios.liveevents.core.module.ParamKind.RANGE;
import static com.takumistudios.liveevents.core.module.ParamKind.STRING;

/**
 * Catálogo de módulos oficiales. Es la fuente única de verdad: de aquí salen la validación, el JSON Schema
 * ({@code schema/}) y la referencia de módulos de la wiki.
 */
public final class BuiltinModules {
    public static final String SKY = "cielo";
    public static final String SCREEN = "pantalla";
    public static final String CAMERA = "cámara";
    public static final String AUDIO = "audio";
    public static final String WORLD_FX = "mundo (visual)";
    public static final String GAME = "juego";
    public static final String BLOCKS = "bloques";
    public static final String COMPOSITE = "compuestos";

    private BuiltinModules() {
    }

    private static ModuleType.Builder c(String id) {
        return ModuleType.builder("liveevents:" + id, Side.CLIENT);
    }

    private static ModuleType.Builder s(String id) {
        return ModuleType.builder("liveevents:" + id, Side.SERVER);
    }

    private static ModuleType.Builder b(String id) {
        return ModuleType.builder("liveevents:" + id, Side.BOTH);
    }

    public static List<ModuleType> all() {
        List<ModuleType> list = new ArrayList<>();

        // ---------------------------------------------------------------- cielo
        list.add(c("sky_tint").category(SKY).description("Tiñe el cielo y la niebla con un color.")
                .param("color", COLOR, "", "Color del tinte")
                .param("strength", RANGE, "0.6", "Intensidad (0-1)", 0, 1)
                .param("fade", DURATION, "1s", "Fundido de entrada y salida")
                .param("horizon", COLOR, "-", "Color opcional del horizonte (degradado)")
                .build());
        list.add(c("sky_blackout").category(SKY).description("Apaga las estrellas y oscurece cielo y niebla de golpe.")
                .param("strength", NUMBER, "0.95", "Oscuridad (0-1)", 0, 1)
                .build());
        list.add(c("celestial_body").category(SKY).description("Cuerpo celeste anclado a la bóveda: estrella, sol demonio o púlsar. Se deforma, parpadea, lanza llamaradas y puede colapsar.")
                .param("shape", STRING, "star", "star, boiling (mercurio hirviendo) o sun")
                .param("color", COLOR, "#FFFFFF", "Color principal")
                .param("core", COLOR, "-", "Color del núcleo")
                .param("corona", COLOR, "-", "Color de la corona")
                .param("size_deg", RANGE, "2", "Tamaño aparente en grados", 0.1, 120)
                .param("flicker_hz", RANGE, "0", "Parpadeo en Hz (limitado por accesibilidad si es grande)", 0, 60)
                .param("flares", BOOLEAN, "false", "Llamaradas asimétricas")
                .param("collapse", OBJECT, "-", "{at, duration, curve}: colapso hasta desaparecer")
                .build());
        list.add(c("star_point").category(SKY).description("Punto estelar brillante con halo (versión simple de celestial_body).")
                .param("color", COLOR, "#FFFFFF", "Color")
                .param("size_deg", RANGE, "1", "Tamaño aparente", 0.1, 30)
                .build());
        list.add(c("sky_shatter").category(SKY).description("Rompe la bóveda en fragmentos con grietas luminosas que se extienden desde el ancla.")
                .param("shards", NUMBER, "200", "Número de fragmentos", 8, 600)
                .param("crack_color", COLOR, "#00FFFF", "Color de las grietas")
                .param("spread", DURATION, "1.5s", "Tiempo que tardan las grietas en cubrir el cielo")
                .build());
        list.add(c("atmo_rings").category(SKY).description("Anillos de vapor que salen del ancla y recorren la bóveda.")
                .param("color", COLOR, "#FFFFFF", "Color")
                .param("interval", DURATION, "2.5s", "Tiempo entre anillos")
                .param("speed_deg", NUMBER, "45", "Grados por segundo", 1, 360)
                .param("distortion", NUMBER, "0.5", "Distorsión al pasar (0-1)", 0, 1)
                .build());
        list.add(c("plasma_shell").category(SKY).description("Esfera de plasma con filamentos que crece desde el ancla.")
                .param("colors", LIST, "[\"#FFFFFF\", \"#FF4500\"]", "Colores por los que pasa")
                .param("coverage", RANGE, "{\"from\":0.05,\"to\":0.7}", "Fracción de la bóveda cubierta", 0, 1)
                .param("turbulence", NUMBER, "1", "Turbulencia del ruido", 0, 4)
                .build());
        list.add(c("aurora").category(SKY).description("Cortinas de aurora desde los horizontes norte y sur.")
                .param("colors", LIST, "[\"#39FF14\", \"#DC143C\"]", "Colores")
                .param("intensity", RANGE, "0.7", "Intensidad (0-1)", 0, 1)
                .param("speed", NUMBER, "1", "Velocidad de las cortinas", 0, 5)
                .build());
        list.add(c("nebula").category(SKY).description("Nebulosa por capas. Puede quedarse como secuela persistente.")
                .param("colors", LIST, "[\"#FF0000\", \"#00CED1\", \"#C8B400\"]", "Colores de las capas")
                .param("span_deg", NUMBER, "180", "Anchura en grados de acimut", 10, 360)
                .param("opacity", RANGE, "0.6", "Opacidad", 0, 1)
                .build());
        list.add(c("pulsar").category(SKY).description("Punto que parpadea y haces de faro que giran (con límites de accesibilidad).")
                .param("color", COLOR, "#7DF9FF", "Color")
                .param("hz", NUMBER, "30", "Parpadeo del punto (solo área pequeña)", 0, 60)
                .param("beam_rpm", NUMBER, "40", "Vueltas por minuto de los haces", 0, 180)
                .build());
        list.add(c("lens_flare").category(SKY).description("Flares anamórficos horizontales desde el ancla.")
                .param("colors", LIST, "[\"#7DF9FF\", \"#FF00FF\"]", "Colores")
                .param("intensity", RANGE, "0.8", "Intensidad", 0, 1)
                .build());
        list.add(c("shadow_whip").category(SKY).description("Mueve el ángulo del sol para que giren las sombras. Solo se nota con shaders; sin ellos es un parpadeo de luz.")
                .param("speed", NUMBER, "1", "Velocidad", 0, 10)
                .build());
        list.add(b("time_override").category(SKY).description("Fuerza la noche (o la oscuridad) solo en la vista del cliente. La hora del servidor no cambia.")
                .param("mode", STRING, "night", "night o dark")
                .param("fade", DURATION, "3s", "Fundido")
                .build());
        list.add(b("artificial_day").category(SKY).description("Cliente: ilumina el mundo como de día. Servidor: los no-muertos expuestos arden.")
                .param("color", COLOR, "#FFF6E0", "Color de la luz")
                .param("burn_undead", BOOLEAN, "true", "Los no-muertos bajo el cielo arden")
                .build());

        // ---------------------------------------------------------------- pantalla
        list.add(c("flash").category(SCREEN).description("Destello a pantalla completa, limitado por la regla de destellos.")
                .param("color", COLOR, "#FFFFFF", "Color")
                .param("intensity", NUMBER, "1", "Pico (0-1)", 0, 1)
                .param("fade", DURATION, "0.6s", "Caída")
                .build());
        list.add(c("whiteout").category(SCREEN).description("Sobreexposición a blanco con curva de pico y sostenido.")
                .param("peak", NUMBER, "0.85", "Pico (0-1)", 0, 1)
                .param("peak_time", DURATION, "1s", "Duración del pico")
                .param("sustain", NUMBER, "0.35", "Nivel sostenido", 0, 1)
                .param("color", COLOR, "#FFFFFF", "Color")
                .build());
        list.add(c("light_cut").category(SCREEN).description("Cambia el color del cielo y de la luz a golpes, sin interpolar (o con fundido en modo seguro).")
                .param("cuts", CUTS, "", "Lista de [tiempo, color]")
                .param("mode", STRING, "cut", "cut o fade")
                .param("fade", DURATION, "1s", "Fundido en modo fade")
                .param("strength", NUMBER, "0.45", "Intensidad del tinte", 0, 1)
                .build());
        list.add(c("chromatic_glitch").category(SCREEN).description("Aberración cromática, separación RGB y franjas desplazadas.")
                .param("strength", NUMBER, "0.8", "Intensidad (0-1)", 0, 1)
                .param("slices", BOOLEAN, "true", "Franjas horizontales desplazadas")
                .build());
        list.add(c("bloom").category(SCREEN).description("Bloom falso: resplandor alrededor de las fuentes brillantes del evento.")
                .param("intensity", RANGE, "1", "Intensidad (1 = 100 %)", 0, 3)
                .build());
        list.add(c("screen_overlay").category(SCREEN).description("Viñeta o tinte en pantalla (radiación, calor).")
                .param("color", COLOR, "#000000", "Color")
                .param("vignette", RANGE, "0.4", "Cierre de la viñeta (0-1)", 0, 1)
                .param("tint", RANGE, "0", "Tinte uniforme (0-1)", 0, 1)
                .param("pulse_hz", NUMBER, "0", "Pulso (limitado a 3 Hz)", 0, 3)
                .build());
        list.add(c("light_pulse").category(SCREEN).description("Iluminación dinámica simulada (pulso de luz).")
                .param("color", COLOR, "#FFAA55", "Color")
                .param("intensity", RANGE, "0.5", "Intensidad", 0, 1)
                .param("hz", NUMBER, "1", "Frecuencia (limitada a 3 Hz)", 0, 3)
                .build());

        // ---------------------------------------------------------------- cámara
        list.add(c("camera_shake").category(CAMERA).description("Temblor de cámara con ruido suave; la amplitud baja con la distancia.")
                .param("amplitude", RANGE, "0.2", "Amplitud en grados", 0, 5)
                .param("frequency", NUMBER, "8", "Frecuencia del ruido", 0.1, 40)
                .param("axis", STRING, "both", "both, vertical o horizontal")
                .param("falloff", NUMBER, "0", "Radio en bloques a partir del cual se atenúa (0 = sin atenuación)", 0, 4096)
                .build());

        // ---------------------------------------------------------------- audio
        list.add(c("sound").category(AUDIO).description("Sonido puntual con retraso por distancia y atenuación.")
                .param("sound", IDENTIFIER, "", "Id del sonido")
                .param("volume", NUMBER, "1", "Volumen", 0, 4)
                .param("pitch", NUMBER, "1", "Tono", 0.5, 2)
                .param("position", STRING, "listener", "listener, center o anchor")
                .param("delay_by_distance", BOOLEAN, "true", "Llega más tarde cuanto más lejos")
                .build());
        list.add(c("sound_loop").category(AUDIO).description("Sonido repetido con curvas de volumen, tono o ritmo.")
                .param("sound", IDENTIFIER, "", "Id del sonido")
                .param("volume", RANGE, "1", "Volumen", 0, 4)
                .param("pitch", RANGE, "1", "Tono", 0.5, 2)
                .param("bpm", RANGE, "60", "Golpes por minuto", 1, 600)
                .build());
        list.add(c("audio_duck").category(AUDIO).description("Baja la música del juego para crear silencios.")
                .param("level", NUMBER, "0.1", "Nivel al que baja (0-1)", 0, 1)
                .build());

        // ---------------------------------------------------------------- mundo (visual)
        list.add(c("shockwave").category(WORLD_FX).description("Anillo que se expande desde el centro, con polvo al tocar el suelo.")
                .param("color", COLOR, "#FFE0B0", "Color")
                .param("radius", RANGE, "{\"from\":0,\"to\":64}", "Radio en bloques", 0, 1024)
                .build());
        list.add(c("particle_emitter").category(WORLD_FX).description("Emisor genérico de partículas vanilla.")
                .param("particle", IDENTIFIER, "minecraft:ash", "Id de la partícula")
                .param("rate", RANGE, "40", "Partículas por segundo (antes del LOD)", 0, 2000)
                .param("radius", NUMBER, "24", "Radio de emisión alrededor del jugador o del centro", 0, 256)
                .param("around", STRING, "player", "player o center")
                .param("height", NUMBER, "12", "Altura sobre el suelo", -64, 256)
                .build());
        list.add(c("meteor_trail").category(WORLD_FX).description("Estrellas fugaces y bólidos solo visuales.")
                .param("rate", RANGE, "1", "Estelas por segundo", 0, 30)
                .param("color", COLOR, "#FF7A1A", "Color de la estela")
                .param("core", COLOR, "#FFF4D6", "Color del núcleo")
                .build());
        list.add(c("ash_fall").category(WORLD_FX).description("Ceniza que cae alrededor del jugador.")
                .param("rate", RANGE, "60", "Partículas por segundo", 0, 2000)
                .build());
        list.add(c("debris").category(WORLD_FX).description("Escombros que saltan desde el centro.")
                .param("rate", RANGE, "30", "Partículas por segundo", 0, 2000)
                .param("block", IDENTIFIER, "minecraft:stone", "Bloque de las partículas")
                .build());

        // ---------------------------------------------------------------- juego
        list.add(s("chat_broadcast").category(GAME).description("Mensaje en el chat a los jugadores afectados.")
                .param("message", STRING, "", "Texto o clave de traducción")
                .build());
        list.add(s("title").category(GAME).description("Título y subtítulo en pantalla (oculto en cinematic salvo force).")
                .param("title", STRING, "", "Texto o clave de traducción")
                .param("subtitle", STRING, "-", "Subtítulo")
                .param("force", BOOLEAN, "false", "Mostrar también en cinematic")
                .build());
        list.add(s("boss_bar").category(GAME).description("Barra de jefe con cuenta atrás del evento o de la fase.")
                .param("text", STRING, "-", "Texto (por defecto, el nombre del evento)")
                .param("color", STRING, "red", "pink, blue, red, green, yellow, purple, white")
                .build());
        list.add(s("damage_area").category(GAME).description("Daño en un radio con un tipo de daño propio.")
                .param("damage_type", IDENTIFIER, "liveevents:stellar_burn", "Tipo de daño")
                .param("amount", RANGE, "2", "Daño (medios corazones)", 0, 1000)
                .param("radius", NUMBER, "$radius", "Radio (celestial: toda la dimensión)", 0, 4096)
                .param("interval", DURATION, "1s", "Cada cuánto se aplica")
                .build());
        list.add(s("status_effect").category(GAME).description("Aplica un efecto de estado vanilla o del mod.")
                .param("effect", IDENTIFIER, "", "Id del efecto")
                .param("amplifier", NUMBER, "0", "Nivel - 1", 0, 9)
                .param("duration", DURATION, "10s", "Duración del efecto")
                .param("radius", NUMBER, "$radius", "Radio", 0, 4096)
                .param("interval", DURATION, "-", "Si se indica, se renueva cada intervalo")
                .build());
        list.add(s("ignite").category(GAME).description("Prende fuego a las entidades afectadas.")
                .param("seconds", NUMBER, "4", "Segundos ardiendo", 0, 60)
                .param("radius", NUMBER, "$radius", "Radio", 0, 4096)
                .param("interval", DURATION, "2s", "Cada cuánto")
                .build());
        list.add(s("knockback").category(GAME).description("Empujón radial desde el centro.")
                .param("strength", RANGE, "1", "Fuerza", 0, 10)
                .param("radius", NUMBER, "$radius", "Radio", 0, 1024)
                .param("random", BOOLEAN, "false", "Dirección aleatoria (pérdida de equilibrio)")
                .param("interval", DURATION, "-", "Si se indica, se repite")
                .build());
        list.add(s("gravity_pull").category(GAME).description("Atrae a las entidades hacia el centro (agujero negro).")
                .param("strength", RANGE, "0.1", "Aceleración por tick", 0, 2)
                .param("radius", NUMBER, "$radius", "Radio", 0, 1024)
                .build());
        list.add(s("gaze_check").category(GAME).description("Detecta quién mira al ancla y le aplica módulos extra.")
                .param("cone_deg", NUMBER, "20", "Semiángulo del cono", 1, 90)
                .param("then", MODULES, "", "Módulos que se aplican a quien mira")
                .build());
        list.add(s("lightning_storm").category(GAME).description("Rayos cerca de los jugadores expuestos, con tope global.")
                .param("max", NUMBER, "12", "Rayos como máximo (además del tope del servidor)", 0, 200)
                .param("interval", DURATION, "1.5s", "Tiempo medio entre rayos")
                .param("distance", NUMBER, "24", "Distancia máxima al jugador", 4, 128)
                .param("fire", BOOLEAN, "false", "Los rayos prenden fuego")
                .build());
        list.add(s("weather").category(GAME).description("Cambia el clima temporalmente y lo restaura al terminar.")
                .param("set", STRING, "clear", "clear, rain o thunder")
                .build());

        // ---------------------------------------------------------------- bloques (diario)
        list.add(s("block_transform").category(BLOCKS).description("Sustituye bloques de la superficie según una paleta. Queda en el diario.")
                .param("palette", IDENTIFIER, "liveevents:scorched", "Paleta de transformación")
                .param("chance", RANGE, "0.15", "Probabilidad por columna", 0, 1)
                .param("radius", NUMBER, "$radius", "Radio", 0, 512)
                .param("per_second", NUMBER, "400", "Columnas por segundo", 1, 20000)
                .build());
        list.add(s("melt").category(BLOCKS).description("Derrite nieve y hielo de la superficie. Queda en el diario.")
                .param("radius", NUMBER, "$radius", "Radio", 0, 512)
                .param("per_second", NUMBER, "400", "Columnas por segundo", 1, 20000)
                .build());
        list.add(s("crater").category(BLOCKS).description("Abre un cráter con ruido determinista. Queda en el diario.")
                .param("radius", NUMBER, "6", "Radio del cráter", 1, 48)
                .param("depth", NUMBER, "0.6", "Profundidad relativa al radio", 0.1, 2)
                .param("rim", IDENTIFIER, "minecraft:basalt", "Bloque del borde")
                .param("core", IDENTIFIER, "minecraft:magma_block", "Bloque del fondo")
                .build());
        list.add(s("fire_spread").category(BLOCKS).description("Coloca fuego de forma controlada. Queda en el diario.")
                .param("count", NUMBER, "20", "Fuegos como máximo", 0, 2000)
                .param("radius", NUMBER, "16", "Radio", 0, 512)
                .build());
        list.add(s("crack").category(BLOCKS).description("Grietas a lo largo de una línea de falla determinista. Queda en el diario.")
                .param("length", NUMBER, "96", "Longitud en bloques", 4, 1024)
                .param("width", NUMBER, "2", "Anchura", 1, 8)
                .param("depth", NUMBER, "6", "Profundidad", 1, 32)
                .param("branches", NUMBER, "3", "Ramas", 0, 16)
                .build());

        // ---------------------------------------------------------------- compuestos
        list.add(b("meteor_impacts").category(COMPOSITE).description("Lluvia de meteoritos: N impactos deterministas con aviso en el suelo, estela, cráter, daño, empujón y fuego.")
                .param("count", NUMBER, "40", "Número de impactos", 1, 400)
                .param("radius", NUMBER, "$radius", "Radio alrededor del centro", 8, 1024)
                .param("crater_radius", RANGE, "{\"from\":2,\"to\":5}", "Radio de cada cráter", 1, 24)
                .param("damage", NUMBER, "8", "Daño en el centro del impacto", 0, 200)
                .param("blast_radius", NUMBER, "6", "Radio de daño y empujón", 0, 48)
                .param("warning", DURATION, "1.5s", "Aviso en el suelo antes del impacto")
                .param("fire", BOOLEAN, "true", "Pone fuego alrededor")
                .param("first_big", BOOLEAN, "false", "El primer meteorito es un bólido grande que se parte")
                .build());
        list.add(b("seismic_wave").category(COMPOSITE).description("Terremoto: ondas P y S con retraso según la distancia al epicentro, náusea, empujones y desprendimientos.")
                .param("amplitude", RANGE, "1", "Amplitud del temblor en grados", 0, 5)
                .param("radius", NUMBER, "$radius", "Radio de efecto", 8, 2048)
                .param("p_speed", NUMBER, "120", "Velocidad de la onda P (bloques/s)", 1, 2000)
                .param("s_speed", NUMBER, "60", "Velocidad de la onda S (bloques/s)", 1, 2000)
                .param("nausea", BOOLEAN, "true", "Náusea cerca del epicentro")
                .param("drop_blocks", BOOLEAN, "true", "Hace caer grava, arena y espeleotemas sin soporte")
                .build());
        return list;
    }
}
