# Estado del plan

Correspondencia entre `LiveEvents_Plan.md` y lo implementado en la versión 1.0.0. ✅ hecho · ⚠️ hecho con una
adaptación · ⏳ pendiente.

## Hoja de ruta (§17)

| Fase | Estado | Notas |
|---|---|---|
| 0. Preparación | ✅ | Plantilla TakumiStudios: Stonecutter (26.1.2, 26.2, 26.3), Java 25, mapeos oficiales, CI, licencia MIT (D14), comandos y permisos |
| 1. Núcleo | ✅ | Definiciones JSON con `format_version`, JSON Schema, `/le validate`, director, planificador, red, reloj, handshake, entrar tarde, pausa, `/le panic`, reinicios, tests |
| 2a. Base visual | ⚠️ | Perfiles, regla de destellos, exposición según el cielo, `celestial_body`, `light_cut`, `audio_duck`… con un **backend de render por HUD** (ver abajo) |
| 2b. Supernova | ✅ | Guion completo de §6.3, `radiation_burn`, día artificial, derretido, rayos, chamuscado, secuelas y diario |
| 3. Meteoritos y terremoto | ✅ | `meteor_impacts` y `seismic_wave` (ondas P y S por cliente), cráteres, grietas, enfriamiento |
| 4. Pulido visual | ✅ | LOD, capas de sonido con retraso, subtítulos, calidad automática, mezcla de eventos, clima y dimensiones |
| 5. Compatibilidad y rendimiento | ⚠️ | Compat pack en la CI, watchdog, gametests de presupuesto; Common Protection API y la matriz manual (Iris, Sodium, Flashback, C2ME, DH) pendientes |
| 6. Lanzamiento | ✅ | Documentación y wiki, guía para creadores, Example Pack, `/le schedule`, secuencias, Discord, placeholders, `en_us` y `es_es` |
| 7+. Expansión | ⚠️ | Eventos de las fases 2–4 hechos con datos (gran meteorito, tormenta solar, eclipse, aurora, ventisca, lluvia ácida, agujero negro), multiversión y panel de administración. Pendientes: editor visual de línea de tiempo, addon de Twitch/YouTube (D17), erupción volcánica, tornado y tsunami |

## Adaptaciones respecto al plan

| Plan | Implementación | Motivo |
|---|---|---|
| Post-procesado con cadenas de shaders y geometría en el mundo (§7.1, §7.9) | **Render por HUD**: los objetos del cielo se proyectan sobre la pantalla con texturas propias; destellos, whiteout, glitch, viñeta y tintes son capas de pantalla; las estelas y ondas se proyectan desde el mundo | Es el "modo alternativo" del propio plan (§7.5, §7.9): funciona igual en 26.1–26.3, con Sodium y con Iris, y la API de render de Fabric cambia mucho entre esas versiones (`fabric-rendering-v1` 23 → 25 → 27). Los shaders propios quedan como mejora futura detrás de la misma capa |
| Tinte del cielo con un mixin del renderizado del cielo (§7.5) | Tinte por encima/debajo de la línea del horizonte proyectada | Sin mixins de render; compatible con mods de cielo y shaders |
| `fabric-permissions-api` de lucko (§10) | **API de permisos de Fabric** (incluida en Fabric API 26.x) | Fabric API 26.x ya trae su propia API con nivel de OP de respaldo; LuckPerms la implementa. Los nodos son `liveevents:start.supernova` |
| `LiveEventsAPI.start(...)` (§15) | Lanzar con comandos desde código; callbacks y registro de módulos sí | Así todo pasa por permisos, auditoría y límites |
| Sonidos propios (§27) | Eventos de sonido que **reutilizan sonidos vanilla** con otro tono y volumen | Sin archivos de terceros; los packs pueden sustituirlos por `.ogg` propios |
| `audio_duck` baja música y ambiente | Detiene la música vanilla | Bajar el ambiente exigiría un mixin en el motor de sonido |
| `shadow_whip` con shaders | Parpadeo de luz sincronizado | Mover el sol de un shaderpack no es posible sin integrarse con Iris |
| Brújulas que giran en la tormenta solar | No implementado | Necesita un mixin en el ítem |
| Caídas de grava/arena registradas al aterrizar (§8.5) | El mod mueve el bloque en columna (origen y destino van al diario) | Así se puede revertir; las estalactitas sí caen como entidad y no se pueden recuperar |
| Las secuelas giran con las estrellas | Fijas en la bóveda; solo se ven de noche | — |
| F1 mantiene los efectos (§24) | Con el HUD oculto no se dibujan | Los efectos se dibujan en el HUD |

## Decisiones (§19)

| # | Decisión tomada |
|---|---|
| D1 | Mod obligatorio en el cliente |
| D3 | `revert_manual` por defecto |
| D4 | Sin librería de configuración: pantalla propia con Mod Menu y `client.json` |
| D5 | 26.1 como mínimo, con Stonecutter desde el principio (26.1.2, 26.2, 26.3) |
| D6 | Configurable por evento (`affects`); por defecto jugadores y mobs hostiles |
| D8 | `gameplay` por defecto |
| D9 | Secuelas 3 días de juego, configurable hasta `permanent` |
| D10 | Sombras dobles descartadas |
| D11 | Colores propuestos de la paleta de la supernova aceptados tal cual (se pueden cambiar con una variante) |
| D12 | Borradores de meteoritos y terremoto implementados como estaban |
| D13 | Sin scripting en los eventos de usuario |
| D14 | Licencia MIT |
| D15 | Letales por defecto; `lethal: false` disponible |
| D16 | `cancel` al reiniciar por defecto; `resume` opcional |
| D17 | Twitch/YouTube como addon aparte |
| D18 | Sin sincronización entre servidores en la v1 |

---

Creado por **TakumiStudios**.
