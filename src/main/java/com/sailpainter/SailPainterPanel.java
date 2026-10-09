package com.sailpainter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
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
	private final JLabel communityStatus = new JLabel();
	private final JButton submit = new JButton("Submit my sail for review");
	private final JButton withdraw = new JButton("Stop showing my sail");
	private final JPanel nearbyRows = column();
	private final JPanel hiddenRows = column();
	private final Timer timer;
	/** What the lists were last built from, so they are only rebuilt when it changes. */
	private List<SailPainterPlugin.Nearby> shownNearby;
	private Set<String> shownHidden;
	private Boolean shownOn;

	private static final String[][] REPORT_REASONS = {
		{"offensive", "Offensive or inappropriate"},
		{"impersonation", "Not this player's sail"},
		{"other", "Something else"},
	};

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
		content.add(Box.createVerticalStrut(14));

		JLabel communityTitle = new JLabel("Community sails");
		communityTitle.setFont(FontManager.getRunescapeBoldFont());
		communityTitle.setForeground(Color.WHITE);
		content.add(left(communityTitle));
		content.add(Box.createVerticalStrut(4));
		communityStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		content.add(left(communityStatus));
		content.add(Box.createVerticalStrut(6));
		submit.addActionListener(event ->
		{
			String text = "Your sail goes to the Sail Painter moderator to be checked. Once it is approved, everyone with "
				+ "Community sails turned on sees it on your boat, and it is shown with your character name on " + CommunitySails.SITE + ". Send it?";
			if (JOptionPane.showConfirmDialog(this, html(text), "Sail Painter", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
			{
				submit.setEnabled(false);
				plugin.submitSail(this::tell);
			}
		});
		withdraw.addActionListener(event ->
		{
			if (JOptionPane.showConfirmDialog(this, "Stop showing your sail to other players?", "Sail Painter", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
			{
				plugin.withdrawSail(this::tell);
			}
		});
		JButton gallery = new JButton("View all sails online");
		gallery.addActionListener(event -> plugin.openGallery());
		JPanel communityButtons = new JPanel(new GridLayout(0, 1, 0, 6));
		communityButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		communityButtons.add(submit);
		communityButtons.add(withdraw);
		communityButtons.add(gallery);
		content.add(left(communityButtons));
		content.add(Box.createVerticalStrut(8));
		content.add(left(nearbyRows));
		content.add(left(hiddenRows));
		content.add(Box.createVerticalStrut(12));

		JLabel note = new JLabel(html("Only you can see your painted sail, unless you share it with your RuneLite party or it is approved as a community sail. Settings are under Sail Painter in the configuration panel."));
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
		showCommunity();
	}

	private void showCommunity()
	{
		boolean on = plugin.communityOn();
		communityStatus.setText(html(plugin.communityText()));
		submit.setVisible(on);
		withdraw.setVisible(on && plugin.hasOpenSail());
		List<SailPainterPlugin.Nearby> nearby = on ? plugin.getNearby() : List.of();
		Set<String> hidden = plugin.getHiddenSails();
		if (Objects.equals(nearby, shownNearby) && Objects.equals(hidden, shownHidden) && Objects.equals(on, shownOn)) return;
		shownNearby = nearby;
		shownHidden = hidden;
		shownOn = on;

		nearbyRows.removeAll();
		if (!nearby.isEmpty())
		{
			nearbyRows.add(small("Sails near you"));
			for (SailPainterPlugin.Nearby sail : nearby)
			{
				JButton toggle = tiny(sail.hidden ? "Show" : "Hide");
				toggle.addActionListener(event -> plugin.setHidden(sail.key, !sail.hidden));
				JButton report = tiny("Report");
				report.addActionListener(event -> report(sail));
				nearbyRows.add(row(sail.name + (sail.hidden ? " (hidden)" : ""), toggle, report));
			}
		}
		hiddenRows.removeAll();
		List<String> stillHidden = new ArrayList<>(hidden);
		for (SailPainterPlugin.Nearby sail : nearby) stillHidden.remove(sail.key);
		if (on && !stillHidden.isEmpty())
		{
			hiddenRows.add(small("Hidden sails"));
			for (String key : stillHidden)
			{
				JButton show = tiny("Show");
				show.addActionListener(event -> plugin.setHidden(key, false));
				hiddenRows.add(row(key, show));
			}
		}
		nearbyRows.revalidate();
		hiddenRows.revalidate();
		repaint();
	}

	private void report(SailPainterPlugin.Nearby sail)
	{
		JComboBox<String> reason = new JComboBox<>();
		for (String[] option : REPORT_REASONS) reason.addItem(option[1]);
		JTextField note = new JTextField();
		JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
		form.add(new JLabel("Why are you reporting " + sail.name + "'s sail?"));
		form.add(reason);
		form.add(new JLabel("Anything else the moderator should know (optional)"));
		form.add(note);
		if (JOptionPane.showConfirmDialog(this, form, "Report a sail", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
		plugin.reportSail(sail, REPORT_REASONS[reason.getSelectedIndex()][0], note.getText(), this::tell);
	}

	/** Any thread. Shows the outcome of something sent to the sail server. */
	private void tell(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			submit.setEnabled(true);
			JOptionPane.showMessageDialog(this, html(message), "Sail Painter", JOptionPane.INFORMATION_MESSAGE);
		});
	}

	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return panel;
	}

	private static JLabel small(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		label.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		return left(label);
	}

	private static JButton tiny(String text)
	{
		JButton button = new JButton(text);
		button.setMargin(new Insets(1, 4, 1, 4));
		button.setFont(FontManager.getRunescapeSmallFont());
		return button;
	}

	private static JPanel row(String name, JButton... buttons)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 3));
		JLabel label = new JLabel(name);
		label.setForeground(Color.WHITE);
		row.add(label, BorderLayout.CENTER);
		JPanel actions = new JPanel(new GridLayout(1, 0, 3, 0));
		actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		for (JButton button : buttons) actions.add(button);
		row.add(actions, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(PREVIEW, row.getPreferredSize().height));
		JPanel spaced = column();
		spaced.add(left(row));
		spaced.add(Box.createVerticalStrut(3));
		return left(spaced);
	}

	private static String html(String text)
	{
		// Swing's HTML measures widths larger than the pixels they end up taking, so this is smaller than the space.
		return "<html><div style='width:" + (PREVIEW * 3 / 4) + "px'>" + text + "</div></html>";
	}

	private static <T extends JComponent> T left(T component)
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
