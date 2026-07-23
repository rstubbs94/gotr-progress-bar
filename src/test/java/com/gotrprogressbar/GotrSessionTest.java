package com.gotrprogressbar;

import com.gotrprogressbar.model.GamePhase;
import com.gotrprogressbar.model.GoalState;
import com.gotrprogressbar.model.GotrSession;
import com.gotrprogressbar.model.HudSnapshot;
import com.gotrprogressbar.model.PointsMetric;
import com.gotrprogressbar.model.PortalMark;
import java.time.Instant;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Scenario values mirror live HUD logs from 2026-07-13: during mining, power sits at exactly
 * max/10 with rune indexes 0 and guardianTicks counting down ~200; crafting has rune
 * indexes > 0; between rounds power is 0 (guardianTicks holds garbage negatives).
 */
public class GotrSessionTest
{
	private static final Instant T0 = Instant.ofEpochSecond(1_000_000);
	private static final int MAX_POWER = 101800;

	private GotrSession session;

	@Before
	public void setUp()
	{
		session = new GotrSession();
		session.setInMinigame(true);
	}

	private static HudSnapshot crafting(int elem, int cata, int power,
		int portalLocation, int portalTicks)
	{
		return new HudSnapshot(elem, cata, power, MAX_POWER, portalLocation,
			1, 1, 5, 10, 100, portalTicks);
	}

	/** Crafting snapshot at the given power fraction (e.g. 0.28 = 28%). */
	private static HudSnapshot craftingAtPower(double fraction)
	{
		return crafting(50, 50, (int) (MAX_POWER * fraction), -1, -1);
	}

	private static HudSnapshot mining(int guardianTicks)
	{
		return new HudSnapshot(0, 0, MAX_POWER / 10, MAX_POWER, 0,
			0, 0, 10, 10, guardianTicks, -1);
	}

	private static HudSnapshot idle()
	{
		return new HudSnapshot(0, 0, 0, MAX_POWER, 0, 0, 0, 10, 10, -779337, -1);
	}

	private static HudSnapshot won(int elem, int cata)
	{
		return new HudSnapshot(elem, cata, MAX_POWER, MAX_POWER, 0, 0, 0, 10, 10, 0, -1);
	}

	private static Instant at(long secondsAfterT0)
	{
		return T0.plusSeconds(secondsAfterT0);
	}

	@Test
	public void entersWaitingWhenInMinigame()
	{
		assertEquals(GamePhase.WAITING_FOR_GAME, session.getPhase());
	}

	@Test
	public void fullHappyPath()
	{
		// between rounds: idle HUD, then the chat countdown to round start
		session.onHudUpdate(idle(), at(0));
		assertEquals(GamePhase.WAITING_FOR_GAME, session.getPhase());
		session.onChatMessage(GotrConstants.CHAT_START_30S, at(0));
		assertEquals(GamePhase.WAITING_FOR_GAME, session.getPhase());
		assertEquals(Long.valueOf(30), session.secondsToGameStart(at(0)).get());
		assertEquals(0.5, session.waitingFillFraction(at(15)).get(), 0.01);
		session.onChatMessage(GotrConstants.CHAT_START_10S, at(20));
		assertEquals(Long.valueOf(10), session.secondsToGameStart(at(20)).get());

		// round starts -> mining phase with the nominal 120 s window until HUD refines it
		session.onChatMessage(GotrConstants.CHAT_RIFT_ACTIVE, at(30));
		assertEquals(GamePhase.MINING_PHASE, session.getPhase());
		assertEquals(Long.valueOf(120), session.miningSecondsRemaining(at(30)).get());

		// HUD countdown takes over (178 ticks = 106.8 s)
		session.onHudUpdate(mining(178), at(44));
		assertEquals(Long.valueOf(106), session.miningSecondsRemaining(at(44)).get());

		// altars open -> crafting
		session.onHudUpdate(craftingAtPower(0.11), at(150));
		assertEquals(GamePhase.RIFT_ACTIVE, session.getPhase());
		assertEquals(120, session.elapsedSinceRiftActive(at(150)).get().getSeconds());

		// win: power reaches max
		session.onHudUpdate(won(190, 210), at(400));
		assertEquals(GamePhase.GAME_END, session.getPhase());
		assertTrue(session.wasLastGameWon());
		assertEquals(190, session.getFinalElementalEnergy());
		assertEquals(210, session.getFinalCatalyticEnergy());

		// summary holds, then returns to waiting
		session.tick(at(405));
		assertEquals(GamePhase.GAME_END, session.getPhase());
		session.tick(at(400 + GotrConstants.GAME_END_SUMMARY_SECONDS + 1));
		assertEquals(GamePhase.WAITING_FOR_GAME, session.getPhase());
	}

	@Test
	public void portalMarksAreAnchoredToRealSpawns()
	{
		session.onChatMessage(GotrConstants.CHAT_RIFT_ACTIVE, at(0));
		session.onHudUpdate(mining(178), at(14));

		// futures already laid out during mining: t0+160, t0+300, ...
		List<PortalMark> marks = session.portalMarks(at(30));
		assertFalse(marks.isEmpty());
		assertEquals(PortalMark.State.FUTURE, marks.get(0).state);
		assertEquals(at(GotrConstants.FIRST_PORTAL_DELAY_SECONDS), marks.get(0).time);
		assertEquals(at(GotrConstants.FIRST_PORTAL_DELAY_SECONDS
			+ GotrConstants.PORTAL_CYCLE_SECONDS), marks.get(1).time);

		// fresh spawn at t=165 (full 44-tick lifetime -> no backdating):
		// ACTIVE mark at its real time, futures re-anchored off it
		session.onHudUpdate(craftingAtPower(0.12), at(130));
		session.onHudUpdate(crafting(20, 20, 15000, 3, GotrConstants.PORTAL_LIFETIME_TICKS),
			at(165));
		marks = session.portalMarks(at(166));
		assertEquals(PortalMark.State.ACTIVE, marks.get(0).state);
		assertEquals(at(165), marks.get(0).time);
		assertEquals(PortalMark.State.FUTURE, marks.get(1).state);
		assertEquals(at(165 + GotrConstants.PORTAL_CYCLE_SECONDS), marks.get(1).time);

		// despawn -> PAST tick stays at t=165
		session.onHudUpdate(craftingAtPower(0.2), at(190));
		marks = session.portalMarks(at(191));
		assertEquals(PortalMark.State.PAST, marks.get(0).state);
		assertEquals(at(165), marks.get(0).time);
		assertEquals(Long.valueOf(165 + GotrConstants.PORTAL_CYCLE_SECONDS - 191),
			session.secondsToNextPortal(at(191)).get());
	}

	@Test
	public void portalSeenMidLifeIsBackdatedToItsRealSpawn()
	{
		// join mid-craft while a portal is already open with 20 of 44 ticks left:
		// it spawned (44-20)*0.6 = 14.4 s ago
		session.onHudUpdate(crafting(50, 50, 40000, 3, 20), at(100));
		List<PortalMark> marks = session.portalMarks(at(100));
		assertEquals(PortalMark.State.ACTIVE, marks.get(0).state);
		assertEquals(T0.plusMillis(100_000 - 14_400), marks.get(0).time);
		// the next window anchors off the TRUE spawn, not the moment we noticed it
		assertEquals(T0.plusMillis(100_000 - 14_400)
			.plusSeconds(GotrConstants.PORTAL_CYCLE_SECONDS), marks.get(1).time);
	}

	@Test
	public void miningPhaseDetectedFromHudWithoutChat()
	{
		// joined mid-mining: no chat anchor, HUD alone drives the phase
		session.onHudUpdate(mining(150), at(0));
		assertEquals(GamePhase.MINING_PHASE, session.getPhase());
		assertEquals(Long.valueOf(90), session.miningSecondsRemaining(at(0)).get());
		// round start derived from the nominal window -> the timeline has an anchor
		assertTrue(session.getRiftActiveAt().isPresent());
	}

	@Test
	public void midJoinMiningNeverPlacesRoundStartInFuture()
	{
		// A countdown longer than the nominal mining length must clamp the round start to
		// "now" rather than derive a future instant (which rendered the bar backwards).
		session.onHudUpdate(mining(GotrConstants.MINING_PHASE_TICKS_MAX), at(0));
		assertEquals(GamePhase.MINING_PHASE, session.getPhase());
		Instant start = session.getRiftActiveAt().get();
		assertFalse("round start must not be in the future", start.isAfter(at(0)));
		// elapsed is therefore never negative
		assertTrue(session.elapsedSinceRiftActive(at(0)).get().toMillis() >= 0);
	}

	@Test
	public void midCraftJoinLandsInRiftActive()
	{
		session.onHudUpdate(craftingAtPower(0.4), at(0));
		assertEquals(GamePhase.RIFT_ACTIVE, session.getPhase());
		// round start unknown: no clock, no marks until the first observed portal
		assertFalse(session.getRiftActiveAt().isPresent());
		assertFalse(session.elapsedSinceRiftActive(at(1)).isPresent());
		assertTrue(session.portalMarks(at(1)).isEmpty());

		session.onHudUpdate(crafting(80, 90, 45000, 3, 41), at(20));
		assertTrue(session.isPortalOpen());
		session.onHudUpdate(craftingAtPower(0.5), at(45));
		assertFalse(session.isPortalOpen());
		List<PortalMark> marks = session.portalMarks(at(50));
		assertEquals(PortalMark.State.PAST, marks.get(0).state);
		assertEquals(PortalMark.State.FUTURE, marks.get(1).state);
	}

	@Test
	public void portalCountdownComesFromHudTicks()
	{
		session.onChatMessage(GotrConstants.CHAT_RIFT_ACTIVE, at(0));
		session.onHudUpdate(crafting(10, 10, 30000, 4, 40), at(160));
		// 40 ticks * 600ms = 24s
		assertEquals(Long.valueOf(24), session.portalSecondsRemaining(at(160)).get());
		assertEquals(Long.valueOf(19), session.portalSecondsRemaining(at(165)).get());
	}

	@Test
	public void gameLossEndsGame()
	{
		session.onChatMessage(GotrConstants.CHAT_RIFT_ACTIVE, at(0));
		session.onHudUpdate(craftingAtPower(0.3), at(130));
		assertEquals(GamePhase.RIFT_ACTIVE, session.getPhase());
		session.onHudUpdate(idle(), at(200));
		assertEquals(GamePhase.GAME_END, session.getPhase());
		assertFalse(session.wasLastGameWon());
	}

	@Test
	public void staleStartCountdownIsDropped()
	{
		session.onChatMessage(GotrConstants.CHAT_START_30S, at(0));
		assertEquals(GamePhase.WAITING_FOR_GAME, session.getPhase());
		assertTrue(session.secondsToGameStart(at(5)).isPresent());
		session.tick(at(41));
		assertFalse(session.secondsToGameStart(at(41)).isPresent());
	}

	@Test
	public void portalsExtendMessageAnchorsNextStart()
	{
		session.onChatMessage(GotrConstants.CHAT_PORTALS_EXTEND, at(0));
		assertEquals(GamePhase.WAITING_FOR_GAME, session.getPhase());
		assertEquals(Long.valueOf(60), session.secondsToGameStart(at(0)).get());
	}

	@Test
	public void leavingAreaResetsToOutside()
	{
		session.onChatMessage(GotrConstants.CHAT_RIFT_ACTIVE, at(0));
		session.setInMinigame(false);
		assertEquals(GamePhase.OUTSIDE, session.getPhase());
		session.setInMinigame(true);
		assertEquals(GamePhase.WAITING_FOR_GAME, session.getPhase());
		assertFalse(session.elapsedSinceRiftActive(at(10)).isPresent());
		assertFalse(session.miningSecondsRemaining(at(10)).isPresent());
		assertTrue(session.portalMarks(at(10)).isEmpty());
	}

	@Test
	public void fragmentGoal()
	{
		session.setGoals(120, PointsMetric.COMBINED, 300, 150, 150);
		session.setFragmentCount(84);
		assertEquals(GoalState.BELOW, session.fragmentGoalState());
		session.setFragmentCount(120);
		assertEquals(GoalState.MET, session.fragmentGoalState());
		session.setGoals(0, PointsMetric.COMBINED, 300, 150, 150);
		assertEquals(GoalState.DISABLED, session.fragmentGoalState());
	}

	@Test
	public void pointsMetrics()
	{
		session.onHudUpdate(crafting(160, 90, 30000, -1, -1), at(0));

		session.setGoals(120, PointsMetric.COMBINED, 300, 150, 150);
		assertEquals(250, session.pointsValue());
		assertEquals(300, session.activePointsGoal());
		assertEquals(GoalState.BELOW, session.pointsGoalState());
		assertTrue(session.hasPointsGoal());

		session.setGoals(120, PointsMetric.ELEMENTAL, 300, 150, 150);
		assertEquals(160, session.pointsValue());
		assertEquals(150, session.activePointsGoal());
		assertEquals(GoalState.MET, session.pointsGoalState());

		session.setGoals(120, PointsMetric.CATALYTIC, 300, 150, 150);
		assertEquals(90, session.pointsValue());
		assertEquals(GoalState.BELOW, session.pointsGoalState());

		// split: each side has its OWN goal (200 elemental vs 80 catalytic here)
		session.setGoals(120, PointsMetric.BOTH_SPLIT, 300, 200, 80);
		assertEquals(GoalState.BELOW, session.splitElementalGoalState());
		assertEquals(GoalState.MET, session.splitCatalyticGoalState());
		assertTrue(session.hasPointsGoal());

		// split with one side disabled still shows the other
		session.setGoals(120, PointsMetric.BOTH_SPLIT, 300, 0, 80);
		assertEquals(GoalState.DISABLED, session.splitElementalGoalState());
		assertTrue(session.hasPointsGoal());

		session.setGoals(120, PointsMetric.COMBINED, 0, 150, 150);
		assertEquals(GoalState.DISABLED, session.pointsGoalState());
		assertFalse(session.hasPointsGoal());
	}

	@Test
	public void malformedScriptArgsAreRejected()
	{
		assertEquals(null, HudSnapshot.fromArgs(null));
		assertEquals(null, HudSnapshot.fromArgs(new Object[]{5980, 1, 2}));
		Object[] bad = new Object[GotrConstants.HUD_SCRIPT_ARG_COUNT];
		bad[0] = 5980;
		for (int i = 1; i < bad.length; i++)
		{
			bad[i] = i == 5 ? "not an int" : 1;
		}
		assertEquals(null, HudSnapshot.fromArgs(bad));
	}

	@Test
	public void hudStateClassification()
	{
		assertTrue(mining(178).isMiningPhase());
		assertFalse(mining(178).isCrafting());
		assertFalse(idle().isMiningPhase());
		assertTrue(idle().isIdle());
		assertTrue(craftingAtPower(0.2).isCrafting());
		assertFalse(craftingAtPower(0.2).isMiningPhase());
		assertTrue(won(10, 10).isGameWon());
		// garbage negative ticks between rounds must never read as mining
		HudSnapshot garbage = new HudSnapshot(0, 0, MAX_POWER / 10, MAX_POWER, 0,
			0, 0, 10, 10, -779337, -1);
		assertFalse(garbage.isMiningPhase());
	}

}
