package com.holyworld.autoreply.mixin;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.handler.CommandInterceptor;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "sendMessage", at = @At("HEAD"))
    private void hw_onSendMessage(String message, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (message == null || !message.startsWith("/")) return;
            if (CommandInterceptor.isIgnoring()) return;
            String cmd = message.substring(1);
            HolyWorldAutoReply.LOGGER.info("[HW-SCREEN] Команда из чата: {}", cmd);
            CommandInterceptor.onCommandDetected(cmd);
        } catch (Exception e) {
            HolyWorldAutoReply.LOGGER.error("[HW-SCREEN] Ошибка: {}", e.getMessage());
        }
    }
}
