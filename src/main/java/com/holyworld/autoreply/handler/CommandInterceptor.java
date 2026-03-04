package com.holyworld.autoreply.handler;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.*;

public class CommandInterceptor {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Вызывается из ChatScreenMixin при отправке команды
     */
    public static void onPlayerSendCommand(String command) {
        if (command == null) return;
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        String lower = cmd.toLowerCase().trim();
        ModConfig cfg = HolyWorldAutoReply.getConfig();

        HolyWorldAutoReply.LOGGER.info("[Cmd] Перехвачено: /{}", cmd);

        // === /hm spy <ник> ===
        if (lower.startsWith("hm spy ") && !lower.startsWith("hm spyfrz")) {
            String[] parts = cmd.split("\\s+");
            if (parts.length >= 3) {
                String nick = parts[2].replaceAll("[^a-zA-Z0-9_]", "");
                if (!nick.isEmpty()) {
                    cfg.setLastSpyNick(nick);
                    sendLocal("\u00a7e[HW] \u00a77Запомнен ник: \u00a7f" + nick);
                }
            }
        }

        // === /hm spyfrz === НАЧАЛО ПРОВЕРКИ
        if (lower.startsWith("hm spyfrz")) {
            cfg.startWaiting();
            HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
            sendLocal("\u00a7a[HW] \u00a77Ожидание [CHECK]...");

            // Авто-внос проверки
            String nick = cfg.getLastSpyNick();
            if (nick != null && !nick.isEmpty()) {
                if (cfg.isAutoReports()) {
                    scheduleCommand("hm startcheckout " + nick + " report", 500);
                    sendLocal("\u00a7a[HW] \u00a77Авто-внос: \u00a7f/hm startcheckout " + nick + " report");
                } else if (cfg.isAutoCheckout()) {
                    scheduleCommand("hm startcheckout " + nick + " checkout", 500);
                    sendLocal("\u00a7a[HW] \u00a77Авто-внос: \u00a7f/hm startcheckout " + nick + " checkout");
                }
            }
        }

        // === ЗАВЕРШАЮЩИЕ КОМАНДЫ ===
        // /hm sban ... (без ника - другой плагин подставляет)
        if (lower.startsWith("hm sban")) {
            endCheckIfActive();
        }

        // /banip ...
        if (lower.startsWith("banip")) {
            endCheckIfActive();
        }

        // /hm unfrz ...
        if (lower.startsWith("hm unfrz") || lower.startsWith("hm unfreezing")) {
            endCheckIfActive();
        }
    }

    private static void endCheckIfActive() {
        ModConfig cfg = HolyWorldAutoReply.getConfig();
        if (!cfg.isIdle()) {
            cfg.endCheck();
            HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
            sendLocal("\u00a7e[HW] \u00a77Проверка завершена.");
        }
    }

    private static void scheduleCommand(String cmd, long delay) {
        scheduler.schedule(() -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.player != null && c.getNetworkHandler() != null) {
                c.execute(() -> c.getNetworkHandler().sendChatCommand(cmd));
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private static void sendLocal(String msg) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c != null) {
            c.execute(() -> {
                if (c.player != null) c.player.sendMessage(Text.literal(msg), false);
            });
        }
    }
}
