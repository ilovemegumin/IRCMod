package vip.megumin.ircmod.socket;

public interface SocketChatListener {
    void onMessage(SocketReceivedPacketEvent event);

    default void onConnected() {
    }

    default void onDisconnected(int statusCode, String reason) {
    }

    default void onError(Throwable error) {
    }
}
