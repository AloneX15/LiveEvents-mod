# API para addons

Solo el paquete `com.takumistudios.liveevents.api` (y `client.api`) es **estable** y sigue versionado semántico: un
cambio incompatible solo puede llegar con una versión mayor. El resto del código es interno.

## Eventos (callbacks)

```java
import com.takumistudios.liveevents.api.LiveEventCallbacks;

// Bloquear eventos durante un minijuego
LiveEventCallbacks.START.register(event -> !MiMinijuego.enCurso());

// Logros o avisos al cambiar de fase
LiveEventCallbacks.PHASE_CHANGE.register((event, phaseId) -> {
    if (event.definitionId().equals("liveevents:supernova") && phaseId.equals("demon_sun")) {
        // …
    }
});

// Limpieza o recompensas al terminar ("completed", "stopped", "panic", "error", "shutdown")
LiveEventCallbacks.END.register((event, reason) -> { /* … */ });

// Proteger bloques propios
LiveEventCallbacks.BEFORE_BLOCK_CHANGE.register((event, level, pos, from, to) -> !MisClaims.protegido(level, pos));

// Inmunidad o daño reducido (1 = normal, 0 = inmune; gana el menor)
LiveEventCallbacks.BEFORE_ENTITY_AFFECTED.register((event, entity, effectType) -> MiModAfk.estaAfk(entity) ? 0f : 1f);
```

`LiveEventInfo` da el id de instancia y de definición, la dimensión, el centro, la semilla, el perfil, el tiempo
transcurrido, la duración, la fase actual y si está en pausa o es una previsualización.

## Módulos nuevos

Declara el entrypoint `liveevents` en el `fabric.mod.json` del addon:

```json
"entrypoints": {
  "liveevents": ["com.ejemplo.tornado.TornadoAddon"],
  "client": ["com.ejemplo.tornado.TornadoClient"]
},
"depends": { "liveevents": ">=1.0.0" }
```

Registra el tipo (con sus parámetros: aparecen en la validación) y su comportamiento de servidor:

```java
public final class TornadoAddon implements LiveEventsAddon {
    @Override
    public void registerModules(Registrar registrar) {
        ModuleType type = ModuleType.builder("tornado:vortex", Side.BOTH)
                .category("juego")
                .description("Arrastra entidades en espiral hacia arriba.")
                .param("strength", ParamKind.RANGE, "0.2", "Fuerza", 0, 2)
                .param("radius", ParamKind.NUMBER, "16", "Radio", 1, 128)
                .build();
        registrar.registerModule(type, spec -> new ServerModule() {
            @Override
            public void tick(ServerModuleContext ctx) {
                if (!ctx.every(2)) {
                    return;
                }
                double strength = ctx.params().range("strength", ctx.progress(), 0.2);
                for (LivingEntity e : ctx.affected(ctx.params().number("radius", 16))) {
                    Vec3 toCenter = ctx.event().center().subtract(e.position()).normalize();
                    e.addDeltaMovement(new Vec3(-toCenter.z, 0.3, toCenter.x).scale(strength * ctx.factorFor(e)));
                }
            }
        });
    }
}
```

Y su parte visual en el cliente:

```java
public final class TornadoClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LiveEventsClientApi.registerVisual("tornado:vortex", new ClientVisual() {
            @Override
            public void frame(VisualContext ctx, FrameState frame) {
                frame.addShake((float) (0.3 * ctx.fade), 6, 2);
            }

            @Override
            public void tick(VisualContext ctx) {
                // partículas con ctx.particleBudget(n): respeta el presupuesto y el LOD del jugador
            }
        });
    }
}
```

Una vez registrado, se usa en JSON igual que uno oficial: `{ "type": "tornado:vortex", "strength": 0.4 }`.

Lo que hereda automáticamente un módulo de addon:

- `ServerModuleContext.affected()` ya filtra por `affects`, creativo, espectador, `bypass.damage`, gracia al reaparecer y
  `requires_sky`.
- `damage()` respeta `lethal: false`; `setBlock()` comprueba chunks cargados, bloques inmunes, *block entities*, zonas
  excluidas y la API, y apunta el cambio en el diario.
- `submit()` encola trabajo en el planificador con presupuesto por tick.
- En el cliente, el visual recibe parámetros ya resueltos para el perfil efectivo, pasa por la regla de destellos
  (`ctx.requestFlash`) y por el presupuesto de partículas; un error lo desactiva solo a él.

## Lanzar eventos desde código

No hay una fachada estática: usa los comandos (son la API pública para lanzar) con el `CommandSourceStack` del
servidor, o los callbacks para reaccionar. Así todo pasa por permisos, auditoría y límites.

```java
server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "le start supernova profile=gameplay");
```

---

Creado por **TakumiStudios**.
