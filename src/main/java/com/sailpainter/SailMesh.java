package com.sailpainter;

/**
 * One sail model copied out of the client for one frame and worked out ready to paint: which faces
 * are cloth, where each cloth corner falls on the picture, how brightly the game lights it, and
 * where it all lands on screen. It works on plain arrays so that it can be tested without a client.
 *
 * <p>Model space is the game's: x and z across the ground, y pointing down.
 */
final class SailMesh
{
	static final byte HIDDEN = 0;
	static final byte SOLID = 1;
	static final byte CLOTH = 2;

	/** A colour has to cover this share of the main cloth colour's area to count as cloth too. */
	private static final float CLOTH_SHARE = 0.25f;
	/** Faces at least this transparent are glass or haze, and do not hide what is behind them. */
	static final int SEE_THROUGH = 128;
	private static final int MAX_COLOURS = 64;
	private static final float MIN_SHADE = 0.25f;
	private static final float MAX_SHADE = 2f;

	int vertexCount;
	float[] x = new float[0];
	float[] y = new float[0];
	float[] z = new float[0];

	int faceCount;
	int[] a = new int[0];
	int[] b = new int[0];
	int[] c = new int[0];
	byte[] kind = new byte[0];
	int clothFaces;

	/** Where each vertex lands on the picture, from 0 to 1 across and down. Set for cloth vertices only. */
	float[] u = new float[0];
	float[] v = new float[0];
	/** How brightly the game lights each corner of each cloth face, three to a face, against the face's unlit colour. */
	float[] shade = new float[0];

	float[] screenX = new float[0];
	float[] screenY = new float[0];
	/** One over the distance from the camera, or 0 for a vertex too close to put on screen. */
	float[] inverseDepth = new float[0];

	private final int[] colours = new int[MAX_COLOURS];
	private final float[] colourArea = new float[MAX_COLOURS];
	private int[] faceColour = new int[0];
	private float[] faceArea = new float[0];
	private boolean[] inCloth = new boolean[0];

	/**
	 * Copies the model and works out its cloth. The colour arrays are the client's lit colours, where
	 * a third colour of -2 hides a face and -1 shades it flat in the first colour.
	 */
	void build(int vertexCount, float[] vx, float[] vy, float[] vz,
		int faceCount, int[] f1, int[] f2, int[] f3,
		int[] colours1, int[] colours2, int[] colours3,
		short[] unlitColours, short[] textures, byte[] transparencies, boolean wholeModel)
	{
		allocate(vertexCount, faceCount);
		this.vertexCount = vertexCount;
		this.faceCount = faceCount;
		System.arraycopy(vx, 0, x, 0, vertexCount);
		System.arraycopy(vy, 0, y, 0, vertexCount);
		System.arraycopy(vz, 0, z, 0, vertexCount);
		System.arraycopy(f1, 0, a, 0, faceCount);
		System.arraycopy(f2, 0, b, 0, faceCount);
		System.arraycopy(f3, 0, c, 0, faceCount);

		classify(colours1, colours3, unlitColours, textures, transparencies, wholeModel);
		if (clothFaces == 0) return;
		mapPicture();
		shade(colours1, colours2, colours3, unlitColours, textures);
	}

	/** Puts every vertex on screen at a position in its world view. */
	void project(Projector projector, int localX, int localY, int localZ, int orientation)
	{
		projector.project(vertexCount, x, y, z, localX, localY, localZ, orientation, screenX, screenY, inverseDepth);
	}

	/** On screen and facing the camera. The game culls the other side of every face, and so does this. */
	boolean visible(int face)
	{
		int i = a[face], j = b[face], k = c[face];
		if (inverseDepth[i] <= 0 || inverseDepth[j] <= 0 || inverseDepth[k] <= 0) return false;
		return screenArea(face) < 0;
	}

	/** Twice the signed area of a face on screen. Negative when it faces the camera. */
	float screenArea(int face)
	{
		int i = a[face], j = b[face], k = c[face];
		return (screenX[j] - screenX[i]) * (screenY[k] - screenY[i]) - (screenY[j] - screenY[i]) * (screenX[k] - screenX[i]);
	}

	/**
	 * Whether the picture would read back to front as things stand. A face shows the picture the right
	 * way round when its corners go round the same way on the picture as on screen; this asks which
	 * way most of the visible cloth goes, so the whole sail flips at once when the camera crosses it.
	 */
	boolean mirrored()
	{
		float forwards = 0;
		float backwards = 0;
		for (int f = 0; f < faceCount; f++)
		{
			if (kind[f] != CLOTH || !visible(f)) continue;
			int i = a[f], j = b[f], k = c[f];
			float pictureArea = (u[j] - u[i]) * (v[k] - v[i]) - (v[j] - v[i]) * (u[k] - u[i]);
			float area = -screenArea(f);
			// Visible faces have negative screen area, so matching picture area means the right way round.
			if (pictureArea < 0) forwards += area;
			else if (pictureArea > 0) backwards += area;
		}
		return backwards > forwards;
	}

	/**
	 * The screen area the visible cloth covers as {min x, min y, max x, max y} merged into the given
	 * bounds, or false if no cloth is visible.
	 */
	boolean addClothBounds(float[] bounds)
	{
		boolean any = false;
		for (int f = 0; f < faceCount; f++)
		{
			if (kind[f] != CLOTH || !visible(f)) continue;
			any = true;
			include(bounds, a[f]);
			include(bounds, b[f]);
			include(bounds, c[f]);
		}
		return any;
	}

	/** Puts every face that is not cloth into the depth buffer, so ropes and spars can cover the picture. */
	void occlude(SailRaster raster)
	{
		for (int f = 0; f < faceCount; f++)
		{
			if (kind[f] != SOLID || !visible(f)) continue;
			int i = a[f], j = b[f], k = c[f];
			raster.occlude(screenX[i], screenY[i], inverseDepth[i], screenX[j], screenY[j], inverseDepth[j], screenX[k], screenY[k], inverseDepth[k]);
		}
	}

	/** Paints the picture over the visible cloth. The depth buffer sorts out folds that overlap. */
	void paint(SailRaster raster, boolean flip)
	{
		float scale = shadeScale();
		for (int f = 0; f < faceCount; f++)
		{
			if (kind[f] != CLOTH || !visible(f)) continue;
			int i = a[f], j = b[f], k = c[f];
			raster.paint(
				screenX[i], screenY[i], inverseDepth[i], flip ? 1 - u[i] : u[i], v[i], lit(f * 3, scale),
				screenX[j], screenY[j], inverseDepth[j], flip ? 1 - u[j] : u[j], v[j], lit(f * 3 + 1, scale),
				screenX[k], screenY[k], inverseDepth[k], flip ? 1 - u[k] : u[k], v[k], lit(f * 3 + 2, scale));
		}
	}

	/**
	 * What the shading is divided by so that the cloth in view averages 1. Taken over the side being
	 * looked at rather than the whole sail, so the picture keeps its own colours from either side
	 * instead of washing out on the sunny one and going murky on the other.
	 */
	float shadeScale()
	{
		double total = 0;
		double area = 0;
		for (int f = 0; f < faceCount; f++)
		{
			if (kind[f] != CLOTH || !visible(f)) continue;
			total += (shade[f * 3] + shade[f * 3 + 1] + shade[f * 3 + 2]) / 3 * faceArea[f];
			area += faceArea[f];
		}
		return area > 0 && total > 0 ? (float) (area / total) : 1;
	}

	private float lit(int corner, float scale)
	{
		return Math.max(MIN_SHADE, Math.min(MAX_SHADE, shade[corner] * scale));
	}

	private void include(float[] bounds, int vertex)
	{
		bounds[0] = Math.min(bounds[0], screenX[vertex]);
		bounds[1] = Math.min(bounds[1], screenY[vertex]);
		bounds[2] = Math.max(bounds[2], screenX[vertex]);
		bounds[3] = Math.max(bounds[3], screenY[vertex]);
	}

	/**
	 * Sorts faces into hidden, solid and cloth. The cloth is the colour (or texture) covering the most
	 * area, plus any other colour covering a good share of that, which leaves out ropes, trim and spars.
	 */
	private void classify(int[] colours1, int[] colours3, short[] unlitColours, short[] textures, byte[] transparencies, boolean wholeModel)
	{
		int colourCount = 0;
		for (int f = 0; f < faceCount; f++)
		{
			faceColour[f] = -1;
			boolean seeThrough = transparencies != null && (transparencies[f] & 0xff) >= SEE_THROUGH;
			if (colours3[f] == -2 || seeThrough)
			{
				kind[f] = HIDDEN;
				continue;
			}
			kind[f] = SOLID;
			faceArea[f] = modelArea(f);

			int key;
			if (textures != null && textures[f] != -1) key = 0x10000 | (textures[f] & 0xffff);
			else if (unlitColours != null) key = unlitColours[f] & 0xffff;
			// Lit colours keep the hue and saturation of the unlit one, and only the lightness varies.
			else key = 0x20000 | ((colours1[f] >> 7) & 0x1ff);

			int index = 0;
			while (index < colourCount && colours[index] != key) index++;
			if (index == colourCount)
			{
				if (colourCount == MAX_COLOURS) continue;
				colours[colourCount] = key;
				colourArea[colourCount] = 0;
				colourCount++;
			}
			faceColour[f] = index;
			colourArea[index] += faceArea[f];
		}

		float largest = 0;
		for (int i = 0; i < colourCount; i++) largest = Math.max(largest, colourArea[i]);

		clothFaces = 0;
		if (largest <= 0) return;
		for (int f = 0; f < faceCount; f++)
		{
			if (kind[f] == HIDDEN) continue;
			int colour = faceColour[f];
			boolean cloth = wholeModel || (colour >= 0 && colourArea[colour] >= largest * CLOTH_SHARE);
			if (cloth)
			{
				kind[f] = CLOTH;
				clothFaces++;
			}
		}
	}

	/**
	 * Lays the picture flat across the cloth: down is the model's own down, and across is the way the
	 * cloth stretches furthest over the ground, so a sail turned to the wind keeps its picture.
	 */
	private void mapPicture()
	{
		for (int i = 0; i < vertexCount; i++) inCloth[i] = false;
		for (int f = 0; f < faceCount; f++)
		{
			if (kind[f] != CLOTH) continue;
			inCloth[a[f]] = true;
			inCloth[b[f]] = true;
			inCloth[c[f]] = true;
		}

		double meanX = 0;
		double meanZ = 0;
		int count = 0;
		for (int i = 0; i < vertexCount; i++)
		{
			if (!inCloth[i]) continue;
			meanX += x[i];
			meanZ += z[i];
			count++;
		}
		meanX /= count;
		meanZ /= count;

		double xx = 0;
		double xz = 0;
		double zz = 0;
		for (int i = 0; i < vertexCount; i++)
		{
			if (!inCloth[i]) continue;
			double dx = x[i] - meanX;
			double dz = z[i] - meanZ;
			xx += dx * dx;
			xz += dx * dz;
			zz += dz * dz;
		}
		// The main axis of the cloth's spread over the ground.
		double angle = 0.5 * Math.atan2(2 * xz, xx - zz);
		float acrossX = (float) Math.cos(angle);
		float acrossZ = (float) Math.sin(angle);
		// Pointed the same way each frame, so the picture does not flip as the sail swings.
		if (acrossX < -1e-6f || (acrossX <= 1e-6f && acrossZ < 0))
		{
			acrossX = -acrossX;
			acrossZ = -acrossZ;
		}

		float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE;
		float minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
		for (int i = 0; i < vertexCount; i++)
		{
			if (!inCloth[i]) continue;
			u[i] = (float) ((x[i] - meanX) * acrossX + (z[i] - meanZ) * acrossZ);
			v[i] = y[i];
			minU = Math.min(minU, u[i]);
			maxU = Math.max(maxU, u[i]);
			minV = Math.min(minV, v[i]);
			maxV = Math.max(maxV, v[i]);
		}
		float width = maxU - minU;
		float height = maxV - minV;
		for (int i = 0; i < vertexCount; i++)
		{
			if (!inCloth[i]) continue;
			u[i] = width > 0 ? (u[i] - minU) / width : 0.5f;
			v[i] = height > 0 ? (v[i] - minV) / height : 0.5f;
		}
	}

	/**
	 * Reads the game's lighting back out of the lit colours: each corner's lightness against the
	 * face's unlit lightness. {@link #shadeScale()} later evens it out for the side in view.
	 */
	private void shade(int[] colours1, int[] colours2, int[] colours3, short[] unlitColours, short[] textures)
	{
		for (int f = 0; f < faceCount; f++)
		{
			if (kind[f] != CLOTH) continue;
			int first = colours1[f];
			int second = colours2[f];
			int third = colours3[f];
			if (third == -1)
			{
				second = first;
				third = first;
			}
			boolean textured = textures != null && textures[f] != -1;
			// Textured faces keep only a lightness, around the middle of the range.
			float base = textured ? 64 : unlitColours != null ? Math.max(8, unlitColours[f] & 0x7f) : 64;
			shade[f * 3] = (first & 0x7f) / base;
			shade[f * 3 + 1] = (second & 0x7f) / base;
			shade[f * 3 + 2] = (third & 0x7f) / base;
		}
	}

	private float modelArea(int face)
	{
		int i = a[face], j = b[face], k = c[face];
		float ux = x[j] - x[i], uy = y[j] - y[i], uz = z[j] - z[i];
		float wx = x[k] - x[i], wy = y[k] - y[i], wz = z[k] - z[i];
		float nx = uy * wz - uz * wy;
		float ny = uz * wx - ux * wz;
		float nz = ux * wy - uy * wx;
		return (float) Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
	}

	private void allocate(int vertices, int faces)
	{
		if (x.length < vertices)
		{
			int size = Math.max(vertices, x.length * 2);
			x = new float[size];
			y = new float[size];
			z = new float[size];
			u = new float[size];
			v = new float[size];
			screenX = new float[size];
			screenY = new float[size];
			inverseDepth = new float[size];
			inCloth = new boolean[size];
		}
		if (a.length < faces)
		{
			int size = Math.max(faces, a.length * 2);
			a = new int[size];
			b = new int[size];
			c = new int[size];
			kind = new byte[size];
			shade = new float[size * 3];
			faceColour = new int[size];
			faceArea = new float[size];
		}
	}
}
