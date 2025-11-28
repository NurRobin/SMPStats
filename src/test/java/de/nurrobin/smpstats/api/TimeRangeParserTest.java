package de.nurrobin.smpstats.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TimeRangeParserTest {
    
    private TimeRangeParser parser;
    private static final ZoneId TEST_ZONE = ZoneId.of("UTC");
    
    @BeforeEach
    void setUp() {
        parser = new TimeRangeParser(TEST_ZONE);
    }
    
    // ========== Null and Empty Input Tests ==========
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void parseReturnsEmptyForNullOrBlankInput(String input) {
        assertTrue(parser.parse(input).isEmpty());
    }
    
    // ========== Duration Parsing Tests ==========
    
    @Test
    void parseSecondsDuration() {
        long before = System.currentTimeMillis();
        Optional<TimeRangeParser.TimeRange> result = parser.parse("30s");
        long after = System.currentTimeMillis();
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        // Check 'since' is approximately 30 seconds ago
        long expectedSince = before - 30_000L;
        assertTrue(range.since() >= expectedSince - 100 && range.since() <= after - 30_000L + 100);
        assertTrue(range.until() >= before && range.until() <= after);
    }
    
    @Test
    void parseMinutesDuration() {
        long before = System.currentTimeMillis();
        Optional<TimeRangeParser.TimeRange> result = parser.parse("15m");
        long after = System.currentTimeMillis();
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        long expectedSince = before - (15L * 60 * 1000);
        assertTrue(range.since() >= expectedSince - 100 && range.since() <= after - (15L * 60 * 1000) + 100);
    }
    
    @Test
    void parseHoursDuration() {
        long before = System.currentTimeMillis();
        Optional<TimeRangeParser.TimeRange> result = parser.parse("6h");
        long after = System.currentTimeMillis();
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        long expectedSince = before - (6L * 60 * 60 * 1000);
        assertTrue(range.since() >= expectedSince - 100 && range.since() <= after - (6L * 60 * 60 * 1000) + 100);
    }
    
    @Test
    void parseDaysDuration() {
        long before = System.currentTimeMillis();
        Optional<TimeRangeParser.TimeRange> result = parser.parse("3d");
        long after = System.currentTimeMillis();
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        long expectedSince = before - (3L * 24 * 60 * 60 * 1000);
        assertTrue(range.since() >= expectedSince - 100 && range.since() <= after - (3L * 24 * 60 * 60 * 1000) + 100);
    }
    
    @Test
    void parseWeeksDuration() {
        long before = System.currentTimeMillis();
        Optional<TimeRangeParser.TimeRange> result = parser.parse("2w");
        long after = System.currentTimeMillis();
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        long expectedSince = before - (2L * 7 * 24 * 60 * 60 * 1000);
        assertTrue(range.since() >= expectedSince - 100);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"1S", "1M", "1H", "1D", "1W"})
    void parseDurationCaseInsensitive(String input) {
        assertTrue(parser.parse(input).isPresent());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"0h", "0d", "0m"})
    void parseZeroDurationReturnsEmpty(String input) {
        assertTrue(parser.parse(input).isEmpty());
    }
    
    // ========== Epoch Timestamp Tests ==========
    
    @Test
    void parseEpochTimestamp() {
        long epochMs = 1700000000000L; // Nov 2023
        Optional<TimeRangeParser.TimeRange> result = parser.parse(String.valueOf(epochMs));
        
        assertTrue(result.isPresent());
        assertEquals(epochMs, result.get().since());
    }
    
    @Test
    void parseSmallNumberNotTreatedAsEpoch() {
        // Numbers before year 2000 (946684800000L) should not be treated as epoch
        Optional<TimeRangeParser.TimeRange> result = parser.parse("123456");
        
        assertTrue(result.isEmpty());
    }
    
    @Test
    void parseValidEpochTimestampWithTrim() {
        long epochMs = 1700000000000L;
        Optional<TimeRangeParser.TimeRange> result = parser.parse("  " + epochMs + "  ");
        
        assertTrue(result.isPresent());
        assertEquals(epochMs, result.get().since());
    }
    
    // ========== Named Period Tests ==========
    
    @Test
    void parseTodayPeriod() {
        LocalDate today = LocalDate.now(TEST_ZONE);
        long startOfDay = today.atStartOfDay(TEST_ZONE).toInstant().toEpochMilli();
        
        long before = System.currentTimeMillis();
        Optional<TimeRangeParser.TimeRange> result = parser.parse("today");
        long after = System.currentTimeMillis();
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        assertEquals(startOfDay, range.since());
        assertTrue(range.until() >= before && range.until() <= after);
    }
    
    @Test
    void parseYesterdayPeriod() {
        LocalDate today = LocalDate.now(TEST_ZONE);
        LocalDate yesterday = today.minusDays(1);
        
        long startOfYesterday = yesterday.atStartOfDay(TEST_ZONE).toInstant().toEpochMilli();
        long startOfToday = today.atStartOfDay(TEST_ZONE).toInstant().toEpochMilli();
        
        Optional<TimeRangeParser.TimeRange> result = parser.parse("yesterday");
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        assertEquals(startOfYesterday, range.since());
        assertEquals(startOfToday, range.until());
    }
    
    @Test
    void parseThisWeekPeriod() {
        LocalDate today = LocalDate.now(TEST_ZONE);
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long expectedSince = startOfWeek.atStartOfDay(TEST_ZONE).toInstant().toEpochMilli();
        
        long before = System.currentTimeMillis();
        Optional<TimeRangeParser.TimeRange> result = parser.parse("this_week");
        long after = System.currentTimeMillis();
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        assertEquals(expectedSince, range.since());
        assertTrue(range.until() >= before && range.until() <= after);
    }
    
    @Test
    void parseLastWeekPeriod() {
        LocalDate today = LocalDate.now(TEST_ZONE);
        LocalDate startOfThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate startOfLastWeek = startOfThisWeek.minusWeeks(1);
        
        long expectedSince = startOfLastWeek.atStartOfDay(TEST_ZONE).toInstant().toEpochMilli();
        long expectedUntil = startOfThisWeek.atStartOfDay(TEST_ZONE).toInstant().toEpochMilli();
        
        Optional<TimeRangeParser.TimeRange> result = parser.parse("last_week");
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        assertEquals(expectedSince, range.since());
        assertEquals(expectedUntil, range.until());
    }
    
    @Test
    void parseThisMonthPeriod() {
        LocalDate today = LocalDate.now(TEST_ZONE);
        LocalDate startOfMonth = today.withDayOfMonth(1);
        long expectedSince = startOfMonth.atStartOfDay(TEST_ZONE).toInstant().toEpochMilli();
        
        long before = System.currentTimeMillis();
        Optional<TimeRangeParser.TimeRange> result = parser.parse("this_month");
        long after = System.currentTimeMillis();
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        assertEquals(expectedSince, range.since());
        assertTrue(range.until() >= before && range.until() <= after);
    }
    
    @Test
    void parseLastMonthPeriod() {
        LocalDate today = LocalDate.now(TEST_ZONE);
        LocalDate startOfThisMonth = today.withDayOfMonth(1);
        LocalDate startOfLastMonth = startOfThisMonth.minusMonths(1);
        
        long expectedSince = startOfLastMonth.atStartOfDay(TEST_ZONE).toInstant().toEpochMilli();
        long expectedUntil = startOfThisMonth.atStartOfDay(TEST_ZONE).toInstant().toEpochMilli();
        
        Optional<TimeRangeParser.TimeRange> result = parser.parse("last_month");
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        assertEquals(expectedSince, range.since());
        assertEquals(expectedUntil, range.until());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"TODAY", "Today", "tOdAy"})
    void parseNamedPeriodCaseInsensitive(String input) {
        assertTrue(parser.parse(input).isPresent());
    }
    
    // ========== Invalid Input Tests ==========
    
    @ParameterizedTest
    @ValueSource(strings = {"invalid", "6hours", "hours6", "6hh", "h6", "-6h", "6.5h", "next_week", "tomorrow"})
    void parseReturnsEmptyForInvalidInput(String input) {
        assertTrue(parser.parse(input).isEmpty());
    }
    
    // ========== Convenience Method Tests ==========
    
    @Test
    void parseSinceReturnsValueOnSuccess() {
        long defaultValue = 0L;
        long result = parser.parseSince("6h", defaultValue);
        
        assertTrue(result > 0);
        assertTrue(result < System.currentTimeMillis());
        assertTrue(result > System.currentTimeMillis() - 7L * 60 * 60 * 1000); // less than 7 hours ago
    }
    
    @Test
    void parseSinceReturnsDefaultOnFailure() {
        long defaultValue = 12345L;
        long result = parser.parseSince("invalid", defaultValue);
        
        assertEquals(defaultValue, result);
    }
    
    @Test
    void parseUntilReturnsValueOnSuccess() {
        long before = System.currentTimeMillis();
        long result = parser.parseUntil("6h", 0L);
        long after = System.currentTimeMillis();
        
        assertTrue(result >= before && result <= after);
    }
    
    @Test
    void parseUntilReturnsDefaultOnFailure() {
        long defaultValue = 12345L;
        long result = parser.parseUntil("invalid", defaultValue);
        
        assertEquals(defaultValue, result);
    }
    
    // ========== TimeRange Record Tests ==========
    
    @Test
    void timeRangeFromNowCreatesCorrectRange() {
        long since = 1700000000000L;
        long before = System.currentTimeMillis();
        
        TimeRangeParser.TimeRange range = TimeRangeParser.TimeRange.fromNow(since);
        
        long after = System.currentTimeMillis();
        
        assertEquals(since, range.since());
        assertTrue(range.until() >= before && range.until() <= after);
    }
    
    // ========== Default Constructor Tests ==========
    
    @Test
    void defaultConstructorUsesSystemDefaultTimezone() {
        TimeRangeParser defaultParser = new TimeRangeParser();
        
        // Should work without errors
        Optional<TimeRangeParser.TimeRange> result = defaultParser.parse("today");
        assertTrue(result.isPresent());
    }
    
    // ========== Large Duration Tests ==========
    
    @Test
    void parseLargeDuration() {
        // Parse 52 weeks (1 year)
        Optional<TimeRangeParser.TimeRange> result = parser.parse("52w");
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        long expectedDuration = 52L * 7 * 24 * 60 * 60 * 1000;
        long actualDuration = range.until() - range.since();
        
        // Allow 1 second tolerance
        assertTrue(Math.abs(actualDuration - expectedDuration) < 1000);
    }
    
    @Test
    void parseLargeNumberOfDays() {
        Optional<TimeRangeParser.TimeRange> result = parser.parse("365d");
        
        assertTrue(result.isPresent());
        TimeRangeParser.TimeRange range = result.get();
        
        long expectedDuration = 365L * 24 * 60 * 60 * 1000;
        long actualDuration = range.until() - range.since();
        
        assertTrue(Math.abs(actualDuration - expectedDuration) < 1000);
    }
    
    // ========== Edge Case Tests ==========
    
    @Test
    void parseWithLeadingAndTrailingWhitespace() {
        Optional<TimeRangeParser.TimeRange> result = parser.parse("  6h  ");
        assertTrue(result.isPresent());
    }
    
    @Test
    void parseWithMixedCase() {
        Optional<TimeRangeParser.TimeRange> result = parser.parse("THIS_WEEK");
        assertTrue(result.isPresent());
    }
    
    @ParameterizedTest
    @CsvSource({
        "1s, 1000",
        "1m, 60000",
        "1h, 3600000",
        "1d, 86400000",
        "1w, 604800000"
    })
    void verifyDurationMilliseconds(String input, long expectedDurationMs) {
        Optional<TimeRangeParser.TimeRange> result = parser.parse(input);
        assertTrue(result.isPresent());
        
        TimeRangeParser.TimeRange range = result.get();
        long actualDuration = range.until() - range.since();
        
        // Allow 100ms tolerance for timing
        assertTrue(Math.abs(actualDuration - expectedDurationMs) < 100,
                String.format("Expected duration ~%d ms, got %d ms", expectedDurationMs, actualDuration));
    }
}
