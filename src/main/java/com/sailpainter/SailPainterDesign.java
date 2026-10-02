package com.sailpainter;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * A party member's sail design, sent to the rest of the party through RuneLite's party service.
 * The class name is the message's type on the wire, so it is kept specific to this plugin.
 */
public class SailPainterDesign extends PartyMemberMessage
{
	/** The sender's in-game name, which is how their boat is told apart. */
	private final String name;
	/** The design as base64 PNG, or null when the sender stops sharing. */
	private final String png;

	public SailPainterDesign(String name, String png)
	{
		this.name = name;
		this.png = png;
	}

	public String getName()
	{
		return name;
	}

	public String getPng()
	{
		return png;
	}

	// The party service logs every message it sends and receives; the picture itself would swamp the log.
	@Override
	public String toString()
	{
		return "SailPainterDesign(member=" + getMemberId() + ", name=" + name + ", characters=" + (png == null ? 0 : png.length()) + ")";
	}
}
