package com.sailpainter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;

/** The picture being drawn, blown up to fill the space, with whatever tool is chosen. */
class StudioCanvas extends JComponent
{
	private static final int MARGIN = 12;
	/** Grid lines appear once a picture pixel is at least this many screen pixels wide. */
	private static final int GRID_FROM = 6;
	private static final int CHECKER = 8;
	private static final Color CHECKER_LIGHT = new Color(0x55, 0x55, 0x55);
	private static final Color CHECKER_DARK = new Color(0x44, 0x44, 0x44);
	private static final Color GRID = new Color(0, 0, 0, 60);
	private static final Color OUTLINE = new Color(255, 255, 255, 160);

	interface Listener
	{
		/** A change is about to start: time to keep a copy for undo. */
		void beforeChange();

		/** The picture changed; finished is false while a stroke is still being drawn. */
		void changed(boolean finished);

		void picked(int colour);

		/** The picture pixel under the mouse, or -1, -1 off the picture. */
		void hovered(int x, int y);
	}

	private final Listener listener;
	private int width = 1;
	private int height = 1;
	private int[] pixels = new int[1];
	private BufferedImage image;

	private Tool tool = Tool.BRUSH;
	private int colour = 0xff000000;
	private int brushSize = 3;
	private boolean filled;
	private boolean mirror;
	private boolean grid = true;

	private boolean drawing;
	private int drawingColour;
	private int[] beforeShape;
	private int startX;
	private int startY;
	private int lastX;
	private int lastY;
	private int hoverX = -1;
	private int hoverY = -1;

	StudioCanvas(Listener listener)
	{
		this.listener = listener;
		setPreferredSize(new Dimension(560, 560));
		setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				press(event);
			}

			@Override
			public void mouseDragged(MouseEvent event)
			{
				drag(event);
			}

			@Override
			public void mouseReleased(MouseEvent event)
			{
				release();
			}

			@Override
			public void mouseMoved(MouseEvent event)
			{
				hover(event);
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				hoverX = -1;
				hoverY = -1;
				listener.hovered(-1, -1);
				repaint();
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	void setPicture(int width, int height, int[] pixels)
	{
		drawing = false;
		this.width = width;
		this.height = height;
		this.pixels = pixels;
		image = null;
		repaint();
	}

	Design snapshot()
	{
		return Design.of(width, height, pixels);
	}

	int pictureWidth()
	{
		return width;
	}

	int pictureHeight()
	{
		return height;
	}

	void setTool(Tool tool)
	{
		this.tool = tool;
		repaint();
	}

	Tool getTool()
	{
		return tool;
	}

	void setColour(int colour)
	{
		this.colour = colour;
	}

	int getColour()
	{
		return colour;
	}

	void setBrushSize(int brushSize)
	{
		this.brushSize = brushSize;
		repaint();
	}

	void setFilled(boolean filled)
	{
		this.filled = filled;
	}

	void setMirror(boolean mirror)
	{
		this.mirror = mirror;
		repaint();
	}

	void setGrid(boolean grid)
	{
		this.grid = grid;
		repaint();
	}

	private void press(MouseEvent event)
	{
		if (drawing) return;
		int x = pictureX(event.getX());
		int y = pictureY(event.getY());
		boolean inside = x >= 0 && y >= 0 && x < width && y < height;
		boolean rubOut = SwingUtilities.isRightMouseButton(event) || tool == Tool.ERASER;
		drawingColour = rubOut ? 0 : colour;

		if (tool == Tool.PICKER)
		{
			if (inside) listener.picked(pixels[y * width + x]);
			return;
		}
		if (tool == Tool.FILL)
		{
			if (!inside) return;
			listener.beforeChange();
			PixelOps.fill(pixels, width, height, x, y, drawingColour, mirror);
			changed(true);
			return;
		}

		listener.beforeChange();
		drawing = true;
		startX = x;
		startY = y;
		lastX = x;
		lastY = y;
		if (tool.isShape())
		{
			beforeShape = pixels.clone();
			drawShape(x, y, event.isShiftDown());
		}
		else
		{
			PixelOps.dab(pixels, width, height, x, y, brushSize, drawingColour, mirror);
		}
		changed(false);
	}

	private void drag(MouseEvent event)
	{
		hover(event);
		if (!drawing) return;
		int x = pictureX(event.getX());
		int y = pictureY(event.getY());
		if (x == lastX && y == lastY) return;
		if (tool.isShape())
		{
			System.arraycopy(beforeShape, 0, pixels, 0, pixels.length);
			drawShape(x, y, event.isShiftDown());
		}
		else
		{
			PixelOps.line(pixels, width, height, lastX, lastY, x, y, brushSize, drawingColour, mirror);
		}
		lastX = x;
		lastY = y;
		changed(false);
	}

	private void release()
	{
		if (!drawing) return;
		drawing = false;
		beforeShape = null;
		changed(true);
	}

	private void drawShape(int x, int y, boolean constrain)
	{
		int dx = x - startX;
		int dy = y - startY;
		if (constrain && tool == Tool.LINE)
		{
			// Snap to the nearest of horizontal, vertical and the two diagonals.
			if (Math.abs(dx) > 2 * Math.abs(dy)) dy = 0;
			else if (Math.abs(dy) > 2 * Math.abs(dx)) dx = 0;
			else dy = Integer.signum(dy) * Math.abs(dx);
		}
		else if (constrain)
		{
			int side = Math.max(Math.abs(dx), Math.abs(dy));
			dx = (dx < 0 ? -1 : 1) * side;
			dy = (dy < 0 ? -1 : 1) * side;
		}
		int endX = startX + dx;
		int endY = startY + dy;
		switch (tool)
		{
			case LINE:
				PixelOps.line(pixels, width, height, startX, startY, endX, endY, brushSize, drawingColour, mirror);
				break;
			case RECTANGLE:
				PixelOps.rectangle(pixels, width, height, startX, startY, endX, endY, brushSize, drawingColour, filled, mirror);
				break;
			case ELLIPSE:
				PixelOps.ellipse(pixels, width, height, startX, startY, endX, endY, brushSize, drawingColour, filled, mirror);
				break;
			default:
				break;
		}
	}

	private void changed(boolean finished)
	{
		image = null;
		repaint();
		listener.changed(finished);
	}

	private void hover(MouseEvent event)
	{
		int x = pictureX(event.getX());
		int y = pictureY(event.getY());
		if (x < 0 || y < 0 || x >= width || y >= height)
		{
			x = -1;
			y = -1;
		}
		if (x == hoverX && y == hoverY) return;
		hoverX = x;
		hoverY = y;
		listener.hovered(x, y);
		repaint();
	}

	/** How many screen pixels one picture pixel takes up. */
	private float scale()
	{
		float available = Math.min((getWidth() - 2f * MARGIN) / width, (getHeight() - 2f * MARGIN) / height);
		// Whole numbers keep every picture pixel the same size, once there is room for that.
		return available >= 2 ? (float) Math.floor(available) : Math.max(available, 0.1f);
	}

	private int left()
	{
		return Math.round((getWidth() - width * scale()) / 2);
	}

	private int top()
	{
		return Math.round((getHeight() - height * scale()) / 2);
	}

	private int pictureX(int screenX)
	{
		return (int) Math.floor((screenX - left()) / scale());
	}

	private int pictureY(int screenY)
	{
		return (int) Math.floor((screenY - top()) / scale());
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setColor(ColorScheme.DARKER_GRAY_COLOR);
			g.fillRect(0, 0, getWidth(), getHeight());

			float scale = scale();
			int left = left();
			int top = top();
			int drawnWidth = Math.round(width * scale);
			int drawnHeight = Math.round(height * scale);

			// A checkerboard shows through wherever the plain sail will.
			g.setClip(left, top, drawnWidth, drawnHeight);
			for (int y = 0; y < drawnHeight; y += CHECKER)
			{
				for (int x = 0; x < drawnWidth; x += CHECKER)
				{
					g.setColor(((x + y) / CHECKER) % 2 == 0 ? CHECKER_LIGHT : CHECKER_DARK);
					g.fillRect(left + x, top + y, CHECKER, CHECKER);
				}
			}
			g.setClip(null);

			if (image == null)
			{
				image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				image.setRGB(0, 0, width, height, pixels, 0, width);
			}
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(image, left, top, drawnWidth, drawnHeight, null);

			if (grid && scale >= GRID_FROM)
			{
				g.setColor(GRID);
				for (int x = 1; x < width; x++) g.drawLine(left + Math.round(x * scale), top, left + Math.round(x * scale), top + drawnHeight - 1);
				for (int y = 1; y < height; y++) g.drawLine(left, top + Math.round(y * scale), left + drawnWidth - 1, top + Math.round(y * scale));
			}
			if (mirror)
			{
				g.setColor(OUTLINE);
				g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{4, 4}, 0));
				int middle = left + drawnWidth / 2;
				g.drawLine(middle, top, middle, top + drawnHeight - 1);
				g.setStroke(new BasicStroke(1));
			}
			g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
			g.drawRect(left - 1, top - 1, drawnWidth + 1, drawnHeight + 1);

			// Where the brush will land.
			if (hoverX >= 0 && !drawing && (tool == Tool.BRUSH || tool == Tool.ERASER || tool.isShape()))
			{
				int size = brushSize;
				int start = size / 2;
				g.setColor(OUTLINE);
				g.drawRect(left + Math.round((hoverX - start) * scale), top + Math.round((hoverY - start) * scale),
					Math.round(size * scale) - 1, Math.round(size * scale) - 1);
			}
		}
		finally
		{
			g.dispose();
		}
	}
}
