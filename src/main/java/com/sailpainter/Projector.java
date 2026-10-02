package com.sailpainter;

/** Places model vertices on screen the way the client does. */
interface Projector
{
	/**
	 * Fills in where each model-space vertex lands on the canvas, and one over its distance from the
	 * camera, or 0 for a vertex too close to the camera to place.
	 *
	 * @param orientation the model's turn, in the game's 2048ths of a circle
	 */
	void project(int count, float[] x, float[] y, float[] z, int localX, int localY, int localZ, int orientation,
		float[] screenX, float[] screenY, float[] inverseDepth);
}
