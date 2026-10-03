# LiveEvents

[![Build](https://github.com/AloneX15/LiveEvents-mod/actions/workflows/build.yml/badge.svg)](https://github.com/AloneX15/LiveEvents-mod/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/AloneX15/LiveEvents-mod?sort=semver)](https://github.com/AloneX15/LiveEvents-mod/releases)
![Minecraft](https://img.shields.io/badge/Minecraft-26.x-62b47a)
![Loader](https://img.shields.io/badge/loader-Fabric-dbd0b4)
[![Wiki](https://img.shields.io/badge/docs-wiki-blue)](https://github.com/AloneX15/LiveEvents-mod/wiki)

Admin-triggered live cosmic and natural events (supernovas, meteor showers, earthquakes) with cinematic client-side
visuals and real, revertible effects on the world.

Eventos cósmicos y naturales en directo, lanzados por los administradores, con visuales cinematográficos en el cliente
y efectos reales y reversibles en el mundo. Pensado para eventos de servidor, series y directos.

![Supernova de Betelgeuse: la bóveda celeste se rompe](docs/images/supernova_05_shattered_sky.png)

## Características

- **10 eventos oficiales:** Supernova de Betelgeuse, Lluvia de meteoritos, Terremoto, Aurora boreal, Eclipse total,
  Tormenta solar, Gran meteorito, Ventisca, Lluvia ácida y Agujero negro.
- **Efectos reales:** radiación, quemaduras, rayos, cráteres con enfriamiento, grietas a lo largo de una falla, nieve
  que se derrite, superficie chamuscada, atracción gravitatoria… con tipos de daño y mensajes de muerte propios.
- **Todo reversible:** cada cambio de bloque se apunta en un diario en disco; `/le revert` lo deshace poco a poco sin
  tocar lo que los jugadores hayan construido encima.
- **100 % configurable:** un evento es un JSON que combina [48 módulos](docs/modulos.md) sobre una línea de tiempo.
  Variantes con `extends`, eventos nuevos por datapack, JSON Schema para el editor y `/le validate` con errores
  precisos y sugerencias.
- **Pensado para directos:** perfiles `cinematic`/`gameplay`/`safe`, regla de destellos (WCAG 2.3.1) en todos los
  perfiles, tecla de pánico por jugador, `/le panic`, previsualización solo para el admin, cuenta atrás, secuencias,
  programación con hora real, panel de administración y avisos a Discord.
- **Optimizado:** el servidor manda unos pocos paquetes y el cliente simula los visuales de forma determinista; el
  trabajo de bloques pasa por un planificador con presupuesto por tick; nunca se cargan chunks.
- **Permisos** con la API de Fabric (LuckPerms compatible) y nivel de OP de respaldo; nodo automático por evento.
- **API para addons:** callbacks (inicio, fase, fin, cambio de bloque, entidad afectada) y registro de módulos nuevos.

| ![](docs/images/supernova_07_plasma.png) | ![](docs/images/meteor_shower_02_impacts.png) | ![](docs/images/black_hole.png) |
|---|---|---|
| Tormenta de plasma | Lluvia de meteoritos | Agujero negro |

## Instalación

1. Instala [Fabric Loader](https://fabricmc.net/use/) y [Fabric API](https://modrinth.com/mod/fabric-api).
2. Descarga el jar de tu versión de Minecraft desde [Releases](https://github.com/AloneX15/LiveEvents-mod/releases) y
   ponlo en `mods/` **del servidor y de cada cliente**.
3. `/le list` debe mostrar los 10 eventos. Empieza por la [guía para administradores](docs/guia-administradores.md).

Build de desarrollo (último `main`, ya probado): [release `dev`](https://github.com/AloneX15/LiveEvents-mod/releases/tag/dev).

```mcfunction
/le start supernova delay 10 profile=cinematic
/le preview earthquake
/le revert last confirm
/le panic
```

## Compatibilidad

| Minecraft | Estado |
|---|---|
| 26.1.x | ✅ |
| 26.2.x | ✅ |
| 26.3.x | ✅ |

Probado en la CI junto a Lithium y FerriteCore. Integraciones opcionales: LuckPerms, Mod Menu y Text Placeholder API.
Los efectos se dibujan en el HUD, así que no dependen del cielo ni del post-procesado vanilla (Sodium e Iris).
Más en [Rendimiento y compatibilidad](docs/rendimiento-y-compatibilidad.md). Si encuentras un conflicto con otro mod,
abre un [issue](https://github.com/AloneX15/LiveEvents-mod/issues).

## Configuración

- Servidor: `config/liveevents/server.json` (límites, presupuesto, zonas excluidas, perfiles, watchdog, Discord…).
- Cliente: `config/liveevents/client.json` o Mod Menu (calidad, destellos, temblor, perfil máximo que aceptas…).
- Eventos propios: `config/liveevents/events/` o datapacks (`data/<espacio>/liveevents/event/`).

Todo detallado en [Configuración](docs/configuracion.md), [Comandos](docs/comandos.md), [Permisos](docs/permisos.md) y
[Crear eventos](docs/crear-eventos.md). Documentación completa en la [wiki](https://github.com/AloneX15/LiveEvents-mod/wiki).

## Desarrollo

```bash
./gradlew :26.3:build                       # compila, tests unitarios y gametests de servidor
./gradlew :26.3:runGameTest -PcompatPack    # gametests con Lithium y FerriteCore
./gradlew :26.3:runClientGameTest           # test de cliente con capturas (las de la wiki)
./gradlew :26.3:test -PgenerateDocs         # regenera el JSON Schema y la referencia de módulos
./gradlew :26.3:runClient                   # abre el juego
node tools/build-wiki.mjs                   # genera la wiki a partir de docs/
```

Estructura: `core/` (lógica pura, probada con JUnit: definiciones, expresiones, planificador, reloj, regla de destellos),
`server/` (director, módulos, diario, comandos), `network/`, `api/` (estable) y `client/` (estado, director visual,
render por HUD). Errores resueltos en [docs/registro-de-errores.md](docs/registro-de-errores.md) y estado frente al plan
en [docs/estado-del-plan.md](docs/estado-del-plan.md).

## Licencia

Código bajo [MIT](LICENSE). Texturas generadas por el propio proyecto (`tools/gen-textures.mjs`, CC0); los sonidos
reutilizan eventos de sonido de Minecraft sin redistribuir archivos. Ver [CREDITS.md](CREDITS.md).

---

Creado por **TakumiStudios**.
