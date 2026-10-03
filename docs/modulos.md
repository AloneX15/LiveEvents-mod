# Referencia de módulos

> Generado a partir del catálogo de módulos (`BuiltinModules`). No editar a mano: `./gradlew :26.3:test -PgenerateDocs`.

Un evento combina módulos sobre una línea de tiempo. Cada módulo tiene un **tipo** y unos **parámetros**. Todos admiten además los [parámetros comunes](#parámetros-comunes).

**Lado:** S = servidor (efectos de juego, bloques), C = cliente (visuales y sonido), S+C = ambos.

**Tipos de valor:** número (admite `$variable` y expresiones como `$radius * 0.5`), rango (número, `[a, b]` o `{"from": a, "to": b}`, interpolado con la curva), duración (`"8.1s"`, `"20t"`, `"2m"`, `"3d"`), color (`"#RRGGBB"` o `"$nombre"` de la paleta).

## Índice

- **Cielo:** [`sky_tint`](#sky_tint) · [`sky_blackout`](#sky_blackout) · [`celestial_body`](#celestial_body) · [`star_point`](#star_point) · [`sky_shatter`](#sky_shatter) · [`atmo_rings`](#atmo_rings) · [`plasma_shell`](#plasma_shell) · [`aurora`](#aurora) · [`nebula`](#nebula) · [`pulsar`](#pulsar) · [`lens_flare`](#lens_flare) · [`shadow_whip`](#shadow_whip) · [`time_override`](#time_override) · [`artificial_day`](#artificial_day)
- **Pantalla:** [`flash`](#flash) · [`whiteout`](#whiteout) · [`light_cut`](#light_cut) · [`chromatic_glitch`](#chromatic_glitch) · [`bloom`](#bloom) · [`screen_overlay`](#screen_overlay) · [`light_pulse`](#light_pulse)
- **Cámara:** [`camera_shake`](#camera_shake)
- **Audio:** [`sound`](#sound) · [`sound_loop`](#sound_loop) · [`audio_duck`](#audio_duck)
- **Mundo (visual):** [`shockwave`](#shockwave) · [`particle_emitter`](#particle_emitter) · [`meteor_trail`](#meteor_trail) · [`ash_fall`](#ash_fall) · [`debris`](#debris)
- **Juego:** [`chat_broadcast`](#chat_broadcast) · [`title`](#title) · [`boss_bar`](#boss_bar) · [`damage_area`](#damage_area) · [`status_effect`](#status_effect) · [`ignite`](#ignite) · [`knockback`](#knockback) · [`gravity_pull`](#gravity_pull) · [`gaze_check`](#gaze_check) · [`lightning_storm`](#lightning_storm) · [`weather`](#weather)
- **Bloques:** [`block_transform`](#block_transform) · [`melt`](#melt) · [`crater`](#crater) · [`fire_spread`](#fire_spread) · [`crack`](#crack)
- **Compuestos:** [`meteor_impacts`](#meteor_impacts) · [`seismic_wave`](#seismic_wave)

## Cielo

### sky_tint

`liveevents:sky_tint` · C

Tiñe el cielo y la niebla con un color.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `color` | color | **obligatorio** | Color del tinte |
| `strength` | rango | `0.6` | Intensidad (0-1) (0–1) |
| `fade` | duración | `1s` | Fundido de entrada y salida |
| `horizon` | color | `-` | Color opcional del horizonte (degradado) |

### sky_blackout

`liveevents:sky_blackout` · C

Apaga las estrellas y oscurece cielo y niebla de golpe.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `strength` | número | `0.95` | Oscuridad (0-1) (0–1) |

### celestial_body

`liveevents:celestial_body` · C

Cuerpo celeste anclado a la bóveda: estrella, sol demonio o púlsar. Se deforma, parpadea, lanza llamaradas y puede colapsar.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `shape` | texto | `star` | star, boiling (mercurio hirviendo) o sun |
| `color` | color | `#FFFFFF` | Color principal |
| `core` | color | `-` | Color del núcleo |
| `corona` | color | `-` | Color de la corona |
| `size_deg` | rango | `2` | Tamaño aparente en grados (0.1–120) |
| `flicker_hz` | rango | `0` | Parpadeo en Hz (limitado por accesibilidad si es grande) (0–60) |
| `flares` | booleano | `false` | Llamaradas asimétricas |
| `collapse` | objeto | `-` | {at, duration, curve}: colapso hasta desaparecer |

### star_point

`liveevents:star_point` · C

Punto estelar brillante con halo (versión simple de celestial_body).

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `color` | color | `#FFFFFF` | Color |
| `size_deg` | rango | `1` | Tamaño aparente (0.1–30) |

### sky_shatter

`liveevents:sky_shatter` · C

Rompe la bóveda en fragmentos con grietas luminosas que se extienden desde el ancla.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `shards` | número | `200` | Número de fragmentos (8–600) |
| `crack_color` | color | `#00FFFF` | Color de las grietas |
| `spread` | duración | `1.5s` | Tiempo que tardan las grietas en cubrir el cielo |

### atmo_rings

`liveevents:atmo_rings` · C

Anillos de vapor que salen del ancla y recorren la bóveda.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `color` | color | `#FFFFFF` | Color |
| `interval` | duración | `2.5s` | Tiempo entre anillos |
| `speed_deg` | número | `45` | Grados por segundo (1–360) |
| `distortion` | número | `0.5` | Distorsión al pasar (0-1) (0–1) |

### plasma_shell

`liveevents:plasma_shell` · C

Esfera de plasma con filamentos que crece desde el ancla.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `colors` | lista | `["#FFFFFF", "#FF4500"]` | Colores por los que pasa |
| `coverage` | rango | `{"from":0.05,"to":0.7}` | Fracción de la bóveda cubierta (0–1) |
| `turbulence` | número | `1` | Turbulencia del ruido (0–4) |

### aurora

`liveevents:aurora` · C

Cortinas de aurora desde los horizontes norte y sur.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `colors` | lista | `["#39FF14", "#DC143C"]` | Colores |
| `intensity` | rango | `0.7` | Intensidad (0-1) (0–1) |
| `speed` | número | `1` | Velocidad de las cortinas (0–5) |

### nebula

`liveevents:nebula` · C

Nebulosa por capas. Puede quedarse como secuela persistente.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `colors` | lista | `["#FF0000", "#00CED1", "#C8B400"]` | Colores de las capas |
| `span_deg` | número | `180` | Anchura en grados de acimut (10–360) |
| `opacity` | rango | `0.6` | Opacidad (0–1) |

### pulsar

`liveevents:pulsar` · C

Punto que parpadea y haces de faro que giran (con límites de accesibilidad).

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `color` | color | `#7DF9FF` | Color |
| `hz` | número | `30` | Parpadeo del punto (solo área pequeña) (0–60) |
| `beam_rpm` | número | `40` | Vueltas por minuto de los haces (0–180) |

### lens_flare

`liveevents:lens_flare` · C

Flares anamórficos horizontales desde el ancla.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `colors` | lista | `["#7DF9FF", "#FF00FF"]` | Colores |
| `intensity` | rango | `0.8` | Intensidad (0–1) |

### shadow_whip

`liveevents:shadow_whip` · C

Mueve el ángulo del sol para que giren las sombras. Solo se nota con shaders; sin ellos es un parpadeo de luz.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `speed` | número | `1` | Velocidad (0–10) |

### time_override

`liveevents:time_override` · S+C

Fuerza la noche (o la oscuridad) solo en la vista del cliente. La hora del servidor no cambia.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `mode` | texto | `night` | night o dark |
| `fade` | duración | `3s` | Fundido |

### artificial_day

`liveevents:artificial_day` · S+C

Cliente: ilumina el mundo como de día. Servidor: los no-muertos expuestos arden.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `color` | color | `#FFF6E0` | Color de la luz |
| `burn_undead` | booleano | `true` | Los no-muertos bajo el cielo arden |

## Pantalla

### flash

`liveevents:flash` · C

Destello a pantalla completa, limitado por la regla de destellos.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `color` | color | `#FFFFFF` | Color |
| `intensity` | número | `1` | Pico (0-1) (0–1) |
| `fade` | duración | `0.6s` | Caída |

### whiteout

`liveevents:whiteout` · C

Sobreexposición a blanco con curva de pico y sostenido.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `peak` | número | `0.85` | Pico (0-1) (0–1) |
| `peak_time` | duración | `1s` | Duración del pico |
| `sustain` | número | `0.35` | Nivel sostenido (0–1) |
| `color` | color | `#FFFFFF` | Color |

### light_cut

`liveevents:light_cut` · C

Cambia el color del cielo y de la luz a golpes, sin interpolar (o con fundido en modo seguro).

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `cuts` | cortes | **obligatorio** | Lista de [tiempo, color] |
| `mode` | texto | `cut` | cut o fade |
| `fade` | duración | `1s` | Fundido en modo fade |
| `strength` | número | `0.45` | Intensidad del tinte (0–1) |

### chromatic_glitch

`liveevents:chromatic_glitch` · C

Aberración cromática, separación RGB y franjas desplazadas.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `strength` | número | `0.8` | Intensidad (0-1) (0–1) |
| `slices` | booleano | `true` | Franjas horizontales desplazadas |

### bloom

`liveevents:bloom` · C

Bloom falso: resplandor alrededor de las fuentes brillantes del evento.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `intensity` | rango | `1` | Intensidad (1 = 100 %) (0–3) |

### screen_overlay

`liveevents:screen_overlay` · C

Viñeta o tinte en pantalla (radiación, calor).

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `color` | color | `#000000` | Color |
| `vignette` | rango | `0.4` | Cierre de la viñeta (0-1) (0–1) |
| `tint` | rango | `0` | Tinte uniforme (0-1) (0–1) |
| `pulse_hz` | número | `0` | Pulso (limitado a 3 Hz) (0–3) |

### light_pulse

`liveevents:light_pulse` · C

Iluminación dinámica simulada (pulso de luz).

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `color` | color | `#FFAA55` | Color |
| `intensity` | rango | `0.5` | Intensidad (0–1) |
| `hz` | número | `1` | Frecuencia (limitada a 3 Hz) (0–3) |

## Cámara

### camera_shake

`liveevents:camera_shake` · C

Temblor de cámara con ruido suave; la amplitud baja con la distancia.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `amplitude` | rango | `0.2` | Amplitud en grados (0–5) |
| `frequency` | número | `8` | Frecuencia del ruido (0.1–40) |
| `axis` | texto | `both` | both, vertical o horizontal |
| `falloff` | número | `0` | Radio en bloques a partir del cual se atenúa (0 = sin atenuación) (0–4096) |

## Audio

### sound

`liveevents:sound` · C

Sonido puntual con retraso por distancia y atenuación.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `sound` | id | **obligatorio** | Id del sonido |
| `volume` | número | `1` | Volumen (0–4) |
| `pitch` | número | `1` | Tono (0.5–2) |
| `position` | texto | `listener` | listener, center o anchor |
| `delay_by_distance` | booleano | `true` | Llega más tarde cuanto más lejos |

### sound_loop

`liveevents:sound_loop` · C

Sonido repetido con curvas de volumen, tono o ritmo.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `sound` | id | **obligatorio** | Id del sonido |
| `volume` | rango | `1` | Volumen (0–4) |
| `pitch` | rango | `1` | Tono (0.5–2) |
| `bpm` | rango | `60` | Golpes por minuto (1–600) |

### audio_duck

`liveevents:audio_duck` · C

Baja la música del juego para crear silencios.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `level` | número | `0.1` | Nivel al que baja (0-1) (0–1) |

## Mundo (visual)

### shockwave

`liveevents:shockwave` · C

Anillo que se expande desde el centro, con polvo al tocar el suelo.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `color` | color | `#FFE0B0` | Color |
| `radius` | rango | `{"from":0,"to":64}` | Radio en bloques (0–1024) |

### particle_emitter

`liveevents:particle_emitter` · C

Emisor genérico de partículas vanilla.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `particle` | id | `minecraft:ash` | Id de la partícula |
| `rate` | rango | `40` | Partículas por segundo (antes del LOD) (0–2000) |
| `radius` | número | `24` | Radio de emisión alrededor del jugador o del centro (0–256) |
| `around` | texto | `player` | player o center |
| `height` | número | `12` | Altura sobre el suelo (-64–256) |

### meteor_trail

`liveevents:meteor_trail` · C

Estrellas fugaces y bólidos solo visuales.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `rate` | rango | `1` | Estelas por segundo (0–30) |
| `color` | color | `#FF7A1A` | Color de la estela |
| `core` | color | `#FFF4D6` | Color del núcleo |

### ash_fall

`liveevents:ash_fall` · C

Ceniza que cae alrededor del jugador.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `rate` | rango | `60` | Partículas por segundo (0–2000) |

### debris

`liveevents:debris` · C

Escombros que saltan desde el centro.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `rate` | rango | `30` | Partículas por segundo (0–2000) |
| `block` | id | `minecraft:stone` | Bloque de las partículas |

## Juego

### chat_broadcast

`liveevents:chat_broadcast` · S

Mensaje en el chat a los jugadores afectados.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `message` | texto | **obligatorio** | Texto o clave de traducción |

### title

`liveevents:title` · S

Título y subtítulo en pantalla (oculto en cinematic salvo force).

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `title` | texto | **obligatorio** | Texto o clave de traducción |
| `subtitle` | texto | `-` | Subtítulo |
| `force` | booleano | `false` | Mostrar también en cinematic |

### boss_bar

`liveevents:boss_bar` · S

Barra de jefe con cuenta atrás del evento o de la fase.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `text` | texto | `-` | Texto (por defecto, el nombre del evento) |
| `color` | texto | `red` | pink, blue, red, green, yellow, purple, white |

### damage_area

`liveevents:damage_area` · S

Daño en un radio con un tipo de daño propio.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `damage_type` | id | `liveevents:stellar_burn` | Tipo de daño |
| `amount` | rango | `2` | Daño (medios corazones) (0–1000) |
| `radius` | número | `$radius` | Radio (celestial: toda la dimensión) (0–4096) |
| `interval` | duración | `1s` | Cada cuánto se aplica |

### status_effect

`liveevents:status_effect` · S

Aplica un efecto de estado vanilla o del mod.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `effect` | id | **obligatorio** | Id del efecto |
| `amplifier` | número | `0` | Nivel - 1 (0–9) |
| `duration` | duración | `10s` | Duración del efecto |
| `radius` | número | `$radius` | Radio (0–4096) |
| `interval` | duración | `-` | Si se indica, se renueva cada intervalo |

### ignite

`liveevents:ignite` · S

Prende fuego a las entidades afectadas.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `seconds` | número | `4` | Segundos ardiendo (0–60) |
| `radius` | número | `$radius` | Radio (0–4096) |
| `interval` | duración | `2s` | Cada cuánto |

### knockback

`liveevents:knockback` · S

Empujón radial desde el centro.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `strength` | rango | `1` | Fuerza (0–10) |
| `radius` | número | `$radius` | Radio (0–1024) |
| `random` | booleano | `false` | Dirección aleatoria (pérdida de equilibrio) |
| `interval` | duración | `-` | Si se indica, se repite |

### gravity_pull

`liveevents:gravity_pull` · S

Atrae a las entidades hacia el centro (agujero negro).

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `strength` | rango | `0.1` | Aceleración por tick (0–2) |
| `radius` | número | `$radius` | Radio (0–1024) |

### gaze_check

`liveevents:gaze_check` · S

Detecta quién mira al ancla y le aplica módulos extra.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `cone_deg` | número | `20` | Semiángulo del cono (1–90) |
| `then` | módulos | **obligatorio** | Módulos que se aplican a quien mira |

### lightning_storm

`liveevents:lightning_storm` · S

Rayos cerca de los jugadores expuestos, con tope global.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `max` | número | `12` | Rayos como máximo (además del tope del servidor) (0–200) |
| `interval` | duración | `1.5s` | Tiempo medio entre rayos |
| `distance` | número | `24` | Distancia máxima al jugador (4–128) |
| `fire` | booleano | `false` | Los rayos prenden fuego |

### weather

`liveevents:weather` · S

Cambia el clima temporalmente y lo restaura al terminar.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `set` | texto | `clear` | clear, rain o thunder |

## Bloques

### block_transform

`liveevents:block_transform` · S

Sustituye bloques de la superficie según una paleta. Queda en el diario.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `palette` | id | `liveevents:scorched` | Paleta de transformación |
| `chance` | rango | `0.15` | Probabilidad por columna (0–1) |
| `radius` | número | `$radius` | Radio (0–512) |
| `per_second` | número | `400` | Columnas por segundo (1–20000) |

### melt

`liveevents:melt` · S

Derrite nieve y hielo de la superficie. Queda en el diario.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `radius` | número | `$radius` | Radio (0–512) |
| `per_second` | número | `400` | Columnas por segundo (1–20000) |

### crater

`liveevents:crater` · S

Abre un cráter con ruido determinista. Queda en el diario.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `radius` | número | `6` | Radio del cráter (1–48) |
| `depth` | número | `0.6` | Profundidad relativa al radio (0.1–2) |
| `rim` | id | `minecraft:basalt` | Bloque del borde |
| `core` | id | `minecraft:magma_block` | Bloque del fondo |

### fire_spread

`liveevents:fire_spread` · S

Coloca fuego de forma controlada. Queda en el diario.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `count` | número | `20` | Fuegos como máximo (0–2000) |
| `radius` | número | `16` | Radio (0–512) |

### crack

`liveevents:crack` · S

Grietas a lo largo de una línea de falla determinista. Queda en el diario.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `length` | número | `96` | Longitud en bloques (4–1024) |
| `width` | número | `2` | Anchura (1–8) |
| `depth` | número | `6` | Profundidad (1–32) |
| `branches` | número | `3` | Ramas (0–16) |

## Compuestos

### meteor_impacts

`liveevents:meteor_impacts` · S+C

Lluvia de meteoritos: N impactos deterministas con aviso en el suelo, estela, cráter, daño, empujón y fuego.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `count` | número | `40` | Número de impactos (1–400) |
| `radius` | número | `$radius` | Radio alrededor del centro (8–1024) |
| `crater_radius` | rango | `{"from":2,"to":5}` | Radio de cada cráter (1–24) |
| `damage` | número | `8` | Daño en el centro del impacto (0–200) |
| `blast_radius` | número | `6` | Radio de daño y empujón (0–48) |
| `warning` | duración | `1.5s` | Aviso en el suelo antes del impacto |
| `fire` | booleano | `true` | Pone fuego alrededor |
| `first_big` | booleano | `false` | El primer meteorito es un bólido grande que se parte |

### seismic_wave

`liveevents:seismic_wave` · S+C

Terremoto: ondas P y S con retraso según la distancia al epicentro, náusea, empujones y desprendimientos.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `amplitude` | rango | `1` | Amplitud del temblor en grados (0–5) |
| `radius` | número | `$radius` | Radio de efecto (8–2048) |
| `p_speed` | número | `120` | Velocidad de la onda P (bloques/s) (1–2000) |
| `s_speed` | número | `60` | Velocidad de la onda S (bloques/s) (1–2000) |
| `nausea` | booleano | `true` | Náusea cerca del epicentro |
| `drop_blocks` | booleano | `true` | Hace caer grava, arena y espeleotemas sin soporte |

## Parámetros comunes

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `from` | duración | `inicio de la fase` | Momento del evento en que empieza |
| `to` | duración | `fin de la fase` | Momento del evento en que termina |
| `at` | duración | `-` | Momento puntual (sustituye a from) |
| `duration` | duración | `-` | Duración a partir de at |
| `curve` | texto | `linear` | Curva de los rangos: linear, ease_in, ease_out, ease_in_out, pulse, step |
| `priority` | número | `0` | Prioridad al mezclar con otros eventos |
| `profiles` | objeto | `-` | Parámetros distintos por perfil, p. ej. {"safe": {"strength": 0.2}} |
| `requires_sky` | booleano | `false` | Solo afecta a quien está bajo el cielo |

---

Creado por **TakumiStudios**.
