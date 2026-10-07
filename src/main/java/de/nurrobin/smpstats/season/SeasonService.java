package de.nurrobin.smpstats.season;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Imports season folders from {@code plugins/SMPStats/seasons/<season>/} into the database. */
public final class SeasonService {
    private static final Pattern SEASON_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    public record Outcome(String season, int players, int statRows, List<String> problems) {
    }

    private final Logger logger;
    private final Path seasonsRoot;
    private final SeasonStore store;
    private final SeasonImporter importer;
    private final Executor async;
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private volatile java.util.function.Consumer<String> onImported = season -> { };

    public SeasonService(Logger logger, Path seasonsRoot, SeasonStore store, SeasonImporter importer, Executor async) {
        this.logger = logger;
        this.seasonsRoot = seasonsRoot;
        this.store = store;
        this.importer = importer;
        this.async = async;
    }

    /** Called (on the import thread) after a season was written successfully. */
    public void onImported(java.util.function.Consumer<String> listener) {
        this.onImported = listener;
    }

    public static boolean isValidName(String season) {
        return season != null && SEASON_NAME.matcher(season).matches();
    }

    public SeasonStore store() {
        return store;
    }

    public Optional<SeasonMeta> meta(String season) throws SQLException {
        return store.meta(season);
    }

    public List<String> pendingSeasons() {
        if (!Files.isDirectory(seasonsRoot)) {
            return List.of();
        }
        List<String> pending = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(seasonsRoot)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                String name = dir.getFileName().toString();
                if (isValidName(name) && store.meta(name).isEmpty()) {
                    pending.add(name);
                }
            }
        } catch (IOException | SQLException e) {
            logger.warning("Saison-Ordner nicht lesbar: " + e.getMessage());
        }
        return pending;
    }

    public void autoImport() {
        for (String season : pendingSeasons()) {
            importSeason(season).whenComplete((outcome, error) -> {
                if (error != null) {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    logger.warning("Auto-Import Saison " + season + " fehlgeschlagen: " + cause.getMessage());
                }
            });
        }
    }

    public CompletableFuture<Outcome> importSeason(String season) {
        if (!isValidName(season)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Ungültiger Saison-Name: " + season));
        }
        Path dir = seasonsRoot.resolve(season);
        if (!Files.isDirectory(dir)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Ordner fehlt: seasons/" + season));
        }
        if (!running.add(season)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Import für Saison " + season + " läuft bereits"));
        }
        CompletableFuture<Outcome> result = new CompletableFuture<>();
        try {
            async.execute(() -> {
                try {
                    result.complete(runImport(season, dir));
                } catch (SQLException | RuntimeException e) {
                    result.completeExceptionally(e);
                } finally {
                    running.remove(season);
                }
            });
        } catch (RuntimeException e) {
            running.remove(season);
            result.completeExceptionally(e);
        }
        return result;
    }

    private Outcome runImport(String season, Path dir) throws SQLException {
        SeasonImporter.Result read = importer.read(season, dir);
        if (read.data().players().isEmpty()) {
            read.problems().forEach(p -> logger.warning("[Saison " + season + "] " + p));
            throw new IllegalStateException("Keine Spielerdaten in seasons/" + season + " gefunden");
        }
        int rows = store.replace(read.data(), System.currentTimeMillis());
        logger.info("Saison " + season + " importiert: " + read.data().players().size() + " Spieler, " + rows
                + " Werte, Quelle: " + read.data().source());
        read.problems().forEach(p -> logger.warning("[Saison " + season + "] " + p));
        try {
            onImported.accept(season);
        } catch (RuntimeException e) {
            logger.warning("Saison-Import-Hook fehlgeschlagen: " + e.getMessage());
        }
        return new Outcome(season, read.data().players().size(), rows, List.copyOf(read.problems()));
    }
}
