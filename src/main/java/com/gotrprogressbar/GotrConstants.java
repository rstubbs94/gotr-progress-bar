package com.gotrprogressbar;

/**
 * All Guardians of the Rift game identifiers and timing constants used by this plugin.
 *
 * RuneLite core has no named GOTR widget/varbit constants, so raw IDs are kept here in one
 * place. Every value is sourced from an accepted Plugin Hub plugin or the OSRS Wiki:
 * - DatBear/Guardians-of-the-Rift-Helper (GuardiansOfTheRiftHelperPlugin.java)
 * - hawolt/guardian-of-the-rift (StaticConstant.java, MinigameSlice.java)
 * - https://oldschool.runescape.wiki/w/Guardians_of_the_Rift
 */
public final class GotrConstants
{
	private GotrConstants()
	{
	}

	// Map region containing the GOTR game area (DatBear MINIGAME_MAIN_REGION / hawolt MINIGAME_REGION_ID)
	public static final int GOTR_REGION_ID = 14484;

	// Clientscript that refreshes the GOTR HUD; fires with the full minigame state as arguments
	// (hawolt MINIGAME_HUD_UPDATE_SCRIPT_ID)
	public static final int HUD_UPDATE_SCRIPT_ID = 5980;

	// Argument indexes of script 5980 (index 0 is the script id itself; hawolt MinigameSlice)
	public static final int ARG_ELEMENTAL_ENERGY = 1;
	public static final int ARG_CATALYTIC_ENERGY = 2;
	public static final int ARG_CURRENT_POWER = 3;
	public static final int ARG_MAX_POWER = 4;
	public static final int ARG_PORTAL_LOCATION = 5; // <= 0 when no portal is open
	public static final int ARG_ELEMENTAL_RUNE_INDEX = 6; // 0 = no altar open (mining phase / no game)
	public static final int ARG_CATALYTIC_RUNE_INDEX = 7;
	public static final int ARG_CURRENT_GUARDIANS = 8;
	public static final int ARG_MAX_GUARDIANS = 9;
	// During the mining phase this is an exact countdown (in ticks) until the altars open;
	// during crafting it is the current altar set's remaining time; between games it holds a
	// garbage negative value. Verified from live HUD logs 2026-07-13.
	public static final int ARG_GUARDIAN_TICKS = 10;
	public static final int ARG_PORTAL_TICKS = 11; // ticks until the open portal closes
	public static final int HUD_SCRIPT_ARG_COUNT = 12;

	// Live-verified round structure (HUD logs 2026-07-13): "The rift becomes active!" starts
	// the ROUND (mining phase). During mining, power sits at exactly max/10 with both rune
	// indexes 0 and ARG_GUARDIAN_TICKS counting down ~200 ticks (120 s) to the altars opening.
	// Crafting begins when a rune index goes above 0. Between rounds power is 0.
	public static final int MINING_PHASE_TICKS_NOMINAL = 200;
	// Sanity ceiling when interpreting ARG_GUARDIAN_TICKS as the mining countdown
	public static final int MINING_PHASE_TICKS_MAX = 250;

	// GOTR HUD parent widget, packed group 746 child 2; non-null only while inside the minigame
	// (DatBear PARENT_WIDGET_ID / hawolt MINIGAME_WIDGET_PARENT_ID)
	public static final int PARENT_WIDGET_ID = 48889858;

	// HUD power text widget, group 746 child 18 — fallback source for power if script args change
	// (hawolt MINIGAME_WIDGET_POWER_TEXT_WIDGET_ID)
	public static final int POWER_TEXT_WIDGET_ID = 48889874;

	// Banked, spendable reward points (DatBear onVarbitChanged; raw ints, no core constant exists)
	public static final int VARBIT_ELEMENTAL_POINTS = 13686;
	public static final int VARBIT_CATALYTIC_POINTS = 13685;

	// Inventory item container id (hawolt INVENTORY_CONTAINER_ID)
	public static final int INVENTORY_CONTAINER_ID = 93;

	// Exact game messages that anchor phase timing (DatBear onChatMessage, verbatim)
	public static final String CHAT_START_30S = "The rift will become active in 30 seconds.";
	public static final String CHAT_START_10S = "The rift will become active in 10 seconds.";
	public static final String CHAT_START_5S = "The rift will become active in 5 seconds.";
	public static final String CHAT_RIFT_ACTIVE = "The rift becomes active!";
	// Fired when the portal guardians extend the current game; DatBear pushes next start out 60s
	public static final String CHAT_PORTALS_EXTEND =
		"The Portal Guardians will keep their rifts open for another 30 seconds.";

	// Portal cadence. Cycle live-verified 2026-07-13: consecutive fresh spawns 141 s apart.
	// First-portal delay is wiki-sourced and still unmeasured from a full round — verify.
	// The exact countdown of an OPEN portal always comes from ARG_PORTAL_TICKS.
	public static final int FIRST_PORTAL_DELAY_SECONDS = 160;
	public static final int PORTAL_CYCLE_SECONDS = 140;
	public static final int PORTAL_ESTIMATE_TOLERANCE_SECONDS = 10;

	// A portal's full lifetime in ticks (live-verified 2026-07-13: two fresh spawns both
	// reported 44). Used to back-date the spawn when we first see a portal mid-life.
	public static final int PORTAL_LIFETIME_TICKS = 44;

	// One server tick in milliseconds
	public static final long GAME_TICK_MILLIS = 600L;

	// ---- scrolling game timeline (overlay geometry) ----

	// The locked mining window in seconds (200 ticks, live-verified)
	public static final long MINING_PHASE_SECONDS = 120L;

	// The bar shows this fixed span of game time (constant scale — never rescales). The
	// cursor moves left-to-right through a stationary timeline until it reaches the pin
	// fraction, then stays pinned while the timeline scrolls past it (camera with deadzone).
	// 0.40 * 300 s = 120 s: the cursor pins exactly when the mining phase ends, so mining
	// reads as a normal left-to-right progress bar and only crafting scrolls.
	public static final double TIMELINE_WINDOW_SECONDS = 300;
	public static final double TIMELINE_CURSOR_FRACTION = 0.40;

	// How far ahead future portal marks are generated, and a safety cap on their count
	public static final double TIMELINE_LOOKAHEAD_SECONDS = 300;
	public static final int MAX_TIMELINE_MARKS = 12;

	// How long the end-of-game summary stays on the bar before returning to the waiting state
	public static final long GAME_END_SUMMARY_SECONDS = 10L;

	// The chat anchors only give at most a 30 second heads-up; the extend message gives 60.
	// Cap for sanity-checking any future longer anchor sources found by spike S4.5.
	public static final long MAX_START_ETA_SECONDS = 130L;
}
