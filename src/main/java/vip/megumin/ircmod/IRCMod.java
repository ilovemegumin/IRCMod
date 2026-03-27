package vip.megumin.ircmod;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import vip.megumin.ircmod.config.IRCConfig;

/**
 * @author haipi
 */

public class IRCMod implements ClientModInitializer {

    private static final String CATEGORY_KEY = "key.category.ircmod.main";

    private static KeyMapping openConfigKey;
    private static int openConfigKeyCode = GLFW.GLFW_KEY_J;
    private static boolean openConfigKeyWasDown = false;
    private static boolean firstRunMessageSent = false;

    @Override
    public void onInitializeClient() {
        IRCClient.init();

        IRCConfig cfg = IRCClient.getConfig();
        if (cfg != null && cfg.openConfigKeyCode > 0) {
            openConfigKeyCode = cfg.openConfigKeyCode;
        }

        openConfigKey = KeyMappingHelper.registerKeyMapping(createKeyBinding(
                "key.ircmod.open_config",
                openConfigKeyCode
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!firstRunMessageSent && IRCClient.isFirstRun() && client.player != null) {
                IRCClient.sendSystemMessage("Press J to Settings UI");
                firstRunMessageSent = true;
            }
            boolean opened = false;
            while (openConfigKey.consumeClick()) {
                opened = true;
                client.setScreen(new IRCConfigScreen(client.screen));
            }
            if (!opened) {
                handleDirectOpenKey(client);
            }
        });

        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> !IRCClient.handleOutgoingMessage(message));
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

    private static void handleDirectOpenKey(Minecraft client) {
        if (client == null || client.getWindow() == null) {
            return;
        }
        if (client.screen != null) {
            openConfigKeyWasDown = false;
            return;
        }
        long handle = getWindowHandle(client.getWindow());
        if (handle == 0L) {
            return;
        }
        boolean down = GLFW.glfwGetKey(handle, openConfigKeyCode) == GLFW.GLFW_PRESS;
        if (down && !openConfigKeyWasDown) {
            client.setScreen(new IRCConfigScreen(client.screen));
        }
        openConfigKeyWasDown = down;
    }

    private static void tryUpdateKeyBinding(KeyMapping keyBinding, int keyCode) {
        if (keyBinding == null) {
            return;
        }
        try {
            keyBinding.setKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
            KeyMapping.resetMapping();
        } catch (Throwable ignored) {
        }
    }

    private static KeyMapping createKeyBinding(String translationKey, int keyCode) {
        InputConstants.Type type = InputConstants.Type.KEYSYM;
        Object categoryId = createCategoryId("ircmod", "main");

        for (Constructor<?> ctor : KeyMapping.class.getConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            try {
                if (p.length == 5
                        && p[0] == String.class
                        && p[1] == InputConstants.Type.class
                        && p[2] == int.class
                        && p[4] == int.class) {
                    Object categoryArg = createCategoryArg(p[3], categoryId);
                    if (categoryArg != null) {
                        return (KeyMapping) ctor.newInstance(translationKey, type, keyCode, categoryArg, 0);
                    }
                }

                if (p.length == 4
                        && p[0] == String.class
                        && p[1] == InputConstants.Type.class
                        && p[2] == int.class) {
                    Object categoryArg = createCategoryArg(p[3], categoryId);
                    if (categoryArg != null) {
                        return (KeyMapping) ctor.newInstance(translationKey, type, keyCode, categoryArg);
                    }
                }

                if (p.length == 3
                        && p[0] == String.class
                        && p[1] == int.class) {
                    Object categoryArg = createCategoryArg(p[2], categoryId);
                    if (categoryArg != null) {
                        return (KeyMapping) ctor.newInstance(translationKey, keyCode, categoryArg);
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        throw new IllegalStateException("No compatible KeyBinding constructor found for this Minecraft version");
    }

    private static Object createCategoryArg(Class<?> categoryParamType, Object id) {
        if (categoryParamType == String.class) {
            return CATEGORY_KEY;
        }
        if (id == null) {
            return null;
        }

        try {
            Constructor<?> ctor = categoryParamType.getDeclaredConstructor(id.getClass());
            ctor.setAccessible(true);
            return ctor.newInstance(id);
        } catch (Throwable ignored) {
        }

        try {
            for (Method m : categoryParamType.getMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                if (m.getParameterCount() != 1 || !m.getParameterTypes()[0].isAssignableFrom(id.getClass())) {
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

    private static Object createCategoryId(String namespace, String path) {
        try {
            Class<?> resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation");

            try {
                Method factory = resourceLocationClass.getMethod("fromNamespaceAndPath", String.class, String.class);
                return factory.invoke(null, namespace, path);
            } catch (NoSuchMethodException ignored) {
            }

            Constructor<?> ctor = resourceLocationClass.getDeclaredConstructor(String.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(namespace, path);
        } catch (Throwable ignored) {
        }

        try {
            Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier");

            try {
                Method factory = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class);
                return factory.invoke(null, namespace, path);
            } catch (NoSuchMethodException ignored) {
            }

            Constructor<?> ctor = identifierClass.getDeclaredConstructor(String.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(namespace, path);
        } catch (Throwable ignored) {
        }

        return null;
    }

    static long getWindowHandle(Object window) {
        if (window == null) {
            return 0L;
        }

        for (String methodName : new String[]{"getWindow", "handle", "getHandle"}) {
            try {
                Method method = window.getClass().getMethod(methodName);
                Object value = method.invoke(window);
                if (value instanceof Long l) {
                    return l;
                }
                if (value instanceof Number n) {
                    return n.longValue();
                }
            } catch (Throwable ignored) {
            }
        }

        return 0L;
    }
}
