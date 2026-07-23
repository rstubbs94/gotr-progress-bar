package com.gotrprogressbar;

import com.google.inject.Provides;
import com.gotrprogressbar.model.GotrSession;
import com.gotrprogressbar.model.HudSnapshot;
import com.gotrprogressbar.overlay.GotrProgressBarOverlay;
import java.time.Instant;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "GOTR Progress Bar",
	description = "A single at-a-glance timeline bar for Guardians of the Rift: game phase, guardian power, portal timers and configurable fragment/energy goals",
	tags = {"minigame", "overlay", "runecraft", "gotr", "guardians", "rift", "timer", "progress"}
)
public class GotrProgressBarPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GotrProgressBarOverlay overlay;

	@Inject
	private GotrProgressBarConfig config;

	@Getter
	private final GotrSession session = new GotrSession();

	private boolean warnedBadScriptArgs;

	// Ticks the HUD widget must be continuously absent before we consider the player outside.
	// Covers loading screens and short transitions (e.g. entering an altar) without resetting.
	private static final int GATE_GRACE_TICKS = 8;
	private int gateMissedTicks;

	@Provides
	GotrProgressBarConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GotrProgressBarConfig.class);
	}

	@Override
	protected void startUp()
	{
		session.reset();
		pushGoals();
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		session.reset();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GotrProgressBarConfig.GROUP.equals(event.getGroup()))
		{
			pushGoals();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Gate on the GOTR HUD widget being present AND visible (see isHudPresent). The altars
		// are separate map regions but the HUD persists and stays shown inside them, so a
		// region check would wrongly drop the overlay while runecrafting. A short grace period
		// covers loading transitions.
		if (isHudPresent())
		{
			gateMissedTicks = 0;
			session.setInMinigame(true);
			session.tick(Instant.now());
		}
		else if (++gateMissedTicks >= GATE_GRACE_TICKS)
		{
			session.setInMinigame(false);
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != GotrConstants.HUD_UPDATE_SCRIPT_ID)
		{
			return;
		}
		Object[] args = event.getScriptEvent() != null
			? event.getScriptEvent().getArguments()
			: null;
		HudSnapshot snapshot = HudSnapshot.fromArgs(args);
		if (snapshot == null)
		{
			if (!warnedBadScriptArgs)
			{
				warnedBadScriptArgs = true;
				log.warn("Unexpected GOTR HUD script arguments; the game may have updated: {}",
					Arrays.toString(args));
			}
			return;
		}
		log.debug("{}", snapshot);
		session.onHudUpdate(snapshot, Instant.now());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		session.onChatMessage(Text.removeTags(event.getMessage()), Instant.now());
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != GotrConstants.INVENTORY_CONTAINER_ID)
		{
			return;
		}
		ItemContainer inventory = event.getItemContainer();
		if (inventory == null)
		{
			return;
		}
		int fragments = 0;
		for (Item item : inventory.getItems())
		{
			if (item != null && item.getId() == ItemID.GOTR_GUARDIAN_FRAGMENT)
			{
				fragments += item.getQuantity();
			}
		}
		session.setFragmentCount(fragments);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN
			|| event.getGameState() == GameState.HOPPING)
		{
			session.reset();
		}
	}

	private boolean isHudPresent()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}
		// The GOTR HUD widget lingers (hidden) after you leave the minigame, so a plain
		// null check kept the overlay up in the bank / after teleporting away. Requiring it
		// to be visible closes the gate on leave while still covering the rune altars, where
		// the HUD is genuinely shown. The grace period in onGameTick debounces brief hides.
		Widget hud = client.getWidget(GotrConstants.PARENT_WIDGET_ID);
		return hud != null && !hud.isHidden();
	}

	private void pushGoals()
	{
		session.setGoals(
			config.fragmentGoal(),
			config.pointsMetric(),
			config.combinedPointsGoal(),
			config.elementalPointsGoal(),
			config.catalyticPointsGoal());
	}
}
