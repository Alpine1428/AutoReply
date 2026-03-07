package com.holyworld.autoreply.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "sendMessage", at = @At("HEAD"))
    private void onSendMessage(String message, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
        // Команды перехватываются через ClientPlayNetworkHandlerMixin
        // Здесь ничего не делаем чтобы избежать двойной обработки
    }
}
