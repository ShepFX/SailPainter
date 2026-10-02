package com.sailpainter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import net.runelite.client.util.Filepath;

/** Keeps the current design as a PNG in the plugin's own data folder. Disk work, so never on the client thread. */
final class DesignStore
{
	private static final String FILE = "sail.png";
	private static final String TEMP = "sail.png.tmp";

	private final Filepath directory;

	DesignStore(Filepath directory)
	{
		this.directory = directory;
	}

	/** The saved design, or null if there is none yet. */
	Design load() throws IOException
	{
		Filepath file = directory.joinSegment(FILE);
		if (!file.isFile()) return null;
		try (InputStream in = file.openInputStream())
		{
			return Design.fromImage(DesignCodec.read(in, DesignCodec.MAX_FILE_SIZE), Design.MAX_SIZE);
		}
	}

	void save(Design design) throws IOException
	{
		if (!directory.isDirectory()) directory.createDirectories();
		// Written beside the real file and moved over it, so a crash mid-write cannot lose the old one.
		Filepath temp = directory.joinSegment(TEMP);
		try (OutputStream out = temp.openOutputStream())
		{
			out.write(DesignCodec.png(design));
		}
		Filepath file = directory.joinSegment(FILE);
		try
		{
			temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException e)
		{
			temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
