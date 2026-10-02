package com.sailpainter;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Opening and saving pictures where the user chooses. Swing thread only. */
final class DesignFiles
{
	private static final String TITLE = "Sail Painter";
	private static File lastDirectory;

	private DesignFiles()
	{
	}

	/** Asks for a picture to open, or null if none was chosen or it could not be read. */
	static BufferedImage open(Component parent)
	{
		JFileChooser chooser = new JFileChooser(lastDirectory);
		chooser.setDialogTitle("Open a picture for your sail");
		chooser.setFileFilter(new FileNameExtensionFilter("Pictures (PNG, JPG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"));
		if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
		File file = chooser.getSelectedFile();
		lastDirectory = file.getParentFile();
		try
		{
			BufferedImage image = ImageIO.read(file);
			if (image == null) error(parent, file.getName() + " is not a picture that can be opened. Try a PNG or JPG.");
			return image;
		}
		catch (IOException e)
		{
			error(parent, "Could not open " + file.getName() + ": " + e.getMessage());
			return null;
		}
	}

	static void save(Component parent, Design design)
	{
		JFileChooser chooser = new JFileChooser(lastDirectory);
		chooser.setDialogTitle("Save your sail picture");
		chooser.setFileFilter(new FileNameExtensionFilter("PNG picture", "png"));
		chooser.setSelectedFile(new File(lastDirectory, "sail.png"));
		if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) file = new File(file.getParentFile(), file.getName() + ".png");
		lastDirectory = file.getParentFile();
		if (file.exists() && JOptionPane.showConfirmDialog(parent, file.getName() + " already exists. Replace it?", TITLE,
			JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
		{
			return;
		}
		try
		{
			ImageIO.write(design.toImage(), "png", file);
		}
		catch (IOException e)
		{
			error(parent, "Could not save " + file.getName() + ": " + e.getMessage());
		}
	}

	private static void error(Component parent, String message)
	{
		JOptionPane.showMessageDialog(parent, message, TITLE, JOptionPane.WARNING_MESSAGE);
	}
}
