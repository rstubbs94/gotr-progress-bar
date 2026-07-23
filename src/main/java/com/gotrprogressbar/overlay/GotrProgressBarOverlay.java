package com.gotrprogressbar.overlay;

import com.gotrprogressbar.GotrConstants;
import com.gotrprogressbar.GotrProgressBarConfig;
import com.gotrprogressbar.GotrProgressBarPlugin;
import com.gotrprogressbar.model.BarSize;
import com.gotrprogressbar.model.GamePhase;
import com.gotrprogressbar.model.GoalState;
import com.gotrprogressbar.model.GotrSession;
import com.gotrprogressbar.model.PointsMetric;
import com.gotrprogressbar.model.PortalMark;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;

/**
 * The two bars, styled like standard RuneLite overlays: translucent warm-dark ground
 * (ComponentConstants standard background), thin bars, translucent fills, small shadowed text.
 *
 * Bar A (between rounds): a plain next-game countdown.
 * Bar B (in a round): a fixed-scale scrolling timeline — 300 s window, "now" cursor pinned at
 * 25%, everything drifts smoothly past it. Phase label and round clock sit in a slim caption
 * row above the bar so the bar itself stays clean for marks. A mid-joined game shows a pulsing
 * "watching for portal" state until the first observed spawn anchors the cycle.
 */
public class GotrProgressBarOverlay extends Overlay
{
	// Bar metrics for the current frame, set from the configured size at the top of render().
	private int captionRow;
	private int barHeight;
	private int subGap;
	private int subHeight;

	// Opaque so opacity=100 is truly solid; the slider (AlphaComposite) scales it down. The
	// RuneLite standard background is translucent, which is why full opacity was unreachable.
	private static final Color BACKGROUND = opaque(ComponentConstants.STANDARD_BACKGROUND_COLOR);
	private static final Color BORDER = new Color(0, 0, 0, 180);
	private static final Color TEXT = Color.WHITE;
	private static final Color TEXT_DIM = new Color(200, 193, 174);
	private static final Color TEXT_YELLOW = new Color(255, 233, 74);
	// Section colors (user-specified): mining light blue, crafting green,
	// portal guess window transparent light yellow, actual portal section yellow
	private static final Color MINE_DONE = new Color(120, 180, 220, 150);
	private static final Color MINE_TODO = new Color(120, 180, 220, 55);
	private static final Color TRAIL_FILL = new Color(64, 142, 72, 110);
	private static final Color PORTAL_ACTIVE = new Color(255, 215, 64, 190);
	private static final Color PORTAL_PAST = new Color(255, 215, 64, 110);
	private static final Color PORTAL_GUESS = new Color(255, 230, 120, 50);
	private static final Color PORTAL_GUESS_EDGE = new Color(255, 230, 120, 130);
	private static final Color PORTAL_TEXT = new Color(255, 224, 96);
	private static final Color WAIT_FILL = new Color(112, 132, 152, 110);
	private static final Color CURSOR_SHADOW = new Color(0, 0, 0, 140);

	private final GotrProgressBarPlugin plugin;
	private final GotrProgressBarConfig config;

	@Inject
	public GotrProgressBarOverlay(GotrProgressBarPlugin plugin, GotrProgressBarConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		GotrSession session = plugin.getSession();
		GamePhase phase = session.getPhase();
		if (phase == GamePhase.OUTSIDE)
		{
			return null;
		}
		if (phase == GamePhase.WAITING_FOR_GAME && !config.showBetweenRounds())
		{
			return null;
		}

		Instant now = Instant.now();
		int width = config.barWidth();

		BarSize size = config.barSize();
		captionRow = size.captionRow();
		barHeight = size.barHeight();
		subGap = size.subGap();
		subHeight = size.subHeight();
		g.setFont(size.largeFont()
			? FontManager.getRunescapeFont()
			: FontManager.getRunescapeSmallFont());

		// Apply the opacity slider once for the whole overlay; our per-element alpha then
		// composites on top of it. Restored before returning so we never leak the composite.
		Composite originalComposite = g.getComposite();
		float opacity = Math.min(1f, Math.max(0f, config.opacity() / 100f));
		if (opacity < 1f)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
		}
		try
		{
			if (phase == GamePhase.WAITING_FOR_GAME)
			{
				renderNextGameBar(g, session, now, width);
				return new Dimension(width, barHeight);
			}

			boolean sub = hasGoalSubBar(session, phase);
			renderTimeline(g, session, phase, now, width);
			if (sub)
			{
				renderGoalSubBar(g, session, phase, width, captionRow + barHeight + subGap);
			}
			return new Dimension(width,
				captionRow + barHeight + (sub ? subGap + subHeight : 0));
		}
		finally
		{
			g.setComposite(originalComposite);
		}
	}

	private static Color opaque(Color c)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
	}

	// ---- Bar A: next game ----------------------------------------------------------------

	private void renderNextGameBar(Graphics2D g, GotrSession session, Instant now, int width)
	{
		frame(g, 0, 0, width, barHeight);
		Optional<Double> fill = session.waitingFillFraction(now);
		fill.ifPresent(f ->
		{
			g.setColor(WAIT_FILL);
			g.fillRect(1, 1, (int) ((width - 2) * f), barHeight - 2);
		});

		int base = baseline(g, 0, barHeight);
		shadowText(g, "Next game", 6, base, fill.isPresent() ? TEXT : TEXT_DIM);
		FontMetrics fm = g.getFontMetrics();
		Optional<Long> toStart = session.secondsToGameStart(now);
		if (toStart.isPresent())
		{
			String right = mmss(toStart.get());
			shadowText(g, right, width - fm.stringWidth(right) - 6, base, TEXT_YELLOW);
		}
		else
		{
			shadowText(g, "waiting...", width - fm.stringWidth("waiting...") - 6, base,
				pulse(TEXT_DIM));
		}
	}

	// ---- Bar B: scrolling game timeline -----------------------------------------------------

	private void renderTimeline(Graphics2D g, GotrSession session, GamePhase phase,
		Instant now, int width)
	{
		int y = captionRow;
		int innerW = width - 2;
		int capBase = captionRow - 4;
		FontMetrics fm = g.getFontMetrics();
		frame(g, 0, y, width, barHeight);

		if (phase == GamePhase.GAME_END)
		{
			g.setColor(TRAIL_FILL);
			g.fillRect(1, y + 1, innerW, barHeight - 2);
			String text = (session.wasLastGameWon() ? "Rift sealed" : "Rift closed")
				+ " - " + session.getFinalElementalEnergy() + "E / "
				+ session.getFinalCatalyticEnergy() + "C";
			shadowText(g, text, (width - fm.stringWidth(text)) / 2,
				baseline(g, y, barHeight), TEXT);
			return;
		}

		// caption row: phase label left, clock right (clock optional)
		shadowText(g, phase == GamePhase.MINING_PHASE ? "Mining" : "Crafting", 2, capBase, TEXT);
		if (config.showGameTimer())
		{
			Optional<String> clock = session.elapsedSinceRiftActive(now)
				.map(d -> mmss(d.getSeconds()));
			boolean observedOnly = false;
			if (!clock.isPresent())
			{
				clock = session.observedCraftingElapsed(now)
					.map(d -> "+" + mmss(d.getSeconds()));
				observedOnly = true;
			}
			if (clock.isPresent())
			{
				shadowText(g, clock.get(), width - fm.stringWidth(clock.get()) - 2, capBase,
					observedOnly ? TEXT_DIM : TEXT_YELLOW);
			}
		}

		// Camera-with-deadzone timeline: the cursor moves left-to-right through a stationary
		// timeline until it reaches the pin fraction, then stays pinned while the timeline
		// scrolls past it. Constant scale — never rescales.
		double pxPerSec = innerW / GotrConstants.TIMELINE_WINDOW_SECONDS;
		long pinMs = (long) (GotrConstants.TIMELINE_CURSOR_FRACTION
			* GotrConstants.TIMELINE_WINDOW_SECONDS * 1000);
		Optional<Instant> roundStart = session.getRiftActiveAt();
		Instant viewportStart = now.minusMillis(pinMs);
		if (roundStart.isPresent() && roundStart.get().isAfter(viewportStart))
		{
			viewportStart = roundStart.get();
		}
		int cursorX = xOf(now, viewportStart, pxPerSec);
		int base = baseline(g, y, barHeight);

		if (roundStart.isPresent())
		{
			int mineStartX = xOf(roundStart.get(), viewportStart, pxPerSec);
			int mineEndX = xOf(roundStart.get().plusSeconds(GotrConstants.MINING_PHASE_SECONDS),
				viewportStart, pxPerSec);
			// The whole mining section is always visible (dim); the elapsed part (behind the
			// cursor) is bright — so the fill advances left-to-right within the section.
			fillSpan(g, y, mineStartX, Math.min(cursorX, mineEndX), innerW, MINE_DONE);
			fillSpan(g, y, Math.max(cursorX, mineStartX), mineEndX, innerW, MINE_TODO);
			// crafting trail behind the cursor
			fillSpan(g, y, mineEndX, cursorX, innerW, TRAIL_FILL);
			if (mineEndX >= 1 && mineEndX <= innerW)
			{
				g.setColor(BORDER);
				g.fillRect(mineEndX, y + 1, 1, barHeight - 2);
			}
		}
		else if (phase == GamePhase.RIFT_ACTIVE)
		{
			fillSpan(g, y, 1, cursorX, innerW, TRAIL_FILL);
		}

		// portal marks (drift with the timeline; re-anchor only on real spawns)
		List<PortalMark> marks = session.portalMarks(now);
		Integer firstFutureX = null;
		int windowHalfPx = (int) (GotrConstants.PORTAL_ESTIMATE_TOLERANCE_SECONDS * pxPerSec);
		for (PortalMark mark : marks)
		{
			int x = xOf(mark.time, viewportStart, pxPerSec);
			int xEnd = xOf(mark.endTime, viewportStart, pxPerSec);
			switch (mark.state)
			{
				case PAST:
					// a real section spanning the portal's actual open duration
					if (config.showPortalMarkers())
					{
						fillSpan(g, y, x, xEnd, innerW, PORTAL_PAST);
					}
					break;
				case ACTIVE:
				{
					// solid yellow section from the real spawn to the real close
					fillSpan(g, y, x, xEnd, innerW, PORTAL_ACTIVE);
					String live = "Portal "
						+ (mark.secondsRemaining >= 0 ? mmss(mark.secondsRemaining) : "");
					shadowText(g, live,
						Math.min(Math.max(xEnd, 1) + 5, width - fm.stringWidth(live) - 4),
						base, PORTAL_TEXT);
					break;
				}
				case FUTURE:
					// transparent guess window: spawn tolerance plus the portal's duration
					if (config.showPortalMarkers() && xEnd + windowHalfPx >= 1
						&& x - windowHalfPx <= innerW)
					{
						int x1 = Math.max(1, x - windowHalfPx);
						int x2 = Math.min(innerW, xEnd + windowHalfPx);
						g.setColor(PORTAL_GUESS);
						g.fillRect(x1, y + 1, Math.max(2, x2 - x1), barHeight - 2);
						g.setColor(PORTAL_GUESS_EDGE);
						g.drawLine(x1, y + 1, x1, y + barHeight - 2);
						g.drawLine(x2, y + 1, x2, y + barHeight - 2);
						if (firstFutureX == null)
						{
							firstFutureX = x2;
						}
					}
					break;
			}
		}

		// the pinned "now" cursor
		g.setColor(CURSOR_SHADOW);
		g.fillRect(cursorX - 1, y, 3, barHeight);
		g.setColor(TEXT);
		g.fillRect(cursorX, y, 1, barHeight);

		// in-bar texts
		if (phase == GamePhase.MINING_PHASE)
		{
			String countdown = session.miningSecondsRemaining(now)
				.map(s -> "altars open " + mmss(s)).orElse("");
			shadowText(g, countdown, cursorX + 7, base, TEXT_YELLOW);
		}
		else if (!session.isPortalOpen())
		{
			Optional<Long> next = session.secondsToNextPortal(now);
			if (next.isPresent() && config.showPortalMarkers())
			{
				String text = "~" + mmss(next.get());
				int tx = firstFutureX != null
					? Math.min(firstFutureX + 4, width - fm.stringWidth(text) - 4)
					: cursorX + 7;
				shadowText(g, text, tx, base, PORTAL_TEXT);
			}
			else if (marks.isEmpty())
			{
				// mid-join with no anchor yet: show we're alive and waiting
				shadowText(g, "watching for portal...", cursorX + 7, base, pulse(PORTAL_TEXT));
			}
		}
	}

	/** Maps an absolute instant to a bar x within the current viewport. */
	private int xOf(Instant t, Instant viewportStart, double pxPerSec)
	{
		double dt = (t.toEpochMilli() - viewportStart.toEpochMilli()) / 1000.0;
		return 1 + (int) Math.round(dt * pxPerSec);
	}

	/** Fills a horizontal section of the bar, clipped to the bar's interior. */
	private void fillSpan(Graphics2D g, int barY, int fromX, int toX, int innerW, Color color)
	{
		int x1 = Math.max(1, fromX);
		int x2 = Math.min(innerW + 1, toX);
		if (x2 > x1)
		{
			g.setColor(color);
			g.fillRect(x1, barY + 1, x2 - x1, barHeight - 2);
		}
	}

	// ---- goal sub-bar ----------------------------------------------------------------------

	private boolean hasGoalSubBar(GotrSession session, GamePhase phase)
	{
		if (phase == GamePhase.MINING_PHASE)
		{
			return session.getFragmentGoal() > 0;
		}
		return session.hasPointsGoal();
	}

	private void renderGoalSubBar(Graphics2D g, GotrSession session, GamePhase phase,
		int width, int y)
	{
		frame(g, 0, y, width, subHeight);
		int innerW = width - 2;
		int base = baseline(g, y, subHeight);
		FontMetrics fm = g.getFontMetrics();

		if (phase == GamePhase.MINING_PHASE)
		{
			drawGoalFill(g, 1, y, innerW, session.getFragmentCount(),
				session.getFragmentGoal(), session.fragmentGoalState());
			shadowText(g, "Fragments", 6, base, TEXT);
			String v = session.getFragmentCount() + " / " + session.getFragmentGoal();
			shadowText(g, v, width - fm.stringWidth(v) - 6, base, TEXT);
			return;
		}

		if (session.getPointsMetric() == PointsMetric.BOTH_SPLIT)
		{
			// each half tracks its own goal (they can differ; 0 disables one side)
			int half = innerW / 2;
			drawGoalFill(g, 1, y, half - 1, session.getElementalEnergy(),
				session.getElementalPointsGoal(), session.splitElementalGoalState());
			drawGoalFill(g, half + 2, y, innerW - half - 2, session.getCatalyticEnergy(),
				session.getCatalyticPointsGoal(), session.splitCatalyticGoalState());
			g.setColor(BORDER);
			g.fillRect(half, y + 1, 2, subHeight - 2);
			shadowText(g, goalText("E", session.getElementalEnergy(),
				session.getElementalPointsGoal()), 6, base, TEXT);
			String c = goalText("C", session.getCatalyticEnergy(),
				session.getCatalyticPointsGoal());
			shadowText(g, c, width - fm.stringWidth(c) - 6, base, TEXT);
			return;
		}

		drawGoalFill(g, 1, y, innerW, session.pointsValue(), session.activePointsGoal(),
			session.pointsGoalState());
		String label;
		switch (session.getPointsMetric())
		{
			case ELEMENTAL:
				label = "Elemental";
				break;
			case CATALYTIC:
				label = "Catalytic";
				break;
			default:
				label = "Points";
		}
		shadowText(g, label, 6, base, TEXT);
		String v = session.pointsValue() + " / " + session.activePointsGoal();
		shadowText(g, v, width - fm.stringWidth(v) - 6, base, TEXT);
	}

	/** "E 161/200", or just "E 161" when that side's goal is disabled. */
	private String goalText(String prefix, int value, int goal)
	{
		return goal > 0 ? prefix + " " + value + "/" + goal : prefix + " " + value;
	}

	private void drawGoalFill(Graphics2D g, int x, int y, int w, int value, int goal,
		GoalState state)
	{
		if (state == GoalState.DISABLED || goal <= 0)
		{
			return;
		}
		double frac = Math.min(1.0, (double) value / (double) goal);
		int fillW = (int) (w * frac);
		if (fillW <= 0)
		{
			return;
		}
		Color c = state == GoalState.MET ? config.goalMetColor() : config.belowGoalColor();
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 115));
		g.fillRect(x, y + 1, fillW, subHeight - 2);
	}

	// ---- native-style drawing helpers ------------------------------------------------------

	private void frame(Graphics2D g, int x, int y, int w, int h)
	{
		g.setColor(BACKGROUND);
		g.fillRect(x, y, w, h);
		g.setColor(BORDER);
		g.drawRect(x, y, w - 1, h - 1);
	}

	private void shadowText(Graphics2D g, String text, int x, int y, Color color)
	{
		if (text.isEmpty())
		{
			return;
		}
		g.setColor(new Color(0, 0, 0, color.getAlpha()));
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	/** Gentle alpha pulse for "alive but waiting" texts. */
	private Color pulse(Color c)
	{
		double s = (Math.sin(System.currentTimeMillis() / 400.0) + 1) / 2;
		int alpha = 130 + (int) (s * 125);
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}

	private int baseline(Graphics2D g, int y, int h)
	{
		// center the full line box (ascent+descent+leading); the +ascent-descent shortcut
		// ignores leading and sits visibly too low in the short bars
		FontMetrics fm = g.getFontMetrics();
		return y + (h - fm.getHeight()) / 2 + fm.getAscent();
	}

	private String mmss(long seconds)
	{
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
