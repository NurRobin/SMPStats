package de.nurrobin.smpstats.throwback;

/** Persisted state of one player's mandatory tour; {@code completedAt == null} means the tour is still open. */
public record Progress(Long completedAt, Long rulesConfirmedAt, String rulesVersion) {

    public static Progress fresh() {
        return new Progress(null, null, null);
    }

    public boolean completed() {
        return completedAt != null;
    }

    public Progress withRules(long at, String version) {
        return new Progress(completedAt, at, version);
    }

    public Progress withCompleted(long at) {
        return new Progress(at, rulesConfirmedAt, rulesVersion);
    }
}
