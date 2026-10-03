package com.takumistudios.liveevents.server.journal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.util.AtomicFiles;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Diario de cambios de bloques (§8.5). Cada instancia tiene su archivo {@code <id>.bin.gz} en
 * {@code <mundo>/liveevents/journal/}; los cambios se acumulan en memoria y cada segundo se añaden como un segmento
 * gzip nuevo desde el hilo de E/S (los segmentos concatenados forman un gzip válido). Si el servidor se cae, se pierde
 * como mucho el último segundo.
 */
public final class WorldJournal {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAGIC = 0x4C454A31; // "LEJ1"

    /** Un cambio: dimensión, posición empaquetada, estado anterior y nuevo (serializados como en /setblock). */
    public record Entry(String dimension, long pos, String oldState, String newState) {
    }

    /** Metadatos de un diario (se guardan en index.json). */
    public static final class Meta {
        public String id;
        public String event_id;
        public int instance_id;
        public long created_millis;
        public int entries;
        public boolean reverted;
        public boolean revert_on_end;
    }

    /** Diario abierto de una instancia en curso. */
    public final class Journal {
        private final Meta meta;
        private final List<Entry> pending = new ArrayList<>();

        private Journal(Meta meta) {
            this.meta = meta;
        }

        public String id() {
            return meta.id;
        }

        public Meta meta() {
            return meta;
        }

        public synchronized void record(String dimension, long pos, String oldState, String newState) {
            pending.add(new Entry(dimension, pos, oldState, newState));
            meta.entries++;
        }

        private synchronized List<Entry> drain() {
            if (pending.isEmpty()) {
                return List.of();
            }
            List<Entry> out = new ArrayList<>(pending);
            pending.clear();
            return out;
        }
    }

    private final Path dir;
    private final ExecutorService io;
    private final Map<String, Meta> index = new LinkedHashMap<>();
    private final Map<String, Journal> open = new LinkedHashMap<>();

    public WorldJournal(Path dir, ExecutorService io) {
        this.dir = dir;
        this.io = io;
        loadIndex();
    }

    public synchronized Journal open(String eventId, int instanceId, boolean revertOnEnd) {
        Meta meta = new Meta();
        meta.created_millis = System.currentTimeMillis();
        meta.id = meta.created_millis + "-" + instanceId + "-" + AtomicFiles.safeFileName(eventId);
        meta.event_id = eventId;
        meta.instance_id = instanceId;
        meta.revert_on_end = revertOnEnd;
        index.put(meta.id, meta);
        Journal journal = new Journal(meta);
        open.put(meta.id, journal);
        saveIndexAsync();
        return journal;
    }

    /** Cierra el diario de una instancia terminada (sus cambios pendientes se escriben). */
    public synchronized void close(Journal journal) {
        flush(journal);
        open.remove(journal.id());
        saveIndexAsync();
    }

    /** Escribe los cambios pendientes de todos los diarios abiertos (cada segundo y al guardar). */
    public synchronized void flushAll() {
        for (Journal j : open.values()) {
            flush(j);
        }
    }

    private void flush(Journal journal) {
        List<Entry> batch = journal.drain();
        if (batch.isEmpty()) {
            return;
        }
        Path file = dir.resolve(journal.id() + ".bin.gz");
        io.execute(() -> append(file, batch));
        saveIndexAsync();
    }

    private static void append(Path file, List<Entry> batch) {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream raw = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(raw)))) {
                out.writeInt(MAGIC);
                out.writeInt(batch.size());
                for (Entry e : batch) {
                    writeString(out, e.dimension());
                    out.writeLong(e.pos());
                    writeString(out, e.oldState());
                    writeString(out, e.newState());
                }
            }
        } catch (IOException e) {
            LiveEvents.LOGGER.error("Could not write journal {}", file.getFileName(), e);
        }
    }

    /** Lee todos los cambios de un diario en el hilo de E/S. */
    public CompletableFuture<List<Entry>> readAll(String journalId) {
        flushAll();
        Path file = dir.resolve(journalId + ".bin.gz");
        return CompletableFuture.supplyAsync(() -> {
            List<Entry> entries = new ArrayList<>();
            if (!Files.exists(file)) {
                return entries;
            }
            try (InputStream raw = Files.newInputStream(file);
                 DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
                while (true) {
                    int magic;
                    try {
                        magic = in.readInt();
                    } catch (EOFException eof) {
                        break;
                    }
                    if (magic != MAGIC) {
                        throw new IOException("formato de diario desconocido");
                    }
                    int count = in.readInt();
                    if (count < 0 || count > 10_000_000) {
                        throw new IOException("segmento de diario corrupto");
                    }
                    for (int i = 0; i < count; i++) {
                        entries.add(new Entry(readString(in), in.readLong(), readString(in), readString(in)));
                    }
                }
            } catch (EOFException e) {
                LiveEvents.LOGGER.warn("Journal {} ends abruptly (probably a crash); {} changes recovered", journalId, entries.size());
            } catch (IOException e) {
                LiveEvents.LOGGER.error("Could not read journal {}", journalId, e);
            }
            return entries;
        }, io);
    }

    public synchronized void markReverted(String journalId) {
        Meta meta = index.get(journalId);
        if (meta != null) {
            meta.reverted = true;
            saveIndexAsync();
        }
    }

    public synchronized Optional<Meta> find(String journalId) {
        return Optional.ofNullable(index.get(journalId));
    }

    /** El diario más reciente de una instancia de esta sesión (o de cualquier sesión si no está). */
    public synchronized Optional<Meta> forInstance(int instanceId) {
        return index.values().stream().filter(m -> m.instance_id == instanceId)
                .max(Comparator.comparingLong(m -> m.created_millis));
    }

    /** El último diario sin revertir y con cambios. */
    public synchronized Optional<Meta> last() {
        return index.values().stream().filter(m -> !m.reverted && m.entries > 0)
                .max(Comparator.comparingLong(m -> m.created_millis));
    }

    public synchronized List<Meta> all() {
        return new ArrayList<>(index.values());
    }

    /** Borra los diarios caducados (al arrancar). */
    public synchronized void expire(int days) {
        long limit = System.currentTimeMillis() - days * 86_400_000L;
        List<String> removed = new ArrayList<>();
        index.values().removeIf(m -> {
            if (m.created_millis < limit && !open.containsKey(m.id)) {
                removed.add(m.id);
                return true;
            }
            return false;
        });
        if (!removed.isEmpty()) {
            io.execute(() -> {
                for (String id : removed) {
                    try {
                        Files.deleteIfExists(dir.resolve(id + ".bin.gz"));
                    } catch (IOException e) {
                        LiveEvents.LOGGER.warn("Could not delete expired journal {}", id, e);
                    }
                }
            });
            LiveEvents.LOGGER.info("Removed {} expired journals", removed.size());
            saveIndexAsync();
        }
    }

    private void loadIndex() {
        Path file = dir.resolve("index.json");
        if (!Files.exists(file)) {
            return;
        }
        try {
            Map<String, Meta> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<LinkedHashMap<String, Meta>>() {
                    }.getType());
            if (loaded != null) {
                index.putAll(loaded);
            }
        } catch (IOException | RuntimeException e) {
            LiveEvents.LOGGER.warn("Journal index is corrupt; rebuilding it from scratch", e);
        }
    }

    private void saveIndexAsync() {
        String json = GSON.toJson(index);
        Path file = dir.resolve("index.json");
        io.execute(() -> {
            try {
                AtomicFiles.writeString(file, json);
            } catch (IOException e) {
                LiveEvents.LOGGER.warn("Could not save journal index", e);
            }
        });
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
