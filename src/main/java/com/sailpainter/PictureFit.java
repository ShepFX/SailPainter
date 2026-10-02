package com.sailpainter;

import lombok.RequiredArgsConstructor;

// Public: RuneLite's config proxy cannot reach a package-private enum.
@RequiredArgsConstructor
public enum PictureFit
{
	FIT("Fit inside the sail"),
	STRETCH("Stretch over the sail");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
