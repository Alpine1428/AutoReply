package com.holyworld.autoreply.mixin;
import com.holyworld.autoreply.HolyWorldAutoReply;
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
    @Inject(method="addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",at=@At("HEAD"))
    private void onAddMessage(Text message,@Nullable MessageSignatureData sig,@Nullable MessageIndicator ind,CallbackInfo ci){
        try{if(message==null)return;String plain=message.getString();if(plain!=null&&plain.contains("[CHECK]")&&HolyWorldAutoReply.getChatHandler()!=null){HolyWorldAutoReply.LOGGER.info("[Mixin] {}",plain);HolyWorldAutoReply.getChatHandler().processIncoming(plain);}}catch(Exception e){HolyWorldAutoReply.LOGGER.error("[Mixin] {}",e.getMessage());}
    }
}
