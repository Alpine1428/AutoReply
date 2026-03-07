package com.holyworld.autoreply.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    // Команды теперь перехватываются через ClientPlayNetworkHandlerMixin
    // Этот mixin оставлен для совместимости, но основная логика в NetworkHandler
    @Inject(method = "sendMessage", at = @At("HEAD"))
    private void onSendMessage(String message, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
        // Пустой - вся логика в ClientPlayNetworkHandlerMixin
        // Защита от дублирования встроена в CommandInterceptor
    }
}
