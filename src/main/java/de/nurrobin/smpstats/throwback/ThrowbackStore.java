package de.nurrobin.smpstats.throwback;

import de.nurrobin.smpstats.database.StatsStorage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** SQLite access to {@code throwback_progress}. Blocking; call it from an async thread. */
public final class ThrowbackStore {
    private final StatsStorage storage;

    public ThrowbackStore(StatsStorage storage) {
        this.storage = storage;
    }

    public Optional<Progress> find(UUID uuid) throws SQLException {
        return storage.withConnection(c -> {
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT completed_at, rules_confirmed_at, rules_version FROM throwback_progress WHERE uuid = ?")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<Progress>empty();
                    }
                    return Optional.of(new Progress(nullableLong(rs, 1), nullableLong(rs, 2), rs.getString(3)));
                }
            }
        });
    }

    public void save(UUID uuid, Progress progress) throws SQLException {
        storage.withConnection(c -> {
            try (PreparedStatement st = c.prepareStatement(
                    "INSERT INTO throwback_progress(uuid, completed_at, rules_confirmed_at, rules_version) VALUES (?, ?, ?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET completed_at = excluded.completed_at, "
                            + "rules_confirmed_at = excluded.rules_confirmed_at, rules_version = excluded.rules_version")) {
                st.setString(1, uuid.toString());
                setNullableLong(st, 2, progress.completedAt());
                setNullableLong(st, 3, progress.rulesConfirmedAt());
                st.setString(4, progress.rulesVersion());
                st.executeUpdate();
            }
            return null;
        });
    }

    public void delete(UUID uuid) throws SQLException {
        storage.withConnection(c -> {
            try (PreparedStatement st = c.prepareStatement("DELETE FROM throwback_progress WHERE uuid = ?")) {
                st.setString(1, uuid.toString());
                st.executeUpdate();
            }
            return null;
        });
    }

    private static Long nullableLong(ResultSet rs, int col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static void setNullableLong(PreparedStatement st, int col, Long value) throws SQLException {
        if (value == null) {
            st.setNull(col, java.sql.Types.INTEGER);
        } else {
            st.setLong(col, value);
        }
    }
}
