package com.holyworld.autoreply.handler;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CommandInterceptor {

    private static final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HW-CmdInterceptor");
            t.setDaemon(true);
            return t;
        });

    /**
     * Called from ChatScreenMixin when player sends a command
     */
    public static void onPlayerSendCommand(String command) {
        if (command == null || command.isEmpty()) return;

        String cmd = command.startsWith("/") ? command.substring(1) : command;
        String lower = cmd.toLowerCase().trim();
        ModConfig cfg = HolyWorldAutoReply.getConfig();

        HolyWorldAutoReply.LOGGER.info("[CmdInterceptor] Command: /{}", cmd);

        // === /hm spy <nick> — save nick ===
        if (lower.startsWith("hm spy ") && !lower.startsWith("hm spyfrz")) {
            String afterSpy = cmd.substring(7).trim();
            String nick = afterSpy.split("\\s+")[0].trim();
            nick = nick.replaceAll("[^a-zA-Z0-9_]", "");

            if (!nick.isEmpty()) {
                cfg.setLastSpyNick(nick);
                HolyWorldAutoReply.LOGGER.info("[CmdInterceptor] Saved spy nick: {}", nick);
                sendLocalMessage("\u00a76\u00a7l[Auto] \u00a7eSpy nick: \u00a7f" + nick);
            }
        }

        // === /hm spyfrz — start check, auto startcheckout ===
        if (lower.startsWith("hm spyfrz")) {
            cfg.startWaiting();
            if (HolyWorldAutoReply.getChatHandler() != null) {
                HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
            }
            HolyWorldAutoReply.LOGGER.info("[CmdInterceptor] State -> WAITING_FOR_CHECK");
            sendLocalMessage("\u00a7e\u00a7l[HW] \u00a7fWaiting for [CHECK]...");

            String lastNick = cfg.getLastSpyNick();
            if (lastNick != null && !lastNick.isEmpty()) {
                if (cfg.isAutoReports()) {
                    String autoCmd = "hm startcheckout " + lastNick + " report";
                    HolyWorldAutoReply.LOGGER.info("[CmdInterceptor] Auto: /{}", autoCmd);
                    sendLocalMessage("\u00a7a\u00a7l[Auto] \u00a7f/" + autoCmd);
                    scheduler.schedule(() -> sendCommand(autoCmd), 500, TimeUnit.MILLISECONDS);
                } else if (cfg.isAutoCheckout()) {
                    String autoCmd = "hm startcheckout " + lastNick + " checkout";
                    HolyWorldAutoReply.LOGGER.info("[CmdInterceptor] Auto: /{}", autoCmd);
                    sendLocalMessage("\u00a7a\u00a7l[Auto] \u00a7f/" + autoCmd);
                    scheduler.schedule(() -> sendCommand(autoCmd), 500, TimeUnit.MILLISECONDS);
                }
            }
        }

        // === /hm sban — end check, auto endcheckout ===
        if (lower.startsWith("hm sban ")) {
            if (!cfg.isIdle()) {
                HolyWorldAutoReply.LOGGER.info("[CmdInterceptor] State -> IDLE (sban)");
                cfg.endCheck();
                if (HolyWorldAutoReply.getChatHandler() != null) {
                    HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                }
            }

            if (cfg.isAutoOut()) {
                String afterSban = cmd.substring(8).trim();
                String[] parts = afterSban.split("\\s+");
                if (parts.length >= 1) {
                    String bannedNick = parts[0].replaceAll("[^a-zA-Z0-9_]", "");
                    if (!bannedNick.isEmpty()) {
                        String endCmd = "hm endcheckout ban " + bannedNick + " false";
                        HolyWorldAutoReply.LOGGER.info("[CmdInterceptor] AutoOut: /{}", endCmd);
                        sendLocalMessage("\u00a7a\u00a7l[AutoOut] \u00a7f/" + endCmd);
                        scheduler.schedule(() -> sendCommand(endCmd), 1500, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }

        // === /banip — end check ===
        if (lower.startsWith("banip")) {
            if (!cfg.isIdle()) {
                HolyWorldAutoReply.LOGGER.info("[CmdInterceptor] State -> IDLE (banip)");
                cfg.endCheck();
                if (HolyWorldAutoReply.getChatHandler() != null) {
                    HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                }
            }
        }

        // === /hm unfrz, /hm unfreezing — end check ===
        if (lower.startsWith("hm unfrz") || lower.startsWith("hm unfreezing")) {
            if (!cfg.isIdle()) {
                HolyWorldAutoReply.LOGGER.info("[CmdInterceptor] State -> IDLE (unfrz)");
                cfg.endCheck();
                if (HolyWorldAutoReply.getChatHandler() != null) {
                    HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                }
            }
        }
    }

    private static void sendCommand(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            try {
                if (client.player == null || client.getNetworkHandler() == null) return;
                String cmd = command.startsWith("/") ? command.substring(1) : command;
                client.getNetworkHandler().sendChatCommand(cmd);
                HolyWorldAutoReply.LOGGER.info("[CmdInterceptor] SENT: /{}", cmd);
            } catch (Exception e) {
                HolyWorldAutoReply.LOGGER.error("[CmdInterceptor] Failed: {}", e.getMessage());
            }
        });
    }

    private static void sendLocalMessage(String msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal(msg), false);
            }
        });
    }
}
