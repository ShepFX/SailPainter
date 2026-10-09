package com.sailpainter;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Sails other players submitted and the moderator approved, from sails.shep.rip. The whole list is
 * downloaded and every picture kept on disk, so the server is never asked about anyone in
 * particular and so never learns who is near whom. Only the sails actually in view are decoded.
 */
@Slf4j
class CommunitySails
{
	static final String SITE = "https://sails.shep.rip";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	/** Decoded sails kept in memory; a full screen of boats is well under this. */
	private static final int DECODED_LIMIT = 32;
	private static final String FOLDER = "community";
	private static final Pattern PICTURE_ID = Pattern.compile("[0-9a-f]{16}");
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	private static final Pattern SEPARATORS = Pattern.compile("[ _\\-\\s]+");

	private final OkHttpClient http;
	private final Gson gson;
	private final ExecutorService executor;
	private final Filepath folder;
	private final HttpUrl api;

	/** Live sails by compared name, swapped whole when a new list arrives. */
	private volatile Map<String, Listing> byName = Collections.emptyMap();
	private volatile String etag;
	/** Picture ids being downloaded or decoded, so each is asked for once. */
	private final Set<String> working = ConcurrentHashMap.newKeySet();
	/** Picture ids saved in the folder, so the client thread never has to look at the disk to know. */
	private final Set<String> onDisk = ConcurrentHashMap.newKeySet();
	private final Map<String, Design> decoded = Collections.synchronizedMap(new LinkedHashMap<String, Design>(DECODED_LIMIT, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Design> eldest)
		{
			return size() > DECODED_LIMIT;
		}
	});

	/** A sail on the public list. */
	static final class Listing
	{
		final String id;
		final String name;

		Listing(String id, String name)
		{
			this.id = id;
			this.name = name;
		}
	}

	/** What the server says about the player's own sails. */
	static final class Mine
	{
		boolean banned;
		List<MySail> sails;
	}

	static final class MySail
	{
		String id;
		String name;
		String status;
		String reason;
	}

	private static final class Catalog
	{
		List<CatalogEntry> sails;
	}

	private static final class CatalogEntry
	{
		String id;
		String name;
	}

	private static final class Failure
	{
		String error;
	}

	CommunitySails(OkHttpClient http, Gson gson, ExecutorService executor, Filepath pluginDirectory)
	{
		this(http, gson, executor, pluginDirectory, SITE);
	}

	/** With another server, for tests. */
	CommunitySails(OkHttpClient http, Gson gson, ExecutorService executor, Filepath pluginDirectory, String site)
	{
		this.api = HttpUrl.get(site + "/api/v1/");
		this.http = http;
		this.gson = gson;
		this.executor = executor;
		this.folder = pluginDirectory.joinSegment(FOLDER);
	}

	/**
	 * A name as the game compares it: tags gone, any run of spaces, non-breaking spaces,
	 * underscores and hyphens as one space, and no case. The server compares names the same way.
	 */
	static String nameKey(String name)
	{
		if (name == null) return "";
		return SEPARATORS.matcher(TAGS.matcher(name).replaceAll("")).replaceAll(" ").trim().toLowerCase();
	}

	/** The live sail under this compared name, or null. */
	Listing listing(String nameKey)
	{
		return byName.get(nameKey);
	}

	int size()
	{
		return byName.size();
	}

	/**
	 * Any thread. The sail's picture if it is ready, or null for now while it is loaded from disk
	 * in the background.
	 */
	Design design(Listing listing)
	{
		Design design = decoded.get(listing.id);
		if (design == null && onDisk.contains(listing.id) && working.add(listing.id))
		{
			executor.execute(() ->
			{
				try
				{
					Filepath file = file(listing.id);
					if (file.isFile())
					{
						try (InputStream in = file.openInputStream())
						{
							decoded.put(listing.id, Design.fromImage(DesignCodec.read(in, Design.MAX_SIZE), Design.MAX_SIZE));
						}
					}
				}
				catch (IOException e)
				{
					log.debug("Could not read community sail {}", listing.id, e);
				}
				finally
				{
					working.remove(listing.id);
				}
			});
		}
		return design;
	}

	/** Fetches the list if it changed, then any pictures not already on disk. */
	void refresh()
	{
		Request.Builder request = new Request.Builder().url(api.resolve("sails"));
		String known = etag;
		if (known != null) request.header("If-None-Match", known);
		http.newCall(request.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Could not fetch the community sails", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					// Unchanged, but a picture that failed to download last time gets another go.
					if (response.code() == 304)
					{
						fetchMissing(byName);
						return;
					}
					if (!response.isSuccessful() || response.body() == null) return;
					Catalog catalog = gson.fromJson(response.body().charStream(), Catalog.class);
					if (catalog == null || catalog.sails == null) return;
					Map<String, Listing> fresh = new HashMap<>();
					for (CatalogEntry entry : catalog.sails)
					{
						if (entry == null || entry.id == null || entry.name == null || !PICTURE_ID.matcher(entry.id).matches()) continue;
						fresh.put(nameKey(entry.name), new Listing(entry.id, entry.name));
					}
					byName = Collections.unmodifiableMap(fresh);
					etag = response.header("ETag");
					fetchMissing(fresh);
				}
				catch (IOException | JsonParseException e)
				{
					log.debug("Unreadable community sail list", e);
				}
			}
		});
	}

	/** OkHttp thread. Downloads pictures not yet on disk and deletes ones no longer on the list. */
	private void fetchMissing(Map<String, Listing> listings) throws IOException
	{
		if (!folder.isDirectory()) folder.createDirectories();
		Set<String> wanted = listings.values().stream().map(listing -> listing.id).collect(Collectors.toSet());
		try (Stream<Filepath> files = folder.walk(1))
		{
			for (Filepath file : (Iterable<Filepath>) files::iterator)
			{
				String fileName = file.getFileName();
				if (!file.isFile() || !fileName.endsWith(".png")) continue;
				String id = fileName.substring(0, fileName.length() - 4);
				if (wanted.contains(id)) onDisk.add(id);
				else
				{
					onDisk.remove(id);
					file.deleteIfExists();
				}
			}
		}
		decoded.keySet().retainAll(wanted);
		onDisk.retainAll(wanted);
		for (String id : wanted)
		{
			if (!onDisk.contains(id) && working.add(id)) download(id);
		}
	}

	private void download(String id)
	{
		http.newCall(new Request.Builder().url(api.resolve("sails/" + id + ".png")).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				working.remove(id);
				log.debug("Could not download community sail {}", id, e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					ResponseBody body = response.body();
					if (!response.isSuccessful() || body == null) return;
					byte[] png = body.bytes();
					if (png.length > DesignCodec.MAX_SHARED_BYTES * 2) return;
					// Checked to be a picture of a sensible size before it is kept.
					DesignCodec.read(new ByteArrayInputStream(png), Design.MAX_SIZE);
					Filepath temp = folder.joinSegment(id + ".tmp");
					temp.write(png);
					temp.moveTo(file(id), StandardCopyOption.REPLACE_EXISTING);
					onDisk.add(id);
				}
				catch (IOException e)
				{
					log.debug("Unusable community sail {}", id, e);
				}
				finally
				{
					working.remove(id);
				}
			}
		});
	}

	private Filepath file(String id)
	{
		return folder.joinSegment(id + ".png");
	}

	// --- The player's own sail and reports. Each answers on an OkHttp thread. ---

	void submit(long accountHash, String name, Design design, Consumer<Mine> done, Consumer<String> failed)
	{
		String png;
		try
		{
			png = DesignCodec.share(design);
		}
		catch (IOException e)
		{
			failed.accept("The picture is too detailed to send. Try a smaller canvas.");
			return;
		}
		Map<String, Object> body = new HashMap<>();
		body.put("accountHash", Long.toString(accountHash));
		body.put("name", name);
		body.put("png", png);
		post("submit", body, Mine.class, done, failed);
	}

	void mine(long accountHash, Consumer<Mine> done, Consumer<String> failed)
	{
		post("mine", Collections.singletonMap("accountHash", Long.toString(accountHash)), Mine.class, done, failed);
	}

	void withdraw(long accountHash, Consumer<Mine> done, Consumer<String> failed)
	{
		post("withdraw", Collections.singletonMap("accountHash", Long.toString(accountHash)), Mine.class, done, failed);
	}

	void report(long accountHash, String id, String reason, String note, Runnable done, Consumer<String> failed)
	{
		Map<String, Object> body = new HashMap<>();
		body.put("accountHash", Long.toString(accountHash));
		body.put("id", id);
		body.put("reason", reason);
		body.put("note", note);
		post("report", body, Object.class, answer -> done.run(), failed);
	}

	private <T> void post(String path, Map<String, ?> body, Class<T> type, Consumer<T> done, Consumer<String> failed)
	{
		Request request = new Request.Builder()
			.url(api.resolve(path))
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				failed.accept("Could not reach the sail server. Try again later.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					String text = response.body() == null ? "" : response.body().string();
					if (!response.isSuccessful())
					{
						Failure failure = parse(text, Failure.class);
						failed.accept(failure != null && failure.error != null ? failure.error : "The sail server said no (" + response.code() + ").");
						return;
					}
					done.accept(parse(text, type));
				}
				catch (IOException e)
				{
					failed.accept("Could not reach the sail server. Try again later.");
				}
			}
		});
	}

	private <T> T parse(String text, Class<T> type)
	{
		try
		{
			return gson.fromJson(text, type);
		}
		catch (JsonParseException e)
		{
			return null;
		}
	}

	// --- Hidden sails, kept in the plugin's config as compared names. ---

	static Set<String> parseHidden(String stored)
	{
		Set<String> hidden = new LinkedHashSet<>();
		if (stored == null) return hidden;
		for (String part : stored.split(","))
		{
			String key = nameKey(part);
			if (!key.isEmpty()) hidden.add(key);
		}
		return hidden;
	}

	static String formatHidden(Set<String> hidden)
	{
		return String.join(",", new HashSet<>(hidden).stream().sorted().collect(Collectors.toList()));
	}
}
