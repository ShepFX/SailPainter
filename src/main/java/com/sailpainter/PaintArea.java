package com.sailpainter;

import lombok.RequiredArgsConstructor;

// Public: RuneLite's config proxy cannot reach a package-private enum.
@RequiredArgsConstructor
public enum PaintArea
{
	CLOTH("Sail cloth"),
	WHOLE_MODEL("Whole sail model");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
