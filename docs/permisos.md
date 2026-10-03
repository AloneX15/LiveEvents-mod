# Permisos

LiveEvents usa la **API de permisos de Fabric** (incluida en Fabric API 26.x). Si hay un gestor de permisos instalado
(LuckPerms u otro compatible), decide él; si no, cada nodo tiene un **nivel de OP de respaldo**. Así el mod funciona
con LuckPerms, con otro gestor o sin ninguno.

Los nodos tienen la forma `liveevents:<nodo>`.

| Nodo | OP de respaldo | Descripción |
|---|---|---|
| `liveevents:command` | 2 | Acceso básico a `/le` |
| `liveevents:list` | 2 | Listar eventos e instancias, `/le info` |
| `liveevents:start` | 2 | Lanzar cualquier evento |
| `liveevents:start.<evento>` | 2 | Lanzar un evento oficial concreto (`liveevents:start.supernova`) |
| `liveevents:start.<espacio>.<evento>` | 2 | **Se crea solo** para cada evento de la comunidad (`liveevents:start.miserie.apocalipsis`) |
| `liveevents:start.anywhere` | 3 | Lanzar en otras coordenadas o dimensión (sin él, un jugador solo lanza en su posición) |
| `liveevents:control` | 2 | Pausar, reanudar y saltar de fase |
| `liveevents:stop` | 2 | Cancelar instancias |
| `liveevents:revert` | 3 | Revertir cambios en el mundo |
| `liveevents:edit` | 3 | `/le set` y secuelas del cielo |
| `liveevents:reload` | 3 | Recargar definiciones y configuración |
| `liveevents:preview` | 2 | Previsualizar sin efectos |
| `liveevents:validate` | 2 | Validar definiciones |
| `liveevents:create` | 3 | Crear variantes con `/le create` |
| `liveevents:debug` | 3 | `seek`, `timescale`, `debug timeline`, `debug perf`, `debug report` |
| `liveevents:panic` | 2 | `/le panic` (dáselo también a los moderadores de un directo) |
| `liveevents:schedule` | 3 | Programar eventos y secuencias |
| `liveevents:gui` | 2 | Panel de administración |
| `liveevents:notify` | 2 | Recibe avisos (inicios, módulos que fallan, watchdog) |
| `liveevents:bypass.damage` | — | Inmune al daño y a los efectos de estado de los eventos |

## Ejemplos con LuckPerms

```text
# Los moderadores de la serie solo pueden lanzar los eventos del pack "miserie" y usar el pánico
/lp group moderador permission set liveevents:command true
/lp group moderador permission set liveevents:start.miserie.* true
/lp group moderador permission set liveevents:panic true

# El cámara de la serie no recibe daño de los eventos
/lp user Camara permission set liveevents:bypass.damage true
```

LuckPerms aplica sus contextos (mundo, servidor…) porque las comprobaciones usan la fuente del comando.

## Consola, RCON y bloques de comandos

La consola y RCON tienen todos los permisos. Los bloques de comandos y las funciones tienen nivel de OP 2, así que
pueden lanzar eventos; las secuencias ejecutan sus comandos **con los permisos de quien las lanzó**, nunca con permisos
elevados.

---

Creado por **TakumiStudios**.
