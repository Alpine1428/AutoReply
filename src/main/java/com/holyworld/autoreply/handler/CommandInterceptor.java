package com.holyworld.autoreply.handler;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.config.ModConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

public class CommandInterceptor {
    public CommandInterceptor() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            handleCommand(command);
            return true;
        });
    }

    private void handleCommand(String command) {
        if (command == null) return;
        String lower = command.toLowerCase().trim();
        ModConfig cfg = HolyWorldAutoReply.getConfig();

        HolyWorldAutoReply.LOGGER.info("[HW] Command: /{}", command);

        if (lower.equals("hm spyfrz") || lower.startsWith("hm spyfrz ")) {
            cfg.startWaiting();
            HolyWorldAutoReply.getChatHandler().resetState();
            HolyWorldAutoReply.LOGGER.info("[HW] State -> WAITING_FOR_CHECK");
        }
        else if (lower.startsWith("hm sban") ||
                 lower.startsWith("hm unfrz") ||
                 lower.startsWith("hm unfreezing") ||
                 lower.startsWith("banip")) {
            if (!cfg.isIdle()) {
                HolyWorldAutoReply.LOGGER.info("[HW] State -> IDLE (ended by command)");
                cfg.endCheck();
                HolyWorldAutoReply.getChatHandler().resetState();
            }
        }
    }
}
