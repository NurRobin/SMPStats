package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.StatsService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {
    private final StatsService statsService;

    public JoinQuitListener(StatsService statsService) {
        this.statsService = statsService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        statsService.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        statsService.handleQuit(event.getPlayer());
    }
}
