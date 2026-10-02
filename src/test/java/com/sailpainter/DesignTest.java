package com.sailpainter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.awt.image.BufferedImage;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DesignTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

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
	public void savesAndLoads() throws Exception
	{
		File directory = new File(folder.getRoot(), "sail-painter");
		DesignStore store = new DesignStore(directory);
		assertNull(store.load());

		int[] pixels = {0xffff0000, 0x00000000, 0x80123456, 0xffffffff};
		store.save(Design.of(2, 2, pixels));
		store.save(Design.of(2, 2, pixels));
		Design loaded = store.load();
		assertEquals(2, loaded.width());
		assertEquals(0xffff0000, loaded.pixel(0, 0));
		assertEquals(0, loaded.pixel(1, 0) >>> 24);
		assertEquals(0x80123456, loaded.pixel(0, 1));
		assertFalse(new File(directory, "sail.png.tmp").exists());
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
