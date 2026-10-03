# Mixins

LiveEvents usa las APIs de Fabric para todo (comandos, red, HUD, ticks, eventos de jugador, permisos, recursos) salvo
un único caso que Fabric API no cubre:

| Mixin | Objetivo | Motivo | Riesgo de conflicto |
|---|---|---|---|
| `client.mixin.CameraMixin` | `@Inject` al final (`TAIL`) de `net.minecraft.client.Camera#update(DeltaTracker)` | Temblor de cámara de los eventos (§7.6 del plan) y guardar la cámara activa para proyectar el cielo (su acceso cambia entre 26.1 y 26.2) | Bajo: es **aditivo** (suma un pequeño desplazamiento a la rotación ya calculada con `setRotation`, sin reemplazar nada), solo actúa mientras hay un temblor activo y tiene `require = 0`: si el método cambiara en otra versión, el temblor se desactivaría sin romper el juego. Mods de cámara que fijen la rotación después (Freecam) pueden anularlo, lo que es aceptable |
