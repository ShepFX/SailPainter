package com.sailpainter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/** Ready-made designs to start from, drawn at whatever size the canvas is. */
enum Examples
{
	TEST_PATTERN("Test pattern", Examples::testPattern),
	JOLLY_ROGER("Jolly Roger", Examples::jollyRoger),
	STRIPES("Stripes", Examples::stripes);

	private static final int BASE = 128;

	private final String label;
	private final Consumer<Graphics2D> painter;

	Examples(String label, Consumer<Graphics2D> painter)
	{
		this.label = label;
		this.painter = painter;
	}

	@Override
	public String toString()
	{
		return label;
	}

	/** Drawn on a 128 square and stretched to the given size. */
	Design draw(int width, int height)
	{
		BufferedImage image = new BufferedImage(BASE, BASE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			painter.accept(g);
		}
		finally
		{
			g.dispose();
		}
		return Design.fromImage(image, width, height);
	}

	/** Shows which way up and which way round the picture lands: an arrow up, and letters that read wrongly when mirrored. */
	private static void testPattern(Graphics2D g)
	{
		g.setColor(new Color(0xd94141));
		g.fillRect(0, 0, 64, 64);
		g.setColor(new Color(0x41a85f));
		g.fillRect(64, 0, 64, 64);
		g.setColor(new Color(0x3d6fd9));
		g.fillRect(0, 64, 64, 64);
		g.setColor(new Color(0xe8c547));
		g.fillRect(64, 64, 64, 64);
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(6));
		g.drawRect(3, 3, 121, 121);

		g.fill(new Polygon(new int[]{64, 92, 74, 74, 54, 54, 36}, new int[]{14, 44, 44, 70, 70, 44, 44}, 7));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
		FontMetrics metrics = g.getFontMetrics();
		String text = "RL";
		g.setColor(Color.BLACK);
		g.drawString(text, 64 - metrics.stringWidth(text) / 2 + 2, 110);
		g.setColor(Color.WHITE);
		g.drawString(text, 64 - metrics.stringWidth(text) / 2, 108);
	}

	private static void jollyRoger(Graphics2D g)
	{
		g.setColor(new Color(0x16, 0x16, 0x18));
		g.fillRect(0, 0, BASE, BASE);
		Color bone = new Color(0xf2, 0xee, 0xe3);

		// Crossed bones behind the skull.
		g.setColor(bone);
		for (double angle : new double[]{Math.PI / 4, -Math.PI / 4})
		{
			AffineTransform saved = g.getTransform();
			g.rotate(angle, 64, 78);
			g.fill(new RoundRectangle2D.Double(18, 73, 92, 10, 10, 10));
			for (int end : new int[]{18, 110})
			{
				g.fill(new Ellipse2D.Double(end - 8, 67, 14, 12));
				g.fill(new Ellipse2D.Double(end - 8, 77, 14, 12));
			}
			g.setTransform(saved);
		}

		// The skull.
		g.fill(new Ellipse2D.Double(34, 18, 60, 54));
		g.fill(new RoundRectangle2D.Double(46, 58, 36, 24, 10, 10));
		g.setColor(new Color(0x16, 0x16, 0x18));
		g.fill(new Ellipse2D.Double(44, 38, 16, 16));
		g.fill(new Ellipse2D.Double(68, 38, 16, 16));
		g.fill(new Polygon(new int[]{64, 59, 69}, new int[]{56, 66, 66}, 3));
		g.setStroke(new BasicStroke(2));
		for (int x = 52; x <= 76; x += 6) g.drawLine(x, 72, x, 81);
	}

	private static void stripes(Graphics2D g)
	{
		Color[] colours = {new Color(0xc0392b), Color.WHITE};
		int stripe = BASE / 8;
		for (int i = 0; i < 8; i++)
		{
			g.setColor(colours[i % 2]);
			g.fillRect(0, i * stripe, BASE, stripe);
		}
	}
}
