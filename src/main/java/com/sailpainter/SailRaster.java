package com.sailpainter;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;

/**
 * A small software renderer for one patch of the screen. Things in front of the sail go into the
 * depth buffer first; then the picture is painted over the cloth wherever nothing nearer is in the
 * way, with perspective-correct mapping so it does not swim across large faces.
 *
 * <p>Depth is stored as one over the distance, which is linear across a face on screen. Bigger is nearer.
 */
final class SailRaster
{
	/** The picture may sit this much further away than another surface and still show, so it does not flicker against ropes lying on the cloth. */
	private static final float CLOTH_BIAS = 1.003f;
	/** Lets pixel centres exactly on a shared edge land in at least one face, so seams have no pinholes. */
	private static final float EDGE = -0.0001f;

	private int stride;
	private int capacityHeight;
	private int[] color = new int[0];
	private float[] depth = new float[0];
	private BufferedImage image;

	private int originX;
	private int originY;
	private int width;
	private int height;

	private int[] picture;
	private int pictureWidth;
	private int pictureHeight;
	private int opacity = 255;
	private float shading;
	private boolean smooth;

	/** Clears the patch of screen to draw into, from its top left corner. */
	void begin(int x, int y, int width, int height)
	{
		if (width > stride || height > capacityHeight)
		{
			stride = Math.max(width, stride);
			capacityHeight = Math.max(height, capacityHeight);
			color = new int[stride * capacityHeight];
			depth = new float[stride * capacityHeight];
			image = null;
		}
		originX = x;
		originY = y;
		this.width = width;
		this.height = height;
		for (int row = 0; row < height; row++)
		{
			int start = row * stride;
			Arrays.fill(color, start, start + width, 0);
			Arrays.fill(depth, start, start + width, 0);
		}
	}

	void setPicture(Design design, int opacity, float shading, boolean smooth)
	{
		this.picture = design.pixels();
		this.pictureWidth = design.width();
		this.pictureHeight = design.height();
		this.opacity = opacity;
		this.shading = shading;
		this.smooth = smooth;
	}

	int originX()
	{
		return originX;
	}

	int originY()
	{
		return originY;
	}

	int width()
	{
		return width;
	}

	int height()
	{
		return height;
	}

	/** The painted pixels; only the top left {@link #width()} by {@link #height()} is this frame's. */
	BufferedImage image()
	{
		if (image == null)
		{
			DataBufferInt buffer = new DataBufferInt(color, color.length);
			WritableRaster raster = Raster.createPackedRaster(buffer, stride, capacityHeight, stride,
				new int[]{0xff0000, 0xff00, 0xff, 0xff000000}, null);
			image = new BufferedImage(ColorModel.getRGBdefault(), raster, false, null);
		}
		return image;
	}

	/** The painted colour at a screen position, for tests. */
	int colorAt(int x, int y)
	{
		return color[(y - originY) * stride + (x - originX)];
	}

	/** Records a surface in front of the sail without painting anything. */
	void occlude(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2)
	{
		triangle(x0, y0, z0, 0, 0, 0, x1, y1, z1, 0, 0, 0, x2, y2, z2, 0, 0, 0, false);
	}

	/** Paints the picture over a cloth face, where u and v place each corner on the picture and s is how lit it is. */
	void paint(float x0, float y0, float z0, float u0, float v0, float s0,
		float x1, float y1, float z1, float u1, float v1, float s1,
		float x2, float y2, float z2, float u2, float v2, float s2)
	{
		triangle(x0, y0, z0, u0, v0, s0, x1, y1, z1, u1, v1, s1, x2, y2, z2, u2, v2, s2, true);
	}

	private void triangle(float x0, float y0, float z0, float u0, float v0, float s0,
		float x1, float y1, float z1, float u1, float v1, float s1,
		float x2, float y2, float z2, float u2, float v2, float s2, boolean paint)
	{
		float area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
		if (area == 0 || Float.isNaN(area) || Float.isInfinite(area)) return;

		int minX = Math.max(originX, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
		int maxX = Math.min(originX + width - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
		int minY = Math.max(originY, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
		int maxY = Math.min(originY + height - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
		if (minX > maxX || minY > maxY) return;

		// Barycentric weights of each corner at the first pixel centre, and how they change per pixel.
		float inverseArea = 1 / area;
		float px = minX + 0.5f;
		float py = minY + 0.5f;
		float w0Row = ((x2 - x1) * (py - y1) - (y2 - y1) * (px - x1)) * inverseArea;
		float w1Row = ((x0 - x2) * (py - y2) - (y0 - y2) * (px - x2)) * inverseArea;
		float w2Row = ((x1 - x0) * (py - y0) - (y1 - y0) * (px - x0)) * inverseArea;
		float w0dx = -(y2 - y1) * inverseArea;
		float w1dx = -(y0 - y2) * inverseArea;
		float w2dx = -(y1 - y0) * inverseArea;
		float w0dy = (x2 - x1) * inverseArea;
		float w1dy = (x0 - x2) * inverseArea;
		float w2dy = (x1 - x0) * inverseArea;

		// u and v are spread evenly over the sail, not the screen, so they are carried over depth.
		float u0z = u0 * z0, u1z = u1 * z1, u2z = u2 * z2;
		float v0z = v0 * z0, v1z = v1 * z1, v2z = v2 * z2;

		for (int y = minY; y <= maxY; y++)
		{
			float w0 = w0Row;
			float w1 = w1Row;
			float w2 = w2Row;
			int index = (y - originY) * stride + (minX - originX);
			for (int x = minX; x <= maxX; x++, index++, w0 += w0dx, w1 += w1dx, w2 += w2dx)
			{
				if (w0 < EDGE || w1 < EDGE || w2 < EDGE) continue;
				float inverseDepth = w0 * z0 + w1 * z1 + w2 * z2;
				if (!paint)
				{
					if (inverseDepth > depth[index]) depth[index] = inverseDepth;
					continue;
				}
				if (inverseDepth * CLOTH_BIAS < depth[index]) continue;
				depth[index] = inverseDepth;
				float u = (w0 * u0z + w1 * u1z + w2 * u2z) / inverseDepth;
				float v = (w0 * v0z + w1 * v1z + w2 * v2z) / inverseDepth;
				// Transparent parts of the picture still claim the pixel, so cloth further back cannot show through them.
				color[index] = light(smooth ? blended(u, v) : nearest(u, v), w0 * s0 + w1 * s1 + w2 * s2);
			}
			w0Row += w0dy;
			w1Row += w1dy;
			w2Row += w2dy;
		}
	}

	private int light(int argb, float shade)
	{
		int alpha = argb >>> 24;
		if (alpha == 0) return 0;
		alpha = alpha * opacity / 255;
		float brightness = 1 + (shade - 1) * shading;
		int red = channel(((argb >> 16) & 0xff) * brightness);
		int green = channel(((argb >> 8) & 0xff) * brightness);
		int blue = channel((argb & 0xff) * brightness);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int channel(float value)
	{
		return value >= 255 ? 255 : value <= 0 ? 0 : (int) (value + 0.5f);
	}

	private int nearest(float u, float v)
	{
		int x = clamp((int) (u * pictureWidth), pictureWidth);
		int y = clamp((int) (v * pictureHeight), pictureHeight);
		return picture[y * pictureWidth + x];
	}

	/** Blends the four nearest picture pixels, weighting colour by alpha so edges do not darken. */
	private int blended(float u, float v)
	{
		float fx = u * pictureWidth - 0.5f;
		float fy = v * pictureHeight - 0.5f;
		int left = (int) Math.floor(fx);
		int top = (int) Math.floor(fy);
		float tx = fx - left;
		float ty = fy - top;
		int x0 = clamp(left, pictureWidth), x1 = clamp(left + 1, pictureWidth);
		int y0 = clamp(top, pictureHeight), y1 = clamp(top + 1, pictureHeight);

		int topLeft = picture[y0 * pictureWidth + x0];
		int topRight = picture[y0 * pictureWidth + x1];
		int bottomLeft = picture[y1 * pictureWidth + x0];
		int bottomRight = picture[y1 * pictureWidth + x1];
		float wTopLeft = (1 - tx) * (1 - ty) * (topLeft >>> 24);
		float wTopRight = tx * (1 - ty) * (topRight >>> 24);
		float wBottomLeft = (1 - tx) * ty * (bottomLeft >>> 24);
		float wBottomRight = tx * ty * (bottomRight >>> 24);
		float alpha = wTopLeft + wTopRight + wBottomLeft + wBottomRight;
		if (alpha < 0.5f) return 0;
		float red = wTopLeft * ((topLeft >> 16) & 0xff) + wTopRight * ((topRight >> 16) & 0xff)
			+ wBottomLeft * ((bottomLeft >> 16) & 0xff) + wBottomRight * ((bottomRight >> 16) & 0xff);
		float green = wTopLeft * ((topLeft >> 8) & 0xff) + wTopRight * ((topRight >> 8) & 0xff)
			+ wBottomLeft * ((bottomLeft >> 8) & 0xff) + wBottomRight * ((bottomRight >> 8) & 0xff);
		float blue = wTopLeft * (topLeft & 0xff) + wTopRight * (topRight & 0xff)
			+ wBottomLeft * (bottomLeft & 0xff) + wBottomRight * (bottomRight & 0xff);
		return channel(alpha) << 24 | channel(red / alpha) << 16 | channel(green / alpha) << 8 | channel(blue / alpha);
	}

	private static int clamp(int value, int size)
	{
		return value < 0 ? 0 : value >= size ? size - 1 : value;
	}
}
