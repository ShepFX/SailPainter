package com.sailpainter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class SailPainterPanelTest
{
	@Test
	public void serverTextCannotAddMarkup()
	{
		String html = SailPainterPanel.html("Rejected: <img src='http://example.com/x.png'> & \"more\"");
		assertFalse(html.contains("<img"));
		assertEquals("Rejected: &lt;img src=&#39;http://example.com/x.png&#39;&gt; &amp; &quot;more&quot;",
			SailPainterPanel.escape("Rejected: <img src='http://example.com/x.png'> & \"more\""));
	}

	@Test
	public void plainTextIsUnchanged()
	{
		assertEquals("Your sail is live.", SailPainterPanel.escape("Your sail is live."));
		assertEquals("", SailPainterPanel.escape(null));
	}
}
