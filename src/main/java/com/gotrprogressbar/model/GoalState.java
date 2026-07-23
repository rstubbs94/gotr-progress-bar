package com.gotrprogressbar.model;

/**
 * Tri-state result of comparing a tracked value against a user-configured goal.
 */
public enum GoalState
{
	/** Goal is set to 0 (feature off) — render neutrally. */
	DISABLED,
	/** Value is below the goal. */
	BELOW,
	/** Value has reached or passed the goal. */
	MET;

	public static GoalState of(int value, int goal)
	{
		if (goal <= 0)
		{
			return DISABLED;
		}
		return value >= goal ? MET : BELOW;
	}
}
