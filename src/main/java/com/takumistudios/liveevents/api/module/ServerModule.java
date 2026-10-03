package com.takumistudios.liveevents.api.module;

/**
 * Comportamiento de un módulo en el servidor. Se crea una instancia por módulo y por evento. Los métodos se llaman en
 * el hilo del servidor; un error desactiva solo este módulo en esta instancia y el resto del evento continúa.
 * <p>
 * Para trabajo pesado (muchos bloques), encolar tareas con {@link ServerModuleContext#submit} en lugar de hacerlo en
 * {@link #tick}: el planificador respeta el presupuesto de milisegundos por tick.
 */
public interface ServerModule {
    /** Cuando el evento llega al inicio del módulo. */
    default void start(ServerModuleContext ctx) {
    }

    /** Cada tick mientras el módulo está activo. Usar {@link ServerModuleContext#every} para espaciar el trabajo. */
    default void tick(ServerModuleContext ctx) {
    }

    /** Al terminar el módulo o el evento (también al cancelarse). */
    default void end(ServerModuleContext ctx) {
    }
}
