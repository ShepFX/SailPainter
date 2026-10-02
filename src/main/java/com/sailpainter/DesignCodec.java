package com.sailpainter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Turns designs into PNGs and back, for the disk and for sending to the party. */
final class DesignCodec
{
	/** Pictures opened from disk may be large photos, but nothing bigger than this. */
	static final int MAX_FILE_SIZE = 8192;
	/** A design sent to the party is shrunk until its PNG is no bigger than this. */
	static final int MAX_SHARED_BYTES = 24 * 1024;
	private static final int MIN_SHARED_SIZE = 16;

	private DesignCodec()
	{
	}

	static byte[] png(Design design) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(design.toImage(), "png", out)) throw new IOException("No PNG writer");
		return out.toByteArray();
	}

	/**
	 * Reads a picture, checking its size before any pixels are decoded, so a file or message that
	 * claims to be enormous is turned away rather than filling memory.
	 */
	static BufferedImage read(InputStream in, int maxSize) throws IOException
	{
		try (ImageInputStream stream = ImageIO.createImageInputStream(in))
		{
			if (stream == null) throw new IOException("Unreadable picture");
			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if (!readers.hasNext()) throw new IOException("Not a picture");
			ImageReader reader = readers.next();
			try
			{
				reader.setInput(stream, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width < 1 || height < 1 || width > maxSize || height > maxSize)
				{
					throw new IOException("Picture is " + width + "x" + height + ", more than " + maxSize + " allowed");
				}
				return reader.read(0);
			}
			finally
			{
				reader.dispose();
			}
		}
	}

	/** The design as text to send to the party, halved in size until it is small enough to send. */
	static String share(Design design) throws IOException
	{
		byte[] png = png(design);
		while (png.length > MAX_SHARED_BYTES && Math.min(design.width(), design.height()) / 2 >= MIN_SHARED_SIZE)
		{
			design = design.resized(design.width() / 2, design.height() / 2);
			png = png(design);
		}
		if (png.length > MAX_SHARED_BYTES) throw new IOException("Design too detailed to share");
		return Base64.getEncoder().encodeToString(png);
	}

	/** A design received from the party. Anything too large or not a picture is refused. */
	static Design unshare(String text) throws IOException
	{
		// Base64 takes four characters for every three bytes.
		if (text.length() > (MAX_SHARED_BYTES + 2) / 3 * 4) throw new IOException("Shared design too large");
		byte[] png;
		try
		{
			png = Base64.getDecoder().decode(text);
		}
		catch (IllegalArgumentException e)
		{
			throw new IOException("Shared design is not base64", e);
		}
		BufferedImage image = read(new ByteArrayInputStream(png), Design.MAX_SIZE);
		return Design.fromImage(image, Design.MAX_SIZE);
	}
}
