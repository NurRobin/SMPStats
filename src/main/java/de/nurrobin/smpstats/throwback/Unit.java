package de.nurrobin.smpstats.throwback;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display units; stored values stay raw (ms, cm, counts, epoch ms) and are converted only for the text. */
public enum Unit {
    /** Raw value in milliseconds. */
    HOURS,
    /** Raw value in centimetres. */
    KM,
    COUNT,
    /** Raw value is an epoch in milliseconds. */
    DATE;

    private static final Locale DE = Locale.GERMANY;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy", DE)
            .withZone(ZoneId.of("Europe/Berlin"));

    /** The raw value expressed in the unit the player reads, e.g. centimetres as kilometres. */
    public double display(double raw) {
        return switch (this) {
            case HOURS -> raw / 3_600_000.0;
            case KM -> raw / 100_000.0;
            default -> raw;
        };
    }

    public String format(double raw) {
        return switch (this) {
            case HOURS -> number(display(raw)) + (display(raw) >= 1.5 ? " Stunden" : " Stunde");
            case KM -> number(display(raw)) + " km";
            case COUNT -> NumberFormat.getIntegerInstance(DE).format(Math.round(raw));
            case DATE -> DATE_FORMAT.format(Instant.ofEpochMilli((long) raw));
        };
    }

    /** Whole numbers from 100 up, one decimal below that, so "412 km" but "7,3 km". */
    public static String number(double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(DE);
        if (value >= 100) {
            nf.setMaximumFractionDigits(0);
        } else {
            nf.setMaximumFractionDigits(1);
            nf.setMinimumFractionDigits(value < 10 ? 1 : 0);
        }
        return nf.format(value);
    }
}
