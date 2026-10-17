# Changelog

## 1.1.0

- Giant meteors: 30-block body radius, 100-block crater radius, 1% random occurrence and explicit `size=giant` strikes.
- Expanding 80-block/s shockwaves reach 300 blocks, apply one distance-scaled hit and respect protections, profiles and zero destructive power.
- Large post-impact clouds and distance-based white flashes with gradual visual recovery. Network protocol 3 requires updated clients and servers.

- Added five weighted meteor sizes with matching bodies, smoke, blast radius and damage.
- Original layered meteor audio: atmospheric entry, close/distant impacts, debris and deep tails; distance delay, size-dependent pitch and bounded voices.
- Fixed smoke depth masking water/clouds, removed meteor screen tints and made impact flashes independent of frame rate.

- Meteor impacts now use server-fixed positions, surface variants and immutable announced power (network protocol 2).
- Added world-space meteor mesh, tapered trails, 3D smoke clusters, impact warnings and distant Blender atlases.
- Added administrative meteor targeting and manual strikes; power, brutality and duration controls.
- Crater preparation runs off-thread; block application and cooling resume within their work budgets.
- Configurable smoke, wind and cooling survive normal event closure; owned hot blocks recover after restart and remain reversible.
- Optional Common Protection API bridge rejects protected impact targets and block changes.
- Blender v2 scenes, videos, multiview reviews and validated exports for earth, water and snow.
- Added meteor regression tests and asset validation to GitHub CI.


## 1.0.0

Primera versión pública. Minecraft 26.1.x, 26.2 y 26.3 (Fabric, Java 25).

- 10 eventos oficiales: Supernova de Betelgeuse, Lluvia de meteoritos, Terremoto, Aurora boreal, Eclipse total,
  Tormenta solar, Gran meteorito, Ventisca, Lluvia ácida y Agujero negro.
- Definiciones JSON con 48 módulos, herencia (`extends`/`overrides`), paletas, expresiones seguras, perfiles,
  JSON Schema y `/le validate` con errores precisos y sugerencias.
- Director de eventos con cola, cuenta atrás, enfriamientos, un evento celeste por dimensión, pausa, salto de fase,
  `seek`, cambios en vivo, watchdog y persistencia ante reinicios (`on_restart`).
- Planificador de trabajo con presupuesto por tick, turnos rotatorios y tope de cambios de bloque.
- Diario de cambios en disco y `/le revert` gradual que respeta lo construido por los jugadores.
- Visuales en el cliente: cuerpos celestes, bóveda rota, anillos, plasma, auroras, nebulosa, púlsar, flares, whiteout,
  glitch, cortes de luz, viñetas, estelas, impactos, ondas sísmicas P/S y temblor de cámara, con LOD, presupuesto de
  partículas, exposición según el cielo visible y calidad automática.
- Accesibilidad: perfiles `cinematic`/`gameplay`/`safe`, regla de destellos (WCAG 2.3.1), opciones vanilla respetadas,
  tecla de pánico, aviso de fotosensibilidad y `/le panic`.
- Permisos con la API de Fabric (LuckPerms compatible) y nodo automático por evento.
- `/le preview`, `/le schedule`, secuencias, panel de administración (`/le gui`), placeholders, avisos a Discord,
  auditoría y `/le debug timeline|perf|report`.
- API para addons: callbacks y registro de módulos nuevos (servidor y cliente).
- LiveEvents Example Pack, documentación completa y wiki con capturas.
- Traducciones: inglés y español.
