package com.sailpainter;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Sail Painter",
	description = "Draw a picture and see it on your boat's sail",
	tags = {"sailing", "sail", "boat", "ship", "paint", "draw", "custom", "cosmetic", "pixel art", "party", "community"},
	internalName = "sail-painter"
)
public class SailPainterPlugin extends Plugin
{
	static final int DEFAULT_SIZE = 128;
	/** Imported pictures are shrunk to fit this; a sail rarely covers more screen than that. */
	static final int IMPORT_SIZE = 256;
	private static final long SAVE_DELAY_MS = 750;
	/** The overlay reports in every frame; a report older than this means it is not running. */
	private static final long STATUS_STALE_MS = 2000;
	/** How often the community list and your own sail's state are fetched again. */
	private static final long COMMUNITY_REFRESH_MINUTES = 5;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private SailPainterConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private SailOverlay overlay;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ScheduledExecutorService executor;
	@Inject private PartyService partyService;
	@Inject private WSClient wsClient;
	@Inject private OkHttpClient okHttpClient;
	@Inject private Gson gson;
	@Inject private ConfigManager configManager;

	private DesignStore store;
	/** Whether the party was last told about a design, so that it can be told when that stops. */
	private volatile boolean shared;

	// Client thread state.
	/** Every object standing on a boat, by the boat's world view, kept up from spawn events. */
	private final Map<Integer, Set<GameObject>> boatObjects = new HashMap<>();
	private final List<Boat> boats = new ArrayList<>();
	private final List<Boat> boatPool = new ArrayList<>();

	// Swing thread state.
	private SailPainterPanel panel;
	private StudioWindow studio;
	private NavigationButton navigationButton;

	// Shared.
	private volatile Design design = Design.blank(DEFAULT_SIZE, DEFAULT_SIZE);
	private volatile Status status = Status.NO_BOAT;
	private volatile long statusAt;
	private volatile String localName;
	/** Designs shared by other party members, by member. Filled from the party connection's thread. */
	private final Map<Long, PartyDesign> partyDesigns = new ConcurrentHashMap<>();
	private ScheduledFuture<?> pendingSave;
	private ScheduledFuture<?> communityRefresh;
	/** Made on the executor at start, since finding the plugin's folder is disk work. */
	private volatile CommunitySails community;
	private volatile long accountHash = -1;
	/** The server's word on your own sail, or null before it has answered. */
	private volatile CommunitySails.Mine mine;
	/** The last thing that went wrong talking to the sail server, shown until the next success. */
	private volatile String communityProblem;
	private volatile Set<String> hiddenSails = Collections.emptySet();
	/** Community sails on boats around you, worked out each tick for the panel. */
	private volatile List<Nearby> nearby = Collections.emptyList();

	/** A community sail on a boat in view. */
	static final class Nearby
	{
		final String id;
		final String name;
		final String key;
		final boolean hidden;

		Nearby(String id, String name, String key, boolean hidden)
		{
			this.id = id;
			this.name = name;
			this.key = key;
			this.hidden = hidden;
		}

		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof Nearby)) return false;
			Nearby that = (Nearby) other;
			return id.equals(that.id) && hidden == that.hidden;
		}

		@Override
		public int hashCode()
		{
			return id.hashCode() * 31 + (hidden ? 1 : 0);
		}
	}

	/** A boat to paint and the design that goes on it. */
	static final class Boat
	{
		WorldView view;
		Design design;
		/** Yours, or the one you are aboard, rather than a party member's. */
		boolean mine;
	}

	private static final class PartyDesign
	{
		/** The sender's in-game name, standardised for comparing. */
		final String name;
		final Design design;

		PartyDesign(String name, Design design)
		{
			this.name = name;
			this.design = design;
		}
	}

	/** What the overlay last found. With more than one boat, the later of the first five wins. */
	enum Status
	{
		NO_BOAT("Board your boat, or go near it, to see your sail painted."),
		NO_SAIL("Your boat is here, but its sail was not recognised. Turn on Show debug info in the settings to see the IDs on board."),
		BLANK("Your boat is here. Draw something to paint its sail."),
		NOT_IN_VIEW("Your sail is out of view."),
		PAINTING("Painting your sail."),
		OFF("Painting is switched off in the settings."),
		LOGGED_OUT("Log in and board your boat to see your sail painted.");

		final String text;

		Status(String text)
		{
			this.text = text;
		}
	}

	@Provides
	SailPainterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SailPainterConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		panel = new SailPainterPanel(this);
		navigationButton = NavigationButton.builder()
			.tooltip("Sail Painter")
			.icon(ImageUtil.loadImageResource(getClass(), "panel_icon.png"))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		wsClient.registerMessage(SailPainterDesign.class);
		hiddenSails = CommunitySails.parseHidden(config.hiddenSails());
		executor.execute(this::loadDesign);
		executor.execute(this::startCommunity);
		clientThread.invokeLater(this::scanAll);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navigationButton);
		if (communityRefresh != null) communityRefresh.cancel(false);
		communityRefresh = null;
		community = null;
		mine = null;
		nearby = Collections.emptyList();
		flushSave();
		// Tell the party this sail is going plain while the message type is still known, then stop listening.
		// Sending nothing needs no encoding, and the send itself only queues.
		share(false);
		wsClient.unregisterMessage(SailPainterDesign.class);
		partyDesigns.clear();
		clientThread.invoke(boatObjects::clear);
		SailPainterPanel oldPanel = panel;
		StudioWindow oldStudio = studio;
		panel = null;
		studio = null;
		navigationButton = null;
		SwingUtilities.invokeLater(() ->
		{
			if (oldPanel != null) oldPanel.stop();
			if (oldStudio != null) oldStudio.dispose();
		});
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		track(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject object = event.getGameObject();
		WorldView view = object.getWorldView();
		if (view != null)
		{
			Set<GameObject> objects = boatObjects.get(view.getId());
			if (objects != null) objects.remove(object);
			return;
		}
		for (Set<GameObject> objects : boatObjects.values()) objects.remove(object);
	}

	@Subscribe
	public void onWorldViewLoaded(WorldViewLoaded event)
	{
		WorldView view = event.getWorldView();
		if (view.isTopLevel()) return;
		// Objects already on the boat when it loads might not each be announced, so look once it has settled.
		clientThread.invokeLater(() ->
		{
			if (client.getWorldView(view.getId()) == view) scan(view);
		});
	}

	@Subscribe
	public void onWorldViewUnloaded(WorldViewUnloaded event)
	{
		boatObjects.remove(event.getWorldView().getId());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			boatObjects.clear();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// The party matches a design to a boat by the sender's name, which is only known once logged in.
		Player me = client.getLocalPlayer();
		String name = me == null ? null : me.getName();
		if (name != null && !name.equals(localName))
		{
			localName = name;
			shareSoon();
		}
		long hash = client.getAccountHash();
		if (hash != accountHash)
		{
			accountHash = hash;
			mine = null;
			if (hash != -1) executor.execute(this::refreshMine);
		}
		findNearby(me);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!SailPainterConfig.GROUP.equals(event.getGroup())) return;
		if ("shareWithParty".equals(event.getKey())) shareSoon();
		if ("hiddenSails".equals(event.getKey())) hiddenSails = CommunitySails.parseHidden(config.hiddenSails());
		if ("communitySails".equals(event.getKey()))
		{
			communityProblem = null;
			if (config.communitySails()) executor.execute(this::refreshCommunity);
			else
			{
				mine = null;
				nearby = Collections.emptyList();
			}
		}
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		partyDesigns.clear();
		shareSoon();
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		// Someone new: they need this design too. Joining ourselves comes as a join as well.
		shareSoon();
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		partyDesigns.remove(event.getMemberId());
	}

	/** Party connection thread. Another member's design, or word that they stopped sharing it. */
	@Subscribe
	public void onSailPainterDesign(SailPainterDesign message)
	{
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getMemberId() == message.getMemberId()) return;
		if (message.getName() == null || message.getPng() == null)
		{
			partyDesigns.remove(message.getMemberId());
			return;
		}
		try
		{
			Design received = DesignCodec.unshare(message.getPng());
			partyDesigns.put(message.getMemberId(), new PartyDesign(Text.standardize(message.getName()), received));
		}
		catch (IOException e)
		{
			log.debug("Ignoring an unreadable sail design from party member {}", message.getMemberId(), e);
		}
	}

	/**
	 * Client thread. The boats to paint and what goes on each: your design on your own boat, a party
	 * member's on the boat they are aboard if they share theirs, and yours on any other boat you are
	 * aboard.
	 */
	List<Boat> boats()
	{
		boats.clear();
		WorldView top = client.getTopLevelWorldView();
		if (top == null) return boats;
		Player me = client.getLocalPlayer();
		WorldView aboard = me == null ? null : me.getWorldView();
		if (aboard != null && aboard.isTopLevel()) aboard = null;
		boolean showParty = config.showPartySails() && !partyDesigns.isEmpty();
		CommunitySails sails = config.communitySails() ? community : null;
		if (sails != null && sails.size() == 0) sails = null;

		boolean aboardSeen = false;
		for (WorldEntity entity : top.worldEntities())
		{
			WorldView view = entity.getWorldView();
			if (view == null) continue;
			boolean isAboard = aboard != null && view.getId() == aboard.getId();
			aboardSeen |= isAboard;
			// The game hides a boat that overlaps another; its sail should vanish with it.
			if (entity.isHiddenForOverlap()) continue;
			if (entity.getOwnerType() == WorldEntity.OWNER_TYPE_SELF_PLAYER)
			{
				addBoat(view, design, true);
				continue;
			}
			Design theirs = showParty ? partyDesignAboard(view, me) : null;
			if (theirs == null && sails != null) theirs = communityDesignAboard(sails, view, me);
			if (theirs != null) addBoat(view, theirs, false);
			else if (isAboard) addBoat(view, design, true);
		}
		if (aboard != null && !aboardSeen) addBoat(aboard, design, true);
		return boats;
	}

	/** The design of a sharing party member standing on the boat, if any. */
	private Design partyDesignAboard(WorldView view, Player me)
	{
		for (Player player : view.players())
		{
			if (player == null || player == me || player.getName() == null) continue;
			String name = Text.standardize(player.getName());
			for (PartyDesign shared : partyDesigns.values())
			{
				if (shared.name.equals(name)) return shared.design;
			}
		}
		return null;
	}

	/** The approved sail of someone standing on the boat, unless you hid it or it is still loading. */
	private Design communityDesignAboard(CommunitySails sails, WorldView view, Player me)
	{
		Set<String> hidden = hiddenSails;
		for (Player player : view.players())
		{
			if (player == null || player == me || player.getName() == null) continue;
			String key = CommunitySails.nameKey(player.getName());
			if (hidden.contains(key)) continue;
			CommunitySails.Listing listing = sails.listing(key);
			if (listing == null) continue;
			Design design = sails.design(listing);
			if (design != null) return design;
		}
		return null;
	}

	/** Client thread, once a tick. The community sails on boats around you, for the panel to list. */
	private void findNearby(Player me)
	{
		CommunitySails sails = config.communitySails() ? community : null;
		WorldView top = client.getTopLevelWorldView();
		if (sails == null || top == null)
		{
			if (!nearby.isEmpty()) nearby = Collections.emptyList();
			return;
		}
		Set<String> hidden = hiddenSails;
		Map<String, Nearby> found = new LinkedHashMap<>();
		for (WorldEntity entity : top.worldEntities())
		{
			WorldView view = entity.getWorldView();
			if (view == null || entity.getOwnerType() == WorldEntity.OWNER_TYPE_SELF_PLAYER) continue;
			for (Player player : view.players())
			{
				if (player == null || player == me || player.getName() == null) continue;
				String key = CommunitySails.nameKey(player.getName());
				CommunitySails.Listing listing = sails.listing(key);
				if (listing != null) found.putIfAbsent(key, new Nearby(listing.id, listing.name, key, hidden.contains(key)));
			}
		}
		List<Nearby> list = new ArrayList<>(found.values());
		if (!list.equals(nearby)) nearby = Collections.unmodifiableList(list);
	}

	private void addBoat(WorldView view, Design design, boolean mine)
	{
		if (boatPool.size() <= boats.size()) boatPool.add(new Boat());
		Boat boat = boatPool.get(boats.size());
		boat.view = view;
		boat.design = design;
		boat.mine = mine;
		boats.add(boat);
	}

	/** Client thread. Everything standing on the boat with the given world view. */
	Collection<GameObject> objectsOn(int worldViewId)
	{
		Set<GameObject> objects = boatObjects.get(worldViewId);
		return objects == null ? Collections.emptySet() : objects;
	}

	Design getDesign()
	{
		return design;
	}

	/**
	 * Any thread. Replaces the design on the sail. Saving is held back briefly so that a stroke
	 * being drawn is written once when it settles rather than on every mouse movement.
	 *
	 * @param source whoever made the change, which is not told about it again
	 */
	void setDesign(Design design, boolean save, Object source)
	{
		this.design = design;
		if (save) scheduleSave();
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null) panel.designChanged(design);
			if (studio != null && source != studio) studio.load(design);
		});
	}

	/** Swing thread. The picture becomes the design, scaled down if it is bigger than a sail can show. */
	void importPicture(BufferedImage image, Object source)
	{
		setDesign(Design.fromImage(image, IMPORT_SIZE), true, source);
	}

	void clearDesign(Object source)
	{
		Design current = design;
		setDesign(Design.blank(current.width(), current.height()), true, source);
	}

	/** Swing thread. */
	void openStudio()
	{
		if (studio == null) studio = new StudioWindow(this);
		studio.load(design);
		studio.setVisible(true);
		studio.toFront();
	}

	SailPainterConfig getConfig()
	{
		return config;
	}

	void reportStatus(Status status)
	{
		this.status = status;
		statusAt = System.currentTimeMillis();
	}

	Status getStatus()
	{
		if (System.currentTimeMillis() - statusAt > STATUS_STALE_MS) return Status.LOGGED_OUT;
		return status;
	}

	private void track(GameObject object)
	{
		WorldView view = object.getWorldView();
		if (view == null || view.isTopLevel()) return;
		boatObjects.computeIfAbsent(view.getId(), id -> Collections.newSetFromMap(new IdentityHashMap<>())).add(object);
	}

	private void scanAll()
	{
		WorldView top = client.getTopLevelWorldView();
		if (top == null) return;
		for (WorldView view : top.worldViews()) scan(view);
	}

	private void scan(WorldView view)
	{
		Scene scene = view.getScene();
		if (scene == null) return;
		for (Tile[][] plane : scene.getTiles())
		{
			if (plane == null) continue;
			for (Tile[] column : plane)
			{
				if (column == null) continue;
				for (Tile tile : column)
				{
					if (tile == null) continue;
					GameObject[] objects = tile.getGameObjects();
					if (objects == null) continue;
					for (GameObject object : objects)
					{
						if (object != null) track(object);
					}
				}
			}
		}
	}

	/** Party status for the panel, or null when there is nothing to say. */
	String partyText()
	{
		int showing = config.showPartySails() ? partyDesigns.size() : 0;
		String received = showing == 0 ? "" : " " + showing + (showing == 1 ? " party member shares" : " party members share") + " their sail with you.";
		if (!config.shareWithParty()) return showing == 0 ? null : received.trim();
		if (!partyService.isInParty()) return "Sharing is on, but you are not in a party. Join one from RuneLite's Party panel." + received;
		return "Your sail is shared with your party." + received;
	}

	// --- Community sails ---

	/** Executor thread. */
	private void startCommunity()
	{
		try
		{
			community = new CommunitySails(okHttpClient, gson, executor, getPluginDirectory());
		}
		catch (IOException e)
		{
			log.warn("Could not open the folder for community sails", e);
			return;
		}
		communityRefresh = executor.scheduleWithFixedDelay(this::refreshCommunity, 0, COMMUNITY_REFRESH_MINUTES, TimeUnit.MINUTES);
	}

	/** Executor thread. Nothing is fetched unless the player turned community sails on. */
	private void refreshCommunity()
	{
		CommunitySails sails = community;
		if (sails == null || !config.communitySails()) return;
		sails.refresh();
		refreshMine();
	}

	private void refreshMine()
	{
		CommunitySails sails = community;
		long hash = accountHash;
		if (sails == null || !config.communitySails() || hash == -1) return;
		sails.mine(hash, this::gotMine, problem -> communityProblem = problem);
	}

	private void gotMine(CommunitySails.Mine answer)
	{
		mine = answer;
		communityProblem = null;
	}

	/** Swing thread. Sends the design for the moderator to check; answers come back through the callbacks. */
	void submitSail(Consumer<String> done)
	{
		CommunitySails sails = community;
		long hash = accountHash;
		String name = localName;
		Design current = design;
		if (sails == null || !config.communitySails()) return;
		if (hash == -1 || name == null)
		{
			done.accept("Log in first, so your sail can be matched to your character.");
			return;
		}
		if (current.isBlank())
		{
			done.accept("Draw something first.");
			return;
		}
		executor.execute(() -> sails.submit(hash, Text.sanitize(name), current, answer ->
		{
			gotMine(answer);
			done.accept("Sent. Your sail will appear to others once it has been checked.");
		}, done));
	}

	/** Swing thread. Takes your sail off the list, or out of the queue. */
	void withdrawSail(Consumer<String> done)
	{
		CommunitySails sails = community;
		long hash = accountHash;
		if (sails == null || hash == -1) return;
		sails.withdraw(hash, answer ->
		{
			gotMine(answer);
			done.accept("Your sail is no longer shown to others.");
		}, done);
	}

	/** Swing thread. Reports a sail to the moderator and hides it for you straight away. */
	void reportSail(Nearby sail, String reason, String note, Consumer<String> done)
	{
		CommunitySails sails = community;
		long hash = accountHash;
		if (sails == null) return;
		if (hash == -1)
		{
			done.accept("Log in first.");
			return;
		}
		setHidden(sail.key, true);
		sails.report(hash, sail.id, reason, note, () -> done.accept("Reported, thank you. " + sail.name + "'s sail is hidden for you."), done);
	}

	/** Any thread. Hides or shows a player's community sail, for you only. */
	void setHidden(String key, boolean hide)
	{
		Set<String> next = new LinkedHashSet<>(hiddenSails);
		if (hide ? next.add(key) : next.remove(key))
		{
			hiddenSails = Collections.unmodifiableSet(next);
			configManager.setConfiguration(SailPainterConfig.GROUP, "hiddenSails", CommunitySails.formatHidden(next));
		}
	}

	Set<String> getHiddenSails()
	{
		return hiddenSails;
	}

	List<Nearby> getNearby()
	{
		return nearby;
	}

	boolean communityOn()
	{
		return config.communitySails() && community != null;
	}

	void openGallery()
	{
		LinkBrowser.browse(CommunitySails.SITE);
	}

	/** Your own sail's state for the panel. */
	String communityText()
	{
		if (!config.communitySails()) return "Turn on Community sails in the settings to see other players' sails and share yours.";
		String problem = communityProblem;
		if (problem != null) return problem;
		if (accountHash == -1) return "Log in to submit your sail.";
		CommunitySails.Mine answer = mine;
		if (answer == null) return "Checking your sail…";
		if (answer.banned) return "You can no longer submit sails.";
		CommunitySails.MySail latest = answer.sails == null || answer.sails.isEmpty() ? null : answer.sails.get(0);
		CommunitySails.MySail live = null;
		if (answer.sails != null)
		{
			for (CommunitySails.MySail sail : answer.sails)
			{
				if ("live".equals(sail.status))
				{
					live = sail;
					break;
				}
			}
		}
		if (latest == null) return "You have not submitted a sail yet.";
		String reason = latest.reason == null || latest.reason.isEmpty() ? "" : " Reason: " + latest.reason;
		switch (latest.status)
		{
			case "pending":
				return live == null ? "Your sail is waiting to be checked." : "Your new sail is waiting to be checked. Others still see your last one.";
			case "live":
				return "Your sail is approved. Everyone with Community sails on sees it.";
			case "rejected":
				return (live == null ? "Your sail was not approved." : "Your new sail was not approved; your last one is still shown.") + reason;
			case "removed":
				return "Your sail was taken down." + reason;
			case "withdrawn":
				return "You withdrew your sail.";
			default:
				return live == null ? "You have no sail shown to others." : "Your sail is approved. Everyone with Community sails on sees it.";
		}
	}

	/** Whether there is anything of yours to withdraw. */
	boolean hasOpenSail()
	{
		CommunitySails.Mine answer = mine;
		if (answer == null || answer.sails == null) return false;
		for (CommunitySails.MySail sail : answer.sails)
		{
			if ("live".equals(sail.status) || "pending".equals(sail.status)) return true;
		}
		return false;
	}

	/** Executor thread. Loads the saved design from the plugin's own folder. */
	private void loadDesign()
	{
		try
		{
			Design saved = store().load();
			if (saved != null) setDesign(saved, false, null);
		}
		catch (IOException e)
		{
			log.warn("Could not load the saved sail design", e);
		}
	}

	/** Executor thread. The plugin's folder is made on first use, which is disk work. */
	private synchronized DesignStore store() throws IOException
	{
		if (store == null) store = new DesignStore(getPluginDirectory());
		return store;
	}

	/** Sends the design to the party a moment from now, off the thread that asked. */
	private void shareSoon()
	{
		executor.execute(() -> share(config.shareWithParty()));
	}

	/**
	 * Sends the current design to the party, or, when sharing has just been turned off, an empty one
	 * so that it disappears from their screens too. Encoding is work, so turning it on belongs on the executor.
	 */
	private synchronized void share(boolean on)
	{
		if (!partyService.isInParty() || localName == null)
		{
			shared = false;
			return;
		}
		if (!on && !shared) return;
		String png = null;
		Design current = design;
		if (on && !current.isBlank())
		{
			try
			{
				png = DesignCodec.share(current);
			}
			catch (IOException e)
			{
				log.debug("Could not prepare the sail design for the party", e);
				return;
			}
		}
		partyService.send(new SailPainterDesign(localName, png));
		shared = png != null;
	}

	private synchronized void scheduleSave()
	{
		if (pendingSave != null) pendingSave.cancel(false);
		pendingSave = executor.schedule(() ->
		{
			save(design);
			share(config.shareWithParty());
		}, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
	}

	/** Writes a save that was still waiting, without waiting for it. */
	private synchronized void flushSave()
	{
		if (pendingSave == null || pendingSave.isDone()) return;
		pendingSave.cancel(false);
		pendingSave = null;
		Design latest = design;
		executor.execute(() -> save(latest));
	}

	private void save(Design design)
	{
		try
		{
			store().save(design);
		}
		catch (IOException e)
		{
			log.warn("Could not save the sail design", e);
		}
	}
}
