# LiveEvents

![LiveEvents](images/supernova_05_shattered_sky.png)

**LiveEvents** es un mod de Fabric para Minecraft 26.x que permite a los administradores lanzar **eventos cósmicos y
naturales en directo**: supernovas, lluvias de meteoritos, terremotos, eclipses, auroras, tormentas solares, agujeros
negros… Cada evento tiene un apartado visual espectacular en el cliente y **efectos reales en el mundo**, que se
pueden **revertir**.

Está pensado para eventos de servidor, series y directos: nada ocurre solo, todo lo lanza un administrador, y todo
respeta la accesibilidad (regla de destellos, perfiles de intensidad y tecla de pánico).

## Empieza aquí

| Si eres… | Lee |
|---|---|
| Admin de un servidor | [Instalación](instalacion.md) → [Guía para administradores](guia-administradores.md) → [Comandos](comandos.md) |
| Creador de series o directos | [Perfiles y accesibilidad](perfiles-y-accesibilidad.md) → [Secuencias y programación](secuencias-y-programacion.md) |
| Quien quiere crear sus propios eventos | [Crear eventos](crear-eventos.md) → [Referencia de módulos](modulos.md) |
| Desarrollador de addons | [API para addons](api-addons.md) |

## Los eventos

| | Evento | Duración | Ámbito |
|---|---|---|---|
| ![](images/supernova_05_shattered_sky.png) | [Supernova de Betelgeuse](eventos-oficiales.md#supernova-de-betelgeuse) | 60 s | Toda la dimensión |
| ![](images/meteor_shower_02_impacts.png) | [Lluvia de meteoritos](eventos-oficiales.md#lluvia-de-meteoritos) | 90 s | Centro + radio |
| ![](images/earthquake_01_s_wave_debug.png) | [Terremoto](eventos-oficiales.md#terremoto) | 45 s | Región |
| ![](images/aurora_borealis.png) | [Aurora boreal](eventos-oficiales.md#aurora-boreal) | 120 s | Toda la dimensión |
| ![](images/total_eclipse_totality.png) | [Eclipse total](eventos-oficiales.md#eclipse-total) | 90 s | Toda la dimensión |
| ![](images/solar_storm.png) | [Tormenta solar](eventos-oficiales.md#tormenta-solar) | 75 s | Toda la dimensión |
| ![](images/great_meteor_crater.png) | [Gran meteorito](eventos-oficiales.md#gran-meteorito) | 40 s | Centro + radio |
| ![](images/blizzard.png) | [Ventisca](eventos-oficiales.md#ventisca) | 90 s | Región |
| | [Lluvia ácida](eventos-oficiales.md#lluvia-ácida) | 80 s | Región |
| ![](images/black_hole.png) | [Agujero negro](eventos-oficiales.md#agujero-negro) | 60 s | Centro + radio |

## En 30 segundos

```mcfunction
/le start supernova                       # lanzar en tu posición con el perfil por defecto
/le start meteor_shower radius=64 count=60 delay 10
/le preview earthquake                    # verlo solo tú, sin tocar el mundo
/le list active                           # qué hay en marcha
/le revert last confirm                   # deshacer los cambios del último evento
/le panic                                 # parada de emergencia
```

## Cómo funciona

- **El servidor decide y el cliente dibuja.** El servidor manda un solo paquete con la fase, la semilla y el tiempo;
  cada cliente reproduce los visuales de forma determinista. Casi no se gasta red.
- **Todo es configurable.** Un evento es un JSON que combina [módulos](modulos.md) sobre una línea de tiempo. Los
  oficiales y los de la comunidad se definen igual.
- **Optimizado.** El trabajo de bloques pasa por un planificador con presupuesto en milisegundos por tick; sin
  eventos, el coste es cero.
- **Seguro.** No existe ningún paquete cliente → servidor con poder de acción; los cambios en el mundo se apuntan en un
  diario que permite revertirlos.

---

Creado por **TakumiStudios**.
