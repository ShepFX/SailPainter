package com.sailpainter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Projection;
import net.runelite.api.Renderable;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Draws the design over the sail of your boat each frame. The game gives no way to change what a
 * sail looks like, so this renders the sail again on top: it projects the sail's own model the way
 * the client does, works out which pixels of cloth nothing on the boat is in front of, and paints
 * the picture across those.
 */
class SailOverlay extends Overlay implements Projector
{
	/** Vertices closer to the camera than this cannot be placed on screen; the client uses the same limit. */
	private static final float NEAR = 50;
	private static final Color DEBUG_SAIL = new Color(80, 255, 120);
	private static final Color DEBUG_OTHER = Color.WHITE;

	private final Client client;
	private final SailPainterPlugin plugin;
	private final SailPainterConfig config;
	private final SailRaster raster = new SailRaster();
	private final List<SailMesh> meshes = new ArrayList<>();
	private final List<GameObject> sails = new ArrayList<>();
	private final float[] point = new float[4];
	private final float[] bounds = new float[4];
	private float[] occluderX = new float[0];
	private float[] occluderY = new float[0];
	private float[] occluderDepth = new float[0];

	// This frame's camera, for the boat being drawn.
	private Projection projection;
	private float centreX;
	private float centreY;
	private float scale;

	@Inject
	SailOverlay(Client client, SailPainterPlugin plugin, SailPainterConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		// Drawn first, so other plugins' markers stay on top of the picture.
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean paint = config.paintSail();
		boolean debug = config.debug();
		if (!paint && !debug)
		{
			plugin.reportStatus(SailPainterPlugin.Status.OFF);
			return null;
		}
		Design design = plugin.getDesign();
		boolean wholeModel = config.paintArea() == PaintArea.WHOLE_MODEL;

		int viewX = client.getViewportXOffset();
		int viewY = client.getViewportYOffset();
		int viewWidth = client.getViewportWidth();
		int viewHeight = client.getViewportHeight();
		centreX = viewWidth / 2f + viewX;
		centreY = viewHeight / 2f + viewY;
		scale = client.getScale();

		SailPainterPlugin.Status status = SailPainterPlugin.Status.NO_BOAT;
		for (WorldView boat : plugin.boats())
		{
			projection = boat.getCanvasProjection();
			if (projection == null) continue;
			Collection<GameObject> objects = plugin.objectsOn(boat.getId());

			sails.clear();
			for (GameObject object : objects)
			{
				if (SailObjects.isSail(shownId(object))) sails.add(object);
				else if (debug) label(graphics, object, describe(object), DEBUG_OTHER);
			}
			if (sails.isEmpty())
			{
				status = better(status, SailPainterPlugin.Status.NO_SAIL);
				continue;
			}
			if (paint && design.isBlank())
			{
				status = better(status, SailPainterPlugin.Status.BLANK);
				if (!debug) continue;
			}

			// Each sail is copied out before any other model is fetched, since animated models share a buffer.
			int count = 0;
			bounds[0] = bounds[1] = Float.MAX_VALUE;
			bounds[2] = bounds[3] = -Float.MAX_VALUE;
			for (GameObject sail : sails)
			{
				SailMesh mesh = mesh(count);
				boolean prepared = prepare(sail, mesh, wholeModel);
				if (debug) label(graphics, sail, describe(sail) + " sail, cloth " + (prepared ? mesh.clothFaces : 0) + "/" + mesh.faceCount, DEBUG_SAIL);
				if (prepared && mesh.addClothBounds(bounds)) count++;
			}

			if (!paint || design.isBlank()) continue;
			int left = Math.max(viewX, (int) Math.floor(bounds[0]));
			int top = Math.max(viewY, (int) Math.floor(bounds[1]));
			int right = Math.min(viewX + viewWidth, (int) Math.ceil(bounds[2]) + 1);
			int bottom = Math.min(viewY + viewHeight, (int) Math.ceil(bounds[3]) + 1);
			if (count == 0 || left >= right || top >= bottom)
			{
				status = better(status, SailPainterPlugin.Status.NOT_IN_VIEW);
				continue;
			}

			raster.begin(left, top, right - left, bottom - top);
			raster.setPicture(design, config.opacity() * 255 / 100, config.shading() / 100f, config.smooth());
			for (int i = 0; i < count; i++) meshes.get(i).occlude(raster);
			if (config.occlusion())
			{
				occludeObjects(objects);
				occludeActors(boat);
			}
			boolean readable = config.readableBothSides();
			for (int i = 0; i < count; i++)
			{
				SailMesh mesh = meshes.get(i);
				mesh.paint(raster, readable && mesh.mirrored());
			}
			graphics.drawImage(raster.image(), left, top, right, bottom, 0, 0, right - left, bottom - top, null);
			status = SailPainterPlugin.Status.PAINTING;
		}
		plugin.reportStatus(paint ? status : SailPainterPlugin.Status.OFF);
		return null;
	}

	@Override
	public void project(int count, float[] x, float[] y, float[] z, int localX, int localY, int localZ, int orientation,
		float[] screenX, float[] screenY, float[] inverseDepth)
	{
		orientation &= 2047;
		float sin = Perspective.SINE[orientation] / 65536f;
		float cos = Perspective.COSINE[orientation] / 65536f;
		for (int i = 0; i < count; i++)
		{
			// Turned about the vertical the same way the client turns a model before placing it.
			float across = x[i];
			float along = z[i];
			if (orientation != 0)
			{
				float turned = across * cos + along * sin;
				along = along * cos - across * sin;
				across = turned;
			}
			projection.project(across + localX, y[i] + localZ, along + localY, point);
			float distance = point[2];
			if (distance < NEAR)
			{
				inverseDepth[i] = 0;
				continue;
			}
			screenX[i] = centreX + point[0] * scale / distance;
			screenY[i] = centreY + point[1] * scale / distance;
			inverseDepth[i] = 1 / distance;
		}
	}

	private boolean prepare(GameObject sail, SailMesh mesh, boolean wholeModel)
	{
		Renderable renderable = sail.getRenderable();
		Model model = modelOf(renderable);
		if (model == null)
		{
			mesh.faceCount = 0;
			return false;
		}
		mesh.build(model.getVerticesCount(), model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(),
			model.getFaceCount(), model.getFaceIndices1(), model.getFaceIndices2(), model.getFaceIndices3(),
			model.getFaceColors1(), model.getFaceColors2(), model.getFaceColors3(),
			model.getUnlitFaceColors(), model.getFaceTextures(), model.getFaceTransparencies(), wholeModel);
		if (mesh.clothFaces == 0) return false;
		mesh.project(this, sail.getX(), sail.getY(), sail.getZ() - renderable.getAnimationHeightOffset(), sail.getModelOrientation());
		return true;
	}

	/** Everything else on the boat goes into the depth buffer, so the mast and the rest can stand in front of the sail. */
	private void occludeObjects(Collection<GameObject> objects)
	{
		for (GameObject object : objects)
		{
			if (sails.contains(object)) continue;
			Renderable renderable = object.getRenderable();
			Model model = modelOf(renderable);
			if (model == null) continue;
			occludeModel(model, object.getX(), object.getY(), object.getZ() - renderable.getAnimationHeightOffset(), object.getModelOrientation());
		}
	}

	/** You, your crew and anyone else aboard. */
	private void occludeActors(WorldView boat)
	{
		Player me = client.getLocalPlayer();
		boolean meSeen = false;
		for (Player player : boat.players())
		{
			if (player == null) continue;
			meSeen |= player == me;
			occludeActor(player, boat);
		}
		for (NPC npc : boat.npcs())
		{
			if (npc != null) occludeActor(npc, boat);
		}
		if (me != null && !meSeen) occludeActor(me, boat);
	}

	private void occludeActor(Actor actor, WorldView boat)
	{
		WorldView view = actor.getWorldView();
		if (view == null || view.getId() != boat.getId()) return;
		LocalPoint location = actor.getLocalLocation();
		if (location == null) return;
		Model model = actor.getModel();
		if (model == null) return;
		int z = Perspective.getFootprintTileHeight(client, location, boat.getPlane(), actor.getFootprintSize()) - actor.getAnimationHeightOffset();
		occludeModel(model, location.getX(), location.getY(), z, actor.getCurrentOrientation());
	}

	private void occludeModel(Model model, int localX, int localY, int localZ, int orientation)
	{
		int vertices = model.getVerticesCount();
		if (occluderX.length < vertices)
		{
			int size = Math.max(vertices, occluderX.length * 2);
			occluderX = new float[size];
			occluderY = new float[size];
			occluderDepth = new float[size];
		}
		project(vertices, model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(), localX, localY, localZ, orientation, occluderX, occluderY, occluderDepth);

		int[] f1 = model.getFaceIndices1();
		int[] f2 = model.getFaceIndices2();
		int[] f3 = model.getFaceIndices3();
		int[] colours3 = model.getFaceColors3();
		byte[] transparencies = model.getFaceTransparencies();
		for (int f = 0, faces = model.getFaceCount(); f < faces; f++)
		{
			if (colours3[f] == -2) continue;
			if (transparencies != null && (transparencies[f] & 0xff) >= SailMesh.SEE_THROUGH) continue;
			int i = f1[f], j = f2[f], k = f3[f];
			float zi = occluderDepth[i], zj = occluderDepth[j], zk = occluderDepth[k];
			if (zi <= 0 || zj <= 0 || zk <= 0) continue;
			float xi = occluderX[i], xj = occluderX[j], xk = occluderX[k];
			float yi = occluderY[i], yj = occluderY[j], yk = occluderY[k];
			// The game only draws faces turned towards the camera.
			if ((xj - xi) * (yk - yi) - (yj - yi) * (xk - xi) >= 0) continue;
			raster.occlude(xi, yi, zi, xj, yj, zj, xk, yk, zk);
		}
	}

	private SailMesh mesh(int index)
	{
		while (meshes.size() <= index) meshes.add(new SailMesh());
		return meshes.get(index);
	}

	/** The object's ID, or for one that changes with game state, the ID it is showing as now. */
	private int shownId(GameObject object)
	{
		int id = object.getId();
		ObjectComposition definition = client.getObjectDefinition(id);
		if (definition == null || definition.getImpostorIds() == null) return id;
		ObjectComposition shown = definition.getImpostor();
		return shown == null ? -1 : shown.getId();
	}

	private String describe(GameObject object)
	{
		int shown = shownId(object);
		return shown == object.getId() ? String.valueOf(shown) : object.getId() + ">" + shown;
	}

	private static void label(Graphics2D graphics, GameObject object, String text, Color color)
	{
		Point location = object.getCanvasLocation();
		if (location != null) OverlayUtil.renderTextLocation(graphics, location, text, color);
	}

	private static Model modelOf(Renderable renderable)
	{
		if (renderable == null) return null;
		return renderable instanceof Model ? (Model) renderable : renderable.getModel();
	}

	private static SailPainterPlugin.Status better(SailPainterPlugin.Status current, SailPainterPlugin.Status candidate)
	{
		return candidate.ordinal() > current.ordinal() ? candidate : current;
	}
}
