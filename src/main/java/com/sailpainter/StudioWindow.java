package com.sailpainter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

/** A window to draw the sail's picture in. The sail follows along as you draw. */
class StudioWindow extends JFrame implements StudioCanvas.Listener
{
	private static final int[] SIZES = {32, 64, 128, 256};
	private static final int UNDO_LIMIT = 100;
	/** While a stroke is being drawn the sail is brought up to date at most this often. */
	private static final long LIVE_MS = 40;
	private static final int MAX_BRUSH = 32;
	private static final int[] PALETTE = {
		0x000000, 0x3c3c3c, 0x7d7d7d, 0xbebebe,
		0xffffff, 0xf2eee3, 0xe0c9a6, 0xc19a6b,
		0x8b5a2b, 0x4a2c17, 0x6b1d1d, 0xc0392b,
		0xff6f61, 0xf39c12, 0xf7dc6f, 0xfff3b0,
		0x1e5631, 0x27ae60, 0x8fd694, 0x0e6655,
		0x48c9b0, 0x1b2a6b, 0x2e86de, 0x85c1e9,
		0x5b2c6f, 0x9b59b6, 0xd7a9e3, 0xff8fc7,
	};

	private final SailPainterPlugin plugin;
	private final StudioCanvas canvas = new StudioCanvas(this);
	private final Deque<Design> undo = new ArrayDeque<>();
	private final Deque<Design> redo = new ArrayDeque<>();
	private final Map<Tool, JToggleButton> toolButtons = new EnumMap<>(Tool.class);
	private final JLabel position = new JLabel(" ");
	private final Swatch current = new Swatch(0xff000000, 44);
	private final JSlider brush = new JSlider(1, MAX_BRUSH, 3);
	private final JLabel brushLabel = new JLabel("3");
	private final JComboBox<String> sizes = new JComboBox<>();
	private final JCheckBox grid = new JCheckBox("Show grid", true);
	private final JCheckBox mirror = new JCheckBox("Mirror drawing");
	private final JButton undoButton = new JButton("Undo");
	private final JButton redoButton = new JButton("Redo");
	private Tool beforePicker = Tool.BRUSH;
	private boolean loaded;
	private boolean updatingSizes;
	private long lastLive;

	StudioWindow(SailPainterPlugin plugin)
	{
		super("Sail Painter Studio");
		this.plugin = plugin;
		setIconImage(ImageUtil.loadImageResource(SailPainterPlugin.class, "panel_icon.png"));
		setDefaultCloseOperation(HIDE_ON_CLOSE);

		JPanel content = new JPanel(new BorderLayout());
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(toolbars(), BorderLayout.NORTH);
		content.add(canvas, BorderLayout.CENTER);
		content.add(palette(), BorderLayout.EAST);
		content.add(statusBar(), BorderLayout.SOUTH);
		setContentPane(content);
		bindKeys();
		selectTool(Tool.BRUSH);
		updateButtons();

		pack();
		setMinimumSize(new Dimension(760, 560));
		setLocationRelativeTo(null);
	}

	/** Shows the given design, keeping what was there before on the undo list. */
	void load(Design design)
	{
		Design shown = canvas.snapshot();
		if (loaded && shown.width() == design.width() && shown.height() == design.height()
			&& Arrays.equals(shown.pixels(), design.pixels()))
		{
			return;
		}
		if (loaded) remember(shown);
		loaded = true;
		canvas.setPicture(design.width(), design.height(), design.copyPixels());
		showSize();
		updateButtons();
	}

	@Override
	public void beforeChange()
	{
		remember(canvas.snapshot());
		updateButtons();
	}

	@Override
	public void changed(boolean finished)
	{
		long now = System.currentTimeMillis();
		if (!finished && now - lastLive < LIVE_MS) return;
		lastLive = now;
		plugin.setDesign(canvas.snapshot(), finished, this);
	}

	@Override
	public void picked(int colour)
	{
		setColour(colour);
		selectTool(beforePicker);
	}

	@Override
	public void hovered(int x, int y)
	{
		String size = canvas.pictureWidth() + " × " + canvas.pictureHeight();
		position.setText(x < 0 ? size : x + ", " + y + "     " + size);
	}

	private JComponent toolbars()
	{
		JPanel tools = row();
		ButtonGroup group = new ButtonGroup();
		for (Tool tool : Tool.values())
		{
			JToggleButton button = new JToggleButton(tool.label);
			button.setToolTipText(tool.tooltip);
			button.setFocusable(false);
			button.addActionListener(event -> selectTool(tool));
			group.add(button);
			toolButtons.put(tool, button);
			tools.add(button);
		}
		tools.add(separator());
		tools.add(new JLabel("Size"));
		brush.setFocusable(false);
		brush.setPreferredSize(new Dimension(120, brush.getPreferredSize().height));
		brush.setToolTipText("Brush and line thickness ([ and ])");
		brush.addChangeListener(event ->
		{
			brushLabel.setText(String.valueOf(brush.getValue()));
			canvas.setBrushSize(brush.getValue());
		});
		canvas.setBrushSize(brush.getValue());
		tools.add(brush);
		brushLabel.setPreferredSize(new Dimension(20, brushLabel.getPreferredSize().height));
		tools.add(brushLabel);

		JPanel actions = row();
		undoButton.setToolTipText("Undo (Ctrl+Z)");
		undoButton.addActionListener(event -> undo());
		actions.add(button(undoButton));
		redoButton.setToolTipText("Redo (Ctrl+Y)");
		redoButton.addActionListener(event -> redo());
		actions.add(button(redoButton));
		JButton clear = new JButton("Clear");
		clear.setToolTipText("Rub out everything, back to the plain sail");
		clear.addActionListener(event -> replace(Design.blank(canvas.pictureWidth(), canvas.pictureHeight())));
		actions.add(button(clear));
		actions.add(separator());

		actions.add(new JLabel("Canvas"));
		for (int size : SIZES) sizes.addItem(size + " × " + size);
		sizes.setFocusable(false);
		sizes.setToolTipText("How many pixels the picture has. Changing it stretches what is already drawn");
		sizes.addActionListener(event ->
		{
			int index = sizes.getSelectedIndex();
			if (updatingSizes || index < 0) return;
			int size = SIZES[index];
			if (size == canvas.pictureWidth() && size == canvas.pictureHeight()) return;
			replace(canvas.snapshot().resized(size, size));
		});
		actions.add(sizes);

		JButton examples = new JButton("Examples");
		examples.setToolTipText("Start from a ready-made design. Test pattern shows which way up and round the picture lands");
		JPopupMenu menu = new JPopupMenu();
		for (Examples example : Examples.values())
		{
			JMenuItem item = new JMenuItem(example.toString());
			item.addActionListener(event -> replace(example.draw(canvas.pictureWidth(), canvas.pictureHeight())));
			menu.add(item);
		}
		examples.addActionListener(event -> menu.show(examples, 0, examples.getHeight()));
		actions.add(button(examples));
		actions.add(separator());

		JButton open = new JButton("Import…");
		open.setToolTipText("Use a picture from your computer");
		open.addActionListener(event ->
		{
			BufferedImage image = DesignFiles.open(this);
			if (image != null) replace(Design.fromImage(image, SailPainterPlugin.IMPORT_SIZE));
		});
		actions.add(button(open));
		JButton save = new JButton("Export…");
		save.setToolTipText("Save the picture as a PNG");
		save.addActionListener(event -> DesignFiles.save(this, canvas.snapshot()));
		actions.add(button(save));

		JPanel bars = new JPanel();
		bars.setLayout(new BoxLayout(bars, BoxLayout.Y_AXIS));
		bars.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bars.add(tools);
		bars.add(actions);
		return bars;
	}

	private JComponent palette()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 10));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(12, 4, 12, 12));

		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		current.setToolTipText("The colour you are drawing with. Click to choose any colour");
		current.onClick(this::chooseColour);
		top.add(current, BorderLayout.WEST);
		top.add(new JLabel("Colour"), BorderLayout.CENTER);
		panel.add(top, BorderLayout.NORTH);

		JPanel swatches = new JPanel(new GridLayout(0, 4, 4, 4));
		swatches.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (int rgb : PALETTE)
		{
			Swatch swatch = new Swatch(0xff000000 | rgb, 26);
			swatch.onClick(() -> setColour(swatch.colour));
			swatches.add(swatch);
		}
		JButton more = new JButton("More colours…");
		more.setFocusable(false);
		more.addActionListener(event -> chooseColour());

		JCheckBox filled = new JCheckBox("Filled shapes");
		filled.setFocusable(false);
		filled.setToolTipText("Fill boxes and ovals rather than only outlining them");
		filled.addActionListener(event -> canvas.setFilled(filled.isSelected()));
		mirror.setFocusable(false);
		mirror.setToolTipText("Draw everything again reflected left to right, for symmetrical designs (M)");
		mirror.addActionListener(event -> canvas.setMirror(mirror.isSelected()));
		grid.setFocusable(false);
		grid.setToolTipText("Show lines between pixels when zoomed in (G)");
		grid.addActionListener(event -> canvas.setGrid(grid.isSelected()));

		JPanel column = new JPanel(new GridLayout(0, 1, 0, 6));
		column.setBackground(ColorScheme.DARK_GRAY_COLOR);
		column.add(more);
		column.add(new JLabel());
		column.add(filled);
		column.add(mirror);
		column.add(grid);

		JPanel holder = new JPanel(new BorderLayout(0, 10));
		holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		holder.add(swatches, BorderLayout.NORTH);
		holder.add(column, BorderLayout.CENTER);
		JPanel stack = new JPanel(new BorderLayout());
		stack.setBackground(ColorScheme.DARK_GRAY_COLOR);
		stack.add(holder, BorderLayout.NORTH);
		panel.add(stack, BorderLayout.CENTER);
		return panel;
	}

	private JComponent statusBar()
	{
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
		bar.add(position, BorderLayout.WEST);
		JLabel hint = new JLabel("Your sail updates as you draw.  Right-click rubs out.  Ctrl+Z undoes.", SwingConstants.RIGHT);
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		bar.add(hint, BorderLayout.EAST);
		return bar;
	}

	private void bindKeys()
	{
		JRootPane root = getRootPane();
		int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu), "undo", this::undo);
		bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_Y, menu), "redo", this::redo);
		bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu | InputEvent.SHIFT_DOWN_MASK), "redo again", this::redo);
		for (Tool tool : Tool.values()) bind(root, KeyStroke.getKeyStroke(tool.key, 0), "tool " + tool, () -> selectTool(tool));
		bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, 0), "smaller", () -> brush.setValue(brush.getValue() - 1));
		bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, 0), "bigger", () -> brush.setValue(brush.getValue() + 1));
		bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_G, 0), "grid", grid::doClick);
		bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_M, 0), "mirror", mirror::doClick);
	}

	private void selectTool(Tool tool)
	{
		if (tool == Tool.PICKER && canvas.getTool() != Tool.PICKER) beforePicker = canvas.getTool();
		canvas.setTool(tool);
		toolButtons.get(tool).setSelected(true);
	}

	private void setColour(int colour)
	{
		canvas.setColour(colour);
		current.setColour(colour);
		// Choosing a colour means wanting to paint with it.
		if (canvas.getTool() == Tool.ERASER) selectTool(Tool.BRUSH);
	}

	private void chooseColour()
	{
		Color chosen = JColorChooser.showDialog(this, "Choose a colour", new Color(canvas.getColour(), true));
		if (chosen != null) setColour(chosen.getRGB());
	}

	/** Swaps in a whole new picture as one undoable step. */
	private void replace(Design design)
	{
		beforeChange();
		show(design);
	}

	private void undo()
	{
		if (undo.isEmpty()) return;
		redo.push(canvas.snapshot());
		show(undo.pop());
	}

	private void redo()
	{
		if (redo.isEmpty()) return;
		undo.push(canvas.snapshot());
		show(redo.pop());
	}

	private void show(Design design)
	{
		canvas.setPicture(design.width(), design.height(), design.copyPixels());
		showSize();
		updateButtons();
		plugin.setDesign(design, true, this);
	}

	/** Keeps a copy for undo. Any change other than an undo or redo also ends the chance to redo. */
	private void remember(Design design)
	{
		undo.push(design);
		while (undo.size() > UNDO_LIMIT) undo.removeLast();
		redo.clear();
	}

	private void showSize()
	{
		updatingSizes = true;
		int index = -1;
		for (int i = 0; i < SIZES.length; i++)
		{
			if (SIZES[i] == canvas.pictureWidth() && SIZES[i] == canvas.pictureHeight()) index = i;
		}
		sizes.setSelectedIndex(index);
		updatingSizes = false;
		hovered(-1, -1);
	}

	private void updateButtons()
	{
		undoButton.setEnabled(!undo.isEmpty());
		redoButton.setEnabled(!redo.isEmpty());
	}

	private static JPanel row()
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return row;
	}

	private static JButton button(JButton button)
	{
		button.setFocusable(false);
		return button;
	}

	private static JComponent separator()
	{
		JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
		separator.setPreferredSize(new Dimension(8, 22));
		return separator;
	}

	private static void bind(JComponent component, KeyStroke key, String name, Runnable action)
	{
		component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
		component.getActionMap().put(name, new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent event)
			{
				action.run();
			}
		});
	}

	/** A square of colour, with a checkerboard showing through any transparency. */
	private static class Swatch extends JComponent
	{
		private int colour;

		Swatch(int colour, int size)
		{
			this.colour = colour;
			setPreferredSize(new Dimension(size, size));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		void setColour(int colour)
		{
			this.colour = colour;
			repaint();
		}

		void onClick(Runnable action)
		{
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent event)
				{
					action.run();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			int w = getWidth();
			int h = getHeight();
			int half = Math.max(1, Math.min(w, h) / 4);
			for (int y = 0; y < h; y += half)
			{
				for (int x = 0; x < w; x += half)
				{
					g.setColor(((x + y) / half) % 2 == 0 ? Color.LIGHT_GRAY : Color.GRAY);
					g.fillRect(x, y, half, half);
				}
			}
			g.setColor(new Color(colour, true));
			g.fillRect(0, 0, w, h);
			g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
			g.drawRect(0, 0, w - 1, h - 1);
		}
	}
}
