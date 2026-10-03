# Crear eventos

Los eventos oficiales y los de la comunidad se definen igual: un **JSON** que combina [módulos](modulos.md) sobre una
**línea de tiempo**. No hay scripts: un evento solo contiene datos y expresiones matemáticas limitadas, así que nadie
puede ejecutar código en un servidor por descargar un pack.

| Nivel | Para quién | Qué puede hacer | Cómo |
|---|---|---|---|
| **1. Variantes** | Admins sin conocimientos técnicos | Cambiar colores, duración, radio, intensidad… de un evento | `/le create` o un JSON con `extends` |
| **2. Eventos nuevos** | Admins avanzados y creadores de series | Eventos completos con los módulos existentes, sonidos y textos propios | Datapack + resource pack |
| **3. Addons** | Desarrolladores Java | Módulos nuevos | Mod de Fabric con la [API](api-addons.md) |

El [LiveEvents Example Pack](https://github.com/AloneX15/LiveEvents-mod/tree/main/examples/LiveEvents-Example-Pack)
trae un ejemplo de cada nivel listo para copiar.

## Nivel 1: variantes

```mcfunction
/le create miserie:supernova_roja from supernova
```

Crea `config/liveevents/events/miserie_supernova_roja.json`. Solo se escribe lo que cambia; todo lo demás se hereda,
también las mejoras futuras del evento original:

```json
{
  "$schema": "https://raw.githubusercontent.com/AloneX15/LiveEvents-mod/main/schema/liveevents-event.schema.json",
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

- `palette` se mezcla color a color; `params` también.
- `overrides` usa rutas `fase.módulo.parámetro`. El módulo es su tipo; si hay varios del mismo tipo en la fase, añade
  `#n` (`demon_sun.camera_shake#1.amplitude`). También vale una ruta de primer nivel como `aftermath.duration`.
- Después: `/le reload` y `/le validate miserie:supernova_roja`.

## Nivel 2: eventos nuevos

### Estructura

```
MiSerie_Eventos/                         ← datapack (world/datapacks/)
├─ pack.mcmeta
└─ data/miserie/
   ├─ liveevents/event/apocalipsis.json  ← definición (id = miserie:apocalipsis)
   ├─ liveevents/palette/tierra.json     ← paleta de bloques (opcional)
   └─ liveevents/sequence/episodio.json  ← secuencia (opcional)

MiSerie_Recursos/                        ← resource pack (el servidor lo envía a los jugadores)
├─ pack.mcmeta
└─ assets/miserie/
   ├─ sounds.json
   └─ lang/es_es.json, en_us.json
```

`pack.mcmeta` del datapack para 26.1–26.3:

```json
{ "pack": { "description": "Mis eventos", "min_format": 101, "max_format": 121 } }
```

y del resource pack: `"min_format": 84, "max_format": 97`.

**Los jugadores no instalan nada extra:** el servidor les envía las definiciones al conectarse y el resource pack se
puede enviar con `resource-pack` en `server.properties`.

### Del guion al JSON

Escribe primero el guion (qué ve y siente el jugador segundo a segundo) y después pásalo al JSON uno a uno:

| Guion | JSON |
|---|---|
| Ficha (ámbito, ancla, hora, perfiles) | `scope`, `anchor`, `night_override`, `default_profile` |
| Paleta (nombre → #hex) | `palette` |
| Segmento `MM:SS – MM:SS \| Título` | Una fase: `id`, `from`, `to`, `title` |
| "El Cielo" y "El Caos" | `description.sky` y `description.chaos` (se ven en `/le info`) |
| Cada fila de la tabla técnica | Uno o varios módulos en `modules` |
| Fila "Modo seguro" | `profiles.safe` dentro de cada módulo |
| Secuelas | `aftermath` |

### Ejemplo completo: Luna de sangre

```json
{
  "$schema": "https://raw.githubusercontent.com/AloneX15/LiveEvents-mod/main/schema/liveevents-event.schema.json",
  "format_version": 1,
  "id": "example:blood_moon",
  "display_name": "event.example.blood_moon",
  "scope": "celestial",
  "anchor": { "azimuth": 90, "altitude": 30 },
  "night_override": "require",
  "lethal": false,
  "affects": ["players"],
  "cooldown": "5m",
  "palette": { "night": "#12030A", "blood": "#9B0A1E", "ember": "#FF4A2A", "mist": "#3A0A14" },
  "phases": [
    { "id": "rise", "from": "0s", "to": "20s", "title": "event.example.blood_moon.phase.rise",
      "modules": [
        { "type": "sky_tint", "color": "$night", "strength": { "from": 0.1, "to": 0.6 }, "fade": "4s", "to": "90s" },
        { "type": "celestial_body", "color": "$blood", "core": "$ember", "size_deg": { "from": 6, "to": 10 }, "curve": "ease_out" },
        { "type": "audio_duck", "level": 0.2 },
        { "type": "sound", "at": "2s", "sound": "example:blood_moon.howl", "position": "anchor" }
      ] },
    { "id": "hunt", "from": "20s", "to": "75s", "title": "event.example.blood_moon.phase.hunt",
      "modules": [
        { "type": "celestial_body", "shape": "boiling", "color": "$blood", "size_deg": 10, "flicker_hz": [0.5, 1.2] },
        { "type": "screen_overlay", "color": "$mist", "vignette": 0.45, "pulse_hz": 1.0,
          "profiles": { "safe": { "pulse_hz": 0 } } },
        { "type": "status_effect", "effect": "minecraft:darkness", "duration": "3s", "interval": "15s", "requires_sky": true },
        { "type": "block_transform", "palette": "example:blood_soil", "chance": 0.03, "radius": 48, "per_second": 100 }
      ] }
  ]
}
```

### Campos de una definición

| Campo | Por defecto | Qué controla |
|---|---|---|
| `format_version` | **obligatorio** | Versión del formato (`1`) |
| `id` | la ruta del archivo | `espacio:nombre` (el espacio `liveevents` está reservado) |
| `display_name`, `description` | el id | Texto o clave de traducción |
| `scope` | `localized` | `celestial` (toda la dimensión), `localized` (centro + radio) o `regional` |
| `anchor` | `{azimuth: 160, altitude: 55}` | Dónde está en la bóveda (0° = norte, 90° = este) |
| `allowed_dimensions` | `["minecraft:overworld"]` | Dónde se puede lanzar (además de la lista del servidor) |
| `night_override` | `none` | `none`, `visual` (el cliente pinta la noche) o `require` (solo de noche) |
| `clear_weather` | `false` | Despeja el cielo y restaura el clima al terminar |
| `lethal` | `true` | Con `false`, el daño nunca baja a nadie de medio corazón |
| `affects` | `["players", "hostile"]` | `players`, `hostile`, `passive`, `pets`, `named` |
| `world_mode` | el del servidor | `permanent`, `revert_on_end`, `revert_manual` |
| `cooldown` | `0s` | Tiempo mínimo entre dos lanzamientos |
| `default_profile` | el del servidor | `cinematic`, `gameplay` o `safe` |
| `queue_policy` | `reject` | `reject` o `queue` si hay un evento incompatible |
| `params` | `{radius: 128}` | Números reutilizables (`$radius`), modificables con `param=valor` al lanzar |
| `palette` | `{}` | Colores con nombre (`$nombre`) |
| `phases` | **obligatorio** | Fases con `id`, `from`, `to`, `title`, `description`, `modules` |
| `aftermath` | — | `{persistent: ["nebula", "pulsar"], duration: "3d"}` |

### Tiempos, rangos, colores y expresiones

- **Tiempos:** `"8.1s"`, `"20t"` (ticks), `"2m"`, `"3d"` (días de juego) o un número (segundos). Son **absolutos**
  desde el inicio del evento. El cliente interpola entre ticks, así que se pueden marcar cortes de 0,1 s.
- **Tramo de un módulo:** por defecto, el de su fase. `from`/`to` lo acotan; `at` lo hace puntual (`at` + `duration`
  para un tramo corto).
- **Rangos:** `{"from": 0.05, "to": 0.2}` o `[0.05, 0.2]` se interpolan a lo largo del módulo con su `curve`
  (`linear`, `ease_in`, `ease_out`, `ease_in_out`, `pulse`, `step`).
- **Colores:** `"#00FFFF"` o `"$cyan"` de la paleta.
- **Expresiones:** `"$radius * 0.5"`, `"clamp($count, 1, 80)"`. Solo aritmética y `min`, `max`, `clamp`, `lerp`, `abs`,
  `sqrt`, `floor`, `ceil`, `round`, `sin`, `cos`, `pow`; sin bucles ni acceso a nada externo. Se evalúan al iniciar.
- **Perfiles:** `"profiles": {"safe": {"strength": 0.2}}` cambia parámetros solo en ese perfil.

### Sonidos y textos propios

`assets/miserie/sounds.json` (puedes usar tus `.ogg` o reutilizar eventos vanilla con `"type": "event"`):

```json
{ "apocalipsis.grito": { "subtitle": "subtitles.miserie.grito",
    "sounds": [ { "name": "minecraft:entity.wolf.howl", "type": "event", "pitch": 0.6 } ] } }
```

Y en el módulo: `{ "type": "sound", "sound": "miserie:apocalipsis.grito" }`. Los textos (`display_name`, `title` de cada
fase, títulos de `title`) admiten una clave de traducción de tu `lang/` o un texto literal.

### Paletas de bloques

`data/miserie/liveevents/palette/tierra.json`; la primera regla que coincide gana, `from` admite `#tag` y `*`, y varias
reglas seguidas con el mismo `from` se eligen por `weight`:

```json
{ "format_version": 1, "rules": [
  { "from": "minecraft:grass_block", "to": "minecraft:crimson_nylium" },
  { "from": "#minecraft:leaves", "to": "minecraft:air" },
  { "from": "*", "to": "minecraft:basalt", "weight": 3 },
  { "from": "*", "to": "minecraft:blackstone", "weight": 1 } ] }
```

Paletas oficiales: `liveevents:scorched`, `liveevents:melt`, `liveevents:frozen`, `liveevents:corroded`,
`liveevents:meteorite`, `liveevents:cooling`.

## Validación

- **JSON Schema:** con la línea `"$schema"` al principio, VS Code y otros editores autocompletan módulos y parámetros y
  marcan errores mientras escribes.
- **`/le validate`** muestra errores precisos:

  ```text
  ✘ miserie:apocalipsis → phases[1].modules[2].color: $cyna no existe en la paleta. ¿Querías decir $cyan?
  ✘ miserie:apocalipsis → phases[1].modules[1].type: tipo de módulo desconocido: sky_tnit. ¿Querías decir sky_tint?
  ⚠ miserie:apocalipsis → phases[0].modules[0].radius: 9000 fuera del rango permitido [0, 4096]; se recortará
  ```

- Un evento inválido **se desactiva y se avisa**; nunca provoca un crash ni impide que carguen los demás.
- Límites que un evento no puede saltarse: presupuesto por tick y topes de bloques y rayos, regla de destellos, perfil
  máximo del servidor y del cliente, LOD y presupuesto de partículas, protecciones, diario y permisos. Si un JSON pide
  algo que supera un límite, se recorta y `/le validate` lo avisa.
- Tamaño máximo por archivo 256 KB, 64 fases, 128 módulos por fase, herencia de 8 niveles como mucho y sin ciclos.

## Herramientas

| Herramienta | Para qué |
|---|---|
| `/le reload` | Recarga las definiciones y las envía a los clientes, sin reiniciar |
| `/le validate` | Encuentra errores antes de lanzar |
| `/le preview <evento>` | Lo reproduce solo para ti, sin tocar el mundo |
| `timescale=0.5` | Cámara lenta para ajustar cortes y sonidos |
| `/le seek <id> <segundo>` | Salta al segmento que estás ajustando |
| `/le debug timeline` | Overlay con el segundo, la fase y los módulos activos |

**Flujo recomendado:** guion → JSON → `/le validate` → `/le preview … timescale=0.5` → ajustar → `/le reload` →
probar con jugadores en `gameplay` y en `safe`.

## Permisos y espacios de nombres

Cada creador usa **su propio espacio de nombres** (`miserie:`, `servidorx:`…), así dos packs nunca chocan. El permiso
de cada evento se crea solo: `liveevents:start.miserie.apocalipsis`.

---

Creado por **TakumiStudios**.
