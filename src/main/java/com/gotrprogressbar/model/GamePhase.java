package com.gotrprogressbar.model;

/**
 * Lifecycle of a Guardians of the Rift round as seen by the player.
 */
public enum GamePhase
{
	/** Not inside the GOTR area; the overlay renders nothing. */
	OUTSIDE,
	/**
	 * Inside the area between rounds (HUD power is 0). Shows a countdown to the next round
	 * once the chat announces it ("The rift will become active in 30/10/5 seconds.").
	 */
	WAITING_FOR_GAME,
	/**
	 * The round has started ("The rift becomes active!") but the altars have not opened yet.
	 * Power sits at exactly 10%; the HUD guardian-ticks arg counts down the ~120 s mining
	 * window exactly. (Live-verified 2026-07-13.)
	 */
	MINING_PHASE,
	/** Crafting phase: altars are open (rune index > 0); power climbs toward 100%. */
	RIFT_ACTIVE,
	/** The round just ended; the bar shows a short summary before returning to waiting. */
	GAME_END
}
