package vip.megumin.ircmod.config;

public final class IRCConfig {
    public String serverUrl = "wss://hack.chat/chat-ws";
    public String channel = "mc-irc-mod-2026";
    public String nick = "";
    public String password = "";
    public String prefix = "@";
    public boolean autoConnect = true;
    public int maxMessageLength = 150;

    // GLFW key code. Default is J (GLFW_KEY_J = 74). Stored as int to avoid version-specific key classes.
    public int openConfigKeyCode = 74;
}
