package com.gotrprogressbar.model;

import com.gotrprogressbar.GotrConstants;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Estimates when the next portal will appear during an active game.
 *
 * Cadence (OSRS Wiki, spike S4.3 confirms in-client): the first portal appears roughly
 * {@link GotrConstants#FIRST_PORTAL_DELAY_SECONDS} after the rift becomes active, and each
 * subsequent portal roughly {@link GotrConstants#PORTAL_CYCLE_SECONDS} after the previous
 * spawn. Every observed spawn re-anchors the cycle, so drift self-corrects. The estimate is
 * a window (± tolerance), never an exact promise, and is suppressed while a portal is open.
 */
public class PortalPrediction
{
	/** An estimated [start, end] window for the next portal spawn. */
	public static final class Window
	{
		public final Instant start;
		public final Instant end;

		Window(Instant start, Instant end)
		{
			this.start = start;
			this.end = end;
		}
	}

	private Instant riftActiveAt;
	private Instant lastSpawnAt;
	private boolean portalOpen;
	private final List<Instant> spawnHistory = new ArrayList<>();

	/** Anchors the first-portal estimate to the moment the rift became active. */
	public void anchor(Instant riftActiveAt)
	{
		this.riftActiveAt = riftActiveAt;
		this.lastSpawnAt = null;
		this.portalOpen = false;
		this.spawnHistory.clear();
	}

	public void onPortalSpawn(Instant now)
	{
		portalOpen = true;
		lastSpawnAt = now;
		spawnHistory.add(now);
	}

	public void onPortalDespawn(Instant now)
	{
		portalOpen = false;
	}

	public boolean isPortalOpen()
	{
		return portalOpen;
	}

	/** Spawn instants observed this game, oldest first (for time-strip markers). */
	public List<Instant> getSpawnHistory()
	{
		return new ArrayList<>(spawnHistory);
	}

	/**
	 * The estimated window for the next portal, or empty when there is no anchor yet
	 * (e.g. joined mid-game before seeing a portal) or a portal is currently open.
	 */
	public Optional<Window> nextWindow(Instant now)
	{
		if (portalOpen)
		{
			return Optional.empty();
		}

		final Instant center;
		if (lastSpawnAt != null)
		{
			center = lastSpawnAt.plusSeconds(GotrConstants.PORTAL_CYCLE_SECONDS);
		}
		else if (riftActiveAt != null)
		{
			center = riftActiveAt.plusSeconds(GotrConstants.FIRST_PORTAL_DELAY_SECONDS);
		}
		else
		{
			return Optional.empty();
		}

		Instant end = center.plusSeconds(GotrConstants.PORTAL_ESTIMATE_TOLERANCE_SECONDS);
		if (end.isBefore(now))
		{
			// The estimate has fully elapsed without an observed spawn; stop showing a stale
			// window rather than inventing a new one from nothing.
			return Optional.empty();
		}
		return Optional.of(new Window(
			center.minusSeconds(GotrConstants.PORTAL_ESTIMATE_TOLERANCE_SECONDS), end));
	}

	/** Seconds from now until the center of the next estimated window, if any. */
	public Optional<Long> secondsToNextEstimate(Instant now)
	{
		return nextWindow(now).map(w ->
		{
			Instant center = w.start.plusSeconds(
				Duration.between(w.start, w.end).getSeconds() / 2);
			return Math.max(0, Duration.between(now, center).getSeconds());
		});
	}

	public void reset()
	{
		riftActiveAt = null;
		lastSpawnAt = null;
		portalOpen = false;
		spawnHistory.clear();
	}
}
