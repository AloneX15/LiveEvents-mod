package com.takumistudios.liveevents.client.visual;

/**
 * Parte de cliente de un módulo. Se llama solo mientras el módulo está activo y en la dimensión del jugador. Un error
 * desactiva el visual de ese módulo, nunca el juego.
 */
public interface ClientVisual {
    /** Cada fotograma: añadir capas u objetos al {@link FrameState}. Sin crear objetos. */
    default void frame(VisualContext ctx, FrameState frame) {
    }

    /** Cada tick de cliente (20/s): partículas y sonidos, con presupuesto. */
    default void tick(VisualContext ctx) {
    }
}
