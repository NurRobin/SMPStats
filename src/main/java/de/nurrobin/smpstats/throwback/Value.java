package de.nurrobin.smpstats.throwback;

/**
 * One player's value for a trophy. {@code number} is in the raw unit, {@code labelKey} an optional client-side
 * translation key (block or mob name) that fills the {label} placeholder of the text.
 */
public record Value(double number, String labelKey) {

    public static Value of(double number) {
        return new Value(number, null);
    }
}
