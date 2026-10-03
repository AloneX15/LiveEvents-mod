package com.takumistudios.liveevents.core.expr;

/** Error al compilar o evaluar una expresión de una definición. */
public final class ExpressionException extends RuntimeException {
    public ExpressionException(String message) {
        super(message);
    }
}
