package com.sailpainter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CommunitySailsTest
{
	private static final String ID = "0123456789abcdef";

	private HttpServer server;
	private ExecutorService executor;
	private Path folder;
	private CommunitySails sails;
	/** What the stand-in server answers, by path. */
	private final Map<String, byte[]> answers = new ConcurrentHashMap<>();
	private final Map<String, String> received = new ConcurrentHashMap<>();
	private volatile int status = 200;

	@Before
	public void setUp() throws IOException
	{
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::answer);
		server.start();
		executor = Executors.newSingleThreadExecutor();
		folder = Files.createTempDirectory("sails");
		Filepath directory = Filepath.Unchecked.getRooted(folder);
		sails = new CommunitySails(new OkHttpClient(), new Gson(), executor, directory, "http://127.0.0.1:" + server.getAddress().getPort());
	}

	@After
	public void tearDown() throws IOException
	{
		server.stop(0);
		executor.shutdownNow();
		try (java.util.stream.Stream<Path> files = Files.walk(folder))
		{
			files.sorted((a, b) -> b.compareTo(a)).forEach(path -> path.toFile().delete());
		}
	}

	private void answer(HttpExchange exchange) throws IOException
	{
		String path = exchange.getRequestURI().getPath();
		received.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
		byte[] body = answers.get(path);
		int code = body == null ? 404 : status;
		if (body == null) body = "{\"error\":\"Not found.\"}".getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(code, body.length);
		try (OutputStream out = exchange.getResponseBody())
		{
			out.write(body);
		}
	}

	private static void waitFor(BooleanSupplier condition) throws InterruptedException
	{
		long end = System.currentTimeMillis() + 5000;
		while (!condition.getAsBoolean())
		{
			assertTrue("Timed out", System.currentTimeMillis() < end);
			TimeUnit.MILLISECONDS.sleep(20);
		}
	}

	private static byte[] png() throws IOException
	{
		int[] pixels = new int[16];
		Arrays.fill(pixels, 0xffff0000);
		return DesignCodec.png(Design.of(4, 4, pixels));
	}

	@Test
	public void namesCompareTheWayTheGameDoes()
	{
		assertEquals("shep fx", CommunitySails.nameKey("Shep_FX"));
		assertEquals("shep fx", CommunitySails.nameKey("Shep FX"));
		assertEquals("shep fx", CommunitySails.nameKey("  SHEP-fx "));
		assertEquals("shep fx", CommunitySails.nameKey("<col=ffffff>Shep FX</col>"));
	}

	@Test
	public void hiddenSailsSurviveBeingStored()
	{
		Set<String> hidden = CommunitySails.parseHidden("Shep_FX, someone ,,");
		assertEquals(Set.of("shep fx", "someone"), hidden);
		assertEquals(hidden, CommunitySails.parseHidden(CommunitySails.formatHidden(hidden)));
		assertTrue(CommunitySails.parseHidden("").isEmpty());
	}

	@Test
	public void approvedSailsAreDownloadedAndMatchedByName() throws Exception
	{
		answers.put("/api/v1/sails", ("{\"sails\":[{\"id\":\"" + ID + "\",\"name\":\"Shep FX\"}]}").getBytes(StandardCharsets.UTF_8));
		answers.put("/api/v1/sails/" + ID + ".png", png());
		sails.refresh();
		waitFor(() -> Files.exists(folder.resolve("community").resolve(ID + ".png")));

		CommunitySails.Listing listing = sails.listing("shep fx");
		assertNotNull(listing);
		assertNull(sails.listing("someone else"));
		waitFor(() -> sails.design(listing) != null);
		assertEquals(0xffff0000, sails.design(listing).pixel(0, 0));
	}

	@Test
	public void sailsTakenOffTheListAreDeleted() throws Exception
	{
		answers.put("/api/v1/sails", ("{\"sails\":[{\"id\":\"" + ID + "\",\"name\":\"Shep FX\"}]}").getBytes(StandardCharsets.UTF_8));
		answers.put("/api/v1/sails/" + ID + ".png", png());
		sails.refresh();
		Path file = folder.resolve("community").resolve(ID + ".png");
		waitFor(() -> Files.exists(file));

		answers.put("/api/v1/sails", "{\"sails\":[]}".getBytes(StandardCharsets.UTF_8));
		sails.refresh();
		waitFor(() -> !Files.exists(file));
		assertEquals(0, sails.size());
	}

	@Test
	public void somethingThatIsNotAPictureIsNotKept() throws Exception
	{
		answers.put("/api/v1/sails", ("{\"sails\":[{\"id\":\"" + ID + "\",\"name\":\"Shep FX\"}]}").getBytes(StandardCharsets.UTF_8));
		answers.put("/api/v1/sails/" + ID + ".png", "<html>not a picture</html>".getBytes(StandardCharsets.UTF_8));
		sails.refresh();
		waitFor(() -> received.containsKey("/api/v1/sails/" + ID + ".png"));
		TimeUnit.MILLISECONDS.sleep(200);
		assertFalse(Files.exists(folder.resolve("community").resolve(ID + ".png")));
	}

	@Test
	public void submittingSendsYourOwnSailAndReadsBackItsState() throws Exception
	{
		answers.put("/api/v1/submit", ("{\"banned\":false,\"sails\":[{\"id\":\"" + ID + "\",\"name\":\"Shep FX\",\"status\":\"pending\"}]}").getBytes(StandardCharsets.UTF_8));
		CompletableFuture<CommunitySails.Mine> answer = new CompletableFuture<>();
		sails.submit(123456789L, "Shep FX", Design.of(4, 4, new int[16]), answer::complete, problem -> answer.completeExceptionally(new AssertionError(problem)));
		CommunitySails.Mine mine = answer.get(5, TimeUnit.SECONDS);
		assertEquals("pending", mine.sails.get(0).status);

		Map<?, ?> sent = new Gson().fromJson(received.get("/api/v1/submit"), Map.class);
		assertEquals("123456789", sent.get("accountHash"));
		assertEquals("Shep FX", sent.get("name"));
		assertNotNull(sent.get("png"));
	}

	@Test
	public void theServersReasonForSayingNoIsPassedOn() throws Exception
	{
		status = 409;
		answers.put("/api/v1/submit", "{\"error\":\"Another account already has a sail under this name.\"}".getBytes(StandardCharsets.UTF_8));
		CompletableFuture<String> problem = new CompletableFuture<>();
		sails.submit(1L, "Shep FX", Design.of(4, 4, new int[16]), mine -> problem.complete("accepted"), problem::complete);
		assertEquals("Another account already has a sail under this name.", problem.get(5, TimeUnit.SECONDS));
	}
}
