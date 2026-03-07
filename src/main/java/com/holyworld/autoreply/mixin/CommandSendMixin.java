package com.holyworld.autoreply.mixin;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.handler.CommandInterceptor;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class CommandSendMixin {

    @Inject(method = "sendChatCommand", at = @At("HEAD"))
    private void hw_onSendChatCommand(String command, CallbackInfo ci) {
        try {
            if (command == null || command.isEmpty()) return;
            if (CommandInterceptor.isIgnoring()) return;
            HolyWorldAutoReply.LOGGER.info("[HW-CMD] sendChatCommand: {}", command);
            CommandInterceptor.onCommandDetected(command);
        } catch (Exception e) {
            HolyWorldAutoReply.LOGGER.error("[HW-CMD] err: {}", e.getMessage());
        }
    }
}
