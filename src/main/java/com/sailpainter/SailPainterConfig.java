package com.sailpainter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(SailPainterConfig.GROUP)
public interface SailPainterConfig extends Config
{
	String GROUP = "sailpainter";

	@ConfigSection(name = "Community", description = "Sails other players submitted, checked by a moderator before anyone sees them", position = 8)
	String community = "community";

	@ConfigItem(
		keyName = "communitySails",
		name = "Community sails",
		description = "See the sails other Sail Painter players have had approved, on their boats, and submit, hide and report sails from the Sail Painter panel. The list and pictures come from " + CommunitySails.SITE,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = community,
		position = 0
	)
	default boolean communitySails()
	{
		return false;
	}

	@ConfigItem(keyName = "hiddenSails", name = "", description = "", hidden = true)
	default String hiddenSails()
	{
		return "";
	}

	@ConfigSection(name = "Party", description = "Sharing sails with friends in your RuneLite party", position = 9)
	String party = "party";

	@ConfigItem(keyName = "shareWithParty", name = "Share with party", description = "Send your design to the people in your RuneLite party who also have Sail Painter, so they see it on your boat. Join a party from RuneLite's Party panel", section = party, position = 0)
	default boolean shareWithParty()
	{
		return false;
	}

	@ConfigItem(keyName = "showPartySails", name = "Show party sails", description = "Paint the sails of party members who share their design, on whichever boat they are aboard", section = party, position = 1)
	default boolean showPartySails()
	{
		return true;
	}

	@ConfigSection(name = "Troubleshooting", description = "For when the picture does not land where it should", position = 10, closedByDefault = true)
	String troubleshooting = "troubleshooting";

	@ConfigItem(keyName = "paintSail", name = "Paint my sail", description = "Show your picture on the sail of your own boat, or of the boat you are aboard. Only you can see it", position = 0)
	default boolean paintSail()
	{
		return true;
	}

	@ConfigItem(keyName = "readableBothSides", name = "Readable from both sides", description = "Show the picture the right way round from behind the sail as well. Turn off to see it mirrored from behind, like paint showing through the cloth", position = 1)
	default boolean readableBothSides()
	{
		return true;
	}

	@ConfigItem(keyName = "fit", name = "Picture", description = "Fit inside the sail: the whole picture in the biggest box that fits on the cloth, so a triangular sail loses none of it. Stretch over the sail: the picture covers all the cloth, and a triangular sail cuts off its corners", position = 2)
	default PictureFit fit()
	{
		return PictureFit.FIT;
	}

	@ConfigItem(keyName = "opacity", name = "Opacity", description = "How solid the picture is. Lower lets the sail's own colour show through", position = 3)
	@Units(Units.PERCENT)
	@Range(min = 10, max = 100)
	default int opacity()
	{
		return 100;
	}

	@ConfigItem(keyName = "shading", name = "Shading", description = "How much of the sail's own light and shadow falls on the picture, so that it follows the folds of the cloth", position = 4)
	@Units(Units.PERCENT)
	@Range(max = 100)
	default int shading()
	{
		return 70;
	}

	@ConfigItem(keyName = "occlusion", name = "Hide behind crew and rigging", description = "Let people and parts of the boat in front of the sail cover the picture", position = 5)
	default boolean occlusion()
	{
		return true;
	}

	@ConfigItem(keyName = "smooth", name = "Smooth picture", description = "Blend between the picture's pixels rather than keeping them sharp. Suits imported photos more than pixel art", position = 6)
	default boolean smooth()
	{
		return false;
	}

	@ConfigItem(keyName = "paintArea", name = "Paint", description = "Sail cloth: only the cloth, leaving ropes and spars alone. Whole sail model: everything the sail is made of, if the cloth is not being picked out properly", section = troubleshooting, position = 0)
	default PaintArea paintArea()
	{
		return PaintArea.CLOTH;
	}

	@ConfigItem(keyName = "debug", name = "Show debug info", description = "Label every object on your boat with its ID, and the sail with how much of it is being painted", section = troubleshooting, position = 1)
	default boolean debug()
	{
		return false;
	}
}
