package vip.megumin.ircmod.socket;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SocketChat implements WebSocket.Listener {
    private static final int MAX_NICK_LEN = 24;
    private static final Pattern NICK_SUFFIX_PARENS = Pattern.compile("^(.*)\\((\\d+)\\)$");
    private static final Pattern NICK_ALLOWED = Pattern.compile("^[A-Za-z0-9_]{1," + MAX_NICK_LEN + "}$");

    private final URI uri;
    private final String channel;
    private final String baseNick;
    private final String password;
    private final HttpClient httpClient;
    private final CopyOnWriteArrayList<SocketChatListener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<String> pendingMessages = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final StringBuilder incomingBuffer = new StringBuilder();
    private volatile WebSocket webSocket;
    private volatile String currentNick;
    private final Object joinLock = new Object();
    private int nickRetryCount;
    private boolean joined;

    public SocketChat(String uri, String channel, String nick, String password) {
        this.uri = URI.create(Objects.requireNonNull(uri, "uri"));
        this.channel = Objects.requireNonNull(channel, "channel");
        String initialNickRaw = Objects.requireNonNull(nick, "nick").trim();
        if (initialNickRaw.isEmpty()) {
            throw new IllegalArgumentException("nick must not be blank");
        }

        String parsedBase = initialNickRaw;
        int parsedRetry = 0;
        Matcher m = NICK_SUFFIX_PARENS.matcher(initialNickRaw);
        if (m.matches()) {
            parsedBase = m.group(1).trim();
            try {
                parsedRetry = Integer.parseInt(m.group(2));
            } catch (NumberFormatException ignored) {
                parsedRetry = 0;
            }
            if (parsedBase.isEmpty()) {
                parsedBase = initialNickRaw;
                parsedRetry = 0;
            }
        }

        this.baseNick = sanitizeNick(parsedBase);
        this.nickRetryCount = Math.max(0, parsedRetry);
        this.currentNick = buildNick(this.baseNick, this.nickRetryCount);
        this.password = password == null ? "" : password;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void addListener(SocketChatListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void connect() {
        if (connected.get() || connecting.getAndSet(true)) {
            return;
        }
        synchronized (joinLock) {
            joined = false;
        }
        httpClient.newWebSocketBuilder()
                .buildAsync(uri, this)
                .whenComplete((ws, err) -> {
                    connecting.set(false);
                    if (err != null) {
                        fireError(err);
                    }
                });
    }

    public void disconnect() {
        WebSocket ws = this.webSocket;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }
    }

    public void sendMessage(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String payload = buildChatPayload(text);
        WebSocket ws = this.webSocket;
        if (ws == null || !connected.get()) {
            pendingMessages.add(payload);
            connect();
            return;
        }
        ws.sendText(payload, true);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        connected.set(true);
        sendJoin();
        flushPending();
        fireConnected();
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        synchronized (incomingBuffer) {
            incomingBuffer.append(data);
            if (last) {
                String payload = incomingBuffer.toString();
                incomingBuffer.setLength(0);
                handlePayload(payload);
            }
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected.set(false);
        this.webSocket = null;
        fireDisconnected(statusCode, reason);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected.set(false);
        this.webSocket = null;
        fireError(error);
    }

    private void sendJoin() {
        WebSocket ws = this.webSocket;
        if (ws == null) {
            return;
        }
        JsonObject join = new JsonObject();
        join.addProperty("cmd", "join");
        join.addProperty("channel", channel);
        join.addProperty("nick", currentNick);
        if (!password.isEmpty()) {
            join.addProperty("password", password);
        }
        ws.sendText(join.toString(), true);
    }

    private void flushPending() {
        WebSocket ws = this.webSocket;
        if (ws == null) {
            return;
        }
        String payload;
        while ((payload = pendingMessages.poll()) != null) {
            ws.sendText(payload, true);
        }
    }

    private String buildChatPayload(String text) {
        JsonObject chat = new JsonObject();
        chat.addProperty("cmd", "chat");
        chat.addProperty("text", text);
        return chat.toString();
    }

    private void handlePayload(String payload) {
        JsonElement root;
        try {
            root = JsonParser.parseString(payload);
        } catch (Exception e) {
            fireError(e);
            return;
        }
        if (!root.isJsonObject()) {
            return;
        }
        JsonObject obj = root.getAsJsonObject();
        String cmd = getString(obj, "cmd");
        if (cmd == null) {
            return;
        }
        switch (cmd) {
            case "chat" -> {
                String nick = getString(obj, "nick");
                String text = getString(obj, "text");
                if (text != null) {
                    fireMessage(new SocketReceivedPacketEvent(nick == null ? "" : nick, text));
                }
            }
            case "info" -> {
                String text = getString(obj, "text");
                if (text != null) {
                    fireMessage(new SocketReceivedPacketEvent("info", text));
                }
            }
            case "warn" -> {
                String text = getString(obj, "text");
                if (text != null && handleNickInUse(text)) {
                    return;
                }
                if (text != null) {
                    fireMessage(new SocketReceivedPacketEvent("warn", text));
                }
            }
            case "onlineSet" -> {
                synchronized (joinLock) {
                    joined = true;
                }
            }
            case "onlineAdd" -> {
                String nick = getString(obj, "nick");
                if (nick != null) {
                    fireMessage(new SocketReceivedPacketEvent("info", nick + " joined"));
                }
            }
            case "onlineRemove" -> {
                String nick = getString(obj, "nick");
                if (nick != null) {
                    fireMessage(new SocketReceivedPacketEvent("info", nick + " left"));
                }
            }
            default -> {
            }
        }
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement value = obj.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        return value.getAsString();
    }

    private boolean handleNickInUse(String warnText) {
        if (!isNickInUseWarning(warnText)) {
            return false;
        }

        WebSocket ws = this.webSocket;
        if (ws == null) {
            return true;
        }

        synchronized (joinLock) {
            if (joined) {
                return false;
            }
            if (nickRetryCount >= 20) {
                fireMessage(new SocketReceivedPacketEvent("warn", "Nick already in use. Too many retries."));
                disconnect();
                return true;
            }
            nickRetryCount++;
            currentNick = buildNick(baseNick, nickRetryCount);
        }

        fireMessage(new SocketReceivedPacketEvent("info", "Nick already in use, trying " + currentNick));
        sendJoin();
        return true;
    }

    private static boolean isNickInUseWarning(String warnText) {
        String t = warnText == null ? "" : warnText.toLowerCase();
        return (t.contains("nick") || t.contains("name"))
                && (t.contains("in use") || t.contains("already") || t.contains("taken") || t.contains("registered"));
    }

    private void fireMessage(SocketReceivedPacketEvent event) {
        for (SocketChatListener listener : listeners) {
            listener.onMessage(event);
        }
    }

    private void fireConnected() {
        for (SocketChatListener listener : listeners) {
            listener.onConnected();
        }
    }

    private void fireDisconnected(int statusCode, String reason) {
        for (SocketChatListener listener : listeners) {
            listener.onDisconnected(statusCode, reason);
        }
    }

    private void fireError(Throwable error) {
        for (SocketChatListener listener : listeners) {
            listener.onError(error);
        }
    }

    private static String sanitizeNick(String nick) {
        String raw = nick == null ? "" : nick.trim();
        if (raw.isEmpty()) {
            return "Player";
        }
        if (NICK_ALLOWED.matcher(raw).matches()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length() && sb.length() < MAX_NICK_LEN; i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_') {
                sb.append(c);
            }
        }
        if (sb.length() == 0) {
            return "Player";
        }
        return sb.toString();
    }

    private static String buildNick(String baseNick, int retryCount) {
        String base = sanitizeNick(baseNick);
        if (retryCount <= 0) {
            return base.length() > MAX_NICK_LEN ? base.substring(0, MAX_NICK_LEN) : base;
        }
        String suffix = "_" + retryCount;
        int maxBaseLen = MAX_NICK_LEN - suffix.length();
        if (maxBaseLen < 1) {
            return ("P" + suffix).substring(0, MAX_NICK_LEN);
        }
        if (base.length() > maxBaseLen) {
            base = base.substring(0, maxBaseLen);
        }
        if (base.isEmpty()) {
            base = "Player";
            if (base.length() > maxBaseLen) {
                base = base.substring(0, maxBaseLen);
            }
        }
        return base + suffix;
    }
}
