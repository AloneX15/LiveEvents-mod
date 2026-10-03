# LiveEvents Example Pack

Plantilla oficial para crear eventos propios con [LiveEvents](https://github.com/AloneX15/LiveEvents-mod). Cópiala,
cambia el espacio de nombres `example` por el tuyo y modifica lo que quieras.

| Carpeta | Qué contiene |
|---|---|
| `datapack/` | Definiciones (va en `world/datapacks/`) |
| `datapack/data/example/liveevents/event/red_supernova.json` | **Nivel 1:** variante de la supernova (solo cambia colores, cortes y secuelas) |
| `datapack/data/example/liveevents/event/blood_moon.json` | **Nivel 2:** evento nuevo completo, con su guion en `GUION-luna-de-sangre.md` |
| `datapack/data/example/liveevents/palette/blood_soil.json` | Paleta propia de transformación de bloques |
| `datapack/data/example/liveevents/sequence/episode_12.json` | Secuencia de un episodio (`/le sequence start example:episode_12`) |
| `resourcepack/` | Sonido propio (`example:blood_moon.howl`) y textos en inglés y español |

## Instalación

1. Copia `datapack/` a `world/datapacks/LiveEvents-Example` y ejecuta `/reload`.
2. Pon `resourcepack/` en el resource pack del servidor (`resource-pack` en `server.properties`) o en
   `resourcepacks/` de cada cliente.
3. `/le validate example:blood_moon` → sin errores. `/le preview example:blood_moon` para verlo solo tú.

El permiso de cada evento se crea solo: `liveevents:start.example.blood_moon`.

Creado por **TakumiStudios**.
