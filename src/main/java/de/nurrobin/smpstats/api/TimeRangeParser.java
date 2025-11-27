package de.nurrobin.smpstats.api;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing human-readable time range strings into epoch timestamps.
 * 
 * <p>Supports the following formats:
 * <ul>
 *   <li><b>Relative durations:</b> {@code 6h}, {@code 30m}, {@code 3d}, {@code 2w}</li>
 *   <li><b>Named periods:</b> {@code today}, {@code yesterday}, {@code this_week}, {@code this_month}</li>
 *   <li><b>Epoch milliseconds:</b> raw long values (for backwards compatibility)</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * TimeRangeParser parser = new TimeRangeParser();
 * 
 * // Parse "last 6 hours"
 * Optional<TimeRange> range = parser.parse("6h");
 * 
 * // Parse "today"
 * Optional<TimeRange> today = parser.parse("today");
 * 
 * // Parse epoch timestamp
 * Optional<TimeRange> epoch = parser.parse("1700000000000");
 * }</pre>
 */
public class TimeRangeParser {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhdw])$", Pattern.CASE_INSENSITIVE);

    private final ZoneId zoneId;

    /**
     * Creates a parser using the system default timezone.
     */
    public TimeRangeParser() {
        this(ZoneId.systemDefault());
    }

    /**
     * Creates a parser using the specified timezone.
     * @param zoneId the timezone to use for named period calculations
     */
    public TimeRangeParser(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    /**
     * Represents a parsed time range with start and end timestamps.
     */
    public record TimeRange(long since, long until) {
        /**
         * Creates a time range from a start time until now.
         * @param since start timestamp in milliseconds
         * @return a new TimeRange ending at current time
         */
        public static TimeRange fromNow(long since) {
            return new TimeRange(since, System.currentTimeMillis());
        }
    }

    /**
     * Parses a time range string into epoch timestamps.
     * 
     * @param input the time range string to parse (case-insensitive)
     * @return an Optional containing the parsed TimeRange, or empty if input is null/invalid
     */
    public Optional<TimeRange> parse(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String trimmed = input.trim().toLowerCase();

        // Try parsing as epoch milliseconds first
        Optional<TimeRange> epochResult = parseEpoch(input.trim());
        if (epochResult.isPresent()) {
            return epochResult;
        }

        // Try parsing as relative duration (e.g., "6h", "3d")
        Optional<TimeRange> durationResult = parseDuration(trimmed);
        if (durationResult.isPresent()) {
            return durationResult;
        }

        // Try parsing as named period
        return parseNamedPeriod(trimmed);
    }

    /**
     * Parses a raw epoch timestamp in milliseconds.
     * @param input the string to parse
     * @return Optional containing the TimeRange if valid epoch, empty otherwise
     */
    private Optional<TimeRange> parseEpoch(String input) {
        try {
            long epoch = Long.parseLong(input);
            // Only treat as epoch if it looks like a reasonable timestamp (after year 2000)
            if (epoch > 946684800000L) {
                return Optional.of(TimeRange.fromNow(epoch));
            }
        } catch (NumberFormatException ignored) {
            // Not a number, try other formats
        }
        return Optional.empty();
    }

    /**
     * Parses relative duration strings like "6h", "30m", "3d", "2w".
     * @param input lowercase trimmed input
     * @return Optional containing the TimeRange if valid duration, empty otherwise
     */
    private Optional<TimeRange> parseDuration(String input) {
        Matcher matcher = DURATION_PATTERN.matcher(input);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();

        long milliseconds = switch (unit) {
            case "s" -> value * 1000L;
            case "m" -> value * 60L * 1000L;
            case "h" -> value * 60L * 60L * 1000L;
            case "d" -> value * 24L * 60L * 60L * 1000L;
            case "w" -> value * 7L * 24L * 60L * 60L * 1000L;
            default -> 0L;
        };

        if (milliseconds > 0) {
            long now = System.currentTimeMillis();
            return Optional.of(new TimeRange(now - milliseconds, now));
        }

        return Optional.empty();
    }

    /**
     * Parses named period strings like "today", "yesterday", "this_week", "this_month".
     * @param input lowercase trimmed input
     * @return Optional containing the TimeRange if valid named period, empty otherwise
     */
    private Optional<TimeRange> parseNamedPeriod(String input) {
        LocalDate today = LocalDate.now(zoneId);
        long now = System.currentTimeMillis();

        return switch (input) {
            case "today" -> {
                long startOfDay = today.atStartOfDay(zoneId).toInstant().toEpochMilli();
                yield Optional.of(new TimeRange(startOfDay, now));
            }
            case "yesterday" -> {
                LocalDate yesterday = today.minusDays(1);
                long start = yesterday.atStartOfDay(zoneId).toInstant().toEpochMilli();
                long end = today.atStartOfDay(zoneId).toInstant().toEpochMilli();
                yield Optional.of(new TimeRange(start, end));
            }
            case "this_week" -> {
                // Start of week (Monday)
                LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                long start = startOfWeek.atStartOfDay(zoneId).toInstant().toEpochMilli();
                yield Optional.of(new TimeRange(start, now));
            }
            case "last_week" -> {
                LocalDate startOfThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate startOfLastWeek = startOfThisWeek.minusWeeks(1);
                long start = startOfLastWeek.atStartOfDay(zoneId).toInstant().toEpochMilli();
                long end = startOfThisWeek.atStartOfDay(zoneId).toInstant().toEpochMilli();
                yield Optional.of(new TimeRange(start, end));
            }
            case "this_month" -> {
                LocalDate startOfMonth = today.withDayOfMonth(1);
                long start = startOfMonth.atStartOfDay(zoneId).toInstant().toEpochMilli();
                yield Optional.of(new TimeRange(start, now));
            }
            case "last_month" -> {
                LocalDate startOfThisMonth = today.withDayOfMonth(1);
                LocalDate startOfLastMonth = startOfThisMonth.minusMonths(1);
                long start = startOfLastMonth.atStartOfDay(zoneId).toInstant().toEpochMilli();
                long end = startOfThisMonth.atStartOfDay(zoneId).toInstant().toEpochMilli();
                yield Optional.of(new TimeRange(start, end));
            }
            default -> Optional.empty();
        };
    }

    /**
     * Convenience method to parse only the 'since' timestamp from a time range string.
     * Falls back to default value if parsing fails.
     * 
     * @param input the time range string
     * @param defaultValue value to return if parsing fails
     * @return the 'since' timestamp in milliseconds
     */
    public long parseSince(String input, long defaultValue) {
        return parse(input).map(TimeRange::since).orElse(defaultValue);
    }

    /**
     * Convenience method to parse only the 'until' timestamp from a time range string.
     * Falls back to default value if parsing fails.
     * 
     * @param input the time range string
     * @param defaultValue value to return if parsing fails
     * @return the 'until' timestamp in milliseconds
     */
    public long parseUntil(String input, long defaultValue) {
        return parse(input).map(TimeRange::until).orElse(defaultValue);
    }
}
