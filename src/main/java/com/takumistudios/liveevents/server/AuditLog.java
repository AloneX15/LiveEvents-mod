package com.takumistudios.liveevents.server;

import com.takumistudios.liveevents.LiveEvents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;

/**
 * Registro de auditoría (§10.3): quién lanzó, paró o revirtió qué, cuándo, dónde y con qué parámetros. Un archivo por
 * día en {@code logs/liveevents-audit-AAAA-MM-DD.log}; se borran solos pasados {@code audit_retention_days}. No hay
 * telemetría: nada sale del servidor.
 */
public final class AuditLog {
    private final Path dir;
    private final ExecutorService io;
    private volatile boolean enabled;

    public AuditLog(Path logsDir, ExecutorService io, boolean enabled) {
        this.dir = logsDir;
        this.io = io;
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void log(String actor, String action, String details) {
        if (!enabled) {
            return;
        }
        String line = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + " | " + sanitize(actor) + " | "
                + sanitize(action) + " | " + sanitize(details) + System.lineSeparator();
        Path file = dir.resolve("liveevents-audit-" + LocalDate.now() + ".log");
        io.execute(() -> {
            try {
                Files.createDirectories(dir);
                Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LiveEvents.LOGGER.warn("Could not write the audit log", e);
            }
        });
    }

    /** Borra los archivos más antiguos que la retención (al arrancar). */
    public void purge(int retentionDays) {
        LocalDate limit = LocalDate.now().minusDays(retentionDays);
        io.execute(() -> {
            if (!Files.isDirectory(dir)) {
                return;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "liveevents-audit-*.log")) {
                for (Path f : files) {
                    String name = f.getFileName().toString();
                    try {
                        LocalDate date = LocalDate.parse(name.substring("liveevents-audit-".length(), name.length() - 4));
                        if (date.isBefore(limit)) {
                            Files.deleteIfExists(f);
                        }
                    } catch (RuntimeException ignored) {
                        LiveEvents.LOGGER.debug("Skipping unexpected audit file {}", name);
                    }
                }
            } catch (IOException e) {
                LiveEvents.LOGGER.warn("Could not purge old audit logs", e);
            }
        });
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }
}
