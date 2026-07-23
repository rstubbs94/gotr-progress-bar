package com.gotrprogressbar.model;

/**
 * Preset overlay sizes. Carries the pixel metrics for the bars and whether to use the larger
 * RuneScape font. Heights are kept at or above each font's glyph height so text never clips.
 */
public enum BarSize
{
	SMALL("Small", 13, 16, 2, 16, false),
	MEDIUM("Medium", 16, 20, 2, 20, true),
	LARGE("Large", 20, 26, 3, 26, true);

	private final String label;
	private final int captionRow;
	private final int barHeight;
	private final int subGap;
	private final int subHeight;
	private final boolean largeFont;

	BarSize(String label, int captionRow, int barHeight, int subGap, int subHeight,
		boolean largeFont)
	{
		this.label = label;
		this.captionRow = captionRow;
		this.barHeight = barHeight;
		this.subGap = subGap;
		this.subHeight = subHeight;
		this.largeFont = largeFont;
	}

	public int captionRow()
	{
		return captionRow;
	}

	public int barHeight()
	{
		return barHeight;
	}

	public int subGap()
	{
		return subGap;
	}

	public int subHeight()
	{
		return subHeight;
	}

	public boolean largeFont()
	{
		return largeFont;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
