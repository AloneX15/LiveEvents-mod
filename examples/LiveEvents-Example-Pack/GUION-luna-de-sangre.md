# Luna de sangre (`example:blood_moon`)

Guion en el formato de §6.1 del plan, junto a su JSON (`datapack/data/example/liveevents/event/blood_moon.json`).

## Ficha

- **id:** `example:blood_moon` · **duración:** 90 s · **ámbito:** celeste
- **Ancla:** acimut 90° (este), altura 30°
- **Hora:** solo de noche (`night_override: require`)
- **Perfiles:** cinematic · gameplay (por defecto) · safe
- **Paleta:** night → #12030A · blood → #9B0A1E · ember → #FF4A2A · mist → #3A0A14

## Guion

### 00:00 – 00:20 | La Salida

- **El Cielo:** la luna sale enorme por el este, teñida de un rojo oscuro.
- **El Caos:** los animales callan. Algo antiguo despierta.

| Capa | Qué ocurre | Módulos |
|---|---|---|
| Cielo | El cielo se oscurece en 4 s; la luna crece de 6° a 10° | `sky_tint`, `celestial_body` |
| Audio | La música baja; un aullido lejano a los 2 s | `audio_duck`, `sound` |
| Juego | Título de aviso | `title` |
| Modo seguro | Igual (no hay destellos) | — |

### 00:20 – 01:15 | La Caza

- **El Cielo:** la luna late como un corazón. La niebla se vuelve carmesí.
- **El Caos:** la noche caza. Quédate cerca de la luz.

| Capa | Qué ocurre | Módulos |
|---|---|---|
| Cielo | La luna "hierve" y parpadea despacio (0,5–1,2 Hz) | `celestial_body` |
| Pantalla | Viñeta carmesí que late a 1 Hz | `screen_overlay` |
| Audio | Latido que acelera de 50 a 90 ppm | `sound_loop` |
| Juego | Oscuridad breve cada 15 s a quien está bajo el cielo; la hierba se vuelve nylium carmesí (3 %) | `status_effect`, `block_transform` |
| Modo seguro | La viñeta no late | `profiles.safe` |

### 01:15 – 01:30 | La Puesta

- **El Cielo:** la luna se hunde y su color se apaga.
- **El Caos:** silencio. Por ahora.

| Capa | Qué ocurre | Módulos |
|---|---|---|
| Cielo | La luna encoge de 10° a 6° | `celestial_body` |
| Audio | Último aullido, más grave | `sound` |
| Juego | Nada | — |

## Viabilidad y coste

Todo se hace con módulos existentes. Coste de servidor mínimo (un efecto cada 15 s y 100 columnas/s de bloques).
