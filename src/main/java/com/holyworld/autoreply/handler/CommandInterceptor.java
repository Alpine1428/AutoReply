package com.holyworld.autoreply.handler;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.config.ModConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

public class CommandInterceptor {
    public CommandInterceptor() {
        ClientSendMessageEvents.COMMAND.register(this::handle);
    }

    private void handle(String command) {
        String lower = command.toLowerCase().trim();
        ModConfig cfg = HolyWorldAutoReply.getConfig();

        // Trigger: /hm spyfrz (no args)
        if (lower.equals("hm spyfrz") || lower.startsWith("hm spyfrz ")) {
            cfg.startWaiting();
            HolyWorldAutoReply.getChatHandler().resetState();
            HolyWorldAutoReply.LOGGER.info("[HW] Waiting for [CHECK]...");
        }
        
        // Stop: /hm sban..., /hm unfrz...
        else if (lower.startsWith("hm sban") || 
                 lower.startsWith("hm unfrz") || 
                 lower.startsWith("hm unfreezing") || 
                 lower.startsWith("banip")) {
            
            if (!cfg.isIdle()) {
                HolyWorldAutoReply.LOGGER.info("[HW] Check Ended.");
                cfg.endCheck();
            }
        }
    }
}
