# Secuencias y programación

## Programar con hora real: `/le schedule`

```mcfunction
/le schedule supernova at 2026-10-10T21:00 profile=cinematic
/le schedule meteor_shower at 2026-10-10T21:05 at 0 70 0 count=80
/le schedule list
/le schedule cancel 2
```

- La hora usa la zona horaria de `timezone` en `server.json` (la del sistema si está vacía).
- Admite las mismas opciones que `/le start` (`at`, `in`, `profile=`, parámetros…).
- Se guarda en `<mundo>/liveevents/schedule.json` y **sobrevive a los reinicios**.
- Los jugadores reciben un aviso en el chat `schedule_warning_minutes` antes (5 por defecto).
- Si la hora pasó con el servidor apagado, se avisa en el log y aparece como *missed* en `/le schedule list`: **no se
  lanza solo**.

## Secuencias

Una secuencia encadena eventos y comandos para un episodio o un evento de servidor. Va en un datapack
(`data/<espacio>/liveevents/sequence/<id>.json`) o en `config/liveevents/sequences/`:

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

```mcfunction
/le sequence start miserie:episodio_12
/le sequence list
/le sequence stop
```

| Campo de un paso | Descripción |
|---|---|
| `at` | Momento desde el inicio de la secuencia |
| `command` | Comando que se ejecuta **con los permisos de quien lanzó la secuencia** |
| `event` | Evento que se lanza (comprueba el permiso de quien lanzó la secuencia) |
| `profile` | Perfil del evento |
| `on` | Selector o jugador como centro (`@r`, `@p`, un nombre) |
| `position` | Coordenadas absolutas `"x y z"` |
| `args` | Parámetros del evento (`{"count": 25}`) |

Como máximo 256 pasos. Si un paso falla (selector sin resultados, evento rechazado…), se avisa a quien la lanzó y la
secuencia continúa.

## Placeholders (Text Placeholder API)

Con [Text Placeholder API](https://modrinth.com/mod/placeholder-api) instalado:

| Placeholder | Valor |
|---|---|
| `%liveevents:active%` | Nombre del evento activo |
| `%liveevents:time_left%` | Tiempo restante (mm:ss) |
| `%liveevents:phase%` | Título del segmento actual del guion |
| `%liveevents:next_scheduled%` | Próximo evento programado |

## Integraciones externas

| Integración | Estado |
|---|---|
| **Discord** | Webhook en `server.json`: aviso al empezar y al terminar un evento |
| **RCON / consola** | Todos los comandos funcionan por RCON (paneles web, bots propios) |
| **Twitch / YouTube** | Fuera del mod principal (decisión D17 del plan): irá como addon oficial aparte |

## Grabar series

- **Espectador** ve todo sin recibir efectos de juego: ideal para el cámara.
- F2 captura la imagen con los efectos.
- Los visuales se generan en el cliente a partir de paquetes y semillas, así que una grabación de paquetes (Flashback,
  Replay Mod) puede reproducirlos; esta compatibilidad **todavía no está verificada** (ver
  [Rendimiento y compatibilidad](rendimiento-y-compatibilidad.md)).

---

Creado por **TakumiStudios**.
