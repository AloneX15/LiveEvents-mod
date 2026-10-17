# Meteoritos v2

Creado por **TakumiStudios**.

## Base y alcance

La implementación parte del remoto `0596e0471e7e174845bd1a2d877148d3b902f94a`, no de la base Java vacía local.
`../LiveEvents_Plan.md` conserva el diseño local aportado por el autor; `LiveEvents_Plan.md` de esta carpeta conserva
la referencia previa del remoto. Esta entrega modifica meteoritos y la infraestructura compartida necesaria;
no reimplementa supernova, terremoto ni el panel gráfico existentes.

## Uso

Instalar el jar correcto y Fabric API en servidor y clientes. El protocolo de red es **2**: actualizar ambos lados.

```mcfunction
/le start meteor_shower power=0.7 brutality=0.5 duration=120s seed=2107
/le meteor mode 1 random
/le meteor mode 1 groups min=3 group_radius=24 spread=8 rest=10 fallback=random
/le meteor mode 1 mixed random_weight=70 groups_weight=30
/le meteor mode 1 player player=Nombre spread=8
/le meteor mode 1 coordinates x=0 y=64 z=0 spread=8
/le meteor strike 1 on Nombre power=0.7 spread=4
/le meteor strike 1 at 0 64 0 in minecraft:overworld power=0.7
/le set 1 power 0.9
/le set 1 visual_power 0.9
/le set 1 destructive_power 0
/le set 1 brutality 0.8
/le set 1 cooling_seconds 120
/le set 1 smoke_seconds 120
/le set 1 wind_x 0.2
/le set 1 wind_z 0.05
/le set 1 duration 180s
/le set 1 remaining 45s
/le status 1
/le stop 1
/le revert 1 confirm
/le panic
```

Los IDs son ejemplos: usar el ID devuelto al iniciar. `strike` requiere un módulo de meteoritos activo;
para ensayos puede usarse `/le seek <id> 12` con permiso de depuración. No usar `seek` para dirigir una partida normal.
`at` identifica el punto exacto de impacto, incluida Y; se debe indicar la superficie. `on` fija la superficie al preparar el lanzamiento.
Los modos continuos resuelven el heightmap del servidor. No se cargan chunks para buscar objetivos.

`power`, `visual_power`, `destructive_power` y `brutality` admiten 0..1. Si no se especifican los dos controles
separados, ambos siguen `power`. El valor destructivo cero evita cráter, fuego, daño y empuje en nuevos impactos.
Las órdenes que ya se anunciaron mantienen su posición, superficie y potencia.

Duración y tiempo restante usan segundos, o sufijos `s`, `m`, `h` en `/le set`. El cierre conserva un mínimo de
25 segundos y cualquier impacto anunciado; el comando muestra el valor efectivo. Ampliar prolonga la actividad
mediante nuevos ciclos deterministas, sin repetir los impactos de ciclos previos. `stop` y `panic` cancelan de inmediato.
`status` muestra duración efectiva, modo, lanzamientos y lanzamientos limitados.

Los grupos excluyen espectadores y creativos por defecto; `include_creative=true` permite estos últimos.
Se deduplican grupos y se conserva el descanso por UUID aunque cambie la composición. `fallback=wait` permite esperar
sin grupos válidos. Por defecto se vuelve a caídas aleatorias. Los objetivos manuales inválidos producen un error.

## Secuelas y reinicios

Cada anuncio fija `cooling_seconds` y `smoke_seconds` (0..600 segundos, 120 por defecto) y el viento
`wind_x`/`wind_z` (-4..4 bloques por segundo). Cambiarlos afecta a futuros anuncios. Humo y brasas continúan
tras el cierre normal y se resincronizan al entrar. Se enfría únicamente el magma colocado por el impacto a
obsidiana y se retira su fuego. Puede retrasarse si se agota el presupuesto. Nunca se cargan chunks para ello.

`stop` y `panic` cortan visuales y nuevas caídas; la limpieza térmica continúa con su presupuesto. No revierten
el cráter. `revert` restaura el diario y cancela esa limpieza. Las celdas térmicas se guardan cada segundo y al apagar
en `meteor-cooling.json`; tras reiniciar se limpian las que todavía coinciden con magma/fuego, conservando el diario
reversible. La política predeterminada cancela el evento sin reproducir impactos ni recuperar las nubes visuales.
Una caída brusca puede perder el último segundo de la instantánea térmica.

## Permisos y protección

Además de `liveevents:command`, dirigir meteoritos exige `liveevents:edit` y el nodo
`liveevents:meteor.target.groups`, `.mixed`, `.player` o `.coordinates` según la acción (OP 3 de respaldo).
Los impactos siguen respetando límites del mundo, área autorizada, exclusiones y Common Protection API si está instalada.
La integración opcional usa `CommonProtection.isProtected(Level, BlockPos)`; ante una API incompatible instalada se
rechazan las operaciones protegidas en lugar de ignorar las protecciones. Hay que probar cada proveedor de claims concreto.
El diario no reconstruye los daños causados indirectamente por fuego vanilla; usar las copias de seguridad habituales del servidor.

## Producción en Blender

```powershell
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' --background --python-exit-code 1 --python art/meteor_shower/create_preview_v2.py -- earth all
```

Repetir con `water` y `snow`. Modos: `scene` guarda únicamente la escena, `preview` genera imagen y atlas,
`views` añade cámaras y etapas, `video` añade MP4, `all` produce todo. Las escenas editables guardan calidad
960 × 540, 32 muestras; los vídeos de revisión usan 480 × 270, 8 muestras para agilizar la iteración.

Cada variante en `art/meteor_shower/generated/v2/` incluye escena, vídeo de 240 fotogramas a 24 FPS, atlas,
cinco vistas y ocho etapas. `stages.png` sigue el orden 24, 36, 55, 76, 100, 140, 205, 240;
`views.png` compara las cinco cámaras. El aviso comienza en 36 y el impacto en 72: exactamente 1,5 segundos.
Los recursos anteriores se conservan y siguen siendo prototipos.

Validar con Blender: `--python art/meteor_shower/validate_exports.py -- v2`.
Crear láminas con `--python art/meteor_shower/make_contact_sheets.py`.
`py tools/validate-meteor-assets.py` comprueba el contrato empaquetado sin instalar Blender.

## Renderizado y dependencias

El cliente usa `LevelExtractionEvents`/`LevelRenderEvents` y `SubmitNodeCollector`: geometría con profundidad,
modelo importado, estelas afiladas, avisos y humo dispuesto en tres dimensiones. Los atlas se usan a más de
192 bloques; no sustituyen el volumen cercano. No se llama directamente a OpenGL ni se transfiere la iluminación de Blender.
La malla `liveevents.mesh.v1` está triangulada, en coordenadas Minecraft Y-up, con un importador validado y acotado.

El humo es procedural, no una simulación de fluidos. No hay bloom HDR ni luces dinámicas del entorno en este backend.
Las diferencias de superficie incluyen movimiento, altura, escombros/salpicaduras, sonidos y comportamiento del terreno.
Los sonidos reutilizan eventos vanilla y llegan con retraso calculado por distancia. Los límites de flash y movimiento
siguen las opciones del cliente. El cálculo visual continúa con F1 para no dejar una sacudida congelada.

Evaluación del 16/10/2026: la API de Modrinth no devolvió versiones Fabric 26.3 para Veil ni Photon. No se incorporan
como dependencias. Veil declara LGPL-3.0; Photon requiere revisar su licencia y plataforma antes de cualquier integración.
Fuentes: https://modrinth.com/mod/veil/versions · https://github.com/Low-Drag-MC/Photon
La API gráfica de Minecraft evita llamadas directas al backend: https://docs.fabricmc.net/develop/rendering/basic-concepts
Common Protection API: https://github.com/Patbox/common-protection-api

## Presupuestos y validación

El servidor limita nuevos anuncios a 24 impactos pendientes por evento y como mínimo cuatro ticks entre ráfagas
automáticas. `brutality=0` suprime caídas automáticas; 0,5 conserva el número nominal y 1 permite un segundo
meteorito por candidato y una mayor proporción de bólidos grandes, siempre dentro de los topes. El perfil visual reduce humo y detalle
lejano antes de consumir la reserva de avisos y trayectorias. El renderizador limita la geometría a 2048 quads por fotograma.
Cráteres: geometría pura en `LiveEvents-Geometry`, lecturas y cambios del mundo exclusivamente en el hilo del servidor.
Aplicación de bloques y enfriamiento usan tareas reanudables con un máximo de ocho cambios por trozo del planificador.

Los tests incluyen contratos de malla y paquetes, impactos en agua y tierra, inmutabilidad de anuncios, diario,
resincronización del cliente, F1, cámaras múltiples, oclusión y doce impactos simultáneos. La CI ejecuta vanilla,
Sodium e Iris por versión y gametests con/sin Lithium y FerriteCore. Los perfiles no instalan packs de shaders externos.

Las capturas y `METEOR_PERF` documentan una prueba local en Ryzen 7 5800X y GTX 1660 SUPER. Una muestra de FPS del arnés
no demuestra por sí sola el objetivo de pérdida inferior al 10 % en todos los equipos. Las pruebas prolongadas con dos
clientes reales, latencia, packs de shaders y proveedores de claims siguen necesitando una sesión de aceptación específica.

### Medición local inicial

En 26.3, el ensayo de doce impactos registró **1.894 quads**, **0,358 ms** de extracción media y una lectura de
**119 FPS** (límite del arnés cercano a 120). Son datos del renderizador y escenario indicados; no una comparación
controlada de FPS ni una garantía para otras tarjetas gráficas. Los logs completos se suben como artifacts de CI cuando falla un job.

### Compatibilidad comprobada en GPU local

Meteoritos con Sodium 26.1.2 e Iris 26.3 completaron el ensayo de tres superficies, F1, vistas múltiples,
oclusiones y doce impactos simultáneos en la GTX 1660 SUPER, sin pack de shaders externo.
La CI usa Mesa y Xvfb; en SDL3 se selecciona EGL y se omite la exigencia de una ventana sRGB para ese entorno.
Es un ajuste del runner, no una modificación de la configuración gráfica distribuida a los jugadores.
Referencias: https://wiki.libsdl.org/SDL3/SDL_HINT_VIDEO_FORCE_EGL · https://wiki.libsdl.org/SDL3/SDL_HINT_OPENGL_FORCE_SRGB_FRAMEBUFFER
