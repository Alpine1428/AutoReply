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
            if (command == null || command.isEmpty()) return;
            if (CommandInterceptor.isSelfSending()) return;
            HolyWorldAutoReply.LOGGER.info("[Mixin-NET] /{}", command);
            CommandInterceptor.onCommandDetected(command);
        } catch (Exception e) {
            HolyWorldAutoReply.LOGGER.error("[Mixin-NET] {}", e.getMessage());
        }
    }
}
