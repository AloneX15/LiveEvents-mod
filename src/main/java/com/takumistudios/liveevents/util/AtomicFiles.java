package com.takumistudios.liveevents.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Escrituras atómicas (archivo temporal + move) y saneado de nombres de archivo. */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    public static void writeString(Path target, String content) throws IOException {
        writeBytes(target, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeBytes(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, content);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Convierte un texto (id de evento, nombre…) en un nombre de archivo seguro: sin "..", "/", "\" ni caracteres
     * raros. Evita path traversal con datos que vienen de comandos o de packs.
     */
    public static String safeFileName(String text) {
        String s = text.toLowerCase(Locale.ROOT).replace(':', '_').replaceAll("[^a-z0-9_.-]", "_");
        while (s.contains("..")) {
            s = s.replace("..", "_");
        }
        if (s.isEmpty() || s.equals(".")) {
            s = "_";
        }
        return s.length() > 96 ? s.substring(0, 96) : s;
    }
}
