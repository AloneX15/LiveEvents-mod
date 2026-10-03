# Rendimiento y compatibilidad

## Cómo se mantiene ligero

- **El servidor decide y el cliente dibuja.** Por evento se envían unos pocos paquetes (inicio, cambios de fase, un
  latido de reloj por segundo y fin): unos pocos KB por jugador. Las partículas nunca se mandan desde el servidor.
- **Planificador con presupuesto.** Todo el trabajo de bloques pasa por una cola con presupuesto en milisegundos por
  tick (`tick_budget_ms`, 2 ms) y tope de cambios (`max_block_changes_per_tick`, 256). Las tareas se reparten por
  turnos (ninguna instancia acapara la cola), en trozos de 8 cambios, y **nunca se empieza un trozo con el presupuesto
  agotado**. Con el servidor saturado, el watchdog reduce el presupuesto.
- **Sin eventos, coste cero:** los listeners salen al instante.
- **Nunca se cargan chunks**: antes de leer el terreno se comprueba que el chunk esté cargado.
- **Formas deterministas:** cráteres, grietas y trayectorias salen de la semilla, así el servidor y el cliente obtienen
  lo mismo sin intercambiar datos.
- **E/S fuera del hilo principal:** diario, secuelas, programación y auditoría se escriben en un hilo propio con
  escrituras atómicas; al cerrar el servidor se espera a que terminen.
- **Cliente:** LOD por distancia (completo < 64 bloques, ×0,4 hasta 192, solo cielo y sonido más allá), presupuesto de
  partículas por tick, culling de lo que queda fuera de pantalla, objetos reutilizados por fotograma y calidad
  automática.

## Medición

`/le debug perf` muestra el coste del mod por tick, las tareas y bloques del último tick, la cola pendiente, el factor
de carga y el MSPT. El gametest `blockWorkRespectsTickBudget` comprueba en la CI que el tope de bloques se respeta y que
no se empieza trabajo con el presupuesto agotado.

> Medido en los gametests (mundo nuevo): un `setBlock` vanilla llega a costar varios milisegundos la primera vez que
> se modifica una sección de chunk, así que el tiempo total de un tick puede superar el presupuesto en el coste de ese
> último cambio. En un servidor real, perfila con [spark](https://modrinth.com/mod/spark)
> (`/spark profiler start` / `stop`) durante un evento grande y ajusta `tick_budget_ms` y
> `max_block_changes_per_tick`.

## Compatibilidad

| Mod | Estado |
|---|---|
| Lithium, FerriteCore | ✅ Probados en la CI en cada push (*compat pack*) |
| LuckPerms | ✅ A través de la API de permisos de Fabric |
| Mod Menu, Text Placeholder API | ✅ Integraciones opcionales aisladas |
| Sodium | ✅ Los efectos se dibujan en el HUD, que Sodium no modifica (pendiente de prueba manual) |
| Iris + shaderpacks | ✅ Mismo motivo: los efectos no tocan el cielo ni el post-procesado de vanilla (pendiente de prueba manual) |
| Otros mods de cámara | El temblor se **suma** a la rotación ya calculada (mixin aditivo en `Camera.update`) |
| Distant Horizons | Sus LOD lejanos no reflejarán los cambios de bloques al momento |
| Flashback / Replay Mod | Pendiente de verificar que la repetición reproduce los visuales |
| C2ME | El mod no toca el mundo fuera del hilo del servidor ni carga chunks (pendiente de prueba) |
| Mods de protección (Flan, GOML, FTB Chunks) | Integración con Common Protection API pendiente de que se publique para 26.x; mientras, `excluded_regions` y la API `BeforeBlockChange` |

Un solo mixin (ver `MIXINS.md`): `Camera.update`, aditivo y con `require = 0`.

## Versiones de Minecraft

Un jar por versión: 26.1.x, 26.2 y 26.3. La CI compila y prueba las tres en cada push (tests unitarios, gametests de
servidor, compat pack y test de cliente con capturas).

---

Creado por **TakumiStudios**.
