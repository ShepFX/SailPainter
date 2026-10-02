package com.sailpainter;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * A picture for the sail: ARGB pixels row by row from the top left. Immutable once made, so the
 * studio can hand a fresh one to the client thread while the user carries on drawing.
 */
final class Design
{
	static final int MAX_SIZE = 512;

	private final int width;
	private final int height;
	private final int[] pixels;
	private final boolean blank;

	private Design(int width, int height, int[] pixels)
	{
		this.width = width;
		this.height = height;
		this.pixels = pixels;
		this.blank = isBlank(pixels);
	}

	/** A design holding a copy of the given pixels. */
	static Design of(int width, int height, int[] pixels)
	{
		if (width < 1 || height < 1 || width > MAX_SIZE || height > MAX_SIZE)
		{
			throw new IllegalArgumentException("Design size " + width + "x" + height);
		}
		if (pixels.length < width * height) throw new IllegalArgumentException("Too few pixels");
		int[] copy = new int[width * height];
		System.arraycopy(pixels, 0, copy, 0, copy.length);
		return new Design(width, height, copy);
	}

	static Design blank(int width, int height)
	{
		return of(width, height, new int[width * height]);
	}

	/** The image at its own size, or shrunk to fit a square of the given size if it is bigger. */
	static Design fromImage(BufferedImage image, int maxSize)
	{
		maxSize = Math.min(maxSize, MAX_SIZE);
		int width = image.getWidth();
		int height = image.getHeight();
		if (width > maxSize || height > maxSize)
		{
			double scale = Math.min((double) maxSize / width, (double) maxSize / height);
			width = Math.max(1, (int) Math.round(width * scale));
			height = Math.max(1, (int) Math.round(height * scale));
		}
		return fromImage(image, width, height);
	}

	/** The image stretched to exactly the given size, smoothly, since it is usually a photo. */
	static Design fromImage(BufferedImage image, int width, int height)
	{
		// Copied straight across when no scaling is needed, so saving and loading never shifts a colour.
		if (image.getWidth() == width && image.getHeight() == height)
		{
			return new Design(width, height, image.getRGB(0, 0, width, height, null, 0, width));
		}
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(image, 0, 0, width, height, null);
		}
		finally
		{
			g.dispose();
		}
		return new Design(width, height, scaled.getRGB(0, 0, width, height, null, 0, width));
	}

	int width()
	{
		return width;
	}

	int height()
	{
		return height;
	}

	/** The pixels themselves, for reading only. */
	int[] pixels()
	{
		return pixels;
	}

	int pixel(int x, int y)
	{
		return pixels[y * width + x];
	}

	/** Nothing drawn at all, so there is nothing to put on the sail. */
	boolean isBlank()
	{
		return blank;
	}

	int[] copyPixels()
	{
		return pixels.clone();
	}

	/** Every pixel scaled to a new size without blending, so pixel art stays crisp. */
	Design resized(int newWidth, int newHeight)
	{
		int[] out = new int[newWidth * newHeight];
		for (int y = 0; y < newHeight; y++)
		{
			int sourceY = y * height / newHeight;
			for (int x = 0; x < newWidth; x++)
			{
				out[y * newWidth + x] = pixels[sourceY * width + x * width / newWidth];
			}
		}
		return of(newWidth, newHeight, out);
	}

	BufferedImage toImage()
	{
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, width, height, pixels, 0, width);
		return image;
	}

	private static boolean isBlank(int[] pixels)
	{
		for (int pixel : pixels)
		{
			if ((pixel >>> 24) != 0) return false;
		}
		return true;
	}
}
