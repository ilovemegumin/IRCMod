package vip.megumin.ircmod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import vip.megumin.ircmod.config.IRCConfig;
import vip.megumin.ircmod.config.IRCConfigManager;

public final class IRCConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget serverField;
    private TextFieldWidget channelField;
    private TextFieldWidget nickField;
    private TextFieldWidget passwordField;
    private TextFieldWidget prefixField;
    private TextFieldWidget maxLengthField;
    private ButtonWidget rebindOpenKeyButton;
    private ButtonWidget autoConnectButton;
    private ButtonWidget connectButton;

    private boolean autoConnect;
    private boolean rebindingOpenKey;
    private int openKeyCode;

    public IRCConfigScreen(Screen parent) {
        super(Text.of("IRC Config"));
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

        serverField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Text.of("Server"));
        serverField.setText(config.serverUrl);
        addDrawableChild(serverField);
        y += spacing;

        channelField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Text.of("Channel"));
        channelField.setText(config.channel);
        addDrawableChild(channelField);
        y += spacing;

        nickField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Text.of("Nick"));
        nickField.setText(config.nick == null ? "" : config.nick);
        addDrawableChild(nickField);
        y += spacing;

        passwordField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Text.of("Password"));
        passwordField.setText(config.password == null ? "" : config.password);
        addDrawableChild(passwordField);
        y += spacing;

        prefixField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Text.of("Prefix"));
        prefixField.setText(config.prefix == null ? "@" : config.prefix);
        addDrawableChild(prefixField);
        y += spacing;

        maxLengthField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, y, fieldWidth, fieldHeight, Text.of("Max Length"));
        maxLengthField.setText(Integer.toString(config.maxMessageLength));
        addDrawableChild(maxLengthField);
        y += spacing;

        rebindOpenKeyButton = ButtonWidget.builder(openKeyLabel(), button -> {
            rebindingOpenKey = true;
            button.setMessage(Text.of("Press a key... (ESC to cancel)"));
        }).dimensions(centerX - fieldWidth / 2, y, fieldWidth, 20).build();
        addDrawableChild(rebindOpenKeyButton);
        y += spacing;

        autoConnectButton = ButtonWidget.builder(autoConnectLabel(), button -> {
            autoConnect = !autoConnect;
            button.setMessage(autoConnectLabel());
        }).dimensions(centerX - fieldWidth / 2, y, fieldWidth, 20).build();
        addDrawableChild(autoConnectButton);
        y += spacing;

        connectButton = ButtonWidget.builder(connectLabel(), button -> {
            if (IRCClient.isConnected()) {
                IRCClient.disconnect();
            } else {
                IRCClient.connect();
            }
            button.setMessage(connectLabel());
        }).dimensions(centerX - fieldWidth / 2, y, fieldWidth, 20).build();
        addDrawableChild(connectButton);
        y += spacing + 4;

        addDrawableChild(ButtonWidget.builder(Text.of("Save"), button -> {
            IRCConfig updated = new IRCConfig();
            updated.serverUrl = serverField.getText().trim();
            updated.channel = channelField.getText().trim();
            updated.nick = nickField.getText().trim();
            updated.password = passwordField.getText();
            updated.prefix = sanitizePrefix(prefixField.getText(), config.prefix);
            updated.autoConnect = autoConnect;
            updated.maxMessageLength = parseMaxLength(maxLengthField.getText(), config.maxMessageLength);
            updated.openConfigKeyCode = openKeyCode;
            IRCClient.applyConfig(updated);
            close();
        }).dimensions(centerX - fieldWidth / 2, y, (fieldWidth - 10) / 2, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.of("Cancel"), button -> close())
                .dimensions(centerX + 5, y, (fieldWidth - 10) / 2, 20)
                .build());

        super.init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(textRenderer, "Server URL", serverField.getX(), serverField.getY() - 10, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Channel", channelField.getX(), channelField.getY() - 10, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Nick (blank = session)", nickField.getX(), nickField.getY() - 10, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Password", passwordField.getX(), passwordField.getY() - 10, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Prefix (global chat)", prefixField.getX(), prefixField.getY() - 10, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Max Message Length", maxLengthField.getX(), maxLengthField.getY() - 10, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();
        if (!rebindingOpenKey || client == null || client.getWindow() == null) {
            return;
        }

        long window = client.getWindow().getHandle();

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
    public void close() {
        client.setScreen(parent);
    }

    private Text autoConnectLabel() {
        return Text.of("Auto Connect: " + (autoConnect ? "ON" : "OFF"));
    }

    private Text connectLabel() {
        return Text.of(IRCClient.isConnected() ? "Disconnect" : "Connect");
    }

    private Text openKeyLabel() {
        String name = GLFW.glfwGetKeyName(openKeyCode, 0);
        String shown = name == null || name.isBlank() ? Integer.toString(openKeyCode) : name.toUpperCase();
        return Text.of("Open Config Key: " + shown);
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
