package org.icpc.tools.client.core;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ClientEndpointConfig.Configurator;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.SslEngineConfigurator;
import org.icpc.tools.contest.Trace;
import org.icpc.tools.contest.model.feed.HTTPSSecurity;
import org.icpc.tools.contest.model.feed.JSONEncoder;
import org.icpc.tools.contest.model.feed.JSONParser;
import org.icpc.tools.contest.model.feed.JSONParser.JsonObject;
import org.icpc.tools.contest.model.feed.RESTContestSource;

public class BasicClient {
	private static final boolean DEBUG_INPUT = true;
	private static final boolean DEBUG_OUTPUT = true;

	protected static enum Type {
		PING, PRES_LIST, PROPERTIES, CLIENTS, TIME, STOP, RESTART, REQUEST_LOG, LOG, REQUEST_SNAPSHOT, SNAPSHOT, INFO, THUMBNAIL
	}

	protected interface AddAttrs {
		public void add(JSONEncoder je) throws IOException;
	}

	class WSClientEndpoint extends Endpoint {
		private Session session;

		@Override
		public void onError(Session newSession, Throwable t) {
			Trace.trace(Trace.ERROR, "Error in websocket", t);
		}

		@Override
		public void onOpen(Session newSession, EndpointConfig config) {
			session = newSession;
			session.addMessageHandler(new MessageHandler.Whole<String>() {
				@Override
				public void onMessage(String message) {
					try {
						BasicClient.this.onMessage(message);
					} catch (IOException e) {
						Trace.trace(Trace.ERROR, "Error in websocket", e);
					}
				}
			});
		}

		protected void send(String message) throws IOException {
			if (session == null)
				return;

			session.getBasicRemote().sendText(message);
		}

		protected boolean isConnected() {
			return session != null;
		}

		@Override
		public void onClose(Session session2, CloseReason closeReason) {
			session = null;
			if (closeReason != null && closeReason.getCloseCode() != CloseCodes.NORMAL_CLOSURE) {
				if (closeReason.getCloseCode() == CloseCodes.UNEXPECTED_CONDITION
						&& closeReason.getReasonPhrase().startsWith("CDS: ")) {
					Trace.trace(Trace.ERROR, closeReason.getReasonPhrase().substring(4));
					System.exit(1);
				}
				Trace.trace(Trace.ERROR, "Unexpected websocket disconnect: " + closeReason.getReasonPhrase() + " ("
						+ closeReason.getCloseCode().toString() + ")");
			}
		}
	}

	class AuthorizationConfigurator extends Configurator {
		private final String auth;

		public AuthorizationConfigurator(String auth) {
			this.auth = auth;
		}

		@Override
		public void beforeRequest(Map<String, List<String>> headers) {
			headers.put("Authorization", Arrays.asList("Basic " + auth));
		}
	}

	public static class Client {
		public int uid;
		public String id;
		public String contestId;
	}

	private String clientId = "Unknown";
	private int uid;
	private RESTContestSource contestSource;
	private String role;
	private long nanoTimeDelta;

	private List<IPropertyListener> listeners;
	private List<IConnectionListener> listeners2;

	private WSClientEndpoint clientEndpoint;

	public BasicClient(RESTContestSource contestSource, String clientId, int uid, String role) {
		this.contestSource = contestSource;
		this.clientId = clientId;
		this.uid = uid;
		this.role = role;
	}

	/**
	 * Create a generic client for a client type.
	 *
	 * @param contestSource
	 * @param clientType
	 */
	public BasicClient(RESTContestSource contestSource, String clientType) {
		this.contestSource = contestSource;
		this.clientId = clientType;

		String s = contestSource.getUser();
		try {
			String addr = InetAddress.getLocalHost().getHostAddress();
			s += addr;
			if (addr.contains(":"))
				addr = addr.substring(addr.lastIndexOf(":"), addr.length());
			if (addr.contains("."))
				addr = addr.substring(addr.lastIndexOf("."), addr.length());
			clientId += addr;
		} catch (Exception e) {
			Trace.trace(Trace.WARNING, "Could not determine local host address");
		}
		uid = s.hashCode();
	}

	protected void setUID(int uid) {
		this.uid = uid;
	}

	private void handleProperties(JsonObject obj) {
		int numProps = obj.getInt("num");

		// fire control properties first
		Properties p = new Properties();
		for (int i = 0; i < numProps; i++) {
			String key = obj.getString("key" + i);
			String value = obj.getString("value" + i);

			if (key.contains("."))
				p.setProperty(key, value);
			else
				firePropertyChangedEvent(key, value);
		}

		// then fire properties to presentations
		Iterator<Object> iter = p.keySet().iterator();
		while (iter.hasNext()) {
			String key = (String) iter.next();
			String value = p.getProperty(key);
			firePropertyChangedEvent(key, value);
		}
	}

	protected void handleThumbnail(JsonObject obj) {
		// do nothing
	}

	protected void handleInfo(JsonObject obj) {
		// do nothing
	}

	private void handleLogRequest(JsonObject obj) throws IOException {
		int source = obj.getInt("source");
		sendLog(source);
	}

	/**
	 * @throws IOException
	 */
	protected void handleLogResponse(JsonObject obj) throws IOException {
		// ignore
	}

	private void handleSnapshotRequest(JsonObject obj) throws IOException {
		int source = obj.getInt("source");
		handleSnapshot(source);
	}

	/**
	 * @throws IOException
	 */
	protected void handleSnapshotResponse(JsonObject obj) throws IOException {
		// ignore
	}

	/**
	 * @throws IOException
	 */
	protected void handleSnapshot(int clientUID) throws IOException {
		// do nothing
	}

	private void handleClientList(JsonObject obj) {
		Object[] children = obj.getArray("clients");

		Client[] clients = new Client[children.length];
		for (int i = 0; i < children.length; i++) {
			JsonObject jo = (JsonObject) children[i];
			clients[i] = new Client();
			clients[i].uid = jo.getInt("uid");
			clients[i].id = jo.getString("id");
			clients[i].contestId = jo.getString("contest_id");
		}

		clientsChanged(clients);
	}

	/**
	 * Called whenever the list of logged in clients changes. Only called on admin clients.
	 *
	 * @param clients a list of clients
	 */
	protected void clientsChanged(Client[] clients) {
		// do nothing
	}

	/**
	 * @throws IOException
	 */
	protected void handlePresentationList(JsonObject obj) throws IOException {
		// do nothing
	}

	/**
	 * @throws IOException
	 */
	public void sendProperty(int[] clientUIDs, String key, String value) throws IOException {
		createJSON(Type.PROPERTIES, je -> {
			writeClients(je, clientUIDs);
			if (key == null) {
				je.encode("num", -1);
			} else {
				je.encode("num", 1);
				je.encode("key0", key);
				je.encode("value0", value);
			}
		});
	}

	public void requestPresentationList() throws IOException {
		createJSON(Type.PRES_LIST, null);
	}

	public void sendStop(int[] clientUIDs) throws IOException {
		createJSON(Type.STOP, je -> {
			writeClients(je, clientUIDs);
		});
	}

	public void sendRestart(int[] clientUIDs) throws IOException {
		createJSON(Type.RESTART, je -> {
			writeClients(je, clientUIDs);
		});
	}

	public void sendLogRequest(int[] clientUIDs) throws IOException {
		createJSON(Type.REQUEST_LOG, je -> {
			je.encode("source", uid);
			writeClients(je, clientUIDs);
		});
	}

	public void sendSnapshotRequest(int[] clientUIDs) throws IOException {
		createJSON(Type.REQUEST_SNAPSHOT, je -> {
			je.encode("source", uid);
			writeClients(je, clientUIDs);
		});
	}

	public void sendSnapshot(BufferedImage image) throws IOException {
		createJSON(Type.SNAPSHOT, je -> {
			je.encode("source", uid);
			encodeImage(je, image);
		});
	}

	public void sendThumbnail(BufferedImage image, boolean isHidden, int fps) throws IOException {
		createJSON(Type.THUMBNAIL, je -> {
			je.encode("source", uid);
			je.encode("fps", fps);
			je.encode("hidden", isHidden);
			encodeImage(je, image);
		});
	}

	protected void createJSON(Type type, AddAttrs attr) throws IOException {
		StringWriter sw = new StringWriter();
		JSONEncoder je = new JSONEncoder(new PrintWriter(sw));
		je.open();
		je.encode("type", type.name().toLowerCase());
		if (attr != null)
			attr.add(je);
		je.close();
		sendIt(sw.toString());
	}

	protected void sendLog(int toUID) throws IOException {
		createJSON(Type.LOG, je -> {
			je.encode("source", uid);
			je.encode("target", toUID);
			je.encode("data", Trace.getLogContents2());
		});
	}

	protected void encodeImage(JSONEncoder je, BufferedImage image) throws IOException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", bout);
		je.encode("image", Base64.getEncoder().encodeToString(bout.toByteArray()));
	}

	protected static byte[] decodeImage(JsonObject obj) {
		String imgStr = obj.getString("image");
		return Base64.getDecoder().decode(imgStr);
	}

	protected static byte[] decodeFile(JsonObject obj) {
		return Base64.getDecoder().decode(obj.getString("file"));
	}

	protected void sendSnapshot(BufferedImage image, int toUID) throws IOException {
		createJSON(Type.SNAPSHOT, je -> {
			je.encode("source", uid);
			je.encode("target", toUID);
			encodeImage(je, image);
		});
	}

	protected boolean sendIt(String message) throws IOException {
		if (clientEndpoint == null)
			return false;

		if (DEBUG_OUTPUT) {
			String s = message;
			if (s.length() > 140)
				s = s.substring(0, 140) + "...";
			Trace.trace(Trace.USER, "> " + s);
		}
		clientEndpoint.send(message);
		return true;
	}

	public void addListener(IConnectionListener listener) {
		if (listeners2 == null)
			listeners2 = new ArrayList<>();
		if (!listeners2.contains(listener))
			listeners2.add(listener);
	}

	public void removeListener(IConnectionListener listener) {
		if (listeners2 == null)
			return;
		listeners2.remove(listener);
	}

	private void fireConnectionStateEvent(boolean state) {
		if (listeners2 == null)
			return;
		IConnectionListener[] list;
		synchronized (listeners2) {
			list = new IConnectionListener[listeners2.size()];
			listeners2.toArray(list);
		}

		int size = list.length;
		for (int i = 0; i < size; i++) {
			list[i].connectionStateChanged(state);
		}
	}

	public void addListener(IPropertyListener listener) {
		if (listeners == null)
			listeners = new ArrayList<>();
		if (!listeners.contains(listener))
			listeners.add(listener);
	}

	public void removeListener(IPropertyListener listener) {
		if (listeners == null)
			return;
		listeners.remove(listener);
	}

	private void firePropertyChangedEvent(String key, String value) {
		if (listeners == null)
			return;
		IPropertyListener[] list;
		synchronized (listeners) {
			list = new IPropertyListener[listeners.size()];
			listeners.toArray(list);
		}

		int size = list.length;
		for (int i = 0; i < size; i++) {
			try {
				list[i].propertyUpdated(key, value);
			} catch (Throwable t) {
				Trace.trace(Trace.ERROR, "Error firing property change event", t);
			}
		}
	}

	public void connect() {
		connect(false);
	}

	public void connect(boolean daemon) {
		Thread clientThread = new Thread("Client connection thread") {
			@Override
			public void run() {
				while (true) {
					Trace.trace(Trace.USER, clientId + " connecting...");
					connectImpl();
					try {
						sleep(5000);
					} catch (InterruptedException e) {
						// ignore
					}
				}
			}
		};

		clientThread.setPriority(Thread.NORM_PRIORITY + 1);
		clientThread.setDaemon(daemon);
		clientThread.start();
	}

	protected void connectImpl() {
		try {
			System.setProperty("jsse.enableSNIExtension", "false");

			SSLContext ctx = SSLContext.getInstance("TLS");
			ctx.init(null, new TrustManager[] { new HTTPSSecurity.ContestTrustManager() }, null);
			SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(ctx, true, false, false);
			sslEngineConfigurator.setHostVerificationEnabled(false);

			ClientManager client = ClientManager.createClient();
			client.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);

			clientEndpoint = new WSClientEndpoint();
			ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create()
					.configurator(new AuthorizationConfigurator(contestSource.getAuth())).build();
			StringBuilder sb = new StringBuilder("/presentation/ws");
			sb.append("?id=" + URLEncoder.encode(clientId, "UTF-8"));
			sb.append("&uid=" + uid);
			if (role == null)
				sb.append("&role=any");
			else
				sb.append("&role=" + role);
			sb.append("&version=1.0");
			sb.append("&contestId=" + contestSource.getContestId());
			URI uri = contestSource.getRootURI("wss", sb.toString());

			client.setDefaultMaxSessionIdleTimeout(60000L);
			client.connectToServer(clientEndpoint, endpointConfig, uri);

			Trace.trace(Trace.USER, clientId + " connected");
			fireConnectionStateEvent(true);

			while (clientEndpoint.isConnected()) {
				Thread.sleep(2000);
			}
		} catch (IOException | DeploymentException e) {
			String msg = e.getMessage();
			if (e.getCause() != null)
				msg += " (" + e.getCause().getMessage() + ")";
			Trace.trace(Trace.ERROR, "Error connecting to " + contestSource.getURL().toExternalForm() + ": " + msg
					+ ". Confirm URL and user/password or view log for details");
			Trace.trace(Trace.INFO, "Failure details", e);
		} catch (Exception e) {
			Trace.trace(Trace.ERROR,
					"Unexected error connecting to " + contestSource.getURL().toExternalForm() + ": " + e.getMessage());
			Trace.trace(Trace.INFO, "Failure details", e);
		} catch (Throwable t) {
			Trace.trace(Trace.ERROR,
					"Unexected error connecting to " + contestSource.getURL().toExternalForm() + ": " + t.getMessage());
			Trace.trace(Trace.INFO, "Failure details", t);
		} finally {
			/*try {
				if (session != null)
					session.close();
			} catch (Exception e) {
				// ignore
			}*/
		}
		Trace.trace(Trace.USER, clientId + " disconnected");
		fireConnectionStateEvent(false);
	}

	private void sendPing() throws IOException {
		StringWriter sw = new StringWriter();
		JSONEncoder je = new JSONEncoder(new PrintWriter(sw));
		je.open();
		je.encode("type", Type.PING.name().toLowerCase());
		je.close();
		sendIt(sw.toString());
	}

	protected void handleTime(long time) {
		long newNanoTimeDelta = time * 1000000L - System.nanoTime();
		long diff = Math.abs(newNanoTimeDelta - nanoTimeDelta) / 1000000L;
		Trace.trace(Trace.INFO, "Nano time diff: " + diff);
		nanoTimeDelta = newNanoTimeDelta;
	}

	private void onMessage(String message) throws IOException {
		if (DEBUG_INPUT) {
			String s = message;
			if (s.length() > 140)
				s = s.substring(0, 140) + "...";
			Trace.trace(Trace.USER, "< " + s);
		}

		JSONParser rdr = new JSONParser(message);
		JsonObject obj = rdr.readObject();
		String type = obj.getString("type");

		Type action = null;
		for (Type t : Type.values()) {
			if (t.name().equalsIgnoreCase(type))
				action = t;
		}

		switch (action) {
			case PING: {
				sendPing();
				break;
			}
			case TIME: {
				handleTime(obj.getLong("time"));
				sendPing();
				break;
			}
			case CLIENTS: {
				handleClientList(obj);
				break;
			}
			case THUMBNAIL: {
				handleThumbnail(obj);
				break;
			}
			case INFO: {
				handleInfo(obj);
				break;
			}
			case REQUEST_LOG: {
				handleLogRequest(obj);
				break;
			}
			case LOG: {
				handleLogResponse(obj);
				break;
			}
			case REQUEST_SNAPSHOT: {
				handleSnapshotRequest(obj);
				break;
			}
			case SNAPSHOT: {
				handleSnapshotResponse(obj);
				break;
			}
			case PROPERTIES: {
				handleProperties(obj);
				break;
			}
			case PRES_LIST: {
				handlePresentationList(obj);
				break;
			}
			case STOP: {
				System.exit(0);
				break;
			}
			case RESTART: {
				System.exit(255);
				break;
			}
			default: {
				Trace.trace(Trace.WARNING, "Unknown action: " + message);
				return;
			}
		}
	}

	private static void writeClients(JSONEncoder je, int[] ids) {
		je.encode3("clients");
		je.openArray();
		for (int i = 0; i < ids.length; i++) {
			je.encodeValue(ids[i]);
		}
		je.closeArray();
	}

	public int getUID() {
		return uid;
	}

	public String getClientId() {
		return clientId;
	}

	@Override
	public String toString() {
		return "Basic Client [" + clientId + "]";
	}
}