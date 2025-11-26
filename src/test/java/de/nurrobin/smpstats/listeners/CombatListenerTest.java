package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.social.SocialStatsService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CombatListenerTest {

    @Test
    void handlesPlayerDeathAndKillCounts() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService stats = mock(StatsService.class);
        SocialStatsService social = mock(SocialStatsService.class);
        CombatListener listener = new CombatListener(plugin, stats, social);

        Player victim = mock(Player.class);
        when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        when(victim.getKiller()).thenReturn(mock(Player.class));
        EntityDamageEvent lastDamage = mock(EntityDamageEvent.class);
        when(lastDamage.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        when(victim.getLastDamageCause()).thenReturn(lastDamage);

        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(victim);

        listener.onPlayerDeath(event);

        verify(stats).addDeath(victim.getUniqueId(), "FALL");
        verify(stats).addPlayerKill(victim.getKiller().getUniqueId());
        verify(social).recordSharedKill(victim.getKiller(), true);
    }

    @Test
    void countsMobKillAndIgnoresPlayerDeathEvents() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService stats = mock(StatsService.class);
        SocialStatsService social = mock(SocialStatsService.class);
        CombatListener listener = new CombatListener(plugin, stats, social);

        EntityDeathEvent mobDeath = mock(EntityDeathEvent.class);
        Player killer = mock(Player.class);
        when(killer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mobDeath.getEntity()).thenReturn(mock(org.bukkit.entity.Zombie.class));
        when(mobDeath.getEntity().getKiller()).thenReturn(killer);

        listener.onEntityDeath(mobDeath);
        verify(stats).addMobKill(killer.getUniqueId());
        verify(social).recordSharedKill(killer, false);
    }

    @Test
    void tracksDamageDealtAndTaken() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService stats = mock(StatsService.class);
        CombatListener listener = new CombatListener(plugin, stats, null);

        Player attacker = mock(Player.class);
        UUID attackerId = UUID.randomUUID();
        when(attacker.getUniqueId()).thenReturn(attackerId);

        EntityDamageByEntityEvent byPlayer = mock(EntityDamageByEntityEvent.class);
        when(byPlayer.getDamager()).thenReturn(attacker);
        when(byPlayer.getFinalDamage()).thenReturn(5.0);
        listener.onDamageByEntity(byPlayer);
        verify(stats).addDamageDealt(attackerId, 5.0);

        Projectile projectile = mock(Projectile.class);
        ProjectileSource shooter = attacker;
        when(projectile.getShooter()).thenReturn(shooter);
        EntityDamageByEntityEvent byProjectile = mock(EntityDamageByEntityEvent.class);
        when(byProjectile.getDamager()).thenReturn(projectile);
        when(byProjectile.getFinalDamage()).thenReturn(3.0);
        listener.onDamageByEntity(byProjectile);
        verify(stats).addDamageDealt(attackerId, 3.0);

        Player victim = mock(Player.class);
        UUID victimId = UUID.randomUUID();
        when(victim.getUniqueId()).thenReturn(victimId);
        EntityDamageEvent damageTaken = mock(EntityDamageEvent.class);
        when(damageTaken.getEntity()).thenReturn(victim);
        when(damageTaken.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        when(damageTaken.getFinalDamage()).thenReturn(2.0);
        listener.onDamageTaken(damageTaken);
        verify(stats).addDamageTaken(victimId, 2.0);

        EntityDamageEvent suicide = mock(EntityDamageEvent.class);
        when(suicide.getEntity()).thenReturn(victim);
        when(suicide.getCause()).thenReturn(EntityDamageEvent.DamageCause.SUICIDE);
        listener.onDamageTaken(suicide);
        verify(stats, times(1)).addDamageTaken(victimId, 2.0);
    }
}
