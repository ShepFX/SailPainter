package com.sailpainter;

/**
 * The studio's drawing operations on a grid of ARGB pixels, row by row from the top left. With
 * mirror on, everything is drawn again reflected left to right, for symmetrical designs.
 */
final class PixelOps
{
	private PixelOps()
	{
	}

	/** A round dab of the given diameter centred on a pixel; even sizes centre on its top left corner. */
	static void dab(int[] pixels, int width, int height, int x, int y, int size, int colour, boolean mirror)
	{
		dab(pixels, width, height, x, y, size, colour);
		if (mirror) dab(pixels, width, height, width - 1 - x + (size % 2 == 0 ? 1 : 0), y, size, colour);
	}

	/** Dabs all along a line, so fast strokes leave no gaps. */
	static void line(int[] pixels, int width, int height, int x0, int y0, int x1, int y1, int size, int colour, boolean mirror)
	{
		int dx = Math.abs(x1 - x0);
		int dy = -Math.abs(y1 - y0);
		int stepX = x0 < x1 ? 1 : -1;
		int stepY = y0 < y1 ? 1 : -1;
		int error = dx + dy;
		int x = x0;
		int y = y0;
		while (true)
		{
			dab(pixels, width, height, x, y, size, colour, mirror);
			if (x == x1 && y == y1) return;
			int twice = 2 * error;
			if (twice >= dy)
			{
				error += dy;
				x += stepX;
			}
			if (twice <= dx)
			{
				error += dx;
				y += stepY;
			}
		}
	}

	/** Fills the patch of exactly matching colour around a pixel, edge to edge. */
	static void fill(int[] pixels, int width, int height, int x, int y, int colour, boolean mirror)
	{
		fill(pixels, width, height, x, y, colour);
		if (mirror) fill(pixels, width, height, width - 1 - x, y, colour);
	}

	static void rectangle(int[] pixels, int width, int height, int x0, int y0, int x1, int y1, int size, int colour, boolean filled, boolean mirror)
	{
		shape(pixels, width, height, x0, y0, x1, y1, size, colour, filled, mirror, false);
	}

	static void ellipse(int[] pixels, int width, int height, int x0, int y0, int x1, int y1, int size, int colour, boolean filled, boolean mirror)
	{
		shape(pixels, width, height, x0, y0, x1, y1, size, colour, filled, mirror, true);
	}

	/** Stretches the corner pixels apart so that a drag from one to the other covers both. */
	private static void shape(int[] pixels, int width, int height, int x0, int y0, int x1, int y1, int size, int colour,
		boolean filled, boolean mirror, boolean round)
	{
		shape(pixels, width, height, Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), size, colour, filled, round);
		if (mirror)
		{
			shape(pixels, width, height, width - 1 - Math.max(x0, x1), Math.min(y0, y1), width - 1 - Math.min(x0, x1), Math.max(y0, y1), size, colour, filled, round);
		}
	}

	private static void shape(int[] pixels, int width, int height, int left, int top, int right, int bottom, int size, int colour,
		boolean filled, boolean round)
	{
		int boxWidth = right - left + 1;
		int boxHeight = bottom - top + 1;
		boolean[] inside = new boolean[boxWidth * boxHeight];
		float radiusX = boxWidth / 2f;
		float radiusY = boxHeight / 2f;
		for (int y = 0; y < boxHeight; y++)
		{
			for (int x = 0; x < boxWidth; x++)
			{
				if (!round)
				{
					inside[y * boxWidth + x] = true;
					continue;
				}
				float nx = (x + 0.5f - radiusX) / radiusX;
				float ny = (y + 0.5f - radiusY) / radiusY;
				inside[y * boxWidth + x] = nx * nx + ny * ny <= 1.0001f;
			}
		}
		for (int y = 0; y < boxHeight; y++)
		{
			for (int x = 0; x < boxWidth; x++)
			{
				if (!inside[y * boxWidth + x]) continue;
				boolean edge = x == 0 || y == 0 || x == boxWidth - 1 || y == boxHeight - 1
					|| !inside[y * boxWidth + x - 1] || !inside[y * boxWidth + x + 1]
					|| !inside[(y - 1) * boxWidth + x] || !inside[(y + 1) * boxWidth + x];
				if (filled) set(pixels, width, height, left + x, top + y, colour);
				if (edge) dab(pixels, width, height, left + x, top + y, size, colour);
			}
		}
	}

	private static void dab(int[] pixels, int width, int height, int x, int y, int size, int colour)
	{
		if (size <= 2)
		{
			for (int dy = 0; dy < size; dy++)
			{
				for (int dx = 0; dx < size; dx++) set(pixels, width, height, x - size / 2 + dx, y - size / 2 + dy, colour);
			}
			return;
		}
		// Pixel centres within the circle; the slack rounds off the corners less harshly at small sizes.
		float radius = size / 2f;
		float limit = radius * radius + 0.5f;
		int start = -(size / 2);
		float centre = start + radius;
		for (int dy = start; dy < start + size; dy++)
		{
			for (int dx = start; dx < start + size; dx++)
			{
				float ox = dx + 0.5f - centre;
				float oy = dy + 0.5f - centre;
				if (ox * ox + oy * oy <= limit) set(pixels, width, height, x + dx, y + dy, colour);
			}
		}
	}

	private static void fill(int[] pixels, int width, int height, int x, int y, int colour)
	{
		if (x < 0 || y < 0 || x >= width || y >= height) return;
		int target = pixels[y * width + x];
		if (target == colour) return;
		int[] stack = new int[width * height];
		int top = 0;
		stack[top++] = y * width + x;
		pixels[y * width + x] = colour;
		while (top > 0)
		{
			int index = stack[--top];
			int px = index % width;
			int py = index / width;
			if (px > 0 && pixels[index - 1] == target)
			{
				pixels[index - 1] = colour;
				stack[top++] = index - 1;
			}
			if (px < width - 1 && pixels[index + 1] == target)
			{
				pixels[index + 1] = colour;
				stack[top++] = index + 1;
			}
			if (py > 0 && pixels[index - width] == target)
			{
				pixels[index - width] = colour;
				stack[top++] = index - width;
			}
			if (py < height - 1 && pixels[index + width] == target)
			{
				pixels[index + width] = colour;
				stack[top++] = index + width;
			}
		}
	}

	private static void set(int[] pixels, int width, int height, int x, int y, int colour)
	{
		if (x >= 0 && y >= 0 && x < width && y < height) pixels[y * width + x] = colour;
	}
}
