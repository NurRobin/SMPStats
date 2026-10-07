package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Everything one player sees in the hall, as plain data. {@code statueLabel} is set only for players with
 * trophies (their figure at the start); {@code welcome} is the sign at the start for everybody else.
 */
public record HallPlan(Audience audience, List<StagePlan> stages, List<Component> welcome, Component statueLabel,
                       List<Component> rules, List<Component> privacy) {
}
