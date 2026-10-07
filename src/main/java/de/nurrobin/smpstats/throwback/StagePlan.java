package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;

/** What stands on one stage: the scene type and the text above it. */
public record StagePlan(int index, SceneType scene, Component text) {
}
