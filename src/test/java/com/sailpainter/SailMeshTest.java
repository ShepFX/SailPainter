package com.sailpainter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SailMeshTest
{
	private static final int CLOTH_COLOUR = (10 << 10) | (3 << 7) | 90;
	private static final int ROPE_COLOUR = (5 << 10) | (2 << 7) | 40;

	/**
	 * A flat sail 200 wide and 300 tall standing in the x-y plane, as two layers facing opposite ways,
	 * with a thin rope hanging below it.
	 */
	private static final float[] X = {-100, 100, -100, 100, -2, 2, -2};
	private static final float[] Y = {-300, -300, 0, 0, 0, 0, 100};
	private static final float[] Z = {0, 0, 0, 0, 0, 0, 0};
	// Faces 0 and 1 face one way, 2 and 3 the other, 4 is the rope.
	private static final int[] F1 = {0, 1, 0, 1, 4};
	private static final int[] F2 = {2, 2, 1, 3, 6};
	private static final int[] F3 = {1, 3, 2, 2, 5};

	@Test
	public void ropeIsNotCloth()
	{
		SailMesh mesh = build(false);
		assertEquals(4, mesh.clothFaces);
		assertEquals(SailMesh.SOLID, mesh.kind[4]);
		assertEquals(SailMesh.CLOTH, mesh.kind[0]);
	}

	@Test
	public void wholeModelPaintsTheRopeToo()
	{
		SailMesh mesh = build(true);
		assertEquals(5, mesh.clothFaces);
	}

	@Test
	public void hiddenFacesAreLeftOut()
	{
		SailMesh mesh = new SailMesh();
		int[] colours = {CLOTH_COLOUR, CLOTH_COLOUR, CLOTH_COLOUR, CLOTH_COLOUR, ROPE_COLOUR};
		int[] third = {-2, -1, -1, -1, -1};
		mesh.build(7, X, Y, Z, 5, F1, F2, F3, colours, colours, third, unlit(), null, null, false);
		assertEquals(SailMesh.HIDDEN, mesh.kind[0]);
		assertEquals(3, mesh.clothFaces);
	}

	@Test
	public void pictureRunsAcrossAndDownTheCloth()
	{
		SailMesh mesh = build(false);
		// Top left, top right, bottom left, bottom right of the cloth.
		assertEquals(0, mesh.u[0], 1e-4);
		assertEquals(0, mesh.v[0], 1e-4);
		assertEquals(1, mesh.u[1], 1e-4);
		assertEquals(0, mesh.v[1], 1e-4);
		assertEquals(0, mesh.u[2], 1e-4);
		assertEquals(1, mesh.v[2], 1e-4);
		assertEquals(1, mesh.u[3], 1e-4);
		assertEquals(1, mesh.v[3], 1e-4);
	}

	@Test
	public void sailTurnedSidewaysStillMapsAcross()
	{
		SailMesh mesh = new SailMesh();
		// The same sail stretched along z instead of x.
		int[] colours = colours();
		mesh.build(7, Z, Y, X, 5, F1, F2, F3, colours, colours, minusOnes(), unlit(), null, null, false);
		assertEquals(0, mesh.u[0], 1e-4);
		assertEquals(1, mesh.u[1], 1e-4);
		assertEquals(0, mesh.v[1], 1e-4);
		assertEquals(1, mesh.v[3], 1e-4);
	}

	@Test
	public void shadingComesFromLitAgainstUnlit()
	{
		SailMesh mesh = shadedInShadowOnFront();
		assertEquals(0.5f, mesh.shade[0], 1e-4);
		assertEquals(1f, mesh.shade[6], 1e-4);
	}

	@Test
	public void shadingAveragesOneOnTheSideInView()
	{
		SailMesh mesh = shadedInShadowOnFront();
		mesh.project(front(), 0, 0, 0, 0);
		assertEquals(2f, mesh.shadeScale(), 1e-4);
		mesh.project(behind(), 0, 0, 0, 0);
		assertEquals(1f, mesh.shadeScale(), 1e-4);
	}

	/** The faces towards the front are half as bright as their colour; the back ones are lit fully. */
	private static SailMesh shadedInShadowOnFront()
	{
		SailMesh mesh = new SailMesh();
		int dark = (CLOTH_COLOUR & ~0x7f) | 45;
		int light = (CLOTH_COLOUR & ~0x7f) | 90;
		int[] first = {dark, dark, light, light, ROPE_COLOUR};
		mesh.build(7, X, Y, Z, 5, F1, F2, F3, first, first, minusOnes(), unlit(), null, null, false);
		return mesh;
	}

	@Test
	public void squareSailFitsThePictureWhole()
	{
		SailMesh mesh = build(false);
		mesh.fitInside(new float[4]);
		assertEquals(0, mesh.fitLeft, 1e-4);
		assertEquals(0, mesh.fitTop, 1e-4);
		assertEquals(1, mesh.fitRight, 1e-4);
		assertEquals(1, mesh.fitBottom, 1e-4);
	}

	@Test
	public void triangularSailFitsTheBiggestBoxInside()
	{
		SailMesh mesh = triangle();
		mesh.fitInside(new float[4]);
		// The cloth is the lower left half, so the biggest box is the quarter in its square corner.
		assertEquals(0, mesh.fitLeft, 0.05);
		assertEquals(0.5, mesh.fitTop, 0.05);
		assertEquals(0.5, mesh.fitRight, 0.05);
		assertEquals(1, mesh.fitBottom, 0.05);
	}

	@Test
	public void fittedBoxHoldsStillForSmallChanges()
	{
		SailMesh mesh = triangle();
		float[] previous = {0.01f, 0.49f, 0.51f, 1};
		mesh.fitInside(previous);
		assertEquals(0.51f, mesh.fitRight, 1e-6);
		assertEquals(0.51f, previous[2], 1e-6);

		float[] stale = {0.3f, 0.1f, 0.9f, 0.5f};
		mesh.fitInside(stale);
		assertEquals(0.5, stale[2], 0.05);
	}

	@Test
	public void stretchCoversEverything()
	{
		SailMesh mesh = triangle();
		mesh.fitInside(new float[4]);
		mesh.stretch();
		assertEquals(0, mesh.fitTop, 0);
		assertEquals(1, mesh.fitRight, 0);
	}

	/** A right-angled sail filling the lower left half of its box, as two layers. */
	private static SailMesh triangle()
	{
		SailMesh mesh = new SailMesh();
		float[] x = {-100, 100, -100};
		float[] y = {-300, 0, 0};
		float[] z = {0, 0, 0};
		int[] colours = {CLOTH_COLOUR, CLOTH_COLOUR};
		short[] unlit = {(short) CLOTH_COLOUR, (short) CLOTH_COLOUR};
		mesh.build(3, x, y, z, 2, new int[]{0, 0}, new int[]{2, 1}, new int[]{1, 2}, colours, colours, new int[]{-1, -1}, unlit, null, null, false);
		return mesh;
	}

	@Test
	public void onlyFacesTowardsTheCameraAreVisible()
	{
		SailMesh mesh = build(false);
		mesh.project(front(), 0, 0, 0, 0);
		assertTrue(mesh.visible(0));
		assertTrue(mesh.visible(1));
		assertFalse(mesh.visible(2));
		assertFalse(mesh.visible(3));
	}

	@Test
	public void pictureReadsBackwardsFromBehind()
	{
		SailMesh mesh = build(false);
		mesh.project(front(), 0, 0, 0, 0);
		assertFalse(mesh.mirrored());

		mesh.project(behind(), 0, 0, 0, 0);
		assertTrue(mesh.visible(2));
		assertTrue(mesh.mirrored());
	}

	@Test
	public void clothBoundsCoverVisibleCloth()
	{
		SailMesh mesh = build(false);
		mesh.project(front(), 0, 0, 0, 0);
		float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		assertTrue(mesh.addClothBounds(bounds));
		assertEquals(400, bounds[0], 1e-3);
		assertEquals(100, bounds[1], 1e-3);
		assertEquals(600, bounds[2], 1e-3);
		assertEquals(400, bounds[3], 1e-3);
	}

	@Test
	public void vertexBehindCameraHidesItsFaces()
	{
		SailMesh mesh = build(false);
		mesh.project((count, x, y, z, lx, ly, lz, orientation, sx, sy, depth) ->
		{
			front().project(count, x, y, z, lx, ly, lz, orientation, sx, sy, depth);
			depth[0] = 0;
		}, 0, 0, 0, 0);
		assertFalse(mesh.visible(0));
		assertTrue(mesh.visible(1));
	}

	private static SailMesh build(boolean wholeModel)
	{
		SailMesh mesh = new SailMesh();
		int[] colours = colours();
		mesh.build(7, X, Y, Z, 5, F1, F2, F3, colours, colours, minusOnes(), unlit(), null, null, wholeModel);
		return mesh;
	}

	/** Looking straight at the sail, so model x is screen x and model y is screen y. */
	private static Projector front()
	{
		return (count, x, y, z, lx, ly, lz, orientation, sx, sy, depth) ->
		{
			for (int i = 0; i < count; i++)
			{
				sx[i] = 500 + x[i];
				sy[i] = 400 + y[i];
				depth[i] = 0.001f;
			}
		};
	}

	/** From the other side, where model x runs right to left on screen. */
	private static Projector behind()
	{
		return (count, x, y, z, lx, ly, lz, orientation, sx, sy, depth) ->
		{
			for (int i = 0; i < count; i++)
			{
				sx[i] = 500 - x[i];
				sy[i] = 400 + y[i];
				depth[i] = 0.001f;
			}
		};
	}

	private static int[] colours()
	{
		return new int[]{CLOTH_COLOUR, CLOTH_COLOUR, CLOTH_COLOUR, CLOTH_COLOUR, ROPE_COLOUR};
	}

	private static int[] minusOnes()
	{
		return new int[]{-1, -1, -1, -1, -1};
	}

	private static short[] unlit()
	{
		return new short[]{(short) CLOTH_COLOUR, (short) CLOTH_COLOUR, (short) CLOTH_COLOUR, (short) CLOTH_COLOUR, (short) ROPE_COLOUR};
	}
}
