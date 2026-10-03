# Comandos

Comando raíz: `/liveevents`, con el alias `/le`. Todos funcionan desde la **consola, RCON, bloques de comandos y
funciones** (`.mcfunction`), con autocompletado. Los ids de evento se pueden escribir completos
(`liveevents:supernova`) o sin el espacio de nombres oficial (`supernova`).

| Comando | Permiso (OP de respaldo) | Descripción |
|---|---|---|
| `/le list [definitions\|active]` | `list` (2) | Lista definiciones o instancias activas |
| `/le info <evento>` | `list` (2) | Fases, duración, parámetros y secuelas |
| `/le start <evento> [opciones]` | `start` o `start.<evento>` (2) | Lanza un evento ([opciones](guia-administradores.md#lanzar-un-evento)) |
| `/le preview <evento> [opciones]` | `preview` (2) | Visuales y sonido solo para ti, sin efectos de juego |
| `/le pause <id>` · `/le resume <id>` | `control` (2) | Pausa o reanuda |
| `/le skip <id> [fase]` | `control` (2) | Salta a la fase siguiente o a una concreta |
| `/le set <id> <parámetro> <valor>` | `edit` (3) | Cambia un parámetro en vivo |
| `/le stop <id>` · `/le stop all confirm` | `stop` (2) | Cancela una o todas las instancias |
| `/le revert list` | `revert` (3) | Diarios de cambios guardados |
| `/le revert <id\|last\|diario> [confirm]` | `revert` (3) | Revierte los cambios en el mundo |
| `/le reload` | `reload` (3) | Recarga `server.json` y las definiciones y las envía a los clientes |
| `/le validate [all\|evento]` | `validate` (2) | Errores de las definiciones, con archivo, campo y motivo |
| `/le create <nuevo_id> from <evento>` | `create` (3) | Copia un evento como variante editable en `config/liveevents/events/` |
| `/le scars list` · `/le scars clear <all\|id>` | `edit` (3) | Secuelas persistentes del cielo |
| `/le seek <id> <segundo>` | `debug` (3) | Salta a un momento concreto |
| `/le timescale <id> <0.1–2.0>` | `debug` (3) | Cambia la velocidad de una instancia |
| `/le debug timeline` | `debug` (3) | Overlay con el segundo, la fase y los módulos activos |
| `/le debug perf` | `debug` (3) | Coste del mod por tick, cola pendiente, MSPT |
| `/le debug report` | `debug` (3) | Informe para un *issue*, sin datos personales |
| `/le panic` | `panic` (2) | **Parada de emergencia** |
| `/le schedule <evento> at <fecha-hora> [opciones]` | `schedule` (3) | Programa un lanzamiento con hora real |
| `/le schedule list` · `/le schedule cancel <id>` | `schedule` (3) | Lista o cancela programados |
| `/le sequence start <id>` · `stop` · `list` | `schedule` (3) | Secuencias de eventos y comandos |
| `/le gui` | `gui` (2) | Panel de administración |

Los nodos de permiso completos son `liveevents:<nodo>` (por ejemplo `liveevents:start.supernova`); ver
[Permisos](permisos.md).

## Ejemplos

```mcfunction
# Lanzar con cuenta atrás de 10 s y en cinematic para grabar
/le start supernova delay 10 profile=cinematic

# Lluvia de meteoritos sobre un jugador al azar, más densa
/le start meteor_shower on @r count=80 radius=96

# Programar para una hora concreta (zona horaria de server.json)
/le schedule supernova at 2026-10-10T21:00 profile=cinematic

# Crear una variante y editarla
/le create miserie:supernova_roja from supernova
/le reload
/le validate miserie:supernova_roja
```

## Desde funciones y bloques de comandos

Como todo es un comando normal, se puede guionizar con `/schedule` vanilla o con funciones:

```mcfunction
# data/miserie/function/episodio12.mcfunction
title @a title {"text":"El cielo no es lo que parece"}
schedule function miserie:episodio12_supernova 10s
```

Para algo más elaborado, usa las [secuencias](secuencias-y-programacion.md).

> Los bloques de comandos ejecutan comandos con nivel de OP 2. Si en tu mapa los usan jugadores sin permiso, desactiva
> `enable-command-block` o protégelos.

---

Creado por **TakumiStudios**.
