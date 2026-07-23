package com.gotrprogressbar.model;

import com.gotrprogressbar.GotrConstants;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The plugin's game-state model: an explicit phase state machine plus the fixed-timeline math
 * (cursor position, live round-length estimate, portal marks). Driven only by plain inputs
 * (HUD snapshots, chat strings, instants) — it never touches the RuneLite {@code Client}, so
 * it is fully unit-testable without mocks.
 */
public class GotrSession
{
	private GamePhase phase = GamePhase.OUTSIDE;

	// Countdown to the next round starting ("The rift becomes active!"), anchored by chat
	private Instant startEta;
	private Instant startAnchoredAt;

	// Mining phase: exact end time derived from the HUD guardian-ticks countdown
	private Instant miningEndsAt;

	// Round start ("The rift becomes active!" = mining begins). Anchor of the whole timeline.
	private Instant riftActiveAt;

	private HudSnapshot lastSnapshot;
	private Instant lastSnapshotAt;
	private final PortalPrediction portalPrediction = new PortalPrediction();

	// When we started observing the crafting phase (mid-join fallback clock / liveness)
	private Instant craftingObservedAt;

	// Game end summary
	private Instant gameEndedAt;
	private boolean lastGameWon;
	private int finalElementalEnergy;
	private int finalCatalyticEnergy;

	// Player progress + user goals (0 = disabled), pushed from config by the plugin
	private int fragmentCount;
	private int fragmentGoal;
	private PointsMetric pointsMetric = PointsMetric.COMBINED;
	private int combinedPointsGoal;
	private int elementalPointsGoal;
	private int catalyticPointsGoal;

	// ---- inputs ------------------------------------------------------------------------

	/** Gate signal: whether the player is inside the GOTR area with the HUD present. */
	public void setInMinigame(boolean inMinigame)
	{
		if (!inMinigame)
		{
			if (phase != GamePhase.OUTSIDE)
			{
				resetGameState();
				phase = GamePhase.OUTSIDE;
			}
			return;
		}
		if (phase == GamePhase.OUTSIDE)
		{
			phase = GamePhase.WAITING_FOR_GAME;
		}
	}

	/** Feeds one firing of HUD script 5980. */
	public void onHudUpdate(HudSnapshot snapshot, Instant now)
	{
		if (phase == GamePhase.OUTSIDE || snapshot == null)
		{
			return;
		}

		boolean wasPortalOpen = lastSnapshot != null && lastSnapshot.isPortalOpen();
		lastSnapshot = snapshot;
		lastSnapshotAt = now;

		if (snapshot.isCrafting())
		{
			if (phase != GamePhase.RIFT_ACTIVE)
			{
				phase = GamePhase.RIFT_ACTIVE;
				miningEndsAt = null;
				craftingObservedAt = now;
				clearStartCountdown();
				// Joined mid-craft (riftActiveAt null): the round clock stays hidden and
				// portal marks anchor off the first observed spawn — which is immediate if
				// a portal is already open, thanks to spawn back-dating below. Until then
				// the overlay shows a "watching for portal" liveness state.
			}
			if (snapshot.isPortalOpen() && !wasPortalOpen)
			{
				// Back-date the spawn when we first see a portal mid-life (e.g. joined or
				// logged in while it was already open) so the cycle stays anchored to the
				// true spawn time. A fresh spawn reports the full lifetime, backdate ~0.
				Instant spawnAt = now;
				if (snapshot.portalTicks > 0
					&& snapshot.portalTicks <= GotrConstants.PORTAL_LIFETIME_TICKS)
				{
					spawnAt = now.minusMillis(
						(GotrConstants.PORTAL_LIFETIME_TICKS - snapshot.portalTicks)
							* GotrConstants.GAME_TICK_MILLIS);
				}
				portalPrediction.onPortalSpawn(spawnAt);
			}
			else if (!snapshot.isPortalOpen() && wasPortalOpen)
			{
				portalPrediction.onPortalDespawn(now);
			}
			return;
		}

		if (snapshot.isGameWon())
		{
			if (phase == GamePhase.RIFT_ACTIVE)
			{
				endGame(now, true, snapshot);
			}
			return;
		}

		if (snapshot.isMiningPhase())
		{
			// Exact countdown to the altars opening, straight from the HUD
			miningEndsAt = now.plusMillis(snapshot.guardianTicks * GotrConstants.GAME_TICK_MILLIS);
			if (phase != GamePhase.MINING_PHASE)
			{
				phase = GamePhase.MINING_PHASE;
				clearStartCountdown();
				if (riftActiveAt == null)
				{
					// Joined mid-mining. Mining is a known fixed length, and the HUD gives
					// the exact remaining, so the round start = miningEnds - MINING_LENGTH.
					// Clamp elapsed to >= 0 so a longer-than-nominal countdown can never place
					// the start in the future (which made the bar render backwards).
					long remainingMs = (long) snapshot.guardianTicks
						* GotrConstants.GAME_TICK_MILLIS;
					long elapsedMs = Math.max(0,
						GotrConstants.MINING_PHASE_SECONDS * 1000 - remainingMs);
					riftActiveAt = now.minusMillis(elapsedMs);
					portalPrediction.anchor(riftActiveAt);
				}
			}
			return;
		}

		// Idle (power zero): no round is running
		if (snapshot.isIdle())
		{
			if (phase == GamePhase.RIFT_ACTIVE)
			{
				endGame(now, false, snapshot);
			}
			else if (phase == GamePhase.MINING_PHASE)
			{
				phase = GamePhase.WAITING_FOR_GAME;
				resetRound();
			}
		}
	}

	/** Feeds a game chat message with formatting tags already removed. */
	public void onChatMessage(String message, Instant now)
	{
		if (phase == GamePhase.OUTSIDE || message == null)
		{
			return;
		}

		if (message.startsWith(GotrConstants.CHAT_RIFT_ACTIVE))
		{
			// Round start: the MINING phase begins (live-verified — the altars open ~2
			// minutes later; the HUD countdown takes over from the next snapshot)
			phase = GamePhase.MINING_PHASE;
			riftActiveAt = now;
			clearStartCountdown();
			if (miningEndsAt == null)
			{
				miningEndsAt = now.plusSeconds(GotrConstants.MINING_PHASE_SECONDS);
			}
			portalPrediction.anchor(now);
		}
		else if (message.startsWith(GotrConstants.CHAT_START_30S))
		{
			anchorStart(now, 30);
		}
		else if (message.startsWith(GotrConstants.CHAT_START_10S))
		{
			anchorStart(now, 10);
		}
		else if (message.startsWith(GotrConstants.CHAT_START_5S))
		{
			anchorStart(now, 5);
		}
		else if (message.startsWith(GotrConstants.CHAT_PORTALS_EXTEND))
		{
			// The current round got extended, pushing the next round's start out. Mirrors
			// DatBear's handling (next start ~60s away). Only meaningful between rounds.
			if (phase == GamePhase.WAITING_FOR_GAME || phase == GamePhase.GAME_END)
			{
				anchorStart(now, 60);
			}
		}
	}

	public void setFragmentCount(int fragmentCount)
	{
		this.fragmentCount = Math.max(0, fragmentCount);
	}

	public void setGoals(int fragmentGoal, PointsMetric pointsMetric,
		int combinedPointsGoal, int elementalPointsGoal, int catalyticPointsGoal)
	{
		this.fragmentGoal = fragmentGoal;
		this.pointsMetric = pointsMetric == null ? PointsMetric.COMBINED : pointsMetric;
		this.combinedPointsGoal = combinedPointsGoal;
		this.elementalPointsGoal = elementalPointsGoal;
		this.catalyticPointsGoal = catalyticPointsGoal;
	}

	/** Periodic housekeeping; call once per game tick. */
	public void tick(Instant now)
	{
		if (phase == GamePhase.GAME_END && gameEndedAt != null
			&& Duration.between(gameEndedAt, now).getSeconds() >= GotrConstants.GAME_END_SUMMARY_SECONDS)
		{
			phase = GamePhase.WAITING_FOR_GAME;
		}
		if (phase == GamePhase.WAITING_FOR_GAME && startEta != null
			&& now.isAfter(startEta.plusSeconds(10)))
		{
			// The countdown elapsed but we never saw the round start (missed message,
			// filtered chat, ...). Drop the stale countdown.
			clearStartCountdown();
		}
	}

	public void reset()
	{
		resetGameState();
		phase = GamePhase.OUTSIDE;
	}

	// ---- scrolling timeline --------------------------------------------------------------

	/**
	 * All portal marks for the timeline, in absolute time: solid ticks at actual spawn times,
	 * the live portal (if open), and fixed future windows projected from the last real spawn
	 * (or from the round start for the first portal). Future marks only move when a real
	 * spawn re-anchors them — never with the clock.
	 */
	public List<PortalMark> portalMarks(Instant now)
	{
		List<PortalMark> marks = new ArrayList<>();
		if (phase != GamePhase.MINING_PHASE && phase != GamePhase.RIFT_ACTIVE)
		{
			return marks;
		}

		long lifetimeMs = GotrConstants.PORTAL_LIFETIME_TICKS * GotrConstants.GAME_TICK_MILLIS;
		List<Instant> spawns = portalPrediction.getSpawnHistory();
		Instant lastSpawn = null;
		for (int i = 0; i < spawns.size(); i++)
		{
			lastSpawn = spawns.get(i);
			boolean isActive = portalPrediction.isPortalOpen() && i == spawns.size() - 1;
			if (isActive)
			{
				// Section ends when the portal actually closes: exact remaining from the HUD
				long remaining = portalSecondsRemaining(now).orElse(-1L);
				Instant end = remaining >= 0
					? now.plusSeconds(remaining)
					: lastSpawn.plusMillis(lifetimeMs);
				marks.add(new PortalMark(PortalMark.State.ACTIVE, lastSpawn, end, remaining));
			}
			else
			{
				marks.add(new PortalMark(PortalMark.State.PAST, lastSpawn,
					lastSpawn.plusMillis(lifetimeMs), -1));
			}
		}

		Instant next;
		if (lastSpawn != null)
		{
			next = lastSpawn.plusSeconds(GotrConstants.PORTAL_CYCLE_SECONDS);
		}
		else if (riftActiveAt != null)
		{
			next = riftActiveAt.plusSeconds(GotrConstants.FIRST_PORTAL_DELAY_SECONDS);
		}
		else
		{
			return marks;
		}

		Instant horizon = now.plusSeconds((long) GotrConstants.TIMELINE_LOOKAHEAD_SECONDS);
		while (!next.isAfter(horizon) && marks.size() < GotrConstants.MAX_TIMELINE_MARKS)
		{
			marks.add(new PortalMark(PortalMark.State.FUTURE, next,
				next.plusMillis(lifetimeMs), -1));
			next = next.plusSeconds(GotrConstants.PORTAL_CYCLE_SECONDS);
		}
		return marks;
	}

	/** Seconds until the next expected portal (first FUTURE mark), while a round runs. */
	public Optional<Long> secondsToNextPortal(Instant now)
	{
		for (PortalMark mark : portalMarks(now))
		{
			if (mark.state == PortalMark.State.FUTURE && mark.time.isAfter(now))
			{
				return Optional.of(Duration.between(now, mark.time).getSeconds());
			}
		}
		return Optional.empty();
	}

	// ---- outputs -----------------------------------------------------------------------

	public GamePhase getPhase()
	{
		return phase;
	}

	/** Seconds until the next round starts, when a chat countdown anchor is known. */
	public Optional<Long> secondsToGameStart(Instant now)
	{
		if (startEta == null)
		{
			return Optional.empty();
		}
		return Optional.of(Math.max(0, Duration.between(now, startEta).getSeconds()));
	}

	/** Fill fraction of the next-game bar: share of the anchored wait already elapsed. */
	public Optional<Double> waitingFillFraction(Instant now)
	{
		if (startEta == null || startAnchoredAt == null)
		{
			return Optional.empty();
		}
		long total = Duration.between(startAnchoredAt, startEta).toMillis();
		if (total <= 0)
		{
			return Optional.of(1.0);
		}
		long elapsed = Duration.between(startAnchoredAt, now).toMillis();
		return Optional.of(Math.min(1.0, Math.max(0.0, (double) elapsed / (double) total)));
	}

	/** Seconds until the altars open (mining phase ends), exact from the HUD countdown. */
	public Optional<Long> miningSecondsRemaining(Instant now)
	{
		if (miningEndsAt == null)
		{
			return Optional.empty();
		}
		return Optional.of(Math.max(0, Duration.between(now, miningEndsAt).getSeconds()));
	}

	/** Guardian power as a 0..1 fraction. Not displayed — feeds the estimate and win check. */
	public double powerFraction()
	{
		return lastSnapshot == null ? 0 : lastSnapshot.powerFraction();
	}

	/** Elapsed time since the round started. */
	public Optional<Duration> elapsedSinceRiftActive(Instant now)
	{
		if (riftActiveAt == null)
		{
			return Optional.empty();
		}
		return Optional.of(Duration.between(riftActiveAt, now));
	}

	/** How long we've been watching a crafting phase whose start we didn't see (mid-join). */
	public Optional<Duration> observedCraftingElapsed(Instant now)
	{
		if (craftingObservedAt == null)
		{
			return Optional.empty();
		}
		return Optional.of(Duration.between(craftingObservedAt, now));
	}

	public boolean isPortalOpen()
	{
		return lastSnapshot != null && lastSnapshot.isPortalOpen();
	}

	/** Seconds until the currently open portal closes, from HUD ticks. */
	public Optional<Long> portalSecondsRemaining(Instant now)
	{
		if (!isPortalOpen() || lastSnapshot.portalTicks < 0 || lastSnapshotAt == null)
		{
			return Optional.empty();
		}
		long remainingMs = lastSnapshot.portalTicks * GotrConstants.GAME_TICK_MILLIS
			- Duration.between(lastSnapshotAt, now).toMillis();
		return Optional.of(Math.max(0, remainingMs / 1000));
	}

	public Optional<Instant> getRiftActiveAt()
	{
		return Optional.ofNullable(riftActiveAt);
	}

	public int getFragmentCount()
	{
		return fragmentCount;
	}

	public int getFragmentGoal()
	{
		return fragmentGoal;
	}

	public GoalState fragmentGoalState()
	{
		return GoalState.of(fragmentCount, fragmentGoal);
	}

	public int getElementalEnergy()
	{
		return lastSnapshot == null ? 0 : lastSnapshot.elementalEnergy;
	}

	public int getCatalyticEnergy()
	{
		return lastSnapshot == null ? 0 : lastSnapshot.catalyticEnergy;
	}

	public PointsMetric getPointsMetric()
	{
		return pointsMetric;
	}

	/** The goal used by the active single-value metric (not meaningful for BOTH_SPLIT). */
	public int activePointsGoal()
	{
		switch (pointsMetric)
		{
			case ELEMENTAL:
				return elementalPointsGoal;
			case CATALYTIC:
				return catalyticPointsGoal;
			default:
				return combinedPointsGoal;
		}
	}

	public int getElementalPointsGoal()
	{
		return elementalPointsGoal;
	}

	public int getCatalyticPointsGoal()
	{
		return catalyticPointsGoal;
	}

	/** The tracked points value under the configured metric (not used for BOTH_SPLIT). */
	public int pointsValue()
	{
		switch (pointsMetric)
		{
			case ELEMENTAL:
				return getElementalEnergy();
			case CATALYTIC:
				return getCatalyticEnergy();
			default:
				return getElementalEnergy() + getCatalyticEnergy();
		}
	}

	public GoalState pointsGoalState()
	{
		return GoalState.of(pointsValue(), activePointsGoal());
	}

	/** BOTH_SPLIT: each side tracks its own goal value. */
	public GoalState splitElementalGoalState()
	{
		return GoalState.of(getElementalEnergy(), elementalPointsGoal);
	}

	public GoalState splitCatalyticGoalState()
	{
		return GoalState.of(getCatalyticEnergy(), catalyticPointsGoal);
	}

	/** Whether the crafting-phase goal sub-bar has anything to show under the active metric. */
	public boolean hasPointsGoal()
	{
		if (pointsMetric == PointsMetric.BOTH_SPLIT)
		{
			return elementalPointsGoal > 0 || catalyticPointsGoal > 0;
		}
		return activePointsGoal() > 0;
	}

	public boolean wasLastGameWon()
	{
		return lastGameWon;
	}

	public int getFinalElementalEnergy()
	{
		return finalElementalEnergy;
	}

	public int getFinalCatalyticEnergy()
	{
		return finalCatalyticEnergy;
	}

	// ---- internals ---------------------------------------------------------------------

	private void anchorStart(Instant now, int seconds)
	{
		// Chat countdown to the next round's start; shown during the waiting state.
		startEta = now.plusSeconds(seconds);
		if (startAnchoredAt == null)
		{
			startAnchoredAt = now;
		}
		if (phase == GamePhase.GAME_END)
		{
			phase = GamePhase.WAITING_FOR_GAME;
		}
	}

	private void endGame(Instant now, boolean won, HudSnapshot finalSnapshot)
	{
		phase = GamePhase.GAME_END;
		gameEndedAt = now;
		lastGameWon = won;
		finalElementalEnergy = finalSnapshot.elementalEnergy;
		finalCatalyticEnergy = finalSnapshot.catalyticEnergy;
		resetRound();
	}

	private void resetRound()
	{
		riftActiveAt = null;
		miningEndsAt = null;
		craftingObservedAt = null;
		portalPrediction.reset();
		clearStartCountdown();
	}

	private void clearStartCountdown()
	{
		startEta = null;
		startAnchoredAt = null;
	}

	private void resetGameState()
	{
		resetRound();
		lastSnapshot = null;
		lastSnapshotAt = null;
		gameEndedAt = null;
		fragmentCount = 0;
	}
}
