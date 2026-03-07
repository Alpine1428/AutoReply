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

    // Флаг: мы сами отправляем команду, не обрабатывать
    private static volatile boolean selfSending = false;

    // Защита от двойной обработки
    private static volatile String lastCmd = "";
    private static volatile long lastCmdTime = 0;

    /**
     * Проверяет, отправляем ли мы сами команду (чтобы mixin не перехватывал свои же команды)
     */
    public static boolean isSelfSending() {
        return selfSending;
    }

    /**
     * Вызывается из ClientPlayNetworkHandlerMixin при ЛЮБОЙ исходящей команде.
     * Сюда попадают команды от игрока, от других модов, от кого угодно.
     * Свои команды отфильтрованы через isSelfSending().
     */
    public static void onCommandDetected(String command) {
        if (command == null || command.isEmpty()) return;

        String cmd = command.startsWith("/") ? command.substring(1) : command;
        String lower = cmd.toLowerCase().trim();

        // Защита от двойной обработки одной и той же команды за 300мс
        long now = System.currentTimeMillis();
        if (lower.equals(lastCmd) && (now - lastCmdTime) < 300) {
            return;
        }
        lastCmd = lower;
        lastCmdTime = now;

        ModConfig cfg = HolyWorldAutoReply.getConfig();

        HolyWorldAutoReply.LOGGER.info("[Interceptor] Обработка: /{}", cmd);

        // ============================================================
        // hm spy <nick> (но не hm spyfrz)
        // ============================================================
        if (lower.startsWith("hm spy ") && !lower.startsWith("hm spyfrz")) {
            String[] parts = cmd.split("\\s+");
            if (parts.length >= 3) {
                String nick = parts[2].replaceAll("[^a-zA-Z0-9_]", "");
                if (!nick.isEmpty()) {
                    cfg.setLastSpyNick(nick);
                    sendLocal("\u00a7e[HW] \u00a77Ник: \u00a7f" + nick);
                    HolyWorldAutoReply.LOGGER.info("[Interceptor] Spy ник: {}", nick);
                }
            }
        }

        // ============================================================
        // hm spyfrz
        // ============================================================
        if (lower.startsWith("hm spyfrz")) {
            cfg.startWaiting();
            HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
            sendLocal("\u00a7a[HW] \u00a77Ожидание [CHECK]...");
            HolyWorldAutoReply.LOGGER.info("[Interceptor] Начато ожидание проверки");

            String nick = cfg.getLastSpyNick();
            if (nick != null && !nick.isEmpty()) {
                if (cfg.isAutoReports()) {
                    String ac = "hm startcheckout " + nick + " report";
                    sendLocal("\u00a7a[HW] \u00a77Авто: \u00a7f/" + ac);
                    scheduleSelfCommand(ac, 500);
                } else if (cfg.isAutoCheckout()) {
                    String ac = "hm startcheckout " + nick + " checkout";
                    sendLocal("\u00a7a[HW] \u00a77Авто: \u00a7f/" + ac);
                    scheduleSelfCommand(ac, 500);
                }
            }
        }

        // ============================================================
        // hm sban
        // ============================================================
        if (lower.startsWith("hm sban")) {
            handleCheckEnd(cfg, "sban");
        }

        // ============================================================
        // banip (с ЛЮБЫМИ аргументами: banip, banip nick, banip nick 30d reason...)
        // ============================================================
        if (lower.startsWith("banip")) {
            HolyWorldAutoReply.LOGGER.info("[Interceptor] !!! BANIP обнаружен: /{}", cmd);
            handleCheckEnd(cfg, "banip");
        }

        // ============================================================
        // hm unfrz / hm unfreezing
        // ============================================================
        if (lower.startsWith("hm unfrz") || lower.startsWith("hm unfreezing")) {
            handleCheckEnd(cfg, "unfrz");
        }
    }

    /**
     * Общая логика завершения проверки
     */
    private static void handleCheckEnd(ModConfig cfg, String source) {
        String lastNick = cfg.getCheckedPlayerName();
        if (lastNick == null || lastNick.isEmpty()) {
            lastNick = cfg.getLastSpyNick();
        }

        if (!cfg.isIdle()) {
            cfg.endCheck();
            HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
            sendLocal("\u00a7e[HW] \u00a77Проверка завершена (" + source + ").");
            HolyWorldAutoReply.LOGGER.info("[Interceptor] Проверка завершена ({}), ник: {}", source, lastNick);
        }

        // Авто endcheckout для sban и banip
        if ((source.equals("sban") || source.equals("banip")) && cfg.isAutoOut() && lastNick != null && !lastNick.isEmpty()) {
            String ec = "hm endcheckout ban " + lastNick + " false";
            sendLocal("\u00a7a[HW] \u00a77Авто: \u00a7f/" + ec);
            scheduleSelfCommand(ec, 2000);
        }
    }

    /**
     * Отправка команды от имени мода (с флагом selfSending чтобы mixin не перехватывал)
     */
    public static void scheduleSelfCommand(String cmd, long delay) {
        scheduler.schedule(() -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.player != null && c.getNetworkHandler() != null) {
                c.execute(() -> {
                    selfSending = true;
                    try {
                        c.getNetworkHandler().sendChatCommand(cmd);
                        HolyWorldAutoReply.LOGGER.info("[Interceptor] Отправлено: /{}", cmd);
                    } finally {
                        selfSending = false;
                    }
                });
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private static void sendLocal(String msg) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c != null) {
            c.execute(() -> {
                if (c.player != null) {
                    c.player.sendMessage(Text.literal(msg), false);
                }
            });
        }
    }
}
