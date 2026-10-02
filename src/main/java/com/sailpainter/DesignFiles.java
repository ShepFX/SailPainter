package com.sailpainter;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import javax.swing.JOptionPane;
import net.runelite.client.util.Filepath;

/** Opening and saving pictures where the user chooses. Swing thread only. */
final class DesignFiles
{
	private static final String TITLE = "Sail Painter";

	private DesignFiles()
	{
	}

	/** Asks for a picture to open, or null if none was chosen or it could not be read. */
	static BufferedImage open(Component parent)
	{
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsOpen()
			.setAcceptsFiles()
			.setDialogTitle("Open a picture for your sail")
			.addExtensionFilter("Pictures (PNG, JPG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp")
			.showDialog(parent);
		if (chosen.isEmpty()) return null;
		Filepath file = chosen.get(0);
		try (InputStream in = file.openInputStream())
		{
			return DesignCodec.read(in, DesignCodec.MAX_FILE_SIZE);
		}
		catch (IOException e)
		{
			error(parent, "Could not open " + file.getFileName() + ": " + e.getMessage());
			return null;
		}
	}

	static void save(Component parent, Design design)
	{
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Save your sail picture")
			.addExtensionFilter("PNG picture", "png")
			.setDefaultExtension("png")
			.setFileName("sail.png")
			.showDialog(parent);
		if (chosen.isEmpty()) return;
		Filepath file = chosen.get(0);
		if (file.exists() && JOptionPane.showConfirmDialog(parent, file.getFileName() + " already exists. Replace it?", TITLE,
			JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
		{
			return;
		}
		try (OutputStream out = file.openOutputStream())
		{
			out.write(DesignCodec.png(design));
		}
		catch (IOException e)
		{
			error(parent, "Could not save " + file.getFileName() + ": " + e.getMessage());
		}
	}

	private static void error(Component parent, String message)
	{
		JOptionPane.showMessageDialog(parent, message, TITLE, JOptionPane.WARNING_MESSAGE);
	}
}
