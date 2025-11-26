package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.StatsService;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JoinQuitListenerTest {

    @Test
    void forwardsJoinAndQuitToService() {
        StatsService stats = mock(StatsService.class);
        JoinQuitListener listener = new JoinQuitListener(stats);

        Player player = mock(Player.class);
        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        org.mockito.Mockito.when(join.getPlayer()).thenReturn(player);
        listener.onJoin(join);
        verify(stats).handleJoin(player);

        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        org.mockito.Mockito.when(quit.getPlayer()).thenReturn(player);
        listener.onQuit(quit);
        verify(stats).handleQuit(player);
    }
}
