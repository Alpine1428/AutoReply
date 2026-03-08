package com.holyworld.autoreply.mixin;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.handler.ChatHandler;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD")
    )
    private void hw_onAddMessage(Text message, @Nullable MessageSignatureData sig, @Nullable MessageIndicator ind, CallbackInfo ci) {
        try {
            if (message == null) return;
            String plain = message.getString();
            if (plain == null || plain.isEmpty()) return;

            ChatHandler handler = HolyWorldAutoReply.getChatHandler();
            if (handler == null) return;

            // Перехват [CHECK] сообщений
            if (plain.contains("[CHECK]")) {
                HolyWorldAutoReply.LOGGER.info("[HW-HUD] CHECK: {}", plain);
                handler.processIncoming(plain);
            }

            // Перехват [Тихий] бана за лив с проверки
            if (plain.contains("[Тихий]")) {
                HolyWorldAutoReply.LOGGER.info("[HW-HUD] Тихий: {}", plain);
                handler.processSilentBan(plain);
            }

        } catch (Exception e) {
            HolyWorldAutoReply.LOGGER.error("[HW-HUD] {}", e.getMessage());
        }
    }
}
