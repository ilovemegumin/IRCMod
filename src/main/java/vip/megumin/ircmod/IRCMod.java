package vip.megumin.ircmod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
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
            Object key = createInputKey(keyCode);
            if (key != null) {
                for (Method m : KeyBinding.class.getMethods()) {
                    if (!m.getName().equals("setBoundKey") || m.getParameterCount() != 1) {
                        continue;
                    }
                    Class<?> p = m.getParameterTypes()[0];
                    if (p.isInstance(key)) {
                        m.invoke(keyBinding, key);
                        break;
                    }
                }
            }
            try {
                Method update = KeyBinding.class.getMethod("updateKeysByCode");
                update.invoke(null);
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object createInputKey(int keyCode) {
        try {
            Object type = InputUtil.Type.KEYSYM;
            Method createFromCode = type.getClass().getMethod("createFromCode", int.class);
            return createFromCode.invoke(type, keyCode);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static KeyBinding createKeyBinding(String translationKey, int keyCode) {
        Object type = InputUtil.Type.KEYSYM;
        Object categoryArg = CATEGORY_KEY;

        try {
            Class<?> categoryClass = Class.forName("net.minecraft.client.option.KeyBinding$Category");
            Object id = createIdentifier("ircmod", "main");
            if (id != null) {
                categoryArg = categoryClass.getMethod("create", id.getClass()).invoke(null, id);
            }
        } catch (Throwable ignored) {
            // fallback
        }

        for (Constructor<?> ctor : KeyBinding.class.getConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            try {
                if (p.length == 4
                        && p[0] == String.class
                        && p[1].getName().equals("net.minecraft.client.util.InputUtil$Type")
                        && p[2] == int.class
                        && p[3].isInstance(categoryArg)) {
                    return (KeyBinding) ctor.newInstance(translationKey, type, keyCode, categoryArg);
                }
                if (p.length == 4
                        && p[0] == String.class
                        && p[1].getName().equals("net.minecraft.client.util.InputUtil$Type")
                        && p[2] == int.class
                        && p[3] == String.class) {
                    return (KeyBinding) ctor.newInstance(translationKey, type, keyCode, CATEGORY_KEY);
                }
                if (p.length == 3
                        && p[0] == String.class
                        && p[1] == int.class
                        && p[2] == String.class) {
                    return (KeyBinding) ctor.newInstance(translationKey, keyCode, CATEGORY_KEY);
                }
            } catch (ReflectiveOperationException ignored) {
                // try next
            }
        }

        throw new IllegalStateException("No compatible KeyBinding constructor found for this Minecraft version");
    }

    private static Object createIdentifier(String namespace, String path) {
        try {
            Class<?> idClass = Class.forName("net.minecraft.util.Identifier");
            try {
                Method of = idClass.getMethod("of", String.class, String.class);
                return of.invoke(null, namespace, path);
            } catch (NoSuchMethodException ignored) {
                // fallthrough
            }

            try {
                Constructor<?> ctor = idClass.getDeclaredConstructor(String.class, String.class);
                ctor.setAccessible(true);
                return ctor.newInstance(namespace, path);
            } catch (NoSuchMethodException ignored) {
                // fallthrough
            }

            Constructor<?> ctor = idClass.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(namespace + ":" + path);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
