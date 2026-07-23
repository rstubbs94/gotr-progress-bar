package com.gotrprogressbar.model;

import java.time.Instant;

/**
 * One portal marker on the scrolling game timeline. Times are absolute instants: actual spawn
 * times for PAST/ACTIVE marks, and projected times (last real spawn + cycle) for FUTURE ones.
 * A future mark only re-anchors when a portal actually spawns.
 */
public final class PortalMark
{
	public enum State
	{
		/** Spawned earlier this round; solid tick at its actual time. */
		PAST,
		/** Open right now; bright marker with an exact despawn countdown. */
		ACTIVE,
		/** Expected later; hatched tolerance window at its estimated time. */
		FUTURE
	}

	public final State state;
	/** This mark's start: actual spawn time, or the projected spawn time for FUTURE. */
	public final Instant time;
	/**
	 * When the portal closes/closed: actual projected close for PAST/ACTIVE (spawn +
	 * lifetime, or now + exact remaining for ACTIVE). For FUTURE this is the projected
	 * close of the estimated spawn, drawn as part of the tolerance window.
	 */
	public final Instant endTime;
	/** Despawn countdown in seconds; only meaningful for {@link State#ACTIVE}, else -1. */
	public final long secondsRemaining;

	public PortalMark(State state, Instant time, Instant endTime, long secondsRemaining)
	{
		this.state = state;
		this.time = time;
		this.endTime = endTime;
		this.secondsRemaining = secondsRemaining;
	}
}
