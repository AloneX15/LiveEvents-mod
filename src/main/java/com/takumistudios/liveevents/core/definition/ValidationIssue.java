package com.takumistudios.liveevents.core.definition;

/**
 * Error o aviso de validación con archivo, campo y motivo, p. ej.
 * {@code miserie:apocalipsis → phases[1].modules[2].color: $cyna no existe en la paleta. ¿Querías decir $cyan?}
 */
public record ValidationIssue(Severity severity, String eventId, String source, String path, String message) {
    public enum Severity {
        ERROR,
        WARNING
    }

    public static ValidationIssue error(String eventId, String source, String path, String message) {
        return new ValidationIssue(Severity.ERROR, eventId, source, path, message);
    }

    public static ValidationIssue warning(String eventId, String source, String path, String message) {
        return new ValidationIssue(Severity.WARNING, eventId, source, path, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        return (isError() ? "ERROR " : "WARN ") + eventId + (path.isEmpty() ? "" : " → " + path) + ": " + message
                + (source.isEmpty() ? "" : " (" + source + ")");
    }
}
