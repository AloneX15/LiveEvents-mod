# Perfiles y accesibilidad

## Perfiles de intensidad

| Perfil | Para qué | Comportamiento |
|---|---|---|
| `cinematic` | Grabar series y tráileres | El guion tal cual, siempre con la regla de destellos |
| `gameplay` (por defecto) | Eventos con gente jugando | Picos más cortos, HUD siempre visible, efectos de juego completos |
| `safe` | Directos con público amplio o jugadores sensibles | Sin cortes duros (fundidos), destellos ≤ 40 %, glitch suave, temblor al 30 % |

```mcfunction
/le start supernova profile=cinematic
```

**El cliente tiene la última palabra.** El perfil efectivo es el más suave de: el que pide el comando, el máximo del
servidor (`max_profile`) y el que acepta cada jugador (`max_profile` en sus opciones). Si un jugador elige `safe`, verá
todo en seguro aunque el admin lance `cinematic`.

Whiteout del sol demonio por perfil:

| Perfil | Pico | Sostenido |
|---|---|---|
| `cinematic` | 100 % durante 1,5 s | 70 % |
| `gameplay` | 85 % durante 1 s | 35 % |
| `safe` | 40 %, con fundido de 1 s | 20 % |

| cinematic | gameplay | safe |
|---|---|---|
| ![](images/supernova_profile_cinematic.png) | ![](images/supernova_profile_gameplay.png) | ![](images/supernova_profile_safe.png) |

## Regla de destellos (WCAG 2.3.1)

La aplica el cliente en **todos los perfiles**, también en `cinematic`, y se cuenta **entre todos los eventos activos**:

- Como máximo **3 destellos o cortes grandes por segundo**. Los que sobran se convierten en fundidos suaves.
- **Ningún destello de rojo saturado** que ocupe una parte grande de la pantalla (es el caso más peligroso).
- Los parpadeos rápidos (el púlsar a 30 Hz) solo se permiten en **áreas pequeñas**; los haces del púlsar nunca cruzan
  la cámara más de 3 veces por segundo.
- Los parpadeos siguen el **tiempo real**, nunca los fotogramas: no cambian con FPS bajos.

`/le validate` avisa si una definición pide más de 3 cortes por segundo.

## Opciones vanilla que se respetan

| Opción | Efecto en LiveEvents |
|---|---|
| Escala de efectos de pantalla | Escala el glitch y el temblor |
| Ocultar destellos de rayos | Destellos y whiteout al 30 % |
| Pulsación de oscuridad | Escala los apagones del cielo y la noche forzada |
| Subtítulos | Todos los sonidos del mod tienen subtítulo |

## Opciones propias del cliente

Intensidad máxima del destello, temblor sí/no, **reducir movimiento** y perfil máximo aceptado
([Configuración](configuracion.md#cliente-configliveeventsclientjson)).

## Tecla de pánico

Cada jugador puede asignar una tecla (sin asignar por defecto, *Controles → LiveEvents*) que **desactiva al instante**
todos los efectos de pantalla, cámara y sonido de LiveEvents para él, hasta que la vuelva a pulsar. Sigue recibiendo
los efectos de juego. Es la salida de emergencia si alguien se marea.

Para cortarlo todo para todos, el admin tiene [`/le panic`](guia-administradores.md#parada-de-emergencia-le-panic).

## Exposición según el cielo visible

Los efectos de cielo y de pantalla se escalan según **cuánto cielo ve la cámara** (luz de cielo en su posición). En
una cueva profunda nadie se queda ciego: se ve la luz que entra por la boca y se oye el evento. Además, los efectos con
`requires_sky` (radiación, quemaduras, rayos) solo afectan a quien está bajo el cielo, así que **refugiarse funciona**.

## Casos límite

| Situación | Comportamiento |
|---|---|
| Volando con élitros | El temblor baja al 15 % y los empujones son suaves |
| En un vehículo | Temblor a la mitad; el empujón se aplica al vehículo |
| Pantalla de muerte | No se dibujan efectos |
| Reaparecer en mitad de una fase dañina | Segundos de gracia (`respawn_grace_seconds`) |
| Espectador | Ve todo y no recibe efectos de juego (ideal para el cámara) |
| Creativo | Ve todo y no recibe efectos de juego |
| Cambiar de dimensión | Recibe el estado de los eventos de la nueva dimensión |
| Entrar a mitad de evento | Recibe el estado completo y ve el evento en el punto correcto |
| Bajo el agua | Efectos del cielo atenuados |
| FPS muy bajos | Calidad automática; los parpadeos siguen el tiempo real |

## Antes de un directo

1. Ensaya con `/le preview` en el perfil que vayas a usar.
2. Graba el evento y pásalo por un analizador de fotosensibilidad (por ejemplo PEAT o el test de Harding).
3. Da `liveevents:panic` a los moderadores y recuerda a los jugadores la tecla de pánico.
4. Activa `photosensitivity_notice` (activado por defecto): avisa la primera vez que un jugador ve un evento.

---

Creado por **TakumiStudios**.
