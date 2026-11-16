package dev.anchorstack.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class LoaderStore {

    private static final Type LIST = new TypeToken<List<Loader>>() { }.getType();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;

    public LoaderStore(Path file) {
        this.file = file;
    }

    public List<Loader> load() throws IOException {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Loader> list = gson.fromJson(reader, LIST);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        }
    }

    /** Kopiert eine nicht lesbare Datei zur Seite, damit sie beim naechsten Speichern nicht ueberschrieben wird. */
    public Optional<Path> backupBroken() {
        Path target = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
        try {
            Files.copy(file, target);
            return Optional.of(target);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Schreibt in eine Temp-Datei und ersetzt dann, damit ein Absturz die Datei nicht halb zuruecklaesst. */
    public void save(Collection<Loader> loaders) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            gson.toJson(new ArrayList<>(loaders), LIST, writer);
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
