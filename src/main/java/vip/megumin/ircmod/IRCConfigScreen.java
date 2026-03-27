package vip.megumin.ircmod;

import java.lang.reflect.Method;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import vip.megumin.ircmod.config.IRCConfig;
import vip.megumin.ircmod.config.IRCConfigManager;

/**
 * @author haipi
 */

public final class IRCConfigScreen extends Screen {
    private final Screen parent;
    private EditBox serverField;
    private EditBox channelField;
    private EditBox nickField;
    private EditBox passwordField;
    private EditBox prefixField;
    private EditBox maxLengthField;
    private Button rebindOpenKeyButton;
    private Button autoConnectButton;
    private Button connectButton;

    private boolean autoConnect;
    private boolean rebindingOpenKey;
    private int openKeyCode;

    public IRCConfigScreen(Screen parent) {
        super(Component.nullToEmpty("IRC Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        IRCConfig config = IRCClient.getConfig();
        autoConnect = config.autoConnect;
        openKeyCode = config.openConfigKeyCode > 0 ? config.openConfigKeyCode : IRCMod.getOpenConfigKeyCode();

        int centerX = this.width / 2;
        int y = 40;
        int fieldWidth = 240;
        int fieldHeight = 20;
        int spacing = 28;

        serverField = new EditBox(font, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Component.nullToEmpty("Server"));
        serverField.setValue(config.serverUrl);
        addRenderableWidget(serverField);
        y += spacing;

        channelField = new EditBox(font, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Component.nullToEmpty("Channel"));
        channelField.setValue(config.channel);
        addRenderableWidget(channelField);
        y += spacing;

        nickField = new EditBox(font, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Component.nullToEmpty("Nick"));
        nickField.setValue(config.nick == null ? "" : config.nick);
        addRenderableWidget(nickField);
        y += spacing;

        passwordField = new EditBox(font, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Component.nullToEmpty("Password"));
        passwordField.setValue(config.password == null ? "" : config.password);
        addRenderableWidget(passwordField);
        y += spacing;

        prefixField = new EditBox(font, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Component.nullToEmpty("Prefix"));
        prefixField.setValue(config.prefix == null ? "@" : config.prefix);
        addRenderableWidget(prefixField);
        y += spacing;

        maxLengthField = new EditBox(font, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Component.nullToEmpty("Max Length"));
        maxLengthField.setValue(Integer.toString(config.maxMessageLength));
        addRenderableWidget(maxLengthField);
        y += spacing;

        rebindOpenKeyButton = Button.builder(openKeyLabel(), button -> {
            rebindingOpenKey = true;
            button.setMessage(Component.nullToEmpty("Press a key... (ESC to cancel)"));
        }).bounds(centerX - fieldWidth / 2, y, fieldWidth, 20).build();
        addRenderableWidget(rebindOpenKeyButton);
        y += spacing;

        autoConnectButton = Button.builder(autoConnectLabel(), button -> {
            autoConnect = !autoConnect;
            button.setMessage(autoConnectLabel());
        }).bounds(centerX - fieldWidth / 2, y, fieldWidth, 20).build();
        addRenderableWidget(autoConnectButton);
        y += spacing;

        connectButton = Button.builder(connectLabel(), button -> {
            if (IRCClient.isConnected()) {
                IRCClient.disconnect();
            } else {
                IRCClient.connect();
            }
            button.setMessage(connectLabel());
        }).bounds(centerX - fieldWidth / 2, y, fieldWidth, 20).build();
        addRenderableWidget(connectButton);
        y += spacing + 4;

        addRenderableWidget(Button.builder(Component.nullToEmpty("Save"), button -> {
            IRCConfig updated = new IRCConfig();
            updated.serverUrl = serverField.getValue().trim();
            updated.channel = channelField.getValue().trim();
            updated.nick = nickField.getValue().trim();
            updated.password = passwordField.getValue();
            updated.prefix = sanitizePrefix(prefixField.getValue(), config.prefix);
            updated.autoConnect = autoConnect;
            updated.maxMessageLength = parseMaxLength(maxLengthField.getValue(), config.maxMessageLength);
            updated.openConfigKeyCode = openKeyCode;
            IRCClient.applyConfig(updated);
            onClose();
        }).bounds(centerX - fieldWidth / 2, y, (fieldWidth - 10) / 2, 20).build());

        addRenderableWidget(Button.builder(Component.nullToEmpty("Cancel"), button -> onClose())
                .bounds(centerX + 5, y, (fieldWidth - 10) / 2, 20)
                .build());

        super.init();
    }


    @Override
    public void tick() {
        super.tick();
        if (!rebindingOpenKey || minecraft == null || minecraft.getWindow() == null) {
            return;
        }

        long window = IRCMod.getWindowHandle(minecraft.getWindow());
        if (window == 0L) {
            return;
        }

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
            rebindingOpenKey = false;
            if (rebindOpenKeyButton != null) {
                rebindOpenKeyButton.setMessage(openKeyLabel());
            }
            return;
        }

        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
            if (GLFW.glfwGetKey(window, key) != GLFW.GLFW_PRESS) {
                continue;
            }

            openKeyCode = key;
            rebindingOpenKey = false;
            if (rebindOpenKeyButton != null) {
                rebindOpenKeyButton.setMessage(openKeyLabel());
            }

            IRCConfig cfg = IRCClient.getConfig();
            cfg.openConfigKeyCode = openKeyCode;
            IRCConfigManager.save(cfg);
            IRCMod.setOpenConfigKeyCode(openKeyCode);
            return;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private Component autoConnectLabel() {
        return Component.nullToEmpty("Auto Connect: " + (autoConnect ? "ON" : "OFF"));
    }

    private Component connectLabel() {
        return Component.nullToEmpty(IRCClient.isConnected() ? "Disconnect" : "Connect");
    }

    private Component openKeyLabel() {
        String name = GLFW.glfwGetKeyName(openKeyCode, 0);
        String shown = name == null || name.isBlank() ? Integer.toString(openKeyCode) : name.toUpperCase();
        return Component.nullToEmpty("Open Config Key: " + shown);
    }

    private int parseMaxLength(String input, int fallback) {
        try {
            int value = Integer.parseInt(input.trim());
            return Math.max(1, value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String sanitizePrefix(String input, String fallback) {
        if (input == null) {
            return fallback == null || fallback.isBlank() ? "@" : fallback;
        }
        String s = input.trim();
        if (s.isEmpty()) {
            return fallback == null || fallback.isBlank() ? "@" : fallback;
        }
        return s;
    }
}
