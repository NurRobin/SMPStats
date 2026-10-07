package de.nurrobin.smpstats.throwback;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-player state machine of the hall: join, corridor, rules, portal, finish. It knows nothing about
 * entities or worlds, so every rule of the tour is testable without a server.
 */
public final class ThrowbackFlow {

    public enum Mode { TOUR, VISIT }

    public enum PortalDecision {
        /** The player is not in the hall. */
        NOT_IN_HALL,
        /** Mandatory tour and the rules were not confirmed in this tour. */
        BLOCKED,
        /** Mandatory tour done; progress is marked completed. */
        FINISHED_TOUR,
        /** Voluntary visit, nothing to store. */
        LEFT_VISIT
    }

    private static final class Session {
        final Mode mode;
        boolean rulesRead;

        Session(Mode mode) {
            this.mode = mode;
        }
    }

    private final ProgressRepository repository;
    private final LongSupplier clock;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private volatile String rulesVersion;

    public ThrowbackFlow(ProgressRepository repository, String rulesVersion, LongSupplier clock) {
        this.repository = repository;
        this.rulesVersion = rulesVersion;
        this.clock = clock;
    }

    public void setRulesVersion(String rulesVersion) {
        this.rulesVersion = rulesVersion;
    }

    public String rulesVersion() {
        return rulesVersion;
    }

    /**
     * Called when a player joins the server.
     *
     * @return true when the player has to be sent to the hall (mandatory tour, started or restarted)
     */
    public boolean onJoin(UUID uuid) {
        Optional<Progress> progress = repository.find(uuid);
        if (progress.isPresent() && progress.get().completed()) {
            return false;
        }
        if (progress.isEmpty()) {
            repository.save(uuid, Progress.fresh());
        }
        sessions.put(uuid, new Session(Mode.TOUR));
        return true;
    }

    /** Voluntary visit (portal at the spawn or {@code /throwback}); an open mandatory tour stays mandatory. */
    public Mode startVisit(UUID uuid) {
        return sessions.computeIfAbsent(uuid, k -> new Session(Mode.VISIT)).mode;
    }

    public Optional<Mode> mode(UUID uuid) {
        Session s = sessions.get(uuid);
        return s == null ? Optional.empty() : Optional.of(s.mode);
    }

    public boolean inHall(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public boolean rulesRead(UUID uuid) {
        Session s = sessions.get(uuid);
        return s != null && s.rulesRead;
    }

    /** Stores the confirmation with time and rules version; only possible while the player is in the hall. */
    public boolean confirmRules(UUID uuid) {
        Session s = sessions.get(uuid);
        if (s == null) {
            return false;
        }
        s.rulesRead = true;
        Progress current = repository.find(uuid).orElseGet(Progress::fresh);
        repository.save(uuid, current.withRules(clock.getAsLong(), rulesVersion));
        return true;
    }

    /** The player stepped into the portal region at the end of the hall. */
    public PortalDecision portal(UUID uuid) {
        Session s = sessions.get(uuid);
        if (s == null) {
            return PortalDecision.NOT_IN_HALL;
        }
        if (s.mode == Mode.VISIT) {
            sessions.remove(uuid);
            return PortalDecision.LEFT_VISIT;
        }
        if (!s.rulesRead) {
            return PortalDecision.BLOCKED;
        }
        Progress current = repository.find(uuid).orElseGet(Progress::fresh);
        repository.save(uuid, current.withCompleted(clock.getAsLong()));
        sessions.remove(uuid);
        return PortalDecision.FINISHED_TOUR;
    }

    /** Quit, world change or leaving by command. An unfinished mandatory tour stays unfinished. */
    public void end(UUID uuid) {
        sessions.remove(uuid);
    }

    /**
     * Staff override: the player counts as having finished the tour without confirming the rules. Any running
     * tour session ends.
     */
    public void markCompleted(UUID uuid) {
        Progress current = repository.find(uuid).orElseGet(Progress::fresh);
        repository.save(uuid, current.withCompleted(clock.getAsLong()));
        sessions.remove(uuid);
    }

    public boolean completed(UUID uuid) {
        return repository.find(uuid).map(Progress::completed).orElse(false);
    }
}
