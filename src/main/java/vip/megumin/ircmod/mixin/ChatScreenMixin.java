package vip.megumin.ircmod.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vip.megumin.ircmod.IRCClient;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void ircmod$sendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (IRCClient.handleOutgoingMessage(message)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (addToHistory && client.inGameHud != null) {
                ChatHud chatHud = client.inGameHud.getChatHud();
                chatHud.addToMessageHistory(message);
                chatHud.resetScroll();
            }
            client.setScreen(null);
            ci.cancel();
        }
    }
}
