package com.gotrprogressbar;

import com.gotrprogressbar.model.BarSize;
import com.gotrprogressbar.model.PointsMetric;
import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(GotrProgressBarConfig.GROUP)
public interface GotrProgressBarConfig extends Config
{
	String GROUP = "gotrprogressbar";

	@ConfigSection(
		name = "Goals",
		description = "Personal goals shown on the goal sub-bar (0 disables a goal)",
		position = 0
	)
	String goalsSection = "goals";

	@ConfigSection(
		name = "Display",
		description = "Bar size and optional elements",
		position = 1
	)
	String displaySection = "display";

	@Range(max = 999)
	@ConfigItem(
		keyName = "fragmentGoal",
		name = "Fragment goal",
		description = "Guardian fragments to mine during the mining phase; the sub-bar turns green once reached (0 = off)",
		position = 0,
		section = goalsSection
	)
	default int fragmentGoal()
	{
		return 120;
	}

	@ConfigItem(
		keyName = "pointsMetric",
		name = "Points metric",
		description = "What the crafting-phase goal counts: combined energy, one side only, or both sides split with their own goals",
		position = 1,
		section = goalsSection
	)
	default PointsMetric pointsMetric()
	{
		return PointsMetric.COMBINED;
	}

	@Range(max = 9999)
	@ConfigItem(
		keyName = "combinedPointsGoal",
		name = "Combined goal",
		description = "Total energy (elemental + catalytic) to earn each round; used by the Combined metric (0 = off)",
		position = 2,
		section = goalsSection
	)
	default int combinedPointsGoal()
	{
		return 300;
	}

	@Range(max = 9999)
	@ConfigItem(
		keyName = "elementalPointsGoal",
		name = "Elemental goal",
		description = "Elemental energy to earn each round; used by the Elemental metric and the elemental half of Both (split) (0 = off)",
		position = 3,
		section = goalsSection
	)
	default int elementalPointsGoal()
	{
		return 150;
	}

	@Range(max = 9999)
	@ConfigItem(
		keyName = "catalyticPointsGoal",
		name = "Catalytic goal",
		description = "Catalytic energy to earn each round; used by the Catalytic metric and the catalytic half of Both (split) (0 = off)",
		position = 4,
		section = goalsSection
	)
	default int catalyticPointsGoal()
	{
		return 150;
	}

	@ConfigItem(
		keyName = "barSize",
		name = "Size",
		description = "Preset overlay size (bar thickness and font)",
		position = 0,
		section = displaySection
	)
	default BarSize barSize()
	{
		return BarSize.SMALL;
	}

	@Range(min = 280, max = 700)
	@ConfigItem(
		keyName = "barWidth",
		name = "Bar width",
		description = "Width of the bar in pixels",
		position = 1,
		section = displaySection
	)
	default int barWidth()
	{
		return 420;
	}

	@Range(min = 15, max = 100)
	@ConfigItem(
		keyName = "opacity",
		name = "Opacity",
		description = "Overall opacity of the overlay, as a percentage (100 = fully opaque)",
		position = 2,
		section = displaySection
	)
	default int opacity()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "showGameTimer",
		name = "Show game timer",
		description = "Show the elapsed round clock in the top-right of the timeline",
		position = 3,
		section = displaySection
	)
	default boolean showGameTimer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPortalMarkers",
		name = "Show portal markers",
		description = "Show past sections and estimated future windows for portals on the timeline (a live portal always shows)",
		position = 4,
		section = displaySection
	)
	default boolean showPortalMarkers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBetweenRounds",
		name = "Show between-round countdown",
		description = "Show the 'Next game' countdown bar while no round is running",
		position = 5,
		section = displaySection
	)
	default boolean showBetweenRounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "belowGoalColor",
		name = "Below goal",
		description = "Sub-bar color while a goal has not been reached",
		position = 6,
		section = displaySection
	)
	default Color belowGoalColor()
	{
		return new Color(176, 71, 58);
	}

	@ConfigItem(
		keyName = "goalMetColor",
		name = "Goal met",
		description = "Sub-bar color once a goal has been reached",
		position = 7,
		section = displaySection
	)
	default Color goalMetColor()
	{
		return new Color(87, 166, 74);
	}
}
