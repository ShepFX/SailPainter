package com.sailpainter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import net.runelite.client.party.messages.WebsocketMessage;
import net.runelite.client.util.RuntimeTypeAdapterFactory;
import org.junit.Test;

/** Sends the design message through the same kind of encoding the party connection builds for registered messages. */
public class SailPainterDesignWireTest
{
	private final Gson gson = new Gson().newBuilder()
		.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(WebsocketMessage.class).registerSubtype(SailPainterDesign.class))
		.create();

	@Test
	public void designSurvivesTheTrip()
	{
		WebsocketMessage received = trip(new SailPainterDesign("Shep FX", "iVBORw0K"));
		assertTrue(received instanceof SailPainterDesign);
		assertEquals("Shep FX", ((SailPainterDesign) received).getName());
		assertEquals("iVBORw0K", ((SailPainterDesign) received).getPng());
	}

	@Test
	public void stoppingSharingSurvivesTheTrip()
	{
		assertNull(((SailPainterDesign) trip(new SailPainterDesign("Shep FX", null))).getPng());
	}

	private WebsocketMessage trip(SailPainterDesign message)
	{
		return gson.fromJson(gson.toJson(message, WebsocketMessage.class), WebsocketMessage.class);
	}
}
