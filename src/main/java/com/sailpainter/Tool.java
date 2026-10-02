package com.sailpainter;

import java.awt.event.KeyEvent;

enum Tool
{
	BRUSH("Brush", KeyEvent.VK_B, "Draw freehand (B). Right-click rubs out"),
	ERASER("Eraser", KeyEvent.VK_E, "Rub out back to the plain sail (E)"),
	FILL("Fill", KeyEvent.VK_F, "Fill an area of one colour (F)"),
	PICKER("Pick", KeyEvent.VK_I, "Pick up a colour from the picture (I)"),
	LINE("Line", KeyEvent.VK_L, "Drag a straight line (L). Hold Shift to keep to 45 degrees"),
	RECTANGLE("Box", KeyEvent.VK_R, "Drag a box (R). Hold Shift for a square"),
	ELLIPSE("Oval", KeyEvent.VK_O, "Drag an oval (O). Hold Shift for a circle");

	final String label;
	final int key;
	final String tooltip;

	Tool(String label, int key, String tooltip)
	{
		this.label = label;
		this.key = key;
		this.tooltip = tooltip;
	}

	/** Tools that drag out a shape, previewed until the mouse is let go. */
	boolean isShape()
	{
		return this == LINE || this == RECTANGLE || this == ELLIPSE;
	}
}
