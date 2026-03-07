package com.holyworld.autoreply.mixin;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.handler.CommandInterceptor;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "sendChatCommand", at = @At("HEAD"))
    private void onSendChatCommand(String command, CallbackInfo ci) {
        try {
            if (command != null) {
                HolyWorldAutoReply.LOGGER.info("[Mixin-CMD] Перехвачена команда: /{}", command);
                CommandInterceptor.onPlayerSendCommand(command);
            }
        } catch (Exception e) {
            HolyWorldAutoReply.LOGGER.error("[Mixin-CMD] Ошибка: {}", e.getMessage());
        }
    }

    @Inject(method = "sendChatMessage", at = @At("HEAD"))
    private void onSendChatMessage(String message, CallbackInfo ci) {
        try {
            if (message != null && message.startsWith("/")) {
                HolyWorldAutoReply.LOGGER.info("[Mixin-MSG] Перехвачено сообщение-команда: {}", message);
                CommandInterceptor.onPlayerSendCommand(message);
            }
        } catch (Exception e) {
            HolyWorldAutoReply.LOGGER.error("[Mixin-MSG] Ошибка: {}", e.getMessage());
        }
    }
}
