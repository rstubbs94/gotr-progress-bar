package com.gotrprogressbar;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev launcher: starts a full RuneLite client with this plugin loaded.
 * Run via {@code gradlew.bat run} (passes --developer-mode --debug).
 */
public class GotrProgressBarPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GotrProgressBarPlugin.class);
		RuneLite.main(args);
	}
}
