package vip.megumin.ircmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vip.megumin.ircmod.config.IRCConfig;
import vip.megumin.ircmod.config.IRCConfigManager;
import vip.megumin.ircmod.socket.SocketChat;
import vip.megumin.ircmod.socket.SocketChatListener;
import vip.megumin.ircmod.socket.SocketReceivedPacketEvent;

public final class IRCClient {
    private static final Object LOCK = new Object();
    private static SocketChat chat;
    private static IRCConfig config;

    private IRCClient() {
    }

    public static void init() {
        config = IRCConfigManager.load();
        if (config.autoConnect) {
            connect();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(IRCClient::shutdown, "IRCMod-ShutdownHook"));
    }

    public static boolean handleOutgoingMessage(String message) {
        if (message == null) {
            return false;
        }
        IRCConfig cfg = getConfig();
        String prefix = (cfg.prefix == null || cfg.prefix.isBlank()) ? "@" : cfg.prefix;
        if (!message.startsWith(prefix)) {
            return false;
        }
        String trimmed = message.substring(prefix.length());
        int maxLength = getMaxMessageLength();
        if (trimmed.length() >= maxLength) {
            sendSystemMessage("You cannot send more than " + maxLength + " characters.");
            return true;
        }
        if (trimmed.isBlank()) {
            return true;
        }
        if (chat == null) {
            sendSystemMessage("Not connected.");
            return true;
        }
        chat.sendMessage(trimmed);
        return true;
    }

    public static IRCConfig getConfig() {
        return config == null ? new IRCConfig() : config;
    }

    public static void applyConfig(IRCConfig newConfig) {
        if (newConfig == null) {
            return;
        }
        synchronized (LOCK) {
            config = newConfig;
            IRCConfigManager.save(newConfig);
            disconnect();
            if (newConfig.autoConnect) {
                connect();
            }
        }
    }

    public static void connect() {
        synchronized (LOCK) {
            if (config == null) {
                config = IRCConfigManager.load();
            }
            if (chat != null && chat.isConnected()) {
                return;
            }
            String serverUrl = config.serverUrl == null ? "" : config.serverUrl.trim();
            String channel = config.channel == null ? "" : config.channel.trim();
            if (serverUrl.isBlank() || channel.isBlank()) {
                sendSystemMessage("IRC config missing server URL or channel.");
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            String nick = config.nick == null || config.nick.isBlank()
                    ? client.getSession().getUsername()
                    : config.nick.trim();
            String password = config.password == null ? "" : config.password;
            chat = new SocketChat(serverUrl, channel, nick, password);
            chat.addListener(new SocketChatListener() {
                @Override
                public void onMessage(SocketReceivedPacketEvent event) {
                    IRCClient.handleIncoming(event);
                }

                @Override
                public void onConnected() {
                    sendSystemMessage("Connected!");
                }

                @Override
                public void onDisconnected(int statusCode, String reason) {
                    sendSystemMessage("Disconnected!");
                }

                @Override
                public void onError(Throwable error) {
                    sendSystemMessage("IRC error: " + error.getMessage());
                }
            });
            sendSystemMessage("Connecting To IRC Server...");
            chat.connect();
        }
    }

    public static void disconnect() {
        synchronized (LOCK) {
            if (chat != null) {
                chat.disconnect();
            }
        }
    }

    public static boolean isConnected() {
        synchronized (LOCK) {
            return chat != null && chat.isConnected();
        }
    }

    private static void handleIncoming(SocketReceivedPacketEvent event) {
        if (event == null) {
            return;
        }
        String nick = event.nick();
        String text = event.text();
        if (text == null || text.isBlank()) {
            return;
        }
        if ("info".equalsIgnoreCase(nick)) {
            postText(Text.literal("[IRC] ").formatted(Formatting.DARK_AQUA)
                    .append(Text.literal(text).formatted(Formatting.GRAY)));
            return;
        }
        if ("warn".equalsIgnoreCase(nick)) {
            postText(Text.literal("[IRC] ").formatted(Formatting.DARK_AQUA)
                    .append(Text.literal(text).formatted(Formatting.RED)));
            return;
        }
        String safeNick = nick == null || nick.isBlank() ? "?" : nick;
        postText(Text.literal("[IRC] ").formatted(Formatting.DARK_AQUA)
                .append(Text.literal("<" + safeNick + "> ").formatted(Formatting.GRAY))
                .append(Text.literal(text).formatted(Formatting.WHITE)));
    }

    private static void sendSystemMessage(String message) {
        postText(Text.literal("[IRC] ").formatted(Formatting.DARK_AQUA)
                .append(Text.literal(message).formatted(Formatting.GRAY)));
    }

    private static void postText(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }
        client.execute(() -> client.inGameHud.getChatHud().addMessage(text));
    }

    private static void shutdown() {
        disconnect();
    }

    private static int getMaxMessageLength() {
        if (config == null) {
            return 150;
        }
        return Math.max(1, config.maxMessageLength);
    }
}
