package com.sailpainter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import net.runelite.client.RuneLite;

/** Keeps the current design as a PNG in the RuneLite folder. Disk work, so never on the client thread. */
final class DesignStore
{
	private final File directory;
	private final File file;

	DesignStore()
	{
		this(new File(RuneLite.RUNELITE_DIR, "sail-painter"));
	}

	DesignStore(File directory)
	{
		this.directory = directory;
		this.file = new File(directory, "sail.png");
	}

	/** The saved design, or null if there is none yet. */
	Design load() throws IOException
	{
		if (!file.isFile()) return null;
		BufferedImage image = ImageIO.read(file);
		if (image == null) throw new IOException("Not an image: " + file);
		return Design.fromImage(image, Design.MAX_SIZE);
	}

	void save(Design design) throws IOException
	{
		if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create " + directory);
		// Written beside the real file and moved over it, so a crash mid-write cannot lose the old one.
		File temp = new File(directory, "sail.png.tmp");
		if (!ImageIO.write(design.toImage(), "png", temp)) throw new IOException("No PNG writer");
		try
		{
			Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException e)
		{
			Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
