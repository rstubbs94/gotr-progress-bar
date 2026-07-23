package com.gotrprogressbar.model;

/**
 * What the crafting-phase goal sub-bar counts.
 */
public enum PointsMetric
{
	COMBINED("Combined"),
	ELEMENTAL("Elemental only"),
	CATALYTIC("Catalytic only"),
	BOTH_SPLIT("Both (split)");

	private final String label;

	PointsMetric(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
