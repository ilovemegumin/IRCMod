package vip.megumin.ircmod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vip.megumin.ircmod.IRCClient;

/**
 * @author haipi
 */

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void ircmod$sendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (IRCClient.handleOutgoingMessage(message)) {
            Minecraft client = Minecraft.getInstance();
            if (addToHistory && client.gui != null) {
                ChatComponent chatHud = client.gui.getChat();
                chatHud.addRecentChat(message);
                chatHud.resetChatScroll();
            }
            client.setScreen(null);
            ci.cancel();
        }
    }
}
