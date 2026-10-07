package de.nurrobin.smpstats.throwback;

import de.nurrobin.smpstats.season.SeasonPlayer;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides what a player sees: finds them in the season, applies the playtime threshold, picks the trophies
 * and fills the remaining stages with event info. Pure data in, {@link HallPlan} out.
 */
public final class ThrowbackContent {
    private final TrophyBoard board;
    private final ThrowbackTexts texts;
    private final long minPlaytimeMs;
    private final int badgeTop;
    private final String season;
    private final String privacyUrl;
    private final String rulesVersion;
    private final boolean matchByName;

    public ThrowbackContent(TrophyBoard board, ThrowbackTexts texts, ThrowbackSettings settings, boolean matchByName) {
        this(board, texts, settings.minPlaytimeMinutes() * 60_000L, settings.rankBadgeTop(), settings.season(),
                settings.privacyUrl(), settings.rulesVersion(), matchByName);
    }

    public ThrowbackContent(TrophyBoard board, ThrowbackTexts texts, long minPlaytimeMs, int badgeTop, String season,
                            String privacyUrl, String rulesVersion, boolean matchByName) {
        this.board = board;
        this.texts = texts;
        this.minPlaytimeMs = minPlaytimeMs;
        this.badgeTop = badgeTop;
        this.season = season;
        this.privacyUrl = privacyUrl;
        this.rulesVersion = rulesVersion;
        this.matchByName = matchByName;
    }

    /** The season entry of a player; by uuid, and by name only where uuids cannot match (offline-mode servers). */
    public Optional<SeasonPlayer> find(UUID uuid, String name) {
        Optional<SeasonPlayer> byId = board.player(uuid);
        if (byId.isPresent() || !matchByName) {
            return byId;
        }
        return board.playerByName(name);
    }

    public Audience audience(Optional<SeasonPlayer> match) {
        if (match.isEmpty()) {
            return Audience.NEWCOMER;
        }
        return match.get().playtimeMs() >= minPlaytimeMs ? Audience.TROPHY : Audience.RETURNING;
    }

    public HallPlan plan(UUID uuid, String name, int stageCount) {
        Optional<SeasonPlayer> match = find(uuid, name);
        Audience audience = audience(match);
        List<StagePlan> stages = new ArrayList<>();
        List<ThrowbackTexts.Info> infos = texts.infos();
        int nextInfo = 0;
        List<Trophy> trophies = audience == Audience.TROPHY
                ? TrophySelector.select(board.trophies(match.get().uuid()), stageCount) : List.of();
        for (int i = 0; i < stageCount; i++) {
            if (i < trophies.size()) {
                Trophy t = trophies.get(i);
                stages.add(new StagePlan(i, t.def().scene(), texts.trophy(t, badgeTop)));
            } else if (nextInfo < infos.size()) {
                stages.add(new StagePlan(i, SceneType.INFO, texts.info(infos.get(nextInfo++))));
            }
        }
        Component statue = audience == Audience.TROPHY ? texts.statue(season) : null;
        return new HallPlan(audience, stages, texts.welcome(audience, name), statue, texts.rules(rulesVersion),
                texts.privacy(privacyUrl));
    }

    public Component rulesButton() {
        return texts.button();
    }
}
