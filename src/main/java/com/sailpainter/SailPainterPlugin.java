package com.sailpainter;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Sail Painter",
	description = "Draw a picture and see it on your boat's sail",
	tags = {"sailing", "sail", "boat", "ship", "paint", "draw", "custom", "cosmetic", "pixel art"}
)
public class SailPainterPlugin extends Plugin
{
	static final int DEFAULT_SIZE = 128;
	/** Imported pictures are shrunk to fit this; a sail rarely covers more screen than that. */
	static final int IMPORT_SIZE = 256;
	private static final long SAVE_DELAY_MS = 750;
	/** The overlay reports in every frame; a report older than this means it is not running. */
	private static final long STATUS_STALE_MS = 2000;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private SailPainterConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private SailOverlay overlay;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ScheduledExecutorService executor;

	private final DesignStore store = new DesignStore();

	// Client thread state.
	/** Every object standing on a boat, by the boat's world view, kept up from spawn events. */
	private final Map<Integer, Set<GameObject>> boatObjects = new HashMap<>();
	private final List<WorldView> boats = new ArrayList<>();

	// Swing thread state.
	private SailPainterPanel panel;
	private StudioWindow studio;
	private NavigationButton navigationButton;

	// Shared.
	private volatile Design design = Design.blank(DEFAULT_SIZE, DEFAULT_SIZE);
	private volatile Status status = Status.NO_BOAT;
	private volatile long statusAt;
	private ScheduledFuture<?> pendingSave;

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
		executor.execute(this::loadDesign);
		clientThread.invokeLater(this::scanAll);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navigationButton);
		flushSave();
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

	/** Client thread. The boats to paint: your own, and whichever one you are aboard. */
	List<WorldView> boats()
	{
		boats.clear();
		WorldView top = client.getTopLevelWorldView();
		if (top == null) return boats;
		Player me = client.getLocalPlayer();
		WorldView aboard = me == null ? null : me.getWorldView();
		if (aboard != null && aboard.isTopLevel()) aboard = null;

		boolean aboardSeen = false;
		for (WorldEntity entity : top.worldEntities())
		{
			WorldView view = entity.getWorldView();
			if (view == null) continue;
			boolean isAboard = aboard != null && view.getId() == aboard.getId();
			aboardSeen |= isAboard;
			if (entity.getOwnerType() != WorldEntity.OWNER_TYPE_SELF_PLAYER && !isAboard) continue;
			// The game hides a boat that overlaps another; its sail should vanish with it.
			if (entity.isHiddenForOverlap()) continue;
			boats.add(view);
		}
		if (aboard != null && !aboardSeen) boats.add(aboard);
		return boats;
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

	private void loadDesign()
	{
		try
		{
			Design saved = store.load();
			if (saved != null) setDesign(saved, false, null);
		}
		catch (IOException e)
		{
			log.warn("Could not load the saved sail design", e);
		}
	}

	private synchronized void scheduleSave()
	{
		if (pendingSave != null) pendingSave.cancel(false);
		pendingSave = executor.schedule(() -> save(design), SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
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
			store.save(design);
		}
		catch (IOException e)
		{
			log.warn("Could not save the sail design", e);
		}
	}
}
