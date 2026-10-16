# Registro de errores

Cada error resuelto: síntoma, causa, solución y test que lo reproduce.

## 1. Una instancia grande bloqueaba los cambios de bloques de las demás

- **Síntoma:** el gametest `blockChangesAreJournaledAndReverted` fallaba: tras 30 ticks la hierba seguía sin
  chamuscarse mientras corría a la vez otro evento con radio 48.
- **Causa:** el `WorkScheduler` volvía a poner al principio de la cola la tarea que había avanzado, así que una tarea
  con mucho trabajo se quedaba con todo el presupuesto de su prioridad tick tras tick.
- **Solución:** turnos rotatorios (la tarea que avanza pasa al final) y trozos de 32 cambios por llamada
  (`WorkScheduler.SLICE`); el tiempo se comprueba entre trozos, así una tarea tampoco se pasa del presupuesto.
- **Test:** `WorkSchedulerTest.busyTasksDoNotStarveOthers` y `allowanceIsSliced`.

## 2. Una tarea limitada por ritmo bloqueaba su prioridad

- **Síntoma:** detectado en revisión: un `block_transform` con `per_second` bajo dejaba sin turno a los cráteres.
- **Causa:** la tarea que devolvía "sin avance" volvía al principio y el planificador saltaba a la siguiente prioridad.
- **Solución:** las tareas sin avance pasan al final y el bucle solo se detiene cuando todas las de la cola están
  paradas.
- **Test:** `WorkSchedulerTest.busyTasksDoNotStarveOthers`.

## 3. `/le seek` corto dejaba el cliente desfasado

- **Síntoma:** las capturas tomadas tras un salto de menos de 2 s mostraban el momento anterior.
- **Causa:** el salto se enviaba como latido de reloj (`EventClock`), que el cliente corrige poco a poco (10 %).
- **Solución:** `seek` envía `EventPause` con el tiempo exacto; además, un reloj en pausa siempre salta.
- **Test:** `CoreUtilitiesTest.smoothClockCorrectsDriftGraduallyAndSnapsWhenFar` (salto en pausa) y el test de cliente.

## 4. Los eventos lanzados sin jugadores conectados se quedaban en pausa

- **Síntoma:** en 26.1.2 los gametests de bloques y daño fallaban: el planificador no ejecutaba nada (0 ms por tick).
- **Causa:** `pause_when_empty` pausaba todos los eventos en cuanto el servidor no tenía jugadores, también los lanzados
  desde la consola o un gametest. En 26.3 pasaba por casualidad, por el orden de los ticks.
- **Solución:** solo se pausa cuando el servidor *se queda* vacío (de 1 o más jugadores a 0) y se reanuda al entrar
  alguien, como dice el plan (§21.2).
- **Test:** todos los gametests de servidor (se ejecutan sin jugadores) en las tres versiones.

## 5. Los módulos de bloques forzaban la carga de chunks

- **Síntoma:** el gametest de presupuesto marcaba un p90 de 5 ms y picos de 25 ms por tick (presupuesto: 2 ms).
- **Causa:** se leía el heightmap (`getHeight`) antes de comprobar si el chunk estaba cargado, y esa lectura carga el
  chunk de forma síncrona. Además incumplía la norma del plan: el mod nunca fuerza la carga de chunks (§8.4).
- **Solución:** `BlockModules.loaded()` (`hasChunk`) antes de cada lectura del heightmap en todos los módulos (cráter,
  transformación, fuego, grieta, meteoritos y rayos).
- **Test:** `LiveEventsGameTests.blockWorkRespectsTickBudget` (radio 48, más allá de los chunks cargados).

## Cambios de API entre versiones 26.x encontrados al portar

| Versión | Cambio | Solución en LiveEvents |
|---|---|---|
| 26.2 | `GameRenderer.getMainCamera()` pasa a `mainCamera()` | El mixin de `Camera` guarda la cámara activa (`CameraShake.capture`) |
| 26.2 | `Minecraft.setScreen()` desaparece | `setScreenAndShow()`, presente en todas las 26.x |
| 26.3 | Las constantes `EntityType.*` pasan a `EntityTypes` | Búsqueda por id en `BuiltInRegistries.ENTITY_TYPE` |
| 26.3 | `Entity.hurtMarked` desaparece | `ClientboundSetEntityMotionPacket` para sincronizar empujones |
| 26.3 | `Level.setBlock(pos, state, flags)` de 3 argumentos desaparece | Variante de 4 argumentos (`512` de límite de recursión) |
| 26.3 | `RenderPipeline` cambia de paquete | `RenderPipelines.GUI_TEXTURED` sin importar el tipo |
| 26.3 | `InputConstants.Type.KEYSYM` → `KEYBOARD` | Constructor `KeyMapping(String, int, Category)` |

## Meteoritos v2 · 2026-10-16

- Cliente y servidor resolvían el suelo por separado: anuncios inmutables con coordenadas y superficie del servidor.
  Regresión: impacto en agua, cambio de potencia y resincronización del cliente.
- El enfriamiento perdía bloques al alcanzar el cupo: cursor persistente y Result.more hasta completar el recorrido.
- Una columna de cráter podía superar el cupo del planificador: operaciones reanudables por posición y geometría asíncrona.
- F1 podía congelar la última amplitud de cámara: cálculo visual en extracción del mundo, HUD como consumidor.
- Blender 5.2 requiere media_type=VIDEO antes de elegir FFMPEG; modos y exportaciones revisados.

### CI y compatibilidad

- Un cambio de contexto del SO entre la comprobación del tiempo y el inicio de una tarea permitía empezar fuera
  de presupuesto. Se comprueba otra vez justo antes de ejecutar y se devuelve la tarea a la cola. Regresión con reloj
  simulado que salta de 0 a 3 ms entre esas dos lecturas; no se ha relajado el presupuesto de 2 ms del GameTest.
- Las capturas del arnés 26.1.2 omiten `GameRenderer.update` y Sodium falla con `Global terrain uniforms have not
  been updated` (Fabric API #5466). Workaround exclusivamente en el mod de pruebas, documentado en MIXINS.md.
