# LiveEvents Mod: documento de diseño (Fabric · Minecraft 26.x+)

> **Estado:** Diseño conceptual. Todavía no hay código.
> **Nombre:** LiveEvents Mod · **Autor:** TakumiStudios · **modid:** `liveevents` · **Paquete Java:** `com.takumistudios.liveevents`
> **Alcance actual:** solo eventos y sus efectos sobre el mundo. **El loot queda fuera del alcance.**

---

## 0. Índice

1. Visión y requisitos
2. Verificación del plan anterior (correcciones)
3. Plataforma objetivo
4. Arquitectura general
5. Modelo de evento: línea de tiempo y módulos
6. Catálogo y guiones de eventos (formato de guion, Supernova, Meteoritos, Terremoto)
7. Sistema visual
8. Efectos sobre el mundo
9. Red y sincronización
10. Permisos (LuckPerms)
11. Comandos
12. Configuración
13. Compatibilidad con otros mods
14. Rendimiento
15. API pública para otros mods
16. Estructura del proyecto
17. Hoja de ruta
18. Riesgos
19. Decisiones pendientes
20. Creación de eventos por usuarios
21. Operación y robustez
22. Seguridad
23. Pruebas y calidad
24. Experiencia del jugador: casos límite
25. Herramientas para series y directos
26. Localización
27. Publicación, licencia y recursos

---

## 1. Visión y requisitos

Un mod que permite a los administradores lanzar **eventos cósmicos y naturales en directo** (supernovas, lluvias de meteoritos, terremotos...) con un apartado visual espectacular y efectos reales sobre el mundo y los jugadores. Está pensado para eventos de servidor, series y directos.

| # | Requisito | Implicación de diseño |
|---|-----------|-----------------------|
| R1 | Los eventos se activan **solo por acción de un administrador** | Nada es aleatorio por defecto. Todo se lanza con comandos y también funciona desde consola, bloques de comandos y funciones de datapack. |
| R2 | **100 % configurable** | Los eventos se definen con datos (JSON) a partir de módulos reutilizables, no se programan uno a uno. |
| R3 | **Visuales realistas que impresionen** | El renderizado se hace en el cliente con efectos propios, no solo con partículas vanilla. |
| R4 | **100 % optimizado** | El servidor manda en todo, el trabajo se reparte entre ticks con un presupuesto fijo, se usa LOD y los paquetes de red son mínimos. |
| R5 | **Integración con LuckPerms** | Se usa `fabric-permissions-api`, que LuckPerms implementa. |
| R6 | **Compatible con otros mods** | Pocos mixins, se respetan las protecciones de terreno y hay alternativas para Sodium e Iris. |
| R7 | **Fabric, Minecraft 26.x en adelante** | Mapeos oficiales de Mojang, Java moderno y una capa que aísla el código propio de cada versión. |

---

## 2. Verificación del plan anterior (correcciones)

La propuesta anterior tenía varios errores técnicos. Estas son las correcciones:

| # | Lo que decía el plan anterior | Problema | Corrección |
|---|-------------------------------|----------|------------|
| 1 | "Fabric **1.26.x**" | Desde 2026 Minecraft usa versiones por año: **26.1, 26.2...** No existe "1.26". | La versión objetivo es **Minecraft 26.1+**. |
| 2 | Paquete **C2S `EventActivationPacket`** (el cliente activa eventos) | **Fallo de seguridad.** Un cliente modificado podría lanzar eventos. | La activación es **solo por comando en el servidor**. No hay paquetes cliente→servidor para activar nada. |
| 3 | "Fabric Permissions API (nativo)" | No forma parte de Fabric API. Es una librería aparte de lucko. | Depender de **`fabric-permissions-api`**. Si LuckPerms está instalado, la gestiona él. Si no, se usa el nivel de OP. |
| 4 | "LuckPerms para Fabric (si existe)" | Sí existe. | Se usa de forma indirecta (ver §10). No se depende de la API de LuckPerms directamente. |
| 5 | `ChunkDataPacket.sendBatch(...)` | Ese método no existe. | Vanilla ya agrupa los cambios por sección en cada tick. Hay que limitar cuántos bloques se cambian por tick (§8.4). |
| 6 | `GameRenderEvent` para el temblor de cámara | No es un evento de Fabric API. | Usar un **mixin pequeño en `Camera`**, o un sistema de desplazamiento propio (§7.6). |
| 7 | `HudRenderCallback` | Está obsoleto en Fabric API moderno. | Usar **`HudElementRegistry`** (o lo que lo sustituya en 26.x). |
| 8 | `DamageSource.explosion` para la supernova | El daño ahora se define con datos (*damage types*). | Registrar tipos propios (`liveevents:stellar_burn`, `liveevents:meteor_impact`...) con sus *tags* y mensajes de muerte. |
| 9 | "500+ partículas" enviadas como efecto | Mandar partículas desde el servidor satura la red. Además, el motor vanilla tiene un límite de unas 16k partículas. | El servidor manda **un solo paquete** con la fase, la semilla y los parámetros. El cliente **simula y dibuja** todo por su cuenta. |
| 10 | `FakePlayer` para efectos visuales | No tiene sentido en este contexto. | Eliminado. |
| 11 | Hacer asíncronos los cambios de bloques | Modificar el mundo fuera del hilo del servidor corrompe datos. | Lo asíncrono se limita a E/S, carga de configuración y **precálculo de formas** (cráteres). Aplicar cambios siempre ocurre en el hilo del servidor. |
| 12 | Sin sincronización para quien entra a mitad de evento | Un jugador que entra o cambia de dimensión no vería nada. | Paquete de **estado completo** al entrar o cambiar de dimensión (§9.3). |
| 13 | Sin accesibilidad | Los destellos fuertes son un riesgo de fotosensibilidad. Es especialmente grave en directos. | Respetar las opciones vanilla de accesibilidad y añadir límites propios en el cliente (§7.7). |
| 14 | Se ignoraban las protecciones de terreno | En servidores con claims, el evento destruiría zonas protegidas. | Integración opcional con **Common Protection API** y lista de zonas excluidas (§13.3). |
| 15 | Cambios de bloques sin persistencia | Si el servidor se cae a mitad de evento, ya no se puede revertir. | El **diario de cambios** se guarda en disco y se puede recuperar al reiniciar (§8.5). |

---

## 3. Plataforma objetivo

| Elemento | Decisión | Nota |
|----------|----------|------|
| Minecraft | **26.1+** | Las versiones por año salen varias veces al año y cada una puede romper la API. |
| Loader | Fabric Loader (última versión) | |
| Mapeos | **Mojang oficiales** | A partir de 26.1 el juego ya no está ofuscado y Yarn queda abandonado. ⚠ *Confirmar con la plantilla oficial de Fabric para 26.x.* |
| Java | La que exija 26.x (se espera **Java 25**) | ⚠ *Confirmar en `fabric.mod.json` y en la plantilla.* |
| Build | Gradle + Fabric Loom | |
| Multiversión | **Stonecutter** (opcional, a partir de la fase 7, ver D5) | Permite compilar 26.1, 26.2, etc. desde un solo código fuente. |
| Dependencias obligatorias | Fabric API | |
| Dependencias incluidas (JiJ) | `fabric-permissions-api` | Es una librería muy pequeña y suele incluirse dentro del jar. |
| Dependencias opcionales | LuckPerms, Common Protection API, Mod Menu, librería de configuración | Si no están instaladas, el mod funciona igual. |
| Lado | **Cliente + servidor** (ver decisión D1) | |

> ⚠ Hay que **comprobar los nombres exactos de las API de Fabric en 26.x** antes de programar, sobre todo eventos de renderizado del mundo, HUD, payloads y ticks. Fabric API ha renombrado y rehecho varios de estos módulos en versiones recientes.

---

## 4. Arquitectura general

### 4.1 Principio rector: el servidor decide y el cliente dibuja

```
┌──────────────────────────── SERVIDOR (autoridad) ────────────────────────────┐
│                                                                              │
│  Comando /le start ──► PermissionGate ──► EventDirector                      │
│                                              │                               │
│                     ┌────────────────────────┼─────────────────────┐         │
│                     ▼                        ▼                     ▼         │
│              EventInstance            WorkScheduler          WorldJournal    │
│           (timeline, fase actual)   (presupuesto ms/tick)  (cambios + revert)│
│                     │                        │                     │         │
│                     │        módulos de servidor (daño, bloques,  │         │
│                     │        knockback, efectos, fuego...)  ◄──────┘         │
│                     ▼                                                        │
│              NetworkSync ── 1 paquete por cambio de fase / keyframe ──┐      │
└───────────────────────────────────────────────────────────────────────┼──────┘
                                                                        │
┌──────────────────────────── CLIENTE (presentación) ───────────────────▼──────┐
│  ClientEventState ──► VisualDirector ──► módulos visuales                    │
│  (réplica del estado)    (LOD, presupuesto,   (cielo, destello, onda de      │
│                          accesibilidad)        choque, partículas, sonido,   │
│                                                cámara, overlay, luz)         │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Consecuencias:**
- El servidor nunca manda partículas sueltas. Manda *"fase IMPACT de supernova#12 en (x,y,z), semilla S, t₀ = tick T"*.
- El cliente reproduce la misma secuencia de forma **determinista** a partir de esa semilla. Así todos los jugadores ven lo mismo y casi no se gasta red.
- La lógica del servidor (daño, bloques) **no depende** de que el cliente tenga el mod.

### 4.2 Componentes

| Componente | Lado | Responsabilidad |
|------------|------|-----------------|
| `EventRegistry` | Ambos | Carga las definiciones de evento (JSON) y los tipos de módulo. |
| `EventDirector` | Servidor | Crea, pausa, reanuda, cancela y lista instancias. Aplica los límites de concurrencia. |
| `EventInstance` | Servidor | Una ejecución concreta: definición, centro, dimensión, semilla, tick de inicio y fase actual. |
| `WorkScheduler` | Servidor | Cola de trabajo con un **presupuesto en ms por tick** (§14). |
| `WorldJournal` | Servidor | Guarda cada cambio de bloque de forma persistente para poder revertirlo. |
| `PermissionGate` | Servidor | Comprueba permisos con `fabric-permissions-api`. |
| `NetworkSync` | Servidor | Envía los paquetes de inicio, fase, estado completo y fin. |
| `ClientEventState` | Cliente | Copia ligera de los eventos activos en la dimensión del jugador. |
| `VisualDirector` | Cliente | Coordina los módulos visuales y aplica LOD, presupuesto y accesibilidad. |
| `RenderBackend` | Cliente | **Capa que aísla** el pipeline de render de cada versión de MC (§7.8). |
| `EventClock` | Ambos | Reloj del evento: el servidor manda latidos y el cliente corrige la deriva (§9.4). |
| `ProfileResolver` | Ambos | Calcula el perfil efectivo: el del comando, limitado por el máximo del servidor y el del cliente (§7.10). |
| `LayerMixer` | Cliente | Mezcla los efectos cuando hay varios eventos a la vez (§7.12). |
| `SkyScarManager` | Ambos | Guarda, caduca y sincroniza las secuelas persistentes del cielo. |
| `Scheduler` (programación) | Servidor | Eventos programados y secuencias (§25). Es distinto del `WorkScheduler`. |
| `Watchdog` | Servidor | Detiene los eventos si el servidor se satura (§21.4). |

---

## 5. Modelo de evento: línea de tiempo y módulos

**Esta es la decisión más importante para cumplir con "100 % configurable".**

Un evento no es una clase Java escrita a mano. Es una **definición de datos** que combina módulos reutilizables sobre una línea de tiempo.

### 5.1 Conceptos

- **Evento (definición):** un archivo JSON con su id, nombre, fases y parámetros por defecto.
- **Fase:** equivale a un **segmento del guion** (§6.1). Tiene un id, un inicio y un fin en **segundos con decimales** (por ejemplo `"8.1s"`). El mod los convierte a ticks y el cliente interpola entre ticks para que los cortes de 0,1 s sean precisos.
- **Perfil:** `cinematic`, `gameplay` o `safe` (§7.10). Cada módulo puede tener valores distintos según el perfil.
- **Paleta:** colores con nombre (`$cyan`, `$blood`...) que los módulos usan en lugar del hex directamente.
- **Módulo:** una pieza de comportamiento con parámetros (`sky_tint`, `shockwave`, `damage_area`...). Los módulos son de servidor, de cliente o de ambos.
- **Keyframe:** el momento de una fase en el que un módulo empieza o cambia sus parámetros, con curvas de interpolación (`linear`, `ease_in`, `ease_out`, `pulse`).
- **Variante:** un evento que hereda de otro y cambia algunos parámetros (`supernova_small` hereda de `supernova`).

### 5.2 Ejemplo de definición (esquema, no es código)

Fragmento de la Supernova (§6.3). Solo aparecen los dos primeros segmentos.

```json
{
  "id": "liveevents:supernova",
  "display_name": "Supernova de Betelgeuse",
  "scope": "celestial",
  "anchor": { "azimuth": 160, "altitude": 55 },
  "night_override": "visual",
  "default_profile": "gameplay",
  "palette": {
    "night": "#0A0E1A", "blood": "#8B0000", "cyan": "#00FFFF", "violet": "#8A2BE2",
    "white": "#FFFFFF", "gold": "#FFD700", "orange": "#FF4500", "pulsar": "#7DF9FF"
  },
  "phases": [
    { "id": "agony", "from": "0s", "to": "8s", "modules": [
        { "type": "sky_tint", "color": "$night", "fade": "1.5s" },
        { "type": "celestial_body", "shape": "boiling", "color": "$blood", "flicker_hz": [2, 6], "flares": true,
          "collapse": { "at": "7.0s", "duration": "1.0s", "curve": "ease_in" } },
        { "type": "camera_shake", "from": "3s", "to": "7s", "amplitude": { "from": 0.05, "to": 0.2 } },
        { "type": "sound_loop", "sound": "liveevents:supernova.heartbeat", "bpm": { "from": 60, "to": 140 } },
        { "type": "sky_blackout", "from": "7.3s", "to": "8.0s" },
        { "type": "audio_duck", "from": "7.3s", "to": "8.0s", "level": 0.05 }
    ]},
    { "id": "breakout", "from": "8s", "to": "20s", "modules": [
        { "type": "sky_shatter", "at": "8.0s", "shards": 200, "crack_color": "$cyan" },
        { "type": "light_cut", "cuts": [ ["8.0s", "$cyan"], ["8.1s", "$violet"], ["9.2s", "$cyan"], ["10.0s", "$violet"], ["11.5s", "$cyan"] ],
          "profiles": { "safe": { "mode": "fade", "fade": "1s" } } },
        { "type": "chromatic_glitch", "at": "10.0s", "duration": "0.6s", "strength": 0.8,
          "profiles": { "safe": { "strength": 0.2, "slices": false } } },
        { "type": "status_effect", "at": "8.1s", "effect": "liveevents:radiation_burn", "amplifier": 0, "duration": "20s", "requires_sky": true },
        { "type": "gaze_check", "from": "8.0s", "to": "8.5s", "cone_deg": 20,
          "then": [ { "type": "status_effect", "effect": "minecraft:blindness", "duration": "3s" },
                    { "type": "damage_area", "damage_type": "liveevents:stellar_burn", "amount": 2 } ] }
    ]}
  ],
  "aftermath": { "persistent": ["nebula", "pulsar"], "duration": "3d" }
}
```

- `"profiles"` sobrescribe los parámetros del módulo según el perfil activo.
- Los colores con `$` salen de la paleta y las expresiones como `$radius * 0.5` se evalúan **una sola vez** al iniciar la instancia. Nunca se recalculan en cada tick.
- `requires_sky: true` aplica el efecto solo a quien está bajo el cielo, lo que es realista y además más barato. Los jugadores en cuevas quedan protegidos.
- Los administradores pueden **crear eventos nuevos** combinando módulos sin tocar código.

### 5.2.1 Campos comunes de toda definición

| Campo | Por defecto | Qué controla |
|-------|-------------|--------------|
| `format_version` | — (obligatorio) | Versión del formato (§20.4) |
| `scope` | `localized` | `celestial` (toda la dimensión), `localized` (centro + radio) o `regional` |
| `allowed_dimensions` | `["minecraft:overworld"]` | Dimensiones donde se puede lanzar (§7.13) |
| `night_override` | `none` | `none`, `visual` (el cliente pinta la noche) o `require` (solo se puede lanzar de noche) |
| `clear_weather` | `false` | Despeja el cielo durante el evento y restaura el clima al terminar |
| `lethal` | `true` | Con `false`, el daño del evento **nunca baja a nadie de medio corazón** (§8.6) |
| `affects` | jugadores y mobs hostiles | Qué entidades reciben efectos (§8.3) |
| `world_mode` | el del servidor | `permanent`, `revert_on_end` o `revert_manual` (§8.5) |
| `cooldown` | `0s` | Tiempo mínimo entre dos lanzamientos del mismo evento |
| `default_profile` | el del servidor | Perfil si el comando no indica ninguno |
| `queue_policy` | `reject` | Qué hacer si ya hay un evento incompatible activo: `reject` o `queue` (§7.12) |
| `description` | — | Textos del guion: "El Cielo" y "El Caos" (§20.3) |

### 5.3 Catálogo de módulos (primera versión)

| Módulo | Lado | Qué hace |
|--------|------|----------|
| `sky_tint` | C | Tiñe el cielo y la niebla. |
| `star_point` | C | Dibuja un punto estelar brillante con halo y bloom falso. |
| `flash` | C | Destello a pantalla completa, limitado por accesibilidad. |
| `shockwave` | C | Anillo o esfera que se expande, con distorsión y polvo al tocar el suelo. |
| `particle_emitter` | C | Emisor genérico con forma, velocidad, color y vida configurables. |
| `meteor_trail` | C | Bólido con estela incandescente y humo. |
| `ash_fall` / `debris` | C | Ceniza o escombros que caen. |
| `sound` / `sound_loop` | C | Sonido 3D con atenuación, retardo por distancia y curvas de tono y volumen. |
| `camera_shake` | C | Temblor de cámara según la distancia. |
| `screen_overlay` | C | Viñeta o tinte (por ejemplo radiación) dibujado en el HUD. |
| `light_pulse` | C | Iluminación dinámica simulada. |
| `chat_broadcast` / `title` | S | Mensajes, títulos y barra de jefe con cuenta atrás. |
| `damage_area` | S | Daño en un radio con un *damage type* propio. |
| `status_effect` | S | Aplica efectos vanilla o del mod. |
| `ignite` | S | Prende fuego a las entidades. |
| `knockback` | S | Empujón radial. |
| `gravity_pull` | S | Atracción hacia un punto (agujero negro). |
| `block_transform` | S | Sustituye bloques según una paleta. Queda registrado en el diario. |
| `crater` | S | Abre un cráter con ruido determinista. Queda registrado en el diario. |
| `fire_spread` | S | Coloca fuego de forma controlada. Queda registrado en el diario. |
| `weather` | S | Cambia el clima temporalmente y lo restaura al final. |
| `time_override` | S+C | Oscurece el cielo o fuerza la noche solo durante el evento (eclipse). |

**Módulos añadidos para el guion de la Supernova y los borradores:**

| Módulo | Lado | Qué hace |
|--------|------|----------|
| `celestial_body` | C | Cuerpo celeste anclado a la bóveda (estrella o sol demonio). Se deforma con un shader de ruido, parpadea, lanza llamaradas y puede colapsar. Sustituye a `star_point`. |
| `sky_blackout` | C | Apaga las estrellas y oscurece el cielo y la niebla de golpe. |
| `sky_shatter` | C | Rompe la bóveda en fragmentos (Voronoi) con grietas luminosas. |
| `light_cut` | C | Cambia el color del cielo y de la iluminación del mundo **a golpes**, sin interpolar (o con fundido en modo seguro). |
| `whiteout` | C | Sobreexposición a blanco con una curva de exposición y valores por perfil. |
| `bloom` | C | Bloom falso: extrae las zonas brillantes y las difumina (§7.9). |
| `lens_flare` | C | Flares anamórficos horizontales con sprites. |
| `chromatic_glitch` | C | Aberración cromática, separación RGB y franjas desplazadas. |
| `atmo_rings` | C | Anillos de vapor que recorren la bóveda con distorsión. |
| `plasma_shell` | C | Esfera de plasma con filamentos (ruido en shader) y calidad por niveles. |
| `aurora` | C | Cortinas de aurora con ruido que se desplaza. |
| `nebula` | C | Nebulosa por capas. Puede quedarse como secuela persistente. |
| `pulsar` | C | Punto que parpadea y haces de faro que giran, con los límites de §7.7. |
| `shadow_whip` | C | Mueve el ángulo del sol para que giren las sombras. **Solo funciona con shaders.** |
| `audio_duck` | C | Baja la música y el ambiente del juego para crear silencios. |
| `artificial_day` | S+C | **Cliente:** ilumina el mundo como de día. **Servidor:** los no-muertos expuestos arden. La hora del servidor no cambia. |
| `gaze_check` | S | Detecta quién mira al ancla (producto escalar del vector de visión) y le aplica efectos extra. |
| `lightning_storm` | S | Rayos cerca de los jugadores expuestos, con tope global y respeto a las protecciones. |
| `melt` | S | Derrite la nieve y el hielo de la superficie. Queda registrado en el diario. |
| `crack` | S | Grietas a lo largo de una línea de falla determinista. Queda registrado en el diario. |

---

## 6. Catálogo y guiones de eventos

### 6.1 Formato de guion de evento (plantilla para todos los eventos)

Cada evento se diseña en **dos capas** que se escriben siempre juntas:

1. **Guion cinematográfico:** lo que ve y siente el jugador segundo a segundo, con la paleta en hex. Es la dirección de arte.
2. **Traducción técnica:** cómo se consigue cada segmento con los módulos del mod (§5.3), qué pasa en el juego y qué se hace en modo seguro y sin shaders.

Un guion sin traducción técnica no se puede implementar. Una traducción técnica sin guion da un evento sin alma.

**Plantilla:**

```markdown
## <Nombre del evento>

### Ficha
- id · duración total · ámbito (celeste / localizado / regional)
- Ancla: dónde ocurre (posición en la bóveda o en el mundo)
- Hora: noche / día / indiferente
- Perfiles: cinematic · gameplay · safe
- Paleta: nombre → #hex

### Guion
#### MM:SS – MM:SS | <Título del segmento>
- **El Cielo:** estado del cielo y colores.
- **El Caos:** lo que debe sentir el jugador.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Cielo | | |
| Mundo | | |
| Cámara / Post-FX | | |
| Audio | | |
| Juego (servidor) | | |
| Modo seguro | | |
| Alternativa (sin shaders / calidad baja) | | |

### Dirección de arte global
### Secuelas
### Viabilidad y ajustes
### Coste estimado
```

**Reglas del formato:**
- Cada segmento **debe** tener filas de **Audio** y **Juego**. El sonido sostiene la mitad del impacto, y los efectos en el juego son lo que diferencia un evento de un vídeo.
- Los tiempos van en **segundos con decimales**. El mod los convierte a ticks y el cliente interpola entre ticks, así que se pueden marcar cortes de 0,1 s.
- Todo color lleva **nombre y hex** en la paleta, para poder cambiarlo desde el JSON.
- Todo destello o parpadeo indica **frecuencia y tamaño en pantalla**, para poder comprobar la accesibilidad (§7.7).

### 6.2 Catálogo y prioridad

| Evento | Prioridad | Guion | Efecto en el mundo | Efecto en el jugador |
|--------|-----------|-------|--------------------|----------------------|
| **Supernova de Betelgeuse** | MVP | §6.3 ✅ | Superficie chamuscada, nieve derretida, rayos, nebulosa persistente en el cielo | Radiación, quemaduras, ceguera si se mira a la estrella |
| **Lluvia de meteoritos** | MVP | §6.4 (borrador) | Cráteres, fuego, roca de meteorito que se enfría | Daño por impacto, empujón, aturdimiento |
| **Terremoto** | MVP | §6.5 (borrador) | Grietas, desprendimientos, estalactitas que caen | Temblor, náusea, pérdida de equilibrio |
| Gran meteorito (impacto único) | Fase 2 | Pendiente | Cráter grande y onda expansiva | Onda de choque, ceguera por destello |
| Tormenta solar | Fase 2 | Pendiente | Auroras; brújulas y mapas alterados | Radiación por exposición |
| Eclipse total | Fase 2 | Pendiente | Oscuridad; opción de más mobs | Ambiental |
| Aurora boreal | Fase 2 | Pendiente | Ninguno (evento tranquilo para series) | Ninguno |
| Erupción volcánica | Fase 3 | Pendiente | Lava, ceniza, bombas volcánicas | Asfixia por ceniza, quemaduras |
| Agujero negro / singularidad | Fase 3 | Pendiente | Arranca y absorbe bloques (con tope) | Atracción gravitatoria |
| Lluvia ácida | Fase 3 | Pendiente | Corrosión de bloques y cultivos | Daño por exposición |
| Ventisca | Fase 3 | Pendiente | Nieve y hielo en la superficie | Congelación |
| Tornado | Fase 4 | Pendiente | Arrastre de entidades | Lanzamiento |
| Tsunami | Fase 4 ⚠ | Pendiente | Simular el agua es muy caro; necesita un diseño propio | Arrastre, ahogamiento |

> Los eventos marcados como "Pendiente" se escribirán con el formato de §6.1 **antes** de implementarlos.

---

### 6.3 Supernova de Betelgeuse

#### Ficha

| Campo | Valor |
|-------|-------|
| id | `liveevents:supernova` |
| Duración | **60 s** (1200 ticks) + secuelas persistentes |
| Ámbito | **Celeste:** afecta a toda la dimensión. Todos los jugadores lo ven a la vez, estén donde estén. |
| Ancla | Betelgeuse en la bóveda: acimut 160°, altura 55° (configurable) |
| Hora | Noche. Si se lanza de día, el cliente pinta el cielo de noche durante el evento (3 s de entrada y de salida). **La hora del servidor no cambia.** |
| Perfiles | `cinematic` (el guion tal cual, para grabar series) · `gameplay` (por defecto) · `safe` (§7.10) |

**Paleta**

| Nombre | Hex | Uso |
|--------|-----|-----|
| `night` | #0A0E1A | Cielo base |
| `blood` | #8B0000 | Betelgeuse agonizando |
| `cyan` | #00FFFF | Ruptura atmosférica |
| `violet` | #8A2BE2 | Ruptura atmosférica |
| `white` | #FFFFFF | Sol demonio, whiteout |
| `gold` | #FFD700 | Corona del sol demonio |
| `magenta` | #FF00FF *(propuesto)* | Horizonte químico, flares |
| `orange` | #FF4500 | Plasma |
| `rot_purple` | #5B2A6E *(propuesto)* | Plasma "púrpura podrido" |
| `aurora_green` | #39FF14 *(propuesto)* | Auroras |
| `crimson` | #DC143C *(propuesto)* | Auroras |
| `hydrogen` | #FF0000 | Nebulosa: venas de hidrógeno |
| `oxygen` | #00CED1 | Nebulosa: encajes de oxígeno |
| `sulfur` | #C8B400 *(propuesto)* | Nebulosa: azufre radiactivo |
| `pulsar` | #7DF9FF *(propuesto)* | Púlsar y flares azul eléctrico |

> Los colores marcados como *propuesto* no venían en el guion original y hay que confirmarlos.

#### Guion

##### 00:00 – 00:08 | La Agonía Sangrienta y el Vacío Espantoso

- **El Cielo:** Un firmamento nocturno azul noche casi negro (`night` #0A0E1A).
- **El Caos:** En pleno cielo, Betelgeuse no late: se convulsiona. Es una úlcera roja sangrienta (`blood` #8B0000) que se deforma como una gota de mercurio hirviendo. La estrella parpadea en pánico y escupe llamaradas asimétricas que desgarran las constelaciones vecinas. De repente, la gravedad gana: la estrella sufre un infarto masivo y se traga a sí misma en un segundo. La luz se apaga de golpe. El cielo queda en un silencio visual sofocante, una negrura muerta, artificial y aterradora que dura un palpitar violento.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Cielo | El cielo baja a `night` en 1,5 s y las estrellas pierden brillo. Betelgeuse es una esfera en la bóveda con un shader de ruido deformado ("mercurio hirviendo") en `blood` y un núcleo más caliente. Parpadea de forma irregular (2–6 Hz, área pequeña) y lanza llamaradas asimétricas (cintas de geometría) que tapan y deforman las estrellas vecinas. **7,0 s:** colapsa, se encoge hasta desaparecer en 1 s con aceleración y arrastra hacia dentro las estrellas cercanas. **7,3–8,0 s:** apagón; sin estrellas y el cielo casi negro. | `sky_tint`, `celestial_body`, `sky_blackout` |
| Mundo | Durante el apagón, la iluminación nocturna baja todavía más. | `light_cut` |
| Cámara / Post-FX | Temblor suave y lento que crece de 3 a 7 s ("la gravedad hace temblar la lente"). Pulso de distorsión de lente sincronizado con el latido. La viñeta se cierra. | `camera_shake`, `screen_overlay` |
| Audio | Latido sub-grave que acelera (60 → 140 ppm) y un gemido metálico de algo que se estira. En el colapso, una succión invertida. Luego **silencio total 0,7 s**: la música y el ambiente del juego bajan casi a cero. | `sound`, `sound_loop`, `audio_duck` |
| Juego | Nada dañino. Título opcional (oculto en `cinematic`). | `title` |
| Modo seguro | El parpadeo de la estrella baja a ≤ 2 Hz y el temblor se reduce. | — |
| Alternativa | En calidad baja, la estrella es una textura animada y las llamaradas son partículas. | — |

##### 00:08 – 00:20 | El Latigazo Violento y la Ruptura Atmosférica (Shock Breakout)

- **El Cielo:** ¡Implosión y estallido brutal!
- **El Caos:** La cúpula celeste se rompe en mil pedazos. Un láser de radiación y rayos X perfora la atmósfera como una lámina de cristal roto. El cielo no cambia de color suavemente: cambia **a golpes de luz**. En 0,1 s pasa del negro absoluto a un cian neón desquiciado (`cyan` #00FFFF) y a un violeta eléctrico que quema las retinas (`violet` #8A2BE2). La capa de ozono ionizada brilla como si toda la atmósfera ardiera en gas noble. Las sombras giran frenéticamente.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Cielo | **8,0 s:** la bóveda se rompe en ~200 fragmentos (Voronoi) con grietas luminosas `cyan` que se extienden desde el ancla en 1,5 s. Los fragmentos se separan un poco con paralaje. **Cortes de color sin transición:** 8,0 negro → `cyan`, 8,1 → `violet`, y más cortes en 9,2, 10,0 y 11,5 s. Un haz estrecho de rayos X atraviesa el cielo desde el ancla durante 0,5 s. El horizonte brilla con una banda de "gas noble ionizado" animada con ruido. | `sky_shatter`, `light_cut`, `particle_emitter`, `sky_tint` |
| Mundo | La luz del mundo cambia de color a la vez que el cielo (cian y violeta). **Con shaders (Iris):** el ángulo del sol se mueve de forma caótica y las sombras giran. **Sin shaders:** Minecraft no tiene sombras dinámicas, así que se sustituyen por parpadeos de luz sincronizados con los cortes. | `light_cut`, `shadow_whip` |
| Cámara / Post-FX | **Glitch cromático fuerte en 10,0 s** (0,6 s): separación RGB, franjas horizontales desplazadas y líneas de barrido. Sacudida fuerte en 8,0 s. El bloom empieza a subir. | `chromatic_glitch`, `camera_shake`, `bloom` |
| Audio | Cristal gigante que se rompe (con capas de hielo agrietándose), pitido agudo de rayos X (corto, para no cansar) y chisporroteo eléctrico. Cada corte de color lleva un golpe seco. | `sound` |
| Juego | **8,1 s, pulso de radiación:** `radiation_burn` I durante 20 s a quien esté bajo el cielo. **Quien esté mirando a Betelgeuse** (a menos de 20° del ancla) entre 8,0 y 8,5 s recibe además ceguera 3 s y 2 de daño `stellar_burn`. | `status_effect`, `gaze_check`, `damage_area` |
| Modo seguro | Los cortes pasan a ser fundidos de 1 s. El glitch baja al 20 % y sin franjas. Nunca hay destellos rojos. | — |
| Alternativa | Si el post-procesado no está disponible (por ejemplo, con Iris), la rotura del cielo se hace solo con geometría. | — |

> **Regla de los cortes:** como máximo **3 cortes por segundo** en total (§7.7). El "0,1 s" del guion se hace con un corte doble (8,0 y 8,1 s) seguido de una pausa de al menos 0,34 s.

##### 00:20 – 00:35 | El Apocalipsis de Luz: Un Sol Demonio en la Noche

- **El Cielo:** Blanco ciego (`white` #FFFFFF) y fuego dorado tóxico (`gold` #FFD700).
- **El Caos:** No hay noche: la noche ha sido asesinada. Un sol artificial, mil veces más furioso que el nuestro, se instala en medio del firmamento. La luz es tan brutal que la pantalla se quema en blanco puro (whiteout con aberración cromática extrema y lentes destrozadas por la radiación). Ondas de choque atmosféricas, como anillos de vapor supersónicos, se expanden desde el punto de la explosión y rizan el cielo. El horizonte se tiñe de rosa magenta químico y amarillo incandescente. Es una explosión nuclear a escala cósmica sobre nuestras cabezas.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Cielo | En el ancla aparece el **sol demonio**: núcleo `white`, corona `gold` y un tamaño aparente de ~15° (unas 30 lunas). Flares anamórficos horizontales en `pulsar` y `magenta`. Horizonte en degradado de `magenta` a amarillo incandescente. Cada ~2,5 s sale un **anillo de vapor** desde el ancla que recorre la bóveda y la riza. | `celestial_body`, `lens_flare`, `sky_tint`, `atmo_rings` |
| Mundo | **"No hay noche":** el mundo se ilumina como de día en el cliente, con luz blanca cálida. La hora del servidor no cambia. | `artificial_day` |
| Cámara / Post-FX | **Whiteout** según el perfil (tabla de abajo). Aberración cromática fuerte en los bordes y una textura de "lente quemada" (suciedad y rayas). Bloom al máximo. Los anillos distorsionan la imagen al pasar. **La intensidad depende de cuánto cielo ve la cámara** (§7.11): en una cueva solo se nota la luz que entra por la boca. | `whiteout`, `chromatic_glitch`, `bloom`, `atmo_rings` |
| Audio | Rugido grave sostenido y golpes de presión sincronizados con cada anillo. Tras el pico, un **pitido de tinnitus** mientras el resto del sonido baja (sensación de aturdimiento). | `sound_loop`, `sound`, `audio_duck` |
| Juego | **Quemaduras:** quien siga bajo el cielo más de 5 s sube a `radiation_burn` II y empieza a arder. **Los no-muertos expuestos arden**, igual que bajo el sol. **La nieve y el hielo de la superficie se derriten** (se registra en el diario). | `artificial_day`, `status_effect`, `ignite`, `melt` |
| Modo seguro | Whiteout máximo del 40 %. Sin aberración ni distorsión de anillos. | — |
| Alternativa | Sin post-procesado, el whiteout es una capa blanca semitransparente a pantalla completa. | — |

**Whiteout por perfil:**

| Perfil | Pico (20,0 s) | Sostenido (21,5–35 s) | HUD |
|--------|---------------|-----------------------|-----|
| `cinematic` | 100 % blanco durante 1,5 s | 70 % | Visible |
| `gameplay` | 85 % durante 1 s | 35 % | Siempre visible |
| `safe` | 40 %, con fundido de 1 s | 20 % | Siempre visible |

> 15 s de blanco puro dejarían ciegos a los jugadores en plena partida, con lava, mobs o caídas cerca. Por eso el blanco total solo se mantiene en el pico y solo en `cinematic`.

##### 00:35 – 00:48 | La Tormenta de Plasma y la Danza Macabra de Colores

- **El Cielo:** Un infierno en expansión, como un *time-lapse* frenético.
- **El Caos:** El blanco ciego se desgarra en una bola de fuego fuera de control. El plasma, a 30 000 km/s, devora el cielo nocturno. El color salta del blanco incandescente a un naranja volcánico (`orange` #FF4500) y a un púrpura podrido. La esfera no es lisa: tiene tentáculos de materia, filamentos que hierven y escupen radiación, y nubes de gas que parecen garras sobre la bóveda. Auroras gigantescas y deshilachadas brotan de los polos y se cruzan en el ecuador, en una tormenta magnética verde eléctrica y carmesí.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Cielo | Una **esfera de plasma** crece desde el ancla hasta cubrir ~70 % de la bóveda. Usa un shader de ruido turbulento con filamentos y "tentáculos" (ruido con crestas y dominio deformado) animado a velocidad de time-lapse, y pasa de blanco a `orange` y luego a `rot_purple`. **Auroras:** cortinas que salen de los horizontes norte y sur (Minecraft no tiene polos) y se cruzan en el cénit, en `aurora_green` y `crimson`. | `plasma_shell`, `aurora` |
| Mundo | La luz toma un tono naranja con parpadeo lento, y el suelo recibe un leve reflejo verde de las auroras. | `light_cut`, `aurora` |
| Cámara / Post-FX | El bloom baja poco a poco (200 % → 120 %). Distorsión de calor cerca del horizonte. | `bloom`, `screen_overlay` |
| Audio | Retumbo de plasma, crepitar, zumbido magnético y "silbidos" de aurora (sonidos tipo radio VLF que bajan de tono). Truenos cuando caen rayos. | `sound_loop`, `sound` |
| Juego | **Tormenta eléctrica:** caen rayos cerca de los jugadores que están bajo el cielo, con un tope global (12 por defecto). Se respetan las protecciones y se puede desactivar que prendan fuego. **Las brújulas giran sin control.** La radiación se renueva a nivel I. La superficie expuesta empieza a **chamuscarse** (paleta `scorched`, probabilidad 0,15 en `gameplay` y 0,35 en `cinematic`). | `lightning_storm`, `status_effect`, `block_transform` |
| Modo seguro | Los rayos respetan la opción vanilla "Ocultar destellos de rayos". Las auroras se mueven más despacio. | — |
| Alternativa | En calidad baja, el plasma se hace con esferas texturizadas superpuestas que se desplazan. | — |

##### 00:48 – 01:00 | La Cicatriz Eterna y el Corazón Poseído

- **El Cielo:** Una telaraña de gas venenoso y un cadáver que late.
- **El Caos:** La furia se disipa, pero la noche ya no volverá a ser la misma. El firmamento queda marcado por una cicatriz multicolor colosal que ocupa la mitad del horizonte: una nebulosa fantasmagórica con venas rojas de hidrógeno sangrante (`hydrogen` #FF0000), encajes turquesa de oxígeno destrozado (`oxygen` #00CED1) y nubes de azufre radiactivo. En su centro geométrico, un punto de luz azul eléctrico parpadea 30 veces por segundo y lanza haces de luz como un faro fuera de control: es el púlsar, un demonio atado en el centro de su propia destrucción.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Cielo | El plasma se disuelve y deja una **nebulosa** que ocupa media bóveda (~180° de acimut), hecha de varias capas con venas `hydrogen`, encajes `oxygen` y nubes `sulfur`. En el centro está el **púlsar**: un punto `pulsar` que parpadea a 30 Hz y dos haces de faro que giran. | `nebula`, `pulsar` |
| Mundo | La luz vuelve a ser de noche, aunque algo más clara por el brillo de la nebulosa. | `light_cut` |
| Cámara / Post-FX | Todo vuelve a la normalidad en 6 s. El púlsar deja flares anamórficos pequeños. | `bloom`, `lens_flare` |
| Audio | Viento que se apaga. **El púlsar suena:** un zumbido o tic a 30 Hz a bajo volumen, como en las grabaciones reales de púlsares. Vuelve la música del juego. | `sound_loop`, `audio_duck` |
| Juego | La radiación ya no se renueva y desaparece sola. Los incendios se apagan poco a poco. A los 60 s termina el evento y empiezan las secuelas. | — |
| Modo seguro | El púlsar late despacio (1 Hz) y sus haces nunca barren la cámara. | — |
| Alternativa | En calidad baja, la nebulosa es una textura estática. | — |

#### Dirección de arte global

| Lo que pide el guion | Cómo se implementa |
|----------------------|--------------------|
| Glitch cromático en 00:10 y 00:20 | Keyframes de `chromatic_glitch` en 10,0 s y 20,0 s |
| Temblor también durante el colapso, no solo en la explosión | `camera_shake` de baja amplitud de 3 a 7 s, con picos en 8,0 s y 20,0 s |
| Bloom al 200 % | `bloom` con intensidad 2,0. Minecraft no tiene HDR, así que el bloom es "falso": se extraen las zonas brillantes y se difuminan (§7.9) |
| Flares anamórficos horizontales en azul eléctrico y magenta | `lens_flare` con sprites estirados en horizontal, en `pulsar` y `magenta` |

#### Secuelas persistentes

- **La nebulosa y el púlsar se quedan en el cielo** después del evento. Duración configurable: **3 días de juego** por defecto, o `permanent`.
- Giran con las estrellas y solo se ven de noche (se desvanecen con la luz del día).
- El servidor guarda su estado y se lo envía a quien entra (`SkyScarState`, §9).
- Se gestionan con `/le scars list` y `/le scars clear`.
- *Opcional:* mientras dure la nebulosa, las noches son un poco menos oscuras.

#### Viabilidad y ajustes

| Elemento del guion | ¿Viable? | Cómo / ajuste |
|--------------------|----------|---------------|
| Estrella que se deforma como mercurio hirviendo | ✅ | Shader de ruido; textura animada en calidad baja |
| Llamaradas que desgarran las constelaciones | ✅ | Cintas de geometría que tapan y deforman las estrellas cercanas |
| Cielo que se rompe en mil pedazos | ✅ | Malla de fragmentos sobre la bóveda |
| Cambio de color en 0,1 s | ✅ con límite | Cortes duros, máximo 3 por segundo |
| Sombras que giran frenéticamente | ⚠ Solo con shaders | Moviendo el ángulo del sol. Sin shaders se sustituye por parpadeo de luz |
| Sombras dobles y triples | ❌ | Ni vanilla ni los shaderpacks tienen más de una fuente de sombras. Se puede aproximar alternando ángulos (solo en `cinematic`) o descartarlo |
| Whiteout de 15 s | ⚠ Injugable tal cual | Se resuelve con perfiles (tabla de whiteout) |
| Anillos supersónicos que rizan el cielo | ✅ | Geometría más distorsión por post-procesado |
| Plasma con tentáculos | ✅ coste alto | Shader con niveles de calidad |
| Auroras desde los polos | ✅ adaptado | Salen de los horizontes norte y sur |
| Cicatriz permanente | ✅ | Secuela persistente sincronizada |
| Púlsar a 30 parpadeos/s | ⚠ Con condiciones | Un punto pequeño sí. Unos haces que barrieran la pantalla a 30 Hz serían un estroboscopio a pantalla completa. Los haces giran, pero no cruzan la cámara más de 3 veces por segundo. El parpadeo sigue el tiempo real, no los fotogramas |
| Bloom al 200 % y flares anamórficos | ✅ aproximado | Bloom falso y flares con sprites |
| Sonido | ➕ No estaba en el guion | Añadido en cada segmento |
| Efectos en el juego (quemaduras...) | ➕ No estaban en el guion | Añadidos en cada segmento |

> **Nota de realismo:** una supernova real de Betelgeuse se vería como un punto muy brillante, más o menos como media luna, no como un sol. El guion se toma una licencia artística a propósito, y eso está bien para un evento. Aquí "realista" significa **calidad visual**, no exactitud astrofísica.

#### Coste estimado

- **Servidor:** casi nulo, porque lo visual lo hace el cliente. El gasto está en comprobar la radiación (cada 10 ticks), los rayos (con tope) y el derretido y chamuscado (con presupuesto).
- **Cliente:** el pico está entre 00:20 y 00:48, cuando coinciden el post-procesado y el shader de plasma. Al bajar la calidad se recorta primero el plasma y después la distorsión de los anillos.

---

### 6.4 Lluvia de meteoritos *(borrador, mismo formato)*

#### Ficha
`liveevents:meteor_shower` · **90 s** · ámbito **localizado** (centro + radio, 128 por defecto) · mejor de noche, aunque funciona de día · tres perfiles.

**Paleta:** `night` #0B1020 · `core` #FFF4D6 (núcleo del bólido) · `magnesium` #7CFF6B (destello verde, como en los bólidos reales) · `trail` #FF7A1A · `smoke` #3A3A3A · `crater_glow` #FF3B00.

##### 00:00 – 00:10 | El Presagio de Fuego
- **El Cielo:** La noche se espesa (`night`). Estrellas fugaces lejanas, finas e inofensivas, cada vez más seguidas.
- **El Caos:** Algo viene. Una chispa roja cruza el horizonte demasiado despacio y es demasiado grande. El cielo se llena de cicatrices de luz que no se apagan.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Cielo | Oscurecimiento y estrellas fugaces lejanas (solo visuales) | `sky_tint`, `meteor_trail` |
| Audio | Rumor grave lejano y un crepitar atmosférico muy bajo | `sound_loop` |
| Juego | Cuenta atrás opcional. El admin ve la zona de peligro | `title` |

##### 00:10 – 00:25 | La Primera Sangre
- **El Cielo:** Un bólido enorme cruza de horizonte a horizonte, con destellos verdes `magnesium`, y se parte en tres.
- **El Caos:** Un fragmento cae lejos. Primero el resplandor en el horizonte y un hongo de polvo. **Segundos después**, el estampido.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Cielo / Mundo | Bólido grande con estela incandescente, humo y fragmentación. Primer impacto dentro del radio | `meteor_trail`, `shockwave`, `debris` |
| Audio | Silbido que sube de tono al acercarse (Doppler simulado). **El estampido llega con retraso según la distancia** | `sound` |
| Juego | El primer impacto abre un cráter, aplica daño y empujón y pone fuego | `crater`, `damage_area`, `knockback`, `fire_spread` |

##### 00:25 – 01:05 | La Lluvia del Juicio
- **El Cielo:** Decenas de estelas a la vez. El cielo es una red de líneas incandescentes.
- **El Caos:** El suelo avisa: un círculo de brasas aparece 1,5 s antes de cada impacto. Corre. Detrás de ti, la tierra salta por los aires.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Cielo / Mundo | N meteoritos generados con la semilla (40 por defecto). Marcador de impacto 1,5 s antes. Cráteres, onda de polvo y escombros | `meteor_trail`, `particle_emitter`, `crater`, `debris` |
| Cámara / Post-FX | Sacudida según la distancia al impacto y un destello pequeño en los cercanos | `camera_shake`, `flash` |
| Audio | Capas cercana, media y lejana, con retraso por distancia (§7.4) | `sound` |
| Juego | Daño `meteor_impact`, empujón, fuego y lentitud breve a los cercanos. Los cráteres dejan roca de meteorito decorativa (basalto, roca negra, magma), **sin loot** | `damage_area`, `knockback`, `status_effect`, `crater`, `block_transform` |
| Modo seguro | Destellos de impacto suaves y temblor reducido | — |

##### 01:05 – 01:30 | Las Cicatrices Humeantes
- **El Cielo:** Las últimas chispas. Columnas de humo suben de cada cráter y el cielo se limpia.
- **El Caos:** Silencio, brasas y roca que aún brilla. Durante minutos los cráteres siguen al rojo (`crater_glow`) y se van enfriando.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Mundo | Columnas de humo y brasas. **Enfriamiento:** con el tiempo, el magma de los cráteres pasa a obsidiana o roca negra (se registra en el diario) | `particle_emitter`, `block_transform` |
| Audio | Crepitar, siseo de roca caliente y el ambiente vuelve poco a poco | `sound_loop`, `audio_duck` |
| Juego | Los fuegos se apagan poco a poco | — |

**Viabilidad:** todo se puede hacer. Los impactos solo modifican el terreno en **chunks cargados**: un meteorito que cae en un chunk descargado se ve, pero no cambia nada. Los cráteres se precalculan de forma asíncrona.

---

### 6.5 Terremoto *(borrador, mismo formato)*

#### Ficha
`liveevents:earthquake` · **45 s** · ámbito **regional** (epicentro + radio, 160 por defecto) · a cualquier hora.

**Paleta:** `dust` #B8A27A · `deep_dust` #6B5A43 · `sky_dim` #4A4E57 · `crack_glow` #FF5A1F (solo si una grieta llega a lava).

**Detalle realista:** las ondas sísmicas llegan antes a quien está cerca del epicentro. Cada cliente calcula su propio retraso: primero llega la **onda P** (sacudida seca) y después la **onda S** (la violenta).

##### 00:00 – 00:06 | El Silencio de los Animales
- **El Caos:** El mundo calla. Los grillos de las cuevas se detienen, cae polvo de los techos y las hojas se sueltan solas. Nadie sabe por qué, pero algo va mal.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Audio | Bajan el ambiente y la música. Zumbido sub-grave casi inaudible | `audio_duck`, `sound_loop` |
| Mundo | Polvo que cae de los techos y hojas que se desprenden | `particle_emitter` |

##### 00:06 – 00:12 | El Rugido Subterráneo (onda P)
- **El Caos:** Un golpe seco desde abajo, como si algo enorme chocara contra el mundo.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Cámara | Sacudida vertical seca | `camera_shake` |
| Audio | Golpe grave y un retumbo que va creciendo | `sound` |

##### 00:12 – 00:32 | La Tierra Se Abre (onda S)
- **El Caos:** El suelo se ondula. Las grietas corren por el paisaje siguiendo una línea de falla. Brotan géiseres de polvo, los acantilados se desmoronan y las estalactitas caen.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Mundo | Grietas a lo largo de una falla determinista (se registran en el diario). Cae la grava y la arena sin soporte. **Las estalactitas de espeleotema puntiagudo caen y hacen daño**, igual que en vanilla. Ondas en el agua (partículas) y géiseres de polvo | `crack`, `block_transform`, `particle_emitter` |
| Cámara / Post-FX | Temblor horizontal violento según la distancia, con un desenfoque de movimiento leve | `camera_shake` |
| Audio | Rugido continuo, crujidos de roca y derrumbes | `sound_loop`, `sound` |
| Juego | Náusea y lentitud cerca del epicentro, pequeños empujones aleatorios (pérdida de equilibrio) y daño por caída de bloques | `status_effect`, `knockback` |
| Modo seguro | Sin náusea ni desenfoque, y temblor al 30 % | — |

##### 00:32 – 00:45 | Las Réplicas
- **El Caos:** Réplicas cada vez más débiles. El polvo se posa y el silencio vuelve a pesar.

| Capa | Qué ocurre | Módulos |
|------|------------|---------|
| Mundo / Cámara / Audio | Réplicas que se van apagando, polvo que se asienta y deslizamientos lejanos | `camera_shake`, `particle_emitter`, `sound` |

**Viabilidad:** el escalón de falla (un lado del suelo baja 1–2 bloques) es caro. Es opcional y va con presupuesto y un tope de longitud.

---

## 7. Sistema visual

### 7.1 Niveles de efecto

| Nivel | Técnica | Coste | Para qué |
|-------|---------|-------|----------|
| 1 | Partículas vanilla o propias (`ParticleProvider`) | Bajo | Chispas, ceniza, polvo |
| 2 | **Geometría propia** (billboards, anillos, esferas) con su propio `RenderPipeline` o `RenderType` | Medio | Onda de choque, estela, estrella, nebulosa |
| 3 | Post-procesado (cambio de exposición, viñeta, aberración) | Medio-alto | Destello, radiación, impacto |

La sensación de realismo viene sobre todo del **nivel 2**. Un anillo que se expande y está bien sombreado impresiona más y cuesta menos que 2000 partículas sueltas.

### 7.2 LOD por distancia

| Distancia al jugador | Calidad |
|----------------------|---------|
| Cerca (< 64 bloques) | Completa: todas las capas y escombros |
| Media (64–192) | Menos partículas (×0,4), sin escombros pequeños |
| Lejos (192+) | Solo geometría grande (onda, estrella), cielo y sonido |
| Fuera del rango de visión | Solo cielo y sonido con retraso |

### 7.3 Presupuesto en el cliente

- Un límite global de **partículas propias vivas** (por defecto 4000), configurable en el cliente.
- Un límite de **emisiones por fotograma**. Si se supera, el emisor baja su densidad en lugar de dejar de emitir.
- **Culling por frustum** para emisores y geometría.
- Los objetos se **reutilizan desde un pool** para no crear basura cada fotograma.

### 7.4 Sonido realista

- **Retraso por distancia**: `retraso = distancia / velocidad_sonido_configurable`. Ves el impacto y lo oyes un momento después.
- Atenuación con la distancia y capas de sonido: **cercana** (crujido agudo), **media** (estruendo) y **lejana** (retumbo grave y filtrado).
- Límite de voces simultáneas, porque el motor de sonido tiene canales finitos.
- Los sonidos usan la categoría vanilla `WEATHER` o `AMBIENT`. Crear una categoría nueva exigiría un mixin en un enum y daría problemas con otros mods. El mod añade su propio control de volumen en la configuración del cliente (§12.2).
- **Subtítulos:** todos los sonidos llevan clave de subtítulo en `sounds.json` (por ejemplo "Estrella colapsando", "Cristal celeste rompiéndose"). Así los jugadores con discapacidad auditiva o sin sonido saben qué ocurre.
- Compatibilidad con **Sound Physics Remastered**: los sonidos celestes se marcan como "sin oclusión" para que no queden ahogados por el techo.

### 7.5 Cielo

- Tinte del cielo y la niebla con un mixin mínimo o un hook del renderizado del cielo.
- ⚠ **Iris/shaders reemplazan el cielo.** Si se detecta Iris con un shaderpack activo, el sistema cambia a **sobreposición en el HUD y geometría en el mundo** en vez de modificar el cielo. El evento debe seguir viéndose bien.

### 7.6 Cámara

- Temblor con **ruido suave** (no aleatorio puro) y amplitud que depende de la distancia.
- Se implementa con un único mixin en `Camera`, aplicado como desplazamiento aditivo para convivir con otros mods de cámara.

### 7.7 Accesibilidad (obligatoria en eventos en directo)

- Respetar las opciones vanilla: **Escala de efectos de pantalla**, **Efectos de FOV**, **Ocultar destellos de rayos** y **Pulsación de oscuridad**.
- Opciones propias del cliente: intensidad máxima del destello, desactivar el temblor y un modo "reducir movimiento".
- **Regla de destellos** (criterio WCAG 2.3.1), que el `VisualDirector` aplica en todos los perfiles, también en `cinematic`:
  - Como máximo **3 destellos o cortes grandes por segundo**, sumando todos los módulos.
  - **Ningún destello de rojo saturado** que ocupe una parte grande de la pantalla. Es el caso más peligroso.
  - Los parpadeos rápidos (por ejemplo, el púlsar a 30 Hz) solo se permiten en **áreas pequeñas** de la pantalla.
- Los parpadeos siguen el **tiempo real**, nunca los fotogramas. Así no cambian de comportamiento con FPS bajos.
- Se recomienda que el servidor muestre un **aviso de fotosensibilidad** la primera vez que un jugador presencia un evento (configurable).
- **Antes de un directo:** grabar el evento y pasarlo por una herramienta de análisis de fotosensibilidad (por ejemplo PEAT o el test de Harding).
- **Tecla de pánico del cliente** (asignable, por defecto sin asignar): desactiva **al instante** todos los efectos de pantalla, cámara y post-procesado de LiveEvents para ese jugador, hasta que vuelva a pulsarla. El jugador sigue recibiendo los efectos de juego. Es la salida de emergencia si alguien se marea o se siente mal.

### 7.8 Capa de aislamiento de render

El pipeline de render de Minecraft cambia a menudo. **Todo** el código que toca render va detrás de una interfaz `RenderBackend`. Cuando salga 26.x+1, solo hay que adaptar esa capa y la lógica de los módulos sigue igual.

### 7.9 Post-procesado

- Se usa el sistema de **post-efectos de Minecraft** (cadenas de post-efecto en JSON con shaders propios en `assets/liveevents/`). ⚠ *Confirmar su formato en 26.x.*
- **Pasadas propias:**
  - `bloom`: umbral de brillo, desenfoque en dos pasadas y suma. Minecraft no tiene HDR, así que es un bloom "falso".
  - `whiteout`: curva de exposición.
  - `chromatic_glitch`: separación RGB, franjas desplazadas y líneas de barrido.
  - `distortion`: anillos y calor.
  - `lens_dirt`: suciedad y rayas de lente quemada.
- Las pasadas se encadenan **solo mientras hay un evento activo**. Sin eventos, el coste es cero.
- Los efectos de pantalla completa se dibujan **debajo del HUD**, para que la vida, la barra rápida y el chat se vean siempre.
- **Con Iris y un shaderpack activo**, los post-efectos de vanilla pueden no aplicarse o chocar con el shaderpack. En ese caso se cambia a capas a pantalla completa en el HUD (color con transparencia). Hay que probarlo con los shaderpacks más usados (Complementary, BSL, Photon).

### 7.10 Perfiles de intensidad

| Perfil | Para qué | Comportamiento |
|--------|----------|----------------|
| `cinematic` | Grabar series y tráileres | El guion tal cual, con los límites de §7.7 |
| `gameplay` (por defecto) | Eventos en servidor con gente jugando | Picos más cortos, HUD siempre visible, efectos de juego completos |
| `safe` | Directos con público amplio o jugadores sensibles | Sin cortes duros, destellos ≤ 40 %, glitch suave, temblor mínimo |

- El admin elige el perfil al lanzar: `/le start supernova profile=cinematic`.
- **El cliente tiene la última palabra:** cada jugador puede fijar el perfil máximo que acepta. Si elige `safe`, verá el evento en `safe` aunque el admin haya lanzado `cinematic`.

### 7.11 Exposición según el cielo visible

Los efectos de cielo y de pantalla se escalan según **cuánto cielo ve la cámara**, que se estima con la luz de cielo en la posición de la cámara y alrededor. En una cueva profunda el jugador no se queda ciego: ve la luz que entra por la boca y oye el evento. Es más realista y además le da al jugador una forma de refugiarse.

### 7.12 Varios eventos a la vez

`max_concurrent_events` permite varios eventos simultáneos, así que hay que decidir cómo se mezclan:

| Situación | Regla |
|-----------|-------|
| Dos eventos **celestes** en la misma dimensión | No se permite: como máximo **uno celeste por dimensión**. El segundo se rechaza con un aviso, o se pone en cola si se lanza con `queue`. |
| Un celeste y uno localizado (supernova + meteoritos) | Se permite. El cielo lo controla el celeste; el localizado solo añade sus efectos de mundo, cámara y sonido. |
| Varios localizados | Se permite. Cada efecto tiene una **prioridad** y la cámara usa el temblor de mayor amplitud, no la suma. |
| Tintes de cielo, luz y post-procesado | Los mezcla el `LayerMixer` por prioridad y con fundido. Nunca se acumulan sin control. |
| Regla de destellos (§7.7) | Se cuenta **en conjunto** entre todos los eventos activos, no evento por evento. |
| Audio | Hay un tope de voces total. `audio_duck` aplica el silencio más fuerte que pida cualquier evento activo. |

### 7.13 Clima, nubes y dimensiones

- **Lluvia y tormenta:** los cuerpos celestes se dibujan **detrás de las nubes y la lluvia**, como en la realidad. Cada evento puede pedir `clear_weather: true` para despejar el cielo durante el evento y restaurar el clima al terminar.
- **Nubes:** se respetan las vanilla y las de mods de nubes. La luz del evento tiñe las nubes cuando el `RenderBackend` lo permite.
- **Nether:** no tiene cielo. Los eventos celestes **no se pueden lanzar** ahí. Los localizados (terremoto, meteoritos) sí, con su propia paleta.
- **End:** tiene una bóveda propia. Los eventos celestes se permiten si la definición lo indica (`allowed_dimensions`).
- **Dimensiones de otros mods:** solo se permiten si están en la lista `allowed_dimensions` del evento o del servidor.

### 7.14 Calidad automática

- Opción del cliente **"Calidad automática"**, activada por defecto. Si los FPS bajan de un umbral durante un evento (por ejemplo, menos de 40 durante 2 s), el `VisualDirector` baja un nivel de calidad. Cuando los FPS se recuperan, vuelve a subir poco a poco.
- Los recortes siguen este orden: plasma y nebulosa → distorsión → partículas → bloom. El cielo básico, el sonido y los avisos **nunca se recortan**, porque explican lo que está pasando.

---

## 8. Efectos sobre el mundo

### 8.1 Daño

- **Damage types propios** definidos como datos, por ejemplo `stellar_burn`, `meteor_impact`, `seismic` y `radiation`, cada uno con sus *tags* (`is_fire`, `bypasses_armor` según convenga, `is_explosion`) y su mensaje de muerte traducible.
- Las comprobaciones de daño se hacen **cada N ticks** (configurable), no en cada tick.
- La exposición (`requires_sky`) se calcula con el heightmap `MOTION_BLOCKING`. Es O(1) por entidad y no necesita raycast.

### 8.2 Efectos de estado

- `radiation_burn` es propio. Los demás son vanilla (náusea, lentitud, ceguera breve).
- Duración e intensidad configurables, con escalado según la distancia.

### 8.3 Entidades

- Solo se consultan entidades dentro de una **AABB** del radio y con la frecuencia indicada.
- Filtros configurables: jugadores, mobs hostiles, pasivos, mascotas y entidades con nombre. Por defecto **no se daña a mascotas ni entidades con nombre**.
- Respetar el modo creativo, el espectador y el permiso `bypass.damage`.

### 8.4 Bloques

- Los cambios **nunca se aplican de golpe**. Pasan por el `WorkScheduler` (§14.2).
- Solo se modifican **chunks ya cargados**. El mod nunca fuerza la carga de chunks.
- Las formas se precalculan de forma asíncrona y se aplican en el hilo del servidor.
- **Lista negra de bloques** que no se tocan nunca: bloques con *block entity* (cofres, hornos...), bedrock, portales y bloques de otros mods marcados con un tag `liveevents:immune`. Los *modpacks* pueden ampliar este tag.
- Las paletas de transformación (`liveevents:scorched`, `liveevents:frozen`...) se definen como datos.

### 8.5 Diario de cambios y reversión

- Cada cambio de bloque guarda `(dimensión, posición, estado_anterior, estado_nuevo, id_evento)`.
- El diario se **guarda en disco** (como `SavedData` o en un archivo propio comprimido por evento).
- `/le revert <instancia>` revierte de forma gradual, con el mismo presupuesto por tick.
- Antes de revertir una posición se comprueba que **sigue teniendo el `estado_nuevo`**. Si un jugador ya construyó encima, no se sobrescribe.
- Hay una **caducidad** configurable (por ejemplo, 7 días) para que el diario no crezca sin límite.
- Modos por evento: `permanent`, `revert_on_end` y `revert_manual` (por defecto).
- Los **bloques que caen** (grava, arena, estalactitas) se registran en el diario **cuando aterrizan**: se guarda dónde estaban y dónde quedaron. El **fuego** se registra al colocarse y, al revertir, también se quita. Los bloques que el fuego destruye por sí solo **no** se pueden recuperar, así que conviene usar `fire_spread` con prudencia en zonas construidas.
- **Recomendación para los admins:** hacer una **copia de seguridad del mundo** antes de un evento grande. El diario cubre los cambios del mod, pero no todo lo que pase alrededor (mobs, explosiones vanilla o acciones de los jugadores).

### 8.6 Modo no letal y mundos hardcore

- Con `lethal: false` (§5.2.1), el daño de los eventos nunca deja a nadie por debajo de medio corazón. Es ideal para series en las que nadie debe morir por el evento. Los demás daños del juego no cambian.
- En los **mundos hardcore**, el mod avisa al admin antes de lanzar un evento letal y pide `confirm`.
- Muertes causadas por el evento: mensaje de muerte propio y traducible ("X ardió bajo la luz de Betelgeuse"). El registro de auditoría lo anota.

---

## 9. Red y sincronización

### 9.1 Paquetes (todos servidor → cliente)

| Paquete | Cuándo se envía | Contenido |
|---------|-----------------|-----------|
| `EventStart` | Al iniciar | id de instancia, id de definición, dimensión, centro, semilla, tick de inicio y parámetros que difieren de los valores por defecto |
| `EventPhase` | Al cambiar de fase | id de instancia, fase y tick |
| `EventKeyframe` | Solo para cambios en vivo hechos por el admin | id de instancia y parámetros modificados |
| `EventState` | Al entrar al servidor o cambiar de dimensión | **Estado completo** de las instancias activas en esa dimensión |
| `EventEnd` | Al terminar o cancelar | id de instancia y motivo |
| `DefinitionSync` | Al entrar y al hacer `reload` | Definiciones visuales (para que el cliente pueda dibujar eventos personalizados) |
| `SkyScarState` | Al entrar, al cambiar de dimensión y al crear o borrar una secuela | Secuelas persistentes del cielo (nebulosa, púlsar...): tipo, posición en la bóveda, semilla y caducidad |
| `EventClock` | Cada segundo mientras hay eventos activos | id de instancia y tiempo del evento (§9.4) |
| `EventPause` | Al pausar o reanudar | id de instancia, estado y tiempo del evento. El cliente congela o reanuda los visuales y el audio |
| `PanicStop` | Con `/le panic` | Corta **al instante** todos los efectos de cliente (§21.5) |
| `Handshake` | Al conectarse | Versión del protocolo de LiveEvents (§9.5) |

- No hay **ningún paquete cliente → servidor** con poder de acción.
- Antes de enviar se comprueba si el cliente puede recibir el canal. Los jugadores sin el mod no reciben nada y no se les desconecta.
- Los paquetes se envían solo a los jugadores **de la misma dimensión** y, si se configura, dentro de un radio de interés.

### 9.2 Coste de red estimado

Un evento completo debería usar **unos pocos KB en total por jugador**. Los cambios de bloques los sincroniza vanilla y quedan limitados por el presupuesto por tick.

### 9.3 Entrar a mitad de evento

El cliente recibe `EventState`, calcula `t = tick_actual - tick_inicio` y **avanza la simulación** hasta ese momento usando la misma semilla. Se salta lo que ya pasó y ve el evento en el punto correcto.

### 9.4 Reloj del evento y lag

- **La línea de tiempo avanza con los ticks del servidor**, no con el reloj real. Si el servidor va lento (TPS bajos), el evento entero se ralentiza a la vez para todos, y el daño sigue coincidiendo con los visuales.
- El servidor manda un pequeño paquete **`EventClock`** (id de instancia y tiempo del evento) **cada segundo** mientras hay eventos activos.
- El cliente **interpola** entre latidos con su reloj local, para que la animación sea fluida a cualquier FPS. Si se desvía, **corrige poco a poco** acelerando o frenando un poco, nunca con saltos. Si la diferencia es enorme (por ejemplo, tras un parón del servidor), sí salta.
- El audio se sincroniza con ese mismo reloj, así que los golpes de sonido coinciden con los cortes de color.
- Los paquetes de cambio de fase llevan el **tiempo exacto del evento**, no "ahora", para compensar la latencia de cada jugador.

### 9.5 Versión del protocolo y jugadores sin el mod

- Al conectarse, el cliente y el servidor intercambian la **versión del protocolo de LiveEvents**. Si no coinciden, el jugador recibe un mensaje claro ("Actualiza LiveEvents Mod a la versión X") en lugar de errores raros.
- **Clientes sin el mod:** en la práctica no pueden entrar, porque el mod registra contenido propio (efectos de estado, sonidos, partículas) que un cliente vanilla no conoce. Por eso el mod es **obligatorio en el cliente** (D1). El servidor muestra en la pantalla de desconexión un mensaje traducible con el enlace de descarga, configurable por el admin.

### 9.6 Límites de tamaño

- Cada paquete tiene un **tamaño máximo**. `DefinitionSync` se divide en trozos si un servidor tiene muchos eventos personalizados.
- El cliente **valida y limita** todos los valores que recibe (radios, intensidades, número de elementos). Un servidor malicioso o con un JSON roto no puede bloquear ni colgar el cliente (§22).

---

## 10. Permisos (LuckPerms)

### 10.1 Cómo se integra

- Se depende de **`fabric-permissions-api`** (de lucko), incluida dentro del jar.
- Cada comprobación lleva un **nivel de OP de respaldo**: si no hay gestor de permisos, se usa el nivel de OP.
- Si LuckPerms está instalado, **implementa esa API por sí solo**. No hace falta depender de la API de LuckPerms.
- Así el mod funciona con LuckPerms, con otro gestor compatible o sin ninguno.

### 10.2 Nodos de permiso

| Nodo | OP de respaldo | Descripción |
|------|----------------|-------------|
| `liveevents.command` | 2 | Acceso básico a `/le` |
| `liveevents.list` | 2 | Listar eventos e instancias |
| `liveevents.start` | 2 | Lanzar eventos (cualquiera) |
| `liveevents.start.<evento>` | 2 | Lanzar un evento concreto, por ejemplo `liveevents.start.supernova` |
| `liveevents.start.anywhere` | 3 | Lanzar en coordenadas u otra dimensión (sin este permiso, solo en la posición propia) |
| `liveevents.control` | 2 | Pausar, reanudar y saltar de fase |
| `liveevents.stop` | 2 | Cancelar instancias |
| `liveevents.revert` | 3 | Revertir cambios en el mundo |
| `liveevents.edit` | 3 | Ajustar parámetros en vivo |
| `liveevents.reload` | 3 | Recargar las definiciones |
| `liveevents.preview` | 2 | Previsualizar sin efectos |
| `liveevents.bypass.damage` | — | Inmune al daño y a los efectos de estado |
| `liveevents.bypass.visuals` | — | *(Opcional)* no recibe destellos ni temblor |
| `liveevents.notify` | 2 | Recibe avisos de auditoría |
| `liveevents.start.<namespace>.<evento>` | 2 | **Se crea solo** para cada evento de usuario, por ejemplo `liveevents.start.miserie.apocalipsis_zombi` (§20) |
| `liveevents.create` | 3 | Crear variantes con `/le create` |
| `liveevents.validate` | 2 | Validar definiciones con `/le validate` |
| `liveevents.debug` | 3 | Herramientas para creadores: `seek`, `timescale`, `debug timeline` y `debug perf` |
| `liveevents.panic` | 2 | Usar `/le panic` (§21.5). Conviene dárselo también a los moderadores de un directo |
| `liveevents.schedule` | 3 | Programar eventos y secuencias (§25) |
| `liveevents.gui` | 2 | Abrir el panel de administración en el juego (§25.1) |

### 10.3 Contexto y auditoría

- Las comprobaciones usan la fuente del comando (jugador, consola o bloque de comandos). Así LuckPerms puede aplicar sus contextos (mundo, servidor...).
- **Registro de auditoría** en `logs/liveevents-audit.log`: quién lanzó, paró o revirtió qué, cuándo, dónde y con qué parámetros.

---

## 11. Comandos

Comando raíz: `/liveevents`, con el alias `/le`. Usa Brigadier con autocompletado.

| Comando | Descripción |
|---------|-------------|
| `/le list [definitions\|active]` | Lista definiciones o instancias activas |
| `/le info <evento>` | Muestra fases, duración y parámetros |
| `/le start <evento> [at <pos>\|on <jugador>] [in <dimensión>] [param=valor ...]` | Lanza un evento |
| `/le start <evento> ... delay <segundos>` | Lanza con cuenta atrás (útil en directos) |
| `/le preview <evento> [at <pos>]` | Reproduce los **visuales y el sonido solo para el admin** y dibuja el contorno de la zona afectada. No hay efectos de juego, el mundo no cambia y los demás jugadores no ven nada |
| `/le pause <id>` / `/le resume <id>` | Pausa o reanuda |
| `/le skip <id> [fase]` | Salta a la siguiente fase o a una concreta |
| `/le set <id> <param> <valor>` | Cambia un parámetro en vivo |
| `/le stop <id\|all>` | Cancela |
| `/le revert <id\|last> [confirm]` | Revierte los cambios en el mundo |
| `/le reload` | Recarga las definiciones y sincroniza a los clientes |
| `/le start <evento> ... profile=<cinematic\|gameplay\|safe>` | Lanza con un perfil de intensidad (§7.10) |
| `/le scars list` / `/le scars clear [id\|all]` | Lista o borra las secuelas persistentes del cielo |
| `/le create <nuevo_id> from <evento>` | Copia un evento como variante editable en `config/liveevents/events/` (§20) |
| `/le validate [evento\|all]` | Valida las definiciones y muestra los errores con archivo, campo y motivo |
| `/le start <evento> ... timescale=<0.1–2.0>` | Reproduce a cámara lenta o rápida (para quien crea eventos) |
| `/le seek <id> <segundo>` | Salta a un momento concreto de una instancia |
| `/le debug timeline` | Overlay con el segundo actual, la fase y los módulos activos |
| `/le debug perf` | Tiempo del mod por tick, cola pendiente del `WorkScheduler` y partículas vivas (§14.4) |
| `/le debug report` | Genera un informe (versiones, mods, configuración y errores recientes, **sin datos personales**) para adjuntarlo a un reporte de error (§27) |
| `/le panic` | **Parada de emergencia** (§21.5) |
| `/le start <evento> ... queue` | Si hay un evento incompatible activo, pone este en cola en vez de rechazarlo (§7.12) |
| `/le schedule <evento> at <fecha y hora> [args]` · `/le schedule list` · `/le schedule cancel <id>` | Programa un lanzamiento con hora real; se mantiene tras reiniciar el servidor (§25.2) |
| `/le sequence start <secuencia>` · `/le sequence stop` | Ejecuta una secuencia de eventos definida en JSON (§25.3) |
| `/le gui` | Abre el panel de administración (§25.1) |

- Todos los comandos funcionan desde **consola, bloques de comandos y funciones `.mcfunction`**. Así se pueden **guionizar secuencias** para series, por ejemplo con `/schedule` vanilla.
- Los comandos destructivos (`revert`, `stop all`) piden `confirm`.

---

## 12. Configuración

### 12.1 Servidor

```
config/liveevents/
├─ server.json          # límites globales, presupuesto, protecciones, auditoría
└─ events/
   ├─ supernova.json
   ├─ meteor_shower.json
   ├─ earthquake.json
   └─ mi_supernova_pequeña.json   # variante creada por el admin ("extends")
```

Además, las definiciones se pueden distribuir en **datapacks** (`data/<ns>/liveevents/event/*.json`) para que otros autores las compartan.

**`server.json` (campos principales):**

| Campo | Por defecto | Descripción |
|-------|-------------|-------------|
| `max_concurrent_events` | 2 | Instancias simultáneas en todo el servidor |
| `tick_budget_ms` | 2.0 | Tiempo máximo de trabajo del mod por tick |
| `max_block_changes_per_tick` | 256 | Tope de cambios de bloque por tick |
| `respect_protections` | true | Integración con mods de protección |
| `excluded_regions` | [] | Zonas excluidas propias del mod |
| `journal_expiry_days` | 7 | Caducidad del diario de cambios |
| `default_world_mode` | `revert_manual` | Modo de persistencia por defecto (§8.5) |
| `photosensitivity_notice` | true | Aviso la primera vez |
| `audit_log` | true | Registro de auditoría |
| `default_profile` | `gameplay` | Perfil cuando el comando no indica ninguno |
| `max_profile` | `cinematic` | Perfil máximo permitido en el servidor |
| `sky_scars_default_duration` | `3d` | Duración por defecto de las secuelas del cielo (`permanent` para que no caduquen) |
| `max_lightning_per_event` | 12 | Tope global de rayos de `lightning_storm` |
| `on_restart` | `cancel` | Qué pasa con los eventos activos si el servidor se reinicia: `cancel` o `resume` (§21.1) |
| `pause_when_empty` | `true` | Pausa los eventos si no queda ningún jugador conectado |
| `watchdog_mspt` | 100 | Umbral de MSPT que, mantenido durante `watchdog_seconds`, detiene los eventos (§21.4) |
| `watchdog_seconds` | 10 | Ventana del watchdog |
| `start_rate_limit` | `5/min` | Máximo de lanzamientos por minuto por persona (§22) |
| `allowed_dimensions` | `["minecraft:overworld", "minecraft:the_end"]` | Lista global; cada evento puede restringirla más |
| `missing_mod_message` | (texto traducible) | Mensaje para quien se conecta sin el mod, con enlace de descarga (§9.5) |
| `discord_webhook` | vacío | Aviso opcional a Discord al empezar o terminar un evento (§25.5) |
| `timezone` | la del sistema | Zona horaria para `/le schedule` |
| `audit_retention_days` | 30 | Días que se guarda el registro de auditoría (§22) |

### 12.2 Cliente

| Opción | Por defecto |
|--------|-------------|
| Calidad visual (Baja / Media / Alta / Ultra) | Alta |
| Límite de partículas propias | 4000 |
| Intensidad máxima del destello | 100 % (limitada además por la opción vanilla) |
| Temblor de cámara | Activado |
| Reducir movimiento | Desactivado |
| Volumen de eventos | 100 % |
| Perfil máximo que acepto (§7.10) | `cinematic` |
| Mostrar secuelas del cielo | Activado |
| Calidad automática (§7.14) | Activado |
| Tecla de pánico (§7.7) | Sin asignar |
| Subtítulos de eventos | Siguen la opción vanilla de subtítulos |

- Pantalla de configuración con **Mod Menu** y una librería de configuración (YACL o Cloth Config, la que esté disponible para 26.x). Si no está instalada, se usa el archivo JSON.
- Validación: los valores fuera de rango se corrigen y se avisa en el log. Nunca se produce un crash.

---

## 13. Compatibilidad con otros mods

### 13.1 Normas del propio mod

1. **El mínimo de mixins** y nunca `@Overwrite`. Se prefieren `@Inject` y `@ModifyExpressionValue` (MixinExtras).
2. Siempre que exista un evento de Fabric API, se usa ese evento en vez de un mixin.
3. **Todas las integraciones son opcionales** y se activan solo si el otro mod está cargado (`FabricLoader.isModLoaded`). Ningún mod opcional se carga si no está.
4. **No se modifica** la configuración ni el comportamiento global de otros mods.
5. Los ids van con espacio de nombres `liveevents:` y los bloques se protegen con *tags* que otros mods pueden ampliar.

### 13.2 Mods a probar

| Mod | Riesgo | Plan |
|-----|--------|------|
| Sodium | Medio (renderizado) | Usar las API de render oficiales y probar el bloque de render propio |
| Iris + shaderpacks | **Alto** (cielo, post-procesado) | Detectar el shaderpack activo y cambiar a overlay/geometría (§7.5) |
| Lithium | Bajo | Probar los cambios de bloques y las consultas de entidades |
| Entity Culling | Bajo | Los efectos no son entidades, así que no deberían verse afectados |
| LambDynamicLights | Bajo | Integración opcional para luz dinámica real en impactos |
| Distant Horizons | Medio | Sus LOD lejanos no reflejarán los cambios al momento. Es aceptable, pero hay que documentarlo |
| LuckPerms | Bajo | Mediante `fabric-permissions-api` |
| Flan / GOML / FTB Chunks | Medio | Mediante Common Protection API cuando exista soporte |
| Otros mods de cámara (Freecam, Do a Barrel Roll) | Medio | El temblor se suma como desplazamiento y se desactiva en Freecam |
| **Flashback / Replay Mod** | **Alto para series** | Graban los paquetes del servidor, incluidos los nuestros. Hay que comprobar que la repetición reproduce los visuales: el reloj (§9.4) debe seguir el tiempo de la grabación, no el real, y debe funcionar al saltar hacia delante o hacia atrás (§25.4) |
| C2ME | Medio (cargas de chunks en paralelo) | El mod nunca toca el mundo fuera del hilo del servidor y solo lee chunks ya cargados. Hay que probarlo |
| ModernFix, FerriteCore, Krypton | Bajo | Optimizaciones generales. Basta con incluirlos en la batería de pruebas |
| Sound Physics Remastered | Medio | Los sonidos celestes se marcan como "sin oclusión" (§7.4) |
| Text Placeholder API (Patbox) | Bajo | Integración opcional con *placeholders* para TAB, scoreboards y hologramas (§25.6) |
| Velocity / FabricProxy-Lite | Bajo | Cada servidor de la red es independiente (§21.7) |
| Mods que cambian el ciclo día/noche o el clima | Medio | `night_override: visual` no toca la hora del servidor. `clear_weather` guarda el clima anterior y lo restaura solo si nadie lo ha cambiado mientras tanto |

### 13.3 Protección de terreno

- Integración opcional con **Common Protection API** (de Patbox), que usan varios mods de claims en Fabric.
- Si una posición está protegida, **no se cambian bloques**. El daño a entidades en esa zona es configurable.
- Además existe una lista propia `excluded_regions` por si no hay mod de protección instalado.

---

## 14. Rendimiento

### 14.1 Objetivos

| Métrica | Objetivo |
|---------|----------|
| Coste en el servidor por tick con un evento activo | ≤ presupuesto configurado (2 ms por defecto) |
| Coste en el servidor sin eventos activos | ≈ 0 (sin trabajo en cada tick) |
| Pico al iniciar un evento | < 5 ms (el precálculo pesado es asíncrono) |
| Red por jugador y evento | Unos pocos KB más los cambios de bloque vanilla |
| Impacto en FPS del cliente (calidad Alta, gama media) | < 10 % durante el impacto |
| Memoria extra | Que no sea significativa; objetos en pools y sin listas que crezcan sin límite |

### 14.2 `WorkScheduler` (núcleo del rendimiento)

- Una cola de tareas pequeñas (por ejemplo, *"cambiar estos 64 bloques"*, *"aplicar daño a la AABB"*).
- En cada tick se ejecutan tareas **hasta agotar el presupuesto en ms** o el tope de bloques. Lo que no cabe pasa al tick siguiente.
- Las tareas tienen prioridad: daño y efectos primero (afectan a lo que se juega), luego bloques y por último la limpieza.
- **Si el servidor está bajo carga** (MSPT alto), el presupuesto se reduce automáticamente.

### 14.3 Otras técnicas

- Sin eventos activos, **los listeners de tick salen inmediatamente**.
- Formas (cráteres, grietas, trayectorias) generadas con **ruido determinista** a partir de la semilla. Así el servidor y el cliente obtienen el mismo resultado sin intercambiar datos.
- Las consultas de entidades se cachean por intervalo.
- Las expresiones de la configuración se compilan una sola vez.
- En el cliente: pools de partículas y geometría, LOD y culling por frustum (§7).

### 14.4 Medición

- Perfilado con **Spark** en un servidor de prueba con 20, 50 y 100 jugadores simulados.
- Un comando `/le debug perf` que muestre el tiempo del mod por tick, la cola pendiente y las partículas vivas.

---

## 15. API pública para otros mods

Eventos al estilo Fabric (`Event<T>`):

| Callback | ¿Cancelable? | Para qué sirve |
|----------|--------------|----------------|
| `LiveEventStart` | Sí | Otro mod puede bloquear el evento (por ejemplo, un minijuego en curso) |
| `LiveEventPhaseChange` | No | Integraciones: logros, misiones, Discord |
| `LiveEventEnd` | No | Limpieza o recompensas en mods de terceros |
| `BeforeBlockChange` | Sí | Proteger bloques propios de otro mod |
| `BeforeEntityAffected` | Sí / modificable | Inmunidad o modificación del daño |
| `RegisterModules` | — | **Otros mods pueden registrar módulos nuevos** |

Además, una API Java sencilla para lanzar eventos desde código: `LiveEventsAPI.start(...)`.

- Los addons se registran mediante el **entrypoint `liveevents`** de `fabric.mod.json` (detalle en §20.6).
- **Solo el paquete `com.takumistudios.liveevents.api` es estable** y sigue versionado semántico. El resto del código es interno y puede cambiar sin aviso.

---

## 16. Estructura del proyecto

```
liveevents/
├─ build.gradle(.kts) · gradle.properties · settings.gradle
├─ src/main/java/com/takumistudios/liveevents/
│  ├─ LiveEvents.java                 # entrypoint común
│  ├─ api/                            # API pública y callbacks (§15)
│  ├─ core/
│  │  ├─ definition/                  # EventDefinition, Phase, ModuleSpec, expresiones
│  │  ├─ registry/                    # EventRegistry, ModuleTypeRegistry
│  │  ├─ instance/                    # EventDirector, EventInstance
│  │  ├─ scheduler/                   # WorkScheduler, presupuesto
│  │  └─ random/                      # semillas y ruido determinista
│  ├─ server/
│  │  ├─ modules/                     # damage_area, crater, block_transform...
│  │  ├─ journal/                     # WorldJournal, reversión
│  │  ├─ protection/                  # integración opcional con protecciones
│  │  ├─ permission/                  # PermissionGate (fabric-permissions-api)
│  │  ├─ command/                     # /le
│  │  └─ audit/
│  ├─ network/                        # payloads y codecs
│  ├─ content/                        # MobEffects, sonidos, tipos de partícula
│  └─ config/                         # server.json y validación
├─ src/client/java/com/takumistudios/liveevents/client/
│  ├─ LiveEventsClient.java
│  ├─ state/                          # ClientEventState, avance para quien entra tarde
│  ├─ visual/
│  │  ├─ director/                    # VisualDirector, LOD, presupuesto
│  │  ├─ modules/                     # sky_tint, flash, shockwave, meteor_trail...
│  │  └─ backend/                     # RenderBackend (capa de aislamiento §7.8)
│  ├─ audio/                          # sonido con retraso y capas
│  ├─ accessibility/
│  ├─ compat/                         # Iris, Sodium, LambDynamicLights (opcionales)
│  └─ config/                         # opciones de cliente y Mod Menu
└─ src/main/resources/
   ├─ fabric.mod.json · liveevents.mixins.json · liveevents.client.mixins.json
   ├─ assets/liveevents/  (lang, sounds.json, sounds/, textures/, shaders/, particles/)
   └─ data/liveevents/
      ├─ damage_type/                 # stellar_burn, meteor_impact, seismic, radiation
      ├─ tags/block/                  # immune, scorchable, etc.
      └─ liveevents/event/            # supernova, meteor_shower, earthquake (por defecto)
```

Además, fuera de `src/main`:

```
├─ src/test/java/...                  # tests unitarios (§23)
├─ src/gametest/java/...              # GameTests de servidor y de cliente (§23)
├─ schema/                            # JSON Schema publicado (§20.4)
├─ docs/                              # guía para admins y para creadores, referencia de módulos
├─ examples/                          # LiveEvents Example Pack (datapack + resource pack)
├─ CREDITS.md                         # procedencia y licencia de cada sonido y textura (§27)
├─ CHANGELOG.md · LICENSE
└─ .github/workflows/                 # build, tests y publicación (§27)
```

Se usan los **source sets separados** de cliente y servidor de Loom, para que el servidor nunca cargue clases del cliente.

---

## 17. Hoja de ruta

| Fase | Contenido | Criterio de "terminado" |
|------|-----------|-------------------------|
| **0. Preparación** | Proyecto con la plantilla de Fabric 26.x. Confirmar Java, mapeos y nombres de API. CI en GitHub Actions. Licencia (D14). Comandos básicos y permisos. | `/le list` funciona con LuckPerms y sin él, y la CI compila en cada push |
| **1. Núcleo** | Definiciones JSON con `format_version`, **JSON Schema**, `/le validate`, fases, `EventDirector`, `WorkScheduler`, red, **reloj y handshake** (§9.4, §9.5), sincronización para quien entra tarde, pausa, **`/le panic`**, persistencia ante reinicios (§21.1), tests unitarios | Un evento "vacío" con fases se reproduce sincronizado entre 2 clientes, también con lag simulado |
| **2a. Base visual** | Cadena de post-procesado (§7.9), perfiles (§7.10), regla de destellos (§7.7), exposición según cielo (§7.11), `celestial_body`, `light_cut`, `audio_duck` | Una escena de prueba con un corte de color, bloom y glitch funciona en vanilla, Sodium e Iris (en modo alternativo) |
| **2b. Supernova** | El guion completo de §6.3: todos sus módulos, `radiation_burn`, `artificial_day`, `melt`, `lightning_storm`, secuelas persistentes y diario | La supernova en los 3 perfiles se ajusta al guion, se puede revertir, respeta el presupuesto y pasa un análisis de fotosensibilidad |
| **3. Meteoritos y terremoto** | Cerrar los guiones de §6.4 y §6.5. `meteor_trail`, `crater`, `crack`, retraso de ondas P y S, precálculo asíncrono | Los 3 eventos del MVP funcionan con Sodium e Iris |
| **4. Pulido visual** | LOD fino, capas de sonido y retraso, subtítulos, calidad automática, mezcla de eventos simultáneos (§7.12), clima y dimensiones (§7.13) | Prueba con jugadores según el protocolo de §23.3: "impresiona" y no marea |
| **5. Compatibilidad y rendimiento** | Protecciones, matriz de mods (incluidos Flashback y C2ME), Spark con carga, watchdog, GameTests | Se cumplen los objetivos de §14.1 y todos los casos de §24 |
| **6. Lanzamiento** | Documentación para admins, **guía para creadores**, **LiveEvents Example Pack**, `/le schedule`, secuencias, Discord y *placeholders*, traducciones `es_es` y `en_us`, publicación en Modrinth y CurseForge | Versión 1.0. Una persona ajena al proyecto crea un evento propio siguiendo solo la guía |
| **7+. Expansión** | Eventos de fase 2–4, Stonecutter multiversión, **panel de administración** (§25.1), **editor visual de línea de tiempo**, integraciones con Twitch y YouTube mediante addon | — |

---

## 18. Riesgos

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|---------|------------|
| Cambios de render en cada versión 26.x | Alta | Alto | Capa `RenderBackend` y pocos mixins |
| Incompatibilidad visual con Iris o shaderpacks | Alta | Medio | Detección y modo alternativo con overlay |
| Lag por cambios de bloques grandes | Media | Alto | `WorkScheduler` con presupuesto, reducción bajo carga, topes |
| Pérdida del diario por un crash | Baja | Alto | Guardado incremental en disco |
| Destellos que causan molestias a espectadores | Media | Alto | Límites de accesibilidad y aviso |
| Las librerías opcionales (config, protección) tardan en actualizarse a 26.x | Media | Bajo | Todas son opcionales y hay alternativa sin ellas |
| El alcance crece demasiado (13 eventos) | Alta | Medio | MVP cerrado de 3 eventos y sistema de módulos para el resto |
| Los post-efectos de vanilla no funcionan con Iris | Alta | Medio | Modo alternativo con capas en el HUD (§7.9) |
| Coste de GPU del plasma y la nebulosa en equipos modestos | Media | Medio | Niveles de calidad; en calidad baja se usan texturas en vez de shaders de ruido |
| Un evento en `cinematic` emitido en directo provoca una crisis fotosensible | Baja | Muy alto | Regla de destellos en todos los perfiles, el cliente puede limitar el perfil y se recomienda analizar el vídeo antes |
| Los guiones piden efectos imposibles en Minecraft (como sombras múltiples) | Media | Bajo | Tabla de viabilidad obligatoria en cada guion (§6.1) |
| Desfase entre lo que ve el cliente y lo que hace el servidor con lag | Media | Medio | Reloj con latidos y corrección suave (§9.4) |
| Versiones distintas del mod en cliente y servidor | Alta | Medio | Handshake con mensaje claro (§9.5) |
| Reinicio o crash del servidor en mitad de un evento | Media | Medio | Política `on_restart`, diario persistente y secuelas guardadas (§21.1) |
| Flashback o Replay Mod no reproducen bien los visuales | Media | Alto (para series) | Pruebas específicas y reloj basado en el tiempo de la grabación (§25.4) |
| Un pack de la comunidad con un JSON malicioso o enorme | Baja | Medio | Límites de tamaño, evaluador de expresiones aislado y validación (§22) |
| Uso de sonidos o texturas con derechos de terceros | Media | Alto | Solo recursos propios o CC0, registrados en `CREDITS.md` (§27) |
| Alguien se marea o sufre una crisis en un directo | Baja | Muy alto | Tecla de pánico del cliente, `/le panic` y perfil `safe` (§7.7, §21.5) |

---

## 19. Decisiones pendientes

| # | Pregunta | Recomendación |
|---|----------|---------------|
| D1 | ¿Es **obligatorio el mod en el cliente**? | **Sí.** Los visuales propios, los sonidos y los `MobEffect` necesitan el cliente, y en los servidores de eventos y series se suele distribuir un modpack. Además, al registrar contenido propio, un cliente sin el mod no puede entrar en el servidor, así que se muestra un mensaje claro con el enlace de descarga (§9.5). *Alternativa descartada:* un modo "solo servidor" con partículas vanilla. Obligaría a no registrar contenido propio y empeoraría mucho los visuales. |
| D2 | ¿Afecta a la superficie dentro de un radio o a **toda la dimensión** (supernova)? | ✅ Resuelto en el guion: la supernova es **celeste** y la ve toda la dimensión. Los efectos de juego solo afectan a quien está bajo el cielo (`requires_sky`). |
| D3 | ¿Persistencia por defecto de los cambios en el mundo? | `revert_manual`: el admin decide. |
| D4 | ¿Librería de configuración? | YACL o Cloth Config según cuál esté disponible para 26.1 en el momento de empezar. |
| D5 | ¿Versión mínima exacta? | 26.1, con multiversión (Stonecutter) a partir de la fase 7. |
| D6 | ¿Los mobs hostiles sufren daño o se aprovecha el evento (por ejemplo, el eclipse genera más mobs)? | Configurable por evento. Por defecto, los mobs reciben daño igual que los jugadores. |
| D7 | ¿Nombre definitivo del mod e id? | ✅ **LiveEvents Mod**, de **TakumiStudios**. modid `liveevents`, paquete `com.takumistudios.liveevents`. |
| D8 | ¿Perfil por defecto? | `gameplay`. `cinematic` solo cuando lo pida el admin. |
| D9 | ¿Cuánto duran las secuelas del cielo (nebulosa y púlsar)? | 3 días de juego por defecto, configurable hasta `permanent`. |
| D10 | ¿Qué pasa con las "sombras dobles y triples" del guion? | Descartarlas o aproximarlas solo en `cinematic` con shaders. No se pueden hacer de forma fiel. |
| D11 | ¿Se confirman los colores *propuestos* de la paleta de la Supernova? | Pendiente de revisión por el autor. |
| D12 | ¿Se aprueban los borradores de Meteoritos (§6.4) y Terremoto (§6.5)? | Pendiente de revisión por el autor. |
| D13 | ¿Se permite scripting (JavaScript, Lua...) en los eventos de usuario? | **No.** Los eventos de usuario son solo datos. La lógica nueva se hace con addons Java (§20.6). |
| D14 | ¿Qué licencia? | Código con **LGPL-3.0** (permite addons y modpacks, y obliga a compartir las mejoras del propio mod) o **MIT** (más libre). Recursos (sonidos y texturas) con **todos los derechos reservados** o **CC BY-NC**. Hay que decidirlo antes de publicar código (§27). |
| D15 | ¿Los eventos son letales por defecto? | Sí (`lethal: true`), con `lethal: false` como opción recomendada para series (§8.6). |
| D16 | ¿Qué pasa con los eventos activos al reiniciar el servidor? | `cancel` por defecto, conservando el diario y las secuelas. `resume` es opcional (§21.1). |
| D17 | ¿Integraciones con Twitch o YouTube (el chat lanza eventos) en el mod principal o como addon? | **Addon oficial aparte.** Tiene implicaciones de seguridad y de cuentas externas que no conviene meter en el núcleo (§25.5). |
| D18 | ¿Eventos sincronizados entre varios servidores de una red (Velocity)? | Fuera de la v1. Cada servidor es independiente; se puede coordinar por consola o RCON (§21.7). |

---

## 20. Creación de eventos por usuarios

**Sí, los usuarios del mod pueden crear sus propios eventos.** Es una consecuencia directa del modelo de datos (§5): los eventos oficiales y los de usuarios se definen igual, con JSON que combina módulos sobre una línea de tiempo. Este apartado explica cómo se crean, se empaquetan, se validan, se comparten y se amplían.

### 20.1 Tres niveles de creación

| Nivel | Para quién | Qué puede hacer | Cómo |
|-------|-----------|-----------------|------|
| **1. Variantes** | Admins sin conocimientos técnicos | Cambiar parámetros de un evento existente: duración, colores, radio, intensidad... | `/le create <nuevo_id> from <evento>` o un JSON con `"extends"` en `config/liveevents/events/` |
| **2. Eventos nuevos** | Admins avanzados y creadores de series | Eventos completos combinando los módulos existentes, con sonidos y texturas propios | **Datapack** (definición) + **resource pack** (recursos) |
| **3. Addons** | Desarrolladores Java | **Módulos nuevos** (efectos visuales o de juego que no existan), daños, efectos de estado y paletas propias | Mod de Fabric que depende de la API de LiveEvents |

**Por qué este reparto:**
- Sigue el modelo de Minecraft: **datapack para la lógica y resource pack para los recursos**. Los admins ya lo conocen y se puede publicar en Modrinth como cualquier otro pack.
- **Los jugadores no instalan nada extra.** El servidor les envía las definiciones al conectarse (`DefinitionSync`, §9). Si el evento usa sonidos o texturas propios, el servidor también puede enviarles el resource pack automáticamente (`resource-pack` en `server.properties`).
- **Sin scripts** (D13). Un evento de usuario solo contiene datos y expresiones matemáticas limitadas. Nadie puede ejecutar código arbitrario en un servidor solo con descargar un pack. Quien necesite lógica nueva sube al nivel 3, que es un mod normal y se instala de forma explícita.

### 20.2 Nivel 1: variantes

```json
{
  "format_version": 1,
  "id": "miserie:supernova_roja",
  "extends": "liveevents:supernova",
  "display_name": "Supernova Roja (episodio 12)",
  "palette": { "cyan": "#FF2244", "pulsar": "#FF6688" },
  "overrides": {
    "breakout.light_cut.cuts": [ ["8.0s", "$cyan"], ["9.0s", "$violet"] ],
    "aftermath.duration": "permanent"
  }
}
```

- Solo se escribe lo que cambia. Todo lo demás se hereda.
- `overrides` usa rutas `fase.módulo.parámetro`. Si una ruta no existe, `/le validate` lo indica.
- Si el evento original se actualiza en una versión nueva del mod, la variante recibe las mejoras automáticamente.

### 20.3 Nivel 2: eventos nuevos (datapack + resource pack)

**Estructura:**

```
MiSerie_Eventos/                         ← datapack (va en world/datapacks/)
├─ pack.mcmeta
└─ data/miserie/
   ├─ liveevents/event/
   │  └─ apocalipsis_zombi.json          ← definición del evento
   ├─ liveevents/palette/
   │  └─ tierra_podrida.json             ← paleta de transformación de bloques (opcional)
   └─ tags/block/
      └─ ...                             ← tags propios (opcional)

MiSerie_Recursos/                        ← resource pack (el servidor lo envía a los jugadores)
├─ pack.mcmeta
└─ assets/miserie/
   ├─ sounds.json
   ├─ sounds/apocalipsis/*.ogg
   ├─ textures/liveevents/*.png          ← texturas de cielo, nebulosas, partículas...
   └─ lang/es_es.json                    ← textos traducibles del evento
```

**Del guion al JSON:** el formato de guion (§6.1) se traslada al JSON uno a uno.

| Guion | JSON |
|-------|------|
| Ficha (ámbito, ancla, hora, perfiles) | `scope`, `anchor`, `night_override`, `default_profile` |
| Paleta | `palette` |
| Segmento `MM:SS – MM:SS \| Título` | Una fase: `id`, `from`, `to`, `title` |
| "El Cielo" y "El Caos" | `description.sky` y `description.chaos`. Se muestran en `/le info` y sirven como documentación |
| Cada fila de la tabla técnica | Uno o varios módulos en `modules` |
| Fila "Modo seguro" | `profiles.safe` dentro de cada módulo |
| Secuelas | `aftermath` |

Así, quien escribe un guion siguiendo §6.1 ya tiene hecha la mitad del trabajo.

### 20.4 Validación y errores

- **`"format_version"`** es obligatorio. Si el formato cambia en el futuro, el mod migra automáticamente las versiones antiguas y avisa en el log.
- Se publica un **JSON Schema**. Con una línea `"$schema"` al principio del archivo, VS Code y otros editores ofrecen autocompletado de módulos y parámetros y marcan errores mientras se escribe.
- **`/le validate`** muestra errores precisos:
  > `miserie:apocalipsis_zombi` → `phases[1].modules[2].color`: `$cyna` no existe en la paleta. ¿Querías decir `$cyan`?
- Un evento inválido **se desactiva y se avisa** (en el log y a quien tenga `liveevents.notify`). **Nunca provoca un crash** ni impide que carguen los demás eventos.
- Se avisa (sin bloquear) de configuraciones arriesgadas: radios enormes, demasiados módulos simultáneos, sonidos que no existen en ningún resource pack...

### 20.5 Límites que un evento de usuario no puede saltarse

Los eventos de usuario pasan por **los mismos controles que los oficiales**, y no hay forma de desactivarlos desde el JSON:

| Control | Referencia |
|---------|-----------|
| Presupuesto en ms por tick, topes de bloques y rayos | §14.2, §12.1 |
| Regla de destellos (máximo 3/s, sin rojos saturados grandes) | §7.7 |
| Perfil máximo del servidor y del cliente | §7.10 |
| LOD y presupuesto de partículas del cliente | §7.2, §7.3 |
| Protecciones de terreno, zonas excluidas y bloques inmunes | §13.3, §8.4 |
| Diario de cambios y reversión | §8.5 |
| Permisos | §10 |

Si un JSON pide algo que supera un límite (por ejemplo 10 cortes de color por segundo), **el `VisualDirector` lo recorta** y `/le validate` lo avisa.

### 20.6 Nivel 3: addons Java

- Un addon es un mod de Fabric que declara **LiveEvents Mod como dependencia** y usa el **entrypoint `liveevents`** en su `fabric.mod.json`. Es el patrón estándar de Fabric, el mismo que usan `main` y `client`.
- Desde ese entrypoint, el addon puede registrar:
  - **Tipos de módulo de servidor** (lógica de juego) y **de cliente** (visuales), cada uno con su parser de parámetros y su entrada en el JSON Schema.
  - Paletas de transformación de bloques.
  - Damage types y efectos de estado propios.
- Los módulos de un addon **heredan automáticamente** el LOD, el presupuesto, los perfiles y la accesibilidad del `VisualDirector` y del `WorkScheduler`. El autor del addon no tiene que reimplementarlos y tampoco puede saltárselos.
- Una vez registrado, el módulo de un addon se usa en JSON igual que uno oficial: `{ "type": "miaddon:tornado_de_fuego", ... }`.
- **Contrato de estabilidad:** solo `com.takumistudios.liveevents.api` es público y sigue versionado semántico. Un cambio incompatible solo puede llegar con una versión mayor (2.0, 3.0...).

### 20.7 Espacios de nombres y permisos

- Cada creador usa **su propio espacio de nombres** (`miserie:`, `servidorx:`...). Así dos packs nunca chocan, aunque los dos tengan un evento llamado `apocalipsis`.
- El espacio de nombres `liveevents:` está reservado para los eventos oficiales.
- **El permiso de cada evento se crea automáticamente:** `liveevents.start.<namespace>.<evento>`. LuckPerms lo muestra y permite asignarlo sin configurar nada más. Por ejemplo, un admin puede dar a los moderadores de una serie solo los eventos de `miserie`, con `liveevents.start.miserie.*`.

### 20.8 Herramientas para quien crea eventos

| Herramienta | Para qué sirve |
|-------------|----------------|
| `/le reload` | Recarga en caliente las definiciones y las sincroniza a los clientes, sin reiniciar |
| `/le validate` | Encuentra errores antes de lanzar |
| `/le preview <evento>` | Reproduce el evento **solo para el admin**: los demás jugadores no ven nada y el mundo no se modifica |
| `timescale=0.5` | Reproduce a cámara lenta para ajustar cortes y sincronías de sonido |
| `/le seek <id> <segundo>` | Salta directamente al segmento que se está ajustando |
| `/le debug timeline` | Overlay con el segundo actual, la fase, los módulos activos y el coste en ms |

**Flujo de trabajo recomendado:** escribir el guion con el formato de §6.1 → pasarlo al JSON → `/le validate` → `/le preview` a cámara lenta → ajustar → `/le reload` → probar con jugadores en `gameplay` y en `safe`.

### 20.9 Distribución y ejemplos

- **LiveEvents Example Pack (oficial):** un datapack y un resource pack con:
  - Una variante comentada (nivel 1).
  - Un evento nuevo completo y comentado, con su guion en formato §6.1 junto al JSON (nivel 2).
  - Un sonido y una textura propios.
  Sirve como plantilla para copiar y modificar.
- **Addon de ejemplo** (nivel 3): un repositorio plantilla con un módulo de servidor y uno de cliente.
- **Guía para creadores:** del guion al JSON paso a paso, la referencia de todos los módulos con sus parámetros y la lista de límites.
- Los packs de la comunidad se pueden publicar en Modrinth como datapack o resource pack, indicando LiveEvents Mod como dependencia.
- *Futuro (fase 7+):* un **editor visual de línea de tiempo** en el juego, con pistas por capa (cielo, mundo, cámara, audio, juego), que genere el JSON automáticamente.

---

## 21. Operación y robustez

### 21.1 Reinicios y crashes

| Qué se guarda en disco | Qué ocurre al reiniciar |
|------------------------|-------------------------|
| Diario de cambios (§8.5) | Se conserva siempre. `/le revert` sigue funcionando |
| Secuelas del cielo | Se conservan y se envían a quien entra |
| Eventos programados (§25.2) | Se conservan. Si la hora pasó con el servidor apagado, se avisa al admin y **no** se lanzan solos |
| Instancias activas | Según `on_restart`: **`cancel`** (por defecto, el evento termina limpio) o `resume` (sigue desde el segundo en que se quedó) |

- Se guarda de forma **incremental**: si el servidor se cuelga, se pierde como mucho el último segundo de diario.
- Al cerrar el servidor de forma ordenada, se terminan los cambios de bloques pendientes antes de guardar.

### 21.2 Pausa

- `/le pause` congela la línea de tiempo en el servidor. Los clientes congelan los visuales y bajan el audio (`EventPause`).
- **Un jugador, en local:** cuando el juego se pausa (menú abierto), el servidor integrado se detiene y el evento también. Al volver, sigue donde estaba.
- **Servidor vacío:** con `pause_when_empty`, el evento se pausa si no queda nadie conectado y se reanuda cuando entra el primero.

### 21.3 Un jugador y LAN

- El mod funciona en **mundos de un jugador**. Para lanzar eventos hacen falta los trucos activados (nivel de OP 2), igual que cualquier comando de administración.
- En **LAN**, quien abre el mundo es el admin. Los demás necesitan el mod (D1).

### 21.4 Watchdog

- Si el MSPT del servidor pasa de `watchdog_mspt` durante `watchdog_seconds`, el `Watchdog` reduce el presupuesto al mínimo. Si aun así no se recupera, **pausa los eventos** y avisa a quien tenga `liveevents.notify`.
- Nunca cancela ni revierte por su cuenta. La decisión final es siempre del admin.

### 21.5 Parada de emergencia: `/le panic`

Un solo comando, pensado para usarlo en un directo sin pensar:
1. Detiene **todas** las instancias de todas las dimensiones.
2. Manda `PanicStop`: todos los clientes cortan **al instante** el post-procesado, los destellos, el temblor y el sonido de los eventos. No hay fundidos.
3. Vacía la cola de cambios de bloques pendientes y quita los efectos de estado del mod a todos.
4. **No revierte** el mundo; eso se decide después con `/le revert`.
5. Lo anota en el registro de auditoría.

Se complementa con la **tecla de pánico del cliente** (§7.7), que cada jugador puede usar solo para sí mismo.

### 21.6 Registro y diagnóstico

- Logs con prefijo `[LiveEvents]` y niveles claros. En modo normal, solo avisos y errores.
- `/le debug report` genera un informe para adjuntar a un reporte de error (§27).
- Si un módulo lanza una excepción, **se desactiva ese módulo en esa instancia**, se avisa y el resto del evento continúa. Un error en un efecto nunca debe tumbar el servidor ni el cliente.

### 21.7 Redes de servidores (Velocity / BungeeCord)

- Cada servidor de la red ejecuta sus propios eventos de forma independiente.
- Para lanzar el mismo evento en varios servidores a la vez se puede usar la consola o RCON de cada uno. Una sincronización entre servidores queda fuera de la v1 (D18).

---

## 22. Seguridad

| Riesgo | Medida |
|--------|--------|
| Un cliente modificado intenta lanzar eventos | **No hay paquetes cliente → servidor con poder de acción** (§9.1). El panel de administración (§25.1) envía **comandos normales**, que el servidor comprueba con permisos como cualquier otro |
| Un servidor malicioso intenta colgar el cliente | El cliente valida y limita todo lo que recibe: tamaño de paquetes, radios, número de elementos e intensidades (§9.6) |
| Un pack de la comunidad con un JSON abusivo | Tamaño máximo por archivo, profundidad máxima de herencia (`extends`) y detección de herencias circulares |
| Expresiones maliciosas en el JSON | El evaluador solo admite **aritmética y una lista cerrada de funciones** (`min`, `max`, `clamp`, `lerp`...). Sin bucles, sin acceso a nada externo, con límite de longitud y de profundidad |
| Abuso de comandos (un moderador que lanza eventos sin parar) | `start_rate_limit` por persona, `cooldown` por evento y registro de auditoría |
| Bloques de comandos | Ejecutan comandos con nivel de OP 2. Si los usan jugadores sin permiso (por ejemplo, en un mapa de aventura), conviene desactivar `enable-command-block` o proteger esos bloques. Así se documentará |
| Webhook de Discord | La URL se guarda solo en la configuración del servidor, nunca se envía a los clientes ni aparece en los logs |
| Datos personales | El registro de auditoría guarda nombres y UUID de los jugadores. Se borra automáticamente pasados `audit_retention_days`. **No hay telemetría de ningún tipo** |

---

## 23. Pruebas y calidad

### 23.1 Pruebas automáticas

| Tipo | Qué se prueba |
|------|---------------|
| **Unitarias** (JUnit) | Lectura del JSON, herencia `extends`, `overrides`, evaluador de expresiones, paletas, migración de `format_version`, conversión de segundos a ticks, regla de destellos, `ProfileResolver` |
| **Determinismo** | Con la misma semilla, el servidor y el cliente generan exactamente las mismas trayectorias, cráteres y grietas |
| **GameTests de servidor** (Fabric) | Daño y `requires_sky`, `lethal: false`, diario y reversión (también con bloques modificados por jugadores), protecciones, presupuesto por tick, `on_restart`, `/le panic` |
| **GameTests de cliente** (Fabric) | Capturas en momentos clave del evento (por ejemplo 8,1 s y 20,0 s de la supernova) en cada perfil, comparadas con las de referencia para detectar cambios visuales no deseados |
| **Rendimiento** | Spark con 20, 50 y 100 jugadores simulados. El build falla si se superan los objetivos de §14.1 |

La CI (GitHub Actions) ejecuta las pruebas unitarias y los GameTests en cada push.

### 23.2 Matriz de compatibilidad manual

Antes de cada versión se prueba la combinación de: vanilla · Sodium · Sodium + Iris (Complementary, BSL, Photon) · Distant Horizons · Flashback · C2ME · LuckPerms · un mod de protección, con los 3 perfiles y en calidad Baja y Ultra.

### 23.3 Protocolo de prueba con jugadores

- Grupo pequeño de jugadores que no conocen el evento.
- Preguntas después: "¿qué ha pasado?" (¿se entiende?), "¿impresiona?" (escala del 1 al 5), "¿te has mareado o te ha molestado algo?".
- Se graba el evento en `cinematic` y en `gameplay` y se pasa por un analizador de fotosensibilidad (§7.7). **Si no lo pasa, no se publica.**

---

## 24. Experiencia del jugador: casos límite

| Situación | Comportamiento |
|-----------|----------------|
| En una cueva o bajo techo | Efectos de pantalla reducidos (§7.11) y sin efectos que exigen `requires_sky`. Se oye el evento amortiguado |
| Bajo el agua | Visuales del cielo atenuados por la niebla del agua. El sonido se amortigua como el resto de sonidos vanilla |
| Volando con élitros | El temblor de cámara se reduce mucho para no provocar caídas. Los empujones se aplican con un tope de velocidad |
| En un vehículo (barco, caballo, vagoneta) | Temblor reducido. El empujón se aplica al vehículo, no al jugador |
| Muerto o en la pantalla de muerte | No se dibujan efectos sobre la pantalla de muerte. Al reaparecer, el evento sigue donde esté |
| Reaparece durante el evento | Recibe el estado actual. Si reaparece bajo el cielo en mitad de una fase dañina, tiene unos segundos de gracia configurables |
| Espectador | Ve todos los visuales y no recibe efectos de juego. Es ideal para el cámara de una serie |
| Creativo | Ve todo y no recibe daño. Los demás efectos (lentitud, náusea) son configurables |
| Inactivo (AFK) | Sin trato especial. Si el servidor usa un mod de AFK, se puede excluir a esos jugadores con `BeforeEntityAffected` (§15) |
| Cambia de dimensión | Recibe el estado de los eventos de la nueva dimensión. Los visuales de la anterior se apagan con un fundido corto |
| Se desconecta y vuelve | Recibe el estado completo y se pone al día (§9.3) |
| HUD oculto (F1) | Los efectos se siguen viendo; no hay nada del HUD que depender. Es útil para grabar |
| Menú o inventario abierto | Los efectos de pantalla siguen detrás del menú, atenuados |
| Captura de pantalla (F2) | Captura la imagen tal como se ve, con efectos |
| FPS muy bajos | Calidad automática (§7.14). Los parpadeos siguen el tiempo real (§7.7) |
| Ping alto | El reloj compensa la latencia (§9.4). Los efectos de juego siempre los decide el servidor |
| Pantalla ultrapanorámica o FOV alto | Los efectos de pantalla se calculan en proporción a la pantalla, no en píxeles fijos |

---

## 25. Herramientas para series y directos

### 25.1 Panel de administración en el juego (fase 7+)

- Pantalla de cliente (`/le gui` o una tecla) para quien tenga `liveevents.gui`.
- Muestra la lista de eventos con su descripción del guion, el selector de perfil y la posición, un botón de lanzar con cuenta atrás, las instancias activas con pausa, salto y parada, y un **botón de pánico** siempre visible.
- **Por dentro solo envía comandos normales**, que el servidor comprueba con permisos (§22). No añade ningún poder nuevo.

### 25.2 Programación

- `/le schedule supernova at 2026-10-10T21:00 profile=cinematic`. Usa la hora real y la zona horaria del servidor (`timezone`).
- Se guarda en disco y sobrevive a los reinicios (§21.1).
- Aviso automático a los jugadores X minutos antes (configurable).

### 25.3 Secuencias

Una secuencia es un JSON que encadena eventos y comandos para un episodio o un evento de servidor:

```json
{
  "format_version": 1,
  "id": "miserie:episodio_12",
  "steps": [
    { "at": "0s",   "command": "title @a title {\"text\":\"El cielo no es lo que parece\"}" },
    { "at": "10s",  "event": "liveevents:supernova", "profile": "cinematic" },
    { "at": "75s",  "event": "liveevents:meteor_shower", "on": "@r", "args": { "count": 25 } },
    { "at": "180s", "event": "liveevents:earthquake", "position": "0 64 0" }
  ]
}
```

- Se lanza con `/le sequence start miserie:episodio_12` y se puede pausar o parar como un evento.
- Los comandos de una secuencia se ejecutan **con los permisos de quien la lanza**, no con permisos elevados.

### 25.4 Grabación (Flashback / Replay Mod)

- Para las series, lo más importante es que los visuales **se graben y se reproduzcan bien**. Como los visuales se generan en el cliente a partir de paquetes y semillas, una grabación de paquetes puede volver a reproducirlos.
- Requisitos:
  - En una repetición, el reloj del evento sigue el **tiempo de la grabación**.
  - Debe funcionar al saltar adelante y atrás y a cámara lenta.
  - Los efectos de pantalla (whiteout, glitch) se pueden **desactivar solo en la repetición**, para que quien edita la serie decida en postproducción.
- Se añade a la matriz de compatibilidad (§13.2) como prioridad alta.

### 25.5 Integraciones externas

| Integración | Dónde | Notas |
|-------------|-------|-------|
| **Discord** (aviso al empezar o terminar un evento) | Mod principal, opcional | Webhook en `server.json` (§22) |
| **RCON / consola** | Ya funciona | Todos los comandos sirven por RCON, útil para paneles web o bots propios |
| **Twitch / YouTube** (el chat o los canjes de puntos lanzan eventos) | **Addon oficial aparte** (D17) | Con lista de eventos permitidos, cooldowns, perfil máximo `gameplay` y confirmación opcional de un moderador |

### 25.6 Placeholders

Con **Text Placeholder API** instalado, el mod ofrece *placeholders* para TAB, scoreboards, hologramas y mensajes:
- `%liveevents:active%`: nombre del evento activo.
- `%liveevents:time_left%`: tiempo restante.
- `%liveevents:phase%`: título del segmento actual del guion.
- `%liveevents:next_scheduled%`: próximo evento programado.

---

## 26. Localización

- Todos los textos (mensajes, títulos, subtítulos, muertes, configuración, errores de `/le validate`) usan **claves de traducción**. No hay ningún texto escrito directamente en el código.
- Idiomas en la v1: **español (`es_es`)** e **inglés (`en_us`)**. La comunidad puede añadir más con resource packs o contribuciones.
- Los textos del guion de un evento (`display_name`, `title` de cada fase y `description`) admiten una clave de traducción o un texto literal. Un pack de la comunidad puede traer su propio `lang/` en el resource pack (§20.3).
- La consola y los logs del servidor van en **inglés**, para facilitar el soporte.

---

## 27. Publicación, licencia y recursos

| Tema | Decisión propuesta |
|------|--------------------|
| Versionado | **SemVer** (`1.2.3`) más la versión de Minecraft en el nombre del archivo, por ejemplo `liveevents-1.0.0+mc26.1.jar`. `CHANGELOG.md` en cada versión |
| Publicación | **Modrinth** y **CurseForge**, de forma automática desde la CI al crear una etiqueta de versión |
| Licencia | Pendiente (D14). Recomendación: LGPL-3.0 para el código y derechos reservados o CC BY-NC para los recursos |
| Sonidos y texturas | **Solo recursos propios o con licencia CC0**, nunca sacados de películas, juegos ni bancos sin licencia. Cada archivo queda anotado en `CREDITS.md` con su origen y su licencia |
| Marca | Nombre "LiveEvents Mod", autor "TakumiStudios", icono propio en `fabric.mod.json`, enlaces al repositorio, a la web o wiki y a Discord |
| Documentación | Wiki o web con: guía de instalación, guía para admins, guía para creadores (§20.9), referencia de módulos generada desde el JSON Schema y preguntas frecuentes |
| Soporte | Plantilla de *issues* en GitHub que pide el informe de `/le debug report`. Las incidencias de fotosensibilidad tienen **prioridad máxima** |
| Compatibilidad de versiones | La API (`api/`) y el formato JSON (`format_version`) se mantienen estables dentro de la misma versión mayor del mod |

---

*Fin del documento. Siguiente paso propuesto: cerrar las decisiones abiertas (D1, D3–D6, D8–D18) y empezar la Fase 0.*
