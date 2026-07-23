package com.gotrprogressbar.model;

import com.gotrprogressbar.GotrConstants;

/**
 * Immutable parse of one firing of the GOTR HUD update script (5980).
 * Argument layout verified against hawolt/guardian-of-the-rift MinigameSlice.
 */
public final class HudSnapshot
{
	public final int elementalEnergy;
	public final int catalyticEnergy;
	public final int currentPower;
	public final int maxPower;
	public final int portalLocation;
	public final int elementalRuneIndex;
	public final int catalyticRuneIndex;
	public final int currentGuardians;
	public final int maxGuardians;
	public final int guardianTicks;
	public final int portalTicks;

	public HudSnapshot(int elementalEnergy, int catalyticEnergy, int currentPower, int maxPower,
		int portalLocation, int elementalRuneIndex, int catalyticRuneIndex,
		int currentGuardians, int maxGuardians, int guardianTicks, int portalTicks)
	{
		this.elementalEnergy = elementalEnergy;
		this.catalyticEnergy = catalyticEnergy;
		this.currentPower = currentPower;
		this.maxPower = maxPower;
		this.portalLocation = portalLocation;
		this.elementalRuneIndex = elementalRuneIndex;
		this.catalyticRuneIndex = catalyticRuneIndex;
		this.currentGuardians = currentGuardians;
		this.maxGuardians = maxGuardians;
		this.guardianTicks = guardianTicks;
		this.portalTicks = portalTicks;
	}

	/**
	 * Defensively parses the raw script arguments. Returns null when the layout does not match
	 * expectations (wrong length or non-integer values) so a game update can never crash us.
	 */
	public static HudSnapshot fromArgs(Object[] args)
	{
		if (args == null || args.length < GotrConstants.HUD_SCRIPT_ARG_COUNT)
		{
			return null;
		}
		int[] values = new int[GotrConstants.HUD_SCRIPT_ARG_COUNT];
		for (int i = 1; i < GotrConstants.HUD_SCRIPT_ARG_COUNT; i++)
		{
			if (!(args[i] instanceof Integer))
			{
				return null;
			}
			values[i] = (Integer) args[i];
		}
		return new HudSnapshot(
			values[GotrConstants.ARG_ELEMENTAL_ENERGY],
			values[GotrConstants.ARG_CATALYTIC_ENERGY],
			values[GotrConstants.ARG_CURRENT_POWER],
			values[GotrConstants.ARG_MAX_POWER],
			values[GotrConstants.ARG_PORTAL_LOCATION],
			values[GotrConstants.ARG_ELEMENTAL_RUNE_INDEX],
			values[GotrConstants.ARG_CATALYTIC_RUNE_INDEX],
			values[GotrConstants.ARG_CURRENT_GUARDIANS],
			values[GotrConstants.ARG_MAX_GUARDIANS],
			values[GotrConstants.ARG_GUARDIAN_TICKS],
			values[GotrConstants.ARG_PORTAL_TICKS]);
	}

	public boolean isPortalOpen()
	{
		return portalLocation > 0;
	}

	/**
	 * True during the crafting phase: at least one altar (portal guardian) is open.
	 * Live-verified: rune indexes are 0 throughout the mining phase and between games.
	 */
	public boolean isCrafting()
	{
		return maxPower > 0 && currentPower > 0 && currentPower < maxPower
			&& (elementalRuneIndex > 0 || catalyticRuneIndex > 0);
	}

	/**
	 * True during the mining phase at the start of a round: power parked at exactly max/10,
	 * no altars open, and the guardian-ticks arg counting down to the altars opening.
	 */
	public boolean isMiningPhase()
	{
		return maxPower > 0
			&& currentPower * 10 == maxPower
			&& elementalRuneIndex == 0 && catalyticRuneIndex == 0
			&& guardianTicks > 0
			&& guardianTicks <= GotrConstants.MINING_PHASE_TICKS_MAX;
	}

	/** True when the rift has been sealed (win condition). */
	public boolean isGameWon()
	{
		return maxPower > 0 && currentPower >= maxPower;
	}

	/** True between rounds: the HUD reports zero power. */
	public boolean isIdle()
	{
		return maxPower <= 0 || currentPower <= 0;
	}

	public double powerFraction()
	{
		if (maxPower <= 0)
		{
			return 0;
		}
		return Math.min(1.0, Math.max(0.0, (double) currentPower / (double) maxPower));
	}

	@Override
	public String toString()
	{
		return "HudSnapshot{energy=" + elementalEnergy + "E/" + catalyticEnergy + "C"
			+ ", power=" + currentPower + "/" + maxPower
			+ ", portalLoc=" + portalLocation + ", portalTicks=" + portalTicks
			+ ", guardians=" + currentGuardians + "/" + maxGuardians
			+ ", runeIdx=" + elementalRuneIndex + "/" + catalyticRuneIndex
			+ ", guardianTicks=" + guardianTicks + "}";
	}
}
