package com.sailpainter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** The sidebar: what is on the sail now, whether it is being painted, and the way into the studio. */
class SailPainterPanel extends PluginPanel
{
	private static final int STATUS_MS = 500;
	private static final int PREVIEW = PANEL_WIDTH - 20;

	private final SailPainterPlugin plugin;
	private final Preview preview = new Preview();
	private final JLabel status = new JLabel();
	private final JLabel party = new JLabel();
	private final Timer timer;

	SailPainterPanel(SailPainterPlugin plugin)
	{
		super(false);
		this.plugin = plugin;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Sail Painter");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		content.add(left(title));
		content.add(Box.createVerticalStrut(8));
		content.add(left(preview));
		content.add(Box.createVerticalStrut(8));
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		content.add(left(status));
		party.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		party.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		content.add(left(party));
		content.add(Box.createVerticalStrut(10));

		JButton studio = new JButton("Open drawing studio");
		studio.addActionListener(event -> plugin.openStudio());
		JButton open = new JButton("Import picture…");
		open.addActionListener(event ->
		{
			BufferedImage image = DesignFiles.open(this);
			if (image != null) plugin.importPicture(image, this);
		});
		JButton save = new JButton("Export picture…");
		save.addActionListener(event -> DesignFiles.save(this, plugin.getDesign()));
		JButton clear = new JButton("Clear sail");
		clear.addActionListener(event ->
		{
			if (JOptionPane.showConfirmDialog(this, "Rub out the whole picture?", "Sail Painter", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
			{
				plugin.clearDesign(this);
			}
		});
		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.add(studio);
		buttons.add(open);
		buttons.add(save);
		buttons.add(clear);
		content.add(left(buttons));
		content.add(Box.createVerticalStrut(12));

		JLabel note = new JLabel(html("Only you can see your painted sail, unless you share it with your RuneLite party. Settings are under Sail Painter in the configuration panel."));
		note.setFont(FontManager.getRunescapeSmallFont());
		note.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		content.add(left(note));

		add(content, BorderLayout.NORTH);
		designChanged(plugin.getDesign());
		showStatus();
		timer = new Timer(STATUS_MS, event -> showStatus());
		timer.start();
	}

	void designChanged(Design design)
	{
		preview.setDesign(design);
	}

	void stop()
	{
		timer.stop();
	}

	private void showStatus()
	{
		status.setText(html(plugin.getStatus().text));
		String partyText = plugin.partyText();
		party.setText(partyText == null ? "" : html(partyText));
		party.setVisible(partyText != null);
	}

	private static String html(String text)
	{
		// Swing's HTML measures widths larger than the pixels they end up taking, so this is smaller than the space.
		return "<html><div style='width:" + (PREVIEW * 3 / 4) + "px'>" + text + "</div></html>";
	}

	private static JComponent left(JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		return component;
	}

	/** The design as it stands, over a checkerboard where the plain sail will show. */
	private static class Preview extends JComponent
	{
		private static final int CHECKER = 8;
		private Design design;
		private BufferedImage image;

		Preview()
		{
			Dimension size = new Dimension(PREVIEW, PREVIEW);
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(size);
		}

		void setDesign(Design design)
		{
			this.design = design;
			this.image = design.toImage();
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			try
			{
				int size = Math.min(getWidth(), getHeight());
				for (int y = 0; y < size; y += CHECKER)
				{
					for (int x = 0; x < size; x += CHECKER)
					{
						g.setColor(((x + y) / CHECKER) % 2 == 0 ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
						g.fillRect(x, y, CHECKER, CHECKER);
					}
				}
				if (design == null) return;
				if (design.isBlank())
				{
					String text = "Nothing drawn yet";
					g.setFont(FontManager.getRunescapeFont());
					int width = g.getFontMetrics().stringWidth(text);
					g.setColor(ColorScheme.DARKER_GRAY_COLOR);
					g.fillRect((size - width) / 2 - 6, size / 2 - 14, width + 12, 22);
					g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
					g.drawString(text, (size - width) / 2, size / 2 + 2);
					return;
				}
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				g.drawImage(image, 0, 0, size, size, null);
				g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
				g.drawRect(0, 0, size - 1, size - 1);
			}
			finally
			{
				g.dispose();
			}
		}
	}
}
