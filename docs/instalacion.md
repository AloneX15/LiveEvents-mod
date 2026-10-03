# Instalación

## Requisitos

| | Versión |
|---|---|
| Minecraft | 26.1.x, 26.2 o 26.3 (un jar por versión) |
| Fabric Loader | 0.19.5 o superior |
| Fabric API | la de tu versión de Minecraft |
| Java | 25 |

LiveEvents es **obligatorio en el servidor y en los clientes**: registra contenido propio (el efecto
*Quemadura por radiación* y sus sonidos), así que un cliente sin el mod no puede entrar. Es lo normal en servidores de
eventos y series, que suelen repartir un modpack.

## Pasos

1. Instala [Fabric Loader](https://fabricmc.net/use/) y descarga [Fabric API](https://modrinth.com/mod/fabric-api).
2. Descarga de [Releases](https://github.com/AloneX15/LiveEvents-mod/releases) el jar de **tu** versión de Minecraft
   (`liveevents-1.0.0+mc26.3.jar` para 26.3, etc.).
3. Pon Fabric API y LiveEvents en la carpeta `mods/` del servidor **y** de cada cliente.
4. Arranca el servidor. Se crean:
   - `config/liveevents/server.json`: opciones del servidor ([Configuración](configuracion.md)).
   - `config/liveevents/events/`: variantes de eventos de los admins ([Crear eventos](crear-eventos.md)).
   - `config/liveevents/sequences/`: secuencias ([Secuencias y programación](secuencias-y-programacion.md)).
5. Comprueba que todo cargó: `/le list` (debe mostrar los 10 eventos oficiales) y `/le validate` (sin errores).

Build de desarrollo (último `main`, ya probado en la CI): [release `dev`](https://github.com/AloneX15/LiveEvents-mod/releases/tag/dev).

## Mods opcionales

| Mod | Para qué |
|---|---|
| [LuckPerms](https://luckperms.net/) | Permisos finos por evento, grupo y contexto ([Permisos](permisos.md)) |
| [Mod Menu](https://modrinth.com/mod/modmenu) | Pantalla de opciones del cliente |
| [Text Placeholder API](https://modrinth.com/mod/placeholder-api) | Placeholders `%liveevents:active%`, `%liveevents:time_left%`… para TAB, scoreboards y hologramas |

Ninguno es necesario: sin ellos el mod funciona igual (permisos por nivel de OP y opciones en `client.json`).

## Un jugador y LAN

Funciona en mundos de un jugador: para lanzar eventos hacen falta los trucos activados (nivel de OP 2), igual que
cualquier comando de administración. En LAN, quien abre el mundo es el admin y los demás necesitan el mod.

---

Creado por **TakumiStudios**.
