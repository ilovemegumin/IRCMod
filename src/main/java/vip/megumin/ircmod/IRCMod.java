package vip.megumin.ircmod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import vip.megumin.ircmod.config.IRCConfig;

public class IRCMod implements ClientModInitializer {

    private static final String CATEGORY_KEY = "key.category.ircmod.main";

    private static KeyBinding openConfigKey;
    private static int openConfigKeyCode = GLFW.GLFW_KEY_J;
    private static boolean openConfigKeyWasDown = false;

    @Override
    public void onInitializeClient() {
        IRCClient.init();

        IRCConfig cfg = IRCClient.getConfig();
        if (cfg != null && cfg.openConfigKeyCode > 0) {
            openConfigKeyCode = cfg.openConfigKeyCode;
        }

        openConfigKey = KeyBindingHelper.registerKeyBinding(createKeyBinding(
                "key.ircmod.open_config",
                openConfigKeyCode
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean opened = false;
            while (openConfigKey.wasPressed()) {
                opened = true;
                client.setScreen(new IRCConfigScreen(client.currentScreen));
            }
            if (!opened) {
                handleDirectOpenKey(client);
            }
        });
    }

    static int getOpenConfigKeyCode() {
        return openConfigKeyCode;
    }

    static void setOpenConfigKeyCode(int keyCode) {
        if (keyCode <= 0) {
            return;
        }
        openConfigKeyCode = keyCode;
        tryUpdateKeyBinding(openConfigKey, keyCode);
    }

    private static void handleDirectOpenKey(MinecraftClient client) {
        if (client == null || client.getWindow() == null) {
            return;
        }
        if (client.currentScreen != null) {
            openConfigKeyWasDown = false;
            return;
        }
        long handle = client.getWindow().getHandle();
        boolean down = GLFW.glfwGetKey(handle, openConfigKeyCode) == GLFW.GLFW_PRESS;
        if (down && !openConfigKeyWasDown) {
            client.setScreen(new IRCConfigScreen(client.currentScreen));
        }
        openConfigKeyWasDown = down;
    }

    private static void tryUpdateKeyBinding(KeyBinding keyBinding, int keyCode) {
        if (keyBinding == null) {
            return;
        }
        try {
            keyBinding.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
            KeyBinding.updateKeysByCode();
        } catch (Throwable ignored) {
        }
    }

    private static KeyBinding createKeyBinding(String translationKey, int keyCode) {
        InputUtil.Type type = InputUtil.Type.KEYSYM;
        Identifier categoryId = Identifier.of("ircmod", "main");

        for (Constructor<?> ctor : KeyBinding.class.getConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            try {
                if (p.length == 5
                        && p[0] == String.class
                        && p[1] == InputUtil.Type.class
                        && p[2] == int.class
                        && p[4] == int.class) {
                    Object categoryArg = createCategoryArg(p[3], categoryId);
                    if (categoryArg != null) {
                        return (KeyBinding) ctor.newInstance(translationKey, type, keyCode, categoryArg, 0);
                    }
                }

                if (p.length == 4
                        && p[0] == String.class
                        && p[1] == InputUtil.Type.class
                        && p[2] == int.class) {
                    Object categoryArg = createCategoryArg(p[3], categoryId);
                    if (categoryArg != null) {
                        return (KeyBinding) ctor.newInstance(translationKey, type, keyCode, categoryArg);
                    }
                }

                if (p.length == 3
                        && p[0] == String.class
                        && p[1] == int.class) {
                    Object categoryArg = createCategoryArg(p[2], categoryId);
                    if (categoryArg != null) {
                        return (KeyBinding) ctor.newInstance(translationKey, keyCode, categoryArg);
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        throw new IllegalStateException("No compatible KeyBinding constructor found for this Minecraft version");
    }

    private static Object createCategoryArg(Class<?> categoryParamType, Identifier id) {
        if (categoryParamType == String.class) {
            return CATEGORY_KEY;
        }

        try {
            Constructor<?> ctor = categoryParamType.getDeclaredConstructor(Identifier.class);
            ctor.setAccessible(true);
            return ctor.newInstance(id);
        } catch (Throwable ignored) {
        }

        try {
            for (Method m : categoryParamType.getMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != Identifier.class) {
                    continue;
                }
                if (!categoryParamType.isAssignableFrom(m.getReturnType())) {
                    continue;
                }
                return m.invoke(null, id);
            }
        } catch (Throwable ignored) {
        }

        return null;
    }
}
