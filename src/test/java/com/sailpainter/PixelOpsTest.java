package com.sailpainter;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PixelOpsTest
{
	private static final int SIZE = 16;
	private static final int INK = 0xff112233;

	private final int[] pixels = new int[SIZE * SIZE];

	@Test
	public void smallestDabIsOnePixel()
	{
		PixelOps.dab(pixels, SIZE, SIZE, 5, 6, 1, INK, false);
		assertEquals(1, count());
		assertEquals(INK, at(5, 6));
	}

	@Test
	public void roundDabIsCentredAndRound()
	{
		PixelOps.dab(pixels, SIZE, SIZE, 8, 8, 5, INK, false);
		assertEquals(INK, at(8, 8));
		assertEquals(INK, at(6, 8));
		assertEquals(INK, at(10, 8));
		assertEquals(0, at(6, 6));
		assertEquals(0, at(11, 8));
	}

	@Test
	public void dabOffTheEdgeIsClipped()
	{
		PixelOps.dab(pixels, SIZE, SIZE, 0, 0, 3, INK, false);
		assertEquals(4, count());
	}

	@Test
	public void mirrorDrawsBothSides()
	{
		PixelOps.dab(pixels, SIZE, SIZE, 2, 3, 1, INK, true);
		assertEquals(INK, at(2, 3));
		assertEquals(INK, at(SIZE - 3, 3));
		assertEquals(2, count());
	}

	@Test
	public void mirroredEvenDabIsSymmetrical()
	{
		PixelOps.dab(pixels, SIZE, SIZE, 3, 3, 2, INK, true);
		for (int y = 0; y < SIZE; y++)
		{
			for (int x = 0; x < SIZE; x++) assertEquals(at(x, y), at(SIZE - 1 - x, y));
		}
	}

	@Test
	public void lineHasNoGaps()
	{
		PixelOps.line(pixels, SIZE, SIZE, 1, 1, 14, 6, 1, INK, false);
		assertEquals(14, count());
		assertEquals(INK, at(1, 1));
		assertEquals(INK, at(14, 6));
	}

	@Test
	public void fillStopsAtOutlines()
	{
		PixelOps.rectangle(pixels, SIZE, SIZE, 2, 2, 8, 8, 1, INK, false, false);
		PixelOps.fill(pixels, SIZE, SIZE, 5, 5, 0xffff0000, false);
		assertEquals(0xffff0000, at(5, 5));
		assertEquals(INK, at(2, 5));
		assertEquals(0, at(12, 12));
		assertEquals(5 * 5, countOf(0xffff0000));
	}

	@Test
	public void fillOfSameColourDoesNothing()
	{
		PixelOps.fill(pixels, SIZE, SIZE, 0, 0, 0, false);
		assertEquals(0, count());
	}

	@Test
	public void filledRectangleCoversCorners()
	{
		PixelOps.rectangle(pixels, SIZE, SIZE, 9, 9, 3, 3, 1, INK, true, false);
		assertEquals(7 * 7, count());
	}

	@Test
	public void ellipseOutlineLeavesMiddleEmpty()
	{
		PixelOps.ellipse(pixels, SIZE, SIZE, 0, 0, 15, 15, 1, INK, false, false);
		assertEquals(0, at(8, 8));
		assertEquals(INK, at(8, 0));
		assertEquals(0, at(0, 0));
	}

	private int at(int x, int y)
	{
		return pixels[y * SIZE + x];
	}

	private int count()
	{
		int count = 0;
		for (int pixel : pixels)
		{
			if (pixel != 0) count++;
		}
		return count;
	}

	private int countOf(int colour)
	{
		int count = 0;
		for (int pixel : pixels)
		{
			if (pixel == colour) count++;
		}
		return count;
	}
}
