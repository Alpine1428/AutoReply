package com.holyworld.autoreply.handler;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.*;

public class CommandInterceptor {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HW-Cmd");
        t.setDaemon(true);
        return t;
    });

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

            String nick = cfg.getLastSpyNick();
            if (nick != null && !nick.isEmpty()) {
                if (cfg.isAutoReports()) {
                    String autoCmd = "hm startcheckout " + nick + " report";
                    sendLocal("\u00a7a[HW] \u00a77Авто-внос: \u00a7f/" + autoCmd);
                    scheduleCommand(autoCmd, 500);
                } else if (cfg.isAutoCheckout()) {
                    String autoCmd = "hm startcheckout " + nick + " checkout";
                    sendLocal("\u00a7a[HW] \u00a77Авто-внос: \u00a7f/" + autoCmd);
                    scheduleCommand(autoCmd, 500);
                }
            }
        }

        // === /hm sban ... === ЗАВЕРШЕНИЕ (без ника!) + endcheckout
        if (lower.startsWith("hm sban")) {
            HolyWorldAutoReply.LOGGER.info("[Cmd] Обнаружен /hm sban -> завершаем проверку");

            String lastNick = cfg.getCheckedPlayerName();
            if (lastNick == null || lastNick.isEmpty()) lastNick = cfg.getLastSpyNick();

            // Завершаем проверку
            if (!cfg.isIdle()) {
                cfg.endCheck();
                HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                sendLocal("\u00a7e[HW] \u00a77Проверка завершена.");
            }

            // Auto Out: endcheckout
            if (cfg.isAutoOut() && lastNick != null && !lastNick.isEmpty()) {
                String endCmd = "hm endcheckout ban " + lastNick + " false";
                sendLocal("\u00a7a[HW] \u00a77Авто-выход: \u00a7f/" + endCmd);
                scheduleCommand(endCmd, 2000);
            }
        }

        // === /banip ... ===
        if (lower.startsWith("banip")) {
            endCheckIfActive();
        }

        // === /hm unfrz ... / /hm unfreezing ... ===
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
                c.execute(() -> {
                    c.getNetworkHandler().sendChatCommand(cmd);
                    HolyWorldAutoReply.LOGGER.info("[Cmd] Отправлено: /{}", cmd);
                });
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private static void sendLocal(String msg) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c != null) {
            c.execute(() -> { if (c.player != null) c.player.sendMessage(Text.literal(msg), false); });
        }
    }
}
