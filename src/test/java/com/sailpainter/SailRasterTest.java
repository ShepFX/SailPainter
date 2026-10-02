package com.sailpainter;

import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class SailRasterTest
{
	private static final int RED = 0xffff0000;
	private static final int GREEN = 0xff00ff00;
	private static final int BLUE = 0xff0000ff;
	private static final int WHITE = 0xffffffff;

	private final SailRaster raster = new SailRaster();

	@Before
	public void setUp()
	{
		raster.begin(0, 0, 100, 100);
		// Red top left, green top right, blue bottom left, white bottom right.
		raster.setPicture(Design.of(2, 2, new int[]{RED, GREEN, BLUE, WHITE}), 255, 1, false);
	}

	@Test
	public void pictureCoversSquareTheRightWayRound()
	{
		paintSquare(0.01f, 0.01f, 1);
		assertEquals(RED, raster.colorAt(10, 10));
		assertEquals(GREEN, raster.colorAt(90, 10));
		assertEquals(BLUE, raster.colorAt(10, 90));
		assertEquals(WHITE, raster.colorAt(90, 90));
	}

	@Test
	public void nearerSurfaceHidesPicture()
	{
		raster.occlude(0, 0, 0.02f, 50, 0, 0.02f, 0, 50, 0.02f);
		paintSquare(0.01f, 0.01f, 1);
		assertEquals(0, raster.colorAt(10, 10));
		assertEquals(GREEN, raster.colorAt(90, 10));
	}

	@Test
	public void furtherSurfaceDoesNotHidePicture()
	{
		raster.occlude(0, 0, 0.005f, 50, 0, 0.005f, 0, 50, 0.005f);
		paintSquare(0.01f, 0.01f, 1);
		assertEquals(RED, raster.colorAt(10, 10));
	}

	@Test
	public void transparentPartsLeaveTheSailAlone()
	{
		raster.setPicture(Design.of(1, 1, new int[]{0x00ff0000}), 255, 1, false);
		paintSquare(0.01f, 0.01f, 1);
		assertEquals(0, raster.colorAt(50, 50));
	}

	@Test
	public void shadingDarkensByStrength()
	{
		raster.setPicture(Design.of(1, 1, new int[]{0xffc86432}), 255, 1, false);
		paintSquare(0.01f, 0.01f, 0.5f);
		assertEquals(0xff643219, raster.colorAt(50, 50));

		raster.begin(0, 0, 100, 100);
		raster.setPicture(Design.of(1, 1, new int[]{0xffc86432}), 255, 0, false);
		paintSquare(0.01f, 0.01f, 0.5f);
		assertEquals(0xffc86432, raster.colorAt(50, 50));
	}

	@Test
	public void opacityScalesAlpha()
	{
		raster.setPicture(Design.of(1, 1, new int[]{RED}), 128, 1, false);
		paintSquare(0.01f, 0.01f, 1);
		assertEquals(0x80ff0000, raster.colorAt(50, 50));
	}

	@Test
	public void pictureIsSpreadOverDepthNotScreen()
	{
		// The left edge is three times nearer than the right, so the middle of the screen is only a quarter of the way across the cloth.
		raster.setPicture(Design.of(4, 1, new int[]{RED, GREEN, BLUE, WHITE}), 255, 1, false);
		paintSquare(1 / 100f, 1 / 300f, 1);
		assertEquals(GREEN, raster.colorAt(50, 50));
		assertEquals(RED, raster.colorAt(20, 50));
	}

	@Test
	public void clothOutsideThePictureStaysPlain()
	{
		// The picture fills only the left half: u runs from 0 to 2 across the square.
		raster.setPicture(Design.of(1, 1, new int[]{RED}), 255, 1, false);
		raster.paint(0, 0, 0.01f, 0, 0, 1, 100, 0, 0.01f, 2, 0, 1, 0, 100, 0.01f, 0, 1, 1);
		raster.paint(100, 0, 0.01f, 2, 0, 1, 100, 100, 0.01f, 2, 1, 1, 0, 100, 0.01f, 0, 1, 1);
		assertEquals(RED, raster.colorAt(20, 50));
		assertEquals(0, raster.colorAt(80, 50));

		raster.begin(0, 0, 100, 100);
		raster.setPicture(Design.of(1, 1, new int[]{RED}), 255, 1, true);
		raster.paint(0, 0, 0.01f, 0, 0, 1, 100, 0, 0.01f, 2, 0, 1, 0, 100, 0.01f, 0, 1, 1);
		assertEquals(0, raster.colorAt(70, 10));
	}

	@Test
	public void patchOffsetFromScreenCorner()
	{
		raster.begin(40, 40, 20, 20);
		raster.setPicture(Design.of(1, 1, new int[]{RED}), 255, 1, false);
		paintSquare(0.01f, 0.01f, 1);
		assertEquals(RED, raster.colorAt(40, 40));
		assertEquals(RED, raster.colorAt(59, 59));
		assertEquals(20, raster.width());
	}

	/** The whole 100 pixel square, with its left and right edges at the given inverse depths. */
	private void paintSquare(float leftDepth, float rightDepth, float shade)
	{
		raster.paint(0, 0, leftDepth, 0, 0, shade, 100, 0, rightDepth, 1, 0, shade, 0, 100, leftDepth, 0, 1, shade);
		raster.paint(100, 0, rightDepth, 1, 0, shade, 100, 100, rightDepth, 1, 1, shade, 0, 100, leftDepth, 0, 1, shade);
	}
}
