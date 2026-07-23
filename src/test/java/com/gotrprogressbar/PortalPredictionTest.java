package com.gotrprogressbar;

import com.gotrprogressbar.model.PortalPrediction;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PortalPredictionTest
{
	private static final Instant T0 = Instant.ofEpochSecond(2_000_000);

	private PortalPrediction prediction;

	@Before
	public void setUp()
	{
		prediction = new PortalPrediction();
	}

	private static Instant at(long secondsAfterT0)
	{
		return T0.plusSeconds(secondsAfterT0);
	}

	@Test
	public void noEstimateWithoutAnchor()
	{
		assertFalse(prediction.nextWindow(at(0)).isPresent());
	}

	@Test
	public void firstWindowCentersOnFirstPortalDelay()
	{
		prediction.anchor(at(0));
		PortalPrediction.Window w = prediction.nextWindow(at(10)).get();
		assertEquals(at(GotrConstants.FIRST_PORTAL_DELAY_SECONDS
			- GotrConstants.PORTAL_ESTIMATE_TOLERANCE_SECONDS), w.start);
		assertEquals(at(GotrConstants.FIRST_PORTAL_DELAY_SECONDS
			+ GotrConstants.PORTAL_ESTIMATE_TOLERANCE_SECONDS), w.end);
	}

	@Test
	public void observedSpawnReanchorsTheCycle()
	{
		prediction.anchor(at(0));
		prediction.onPortalSpawn(at(165));
		// suppressed while open
		assertFalse(prediction.nextWindow(at(170)).isPresent());
		assertTrue(prediction.isPortalOpen());

		prediction.onPortalDespawn(at(190));
		PortalPrediction.Window w = prediction.nextWindow(at(200)).get();
		assertEquals(at(165 + GotrConstants.PORTAL_CYCLE_SECONDS
			- GotrConstants.PORTAL_ESTIMATE_TOLERANCE_SECONDS), w.start);
		assertEquals(at(165 + GotrConstants.PORTAL_CYCLE_SECONDS
			+ GotrConstants.PORTAL_ESTIMATE_TOLERANCE_SECONDS), w.end);
	}

	@Test
	public void staleWindowIsDropped()
	{
		prediction.anchor(at(0));
		long pastWindow = GotrConstants.FIRST_PORTAL_DELAY_SECONDS
			+ GotrConstants.PORTAL_ESTIMATE_TOLERANCE_SECONDS + 1;
		assertFalse(prediction.nextWindow(at(pastWindow)).isPresent());
	}

	@Test
	public void secondsToNextEstimateCountsDown()
	{
		prediction.anchor(at(0));
		assertEquals(Long.valueOf(GotrConstants.FIRST_PORTAL_DELAY_SECONDS - 100),
			prediction.secondsToNextEstimate(at(100)).get());
	}

	@Test
	public void historyIsKeptForTheTimeStrip()
	{
		prediction.anchor(at(0));
		prediction.onPortalSpawn(at(160));
		prediction.onPortalDespawn(at(185));
		prediction.onPortalSpawn(at(300));
		assertEquals(2, prediction.getSpawnHistory().size());
		assertEquals(at(160), prediction.getSpawnHistory().get(0));
	}

	@Test
	public void resetClearsEverything()
	{
		prediction.anchor(at(0));
		prediction.onPortalSpawn(at(160));
		prediction.reset();
		assertFalse(prediction.nextWindow(at(161)).isPresent());
		assertEquals(0, prediction.getSpawnHistory().size());
		assertFalse(prediction.isPortalOpen());
	}
}
