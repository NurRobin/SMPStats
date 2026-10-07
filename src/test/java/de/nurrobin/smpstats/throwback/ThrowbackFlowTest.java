package de.nurrobin.smpstats.throwback;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static de.nurrobin.smpstats.throwback.ThrowbackFlow.Mode;
import static de.nurrobin.smpstats.throwback.ThrowbackFlow.PortalDecision;
import static org.junit.jupiter.api.Assertions.*;

class ThrowbackFlowTest {
    private final MemoryProgress repo = new MemoryProgress();
    private final AtomicLong clock = new AtomicLong(1_000);
    private final ThrowbackFlow flow = new ThrowbackFlow(repo, "2026-1", clock::get);
    private final UUID alice = UUID.randomUUID();

    @Test
    void firstJoinSendsPlayerToTheHallAndCreatesAnOpenRow() {
        assertTrue(flow.onJoin(alice));

        assertEquals(Mode.TOUR, flow.mode(alice).orElseThrow());
        assertTrue(flow.inHall(alice));
        assertFalse(repo.rows.get(alice).completed());
    }

    @Test
    void portalStaysClosedUntilTheRulesAreConfirmed() {
        flow.onJoin(alice);

        assertEquals(PortalDecision.BLOCKED, flow.portal(alice));
        assertTrue(flow.inHall(alice), "a blocked player stays in the hall");
        assertFalse(repo.rows.get(alice).completed());
    }

    @Test
    void confirmationStoresTimeAndRulesVersionAndOpensThePortal() {
        flow.onJoin(alice);
        clock.set(5_000);

        assertTrue(flow.confirmRules(alice));
        assertEquals(5_000L, repo.rows.get(alice).rulesConfirmedAt());
        assertEquals("2026-1", repo.rows.get(alice).rulesVersion());

        clock.set(9_000);
        assertEquals(PortalDecision.FINISHED_TOUR, flow.portal(alice));
        assertEquals(9_000L, repo.rows.get(alice).completedAt());
        assertFalse(flow.inHall(alice));
    }

    @Test
    void relogInTheMiddleOfTheTourRestartsItAndNeedsTheRulesAgain() {
        flow.onJoin(alice);
        flow.confirmRules(alice);
        flow.end(alice);

        assertTrue(flow.onJoin(alice), "the open tour starts again");
        assertFalse(flow.rulesRead(alice), "the confirmation of the aborted tour does not count");
        assertEquals(PortalDecision.BLOCKED, flow.portal(alice));
        assertNull(repo.rows.get(alice).completedAt());
    }

    @Test
    void afterTheTourNoMoreForcedHallAndVisitsNeedNoConfirmation() {
        flow.onJoin(alice);
        flow.confirmRules(alice);
        flow.portal(alice);

        assertFalse(flow.onJoin(alice));
        assertEquals(Mode.VISIT, flow.startVisit(alice));
        assertEquals(PortalDecision.LEFT_VISIT, flow.portal(alice));
        assertFalse(flow.inHall(alice));
        assertEquals(1_000L, repo.rows.get(alice).completedAt() == null ? -1 : repo.rows.get(alice).completedAt());
    }

    @Test
    void visitDuringAnOpenTourStaysMandatory() {
        flow.onJoin(alice);

        assertEquals(Mode.TOUR, flow.startVisit(alice));
    }

    @Test
    void confirmingOutsideTheHallIsRejected() {
        assertFalse(flow.confirmRules(alice));
        assertTrue(repo.rows.isEmpty());
    }

    @Test
    void portalOutsideTheHallDoesNothing() {
        assertEquals(PortalDecision.NOT_IN_HALL, flow.portal(alice));
    }

    @Test
    void newRulesVersionIsWrittenWithTheConfirmation() {
        flow.setRulesVersion("2026-2");
        flow.onJoin(alice);
        flow.confirmRules(alice);

        assertEquals("2026-2", repo.rows.get(alice).rulesVersion());
    }
}
