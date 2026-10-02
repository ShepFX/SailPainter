package com.sailpainter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import org.junit.Test;

public class DesignTest
{
	@Test
	public void blankUntilSomethingIsDrawn()
	{
		assertTrue(Design.blank(4, 4).isBlank());
		assertFalse(Design.of(1, 2, new int[]{0, 0x01000000}).isBlank());
	}

	@Test
	public void keepsItsOwnCopy()
	{
		int[] pixels = {0xff000001};
		Design design = Design.of(1, 1, pixels);
		pixels[0] = 0;
		assertEquals(0xff000001, design.pixel(0, 0));
	}

	@Test
	public void resizingKeepsPixelsSharp()
	{
		Design design = Design.of(2, 1, new int[]{0xffff0000, 0xff0000ff});
		Design bigger = design.resized(4, 2);
		assertArrayEquals(new int[]{0xffff0000, 0xffff0000, 0xff0000ff, 0xff0000ff, 0xffff0000, 0xffff0000, 0xff0000ff, 0xff0000ff}, bigger.pixels());
	}

	@Test
	public void bigPicturesAreShrunkToFit()
	{
		BufferedImage image = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_ARGB);
		Design design = Design.fromImage(image, 256);
		assertEquals(256, design.width());
		assertEquals(128, design.height());
	}

	@Test
	public void smallPicturesKeepTheirSize()
	{
		BufferedImage image = new BufferedImage(32, 20, BufferedImage.TYPE_INT_ARGB);
		Design design = Design.fromImage(image, 256);
		assertEquals(32, design.width());
		assertEquals(20, design.height());
	}

	@Test
	public void pngKeepsEveryPixel() throws Exception
	{
		int[] pixels = {0xffff0000, 0x00000000, 0x80123456, 0xffffffff};
		byte[] png = DesignCodec.png(Design.of(2, 2, pixels));
		Design loaded = Design.fromImage(DesignCodec.read(new ByteArrayInputStream(png), 10), Design.MAX_SIZE);
		assertEquals(2, loaded.width());
		assertEquals(0xffff0000, loaded.pixel(0, 0));
		assertEquals(0, loaded.pixel(1, 0) >>> 24);
		assertEquals(0x80123456, loaded.pixel(0, 1));
	}

	@Test(expected = IOException.class)
	public void oversizedPicturesAreRefusedBeforeDecoding() throws Exception
	{
		byte[] png = DesignCodec.png(Design.blank(64, 8));
		DesignCodec.read(new ByteArrayInputStream(png), 32);
	}

	@Test(expected = IOException.class)
	public void nonPicturesAreRefused() throws Exception
	{
		DesignCodec.read(new ByteArrayInputStream("not a picture".getBytes(StandardCharsets.UTF_8)), 32);
	}

	@Test
	public void sharedDesignComesBackTheSame() throws Exception
	{
		Design design = Examples.JOLLY_ROGER.draw(64, 64);
		Design received = DesignCodec.unshare(DesignCodec.share(design));
		assertArrayEquals(design.pixels(), received.pixels());
	}

	@Test
	public void detailedDesignsShrinkToShare() throws Exception
	{
		// Noise does not compress, so a 256 square of it is far over the limit.
		Random random = new Random(1);
		int[] noise = new int[256 * 256];
		for (int i = 0; i < noise.length; i++) noise[i] = 0xff000000 | random.nextInt(0x1000000);
		String text = DesignCodec.share(Design.of(256, 256, noise));
		assertTrue(Base64.getDecoder().decode(text).length <= DesignCodec.MAX_SHARED_BYTES);
		assertTrue(DesignCodec.unshare(text).width() < 256);
	}

	@Test(expected = IOException.class)
	public void oversizedSharedTextIsRefused() throws Exception
	{
		char[] text = new char[DesignCodec.MAX_SHARED_BYTES * 2];
		Arrays.fill(text, 'A');
		DesignCodec.unshare(new String(text));
	}

	@Test(expected = IOException.class)
	public void garbledSharedTextIsRefused() throws Exception
	{
		DesignCodec.unshare("this is %% not base64");
	}

	@Test
	public void examplesFillTheCanvas()
	{
		for (Examples example : Examples.values())
		{
			Design design = example.draw(64, 32);
			assertEquals(64, design.width());
			assertEquals(32, design.height());
			assertFalse(example + " is blank", design.isBlank());
		}
	}
}
