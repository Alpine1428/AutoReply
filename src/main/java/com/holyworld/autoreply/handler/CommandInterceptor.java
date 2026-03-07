package com.holyworld.autoreply.handler;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommandInterceptor {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HW-Cmd");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean ignoring = new AtomicBoolean(false);

    private static volatile long lastBanipTime = 0;
    private static volatile long lastSbanTime = 0;
    private static volatile long lastSpyfrzTime = 0;

    public static boolean isIgnoring() {
        return ignoring.get();
    }

    /**
     * Единая точка входа для обнаруженных команд.
     * Вызывается из ChatScreenMixin и CommandSendMixin.
     * Команда приходит БЕЗ слеша.
     */
    public static void onCommandDetected(String command) {
        if (command == null || command.isEmpty()) return;

        String lower = command.toLowerCase().trim();
        long now = System.currentTimeMillis();
        ModConfig cfg = HolyWorldAutoReply.getConfig();

        // ======= hm spy <nick> (не spyfrz) =======
        if (lower.startsWith("hm spy ") && !lower.startsWith("hm spyfrz")) {
            String[] parts = command.split("\\s+");
            if (parts.length >= 3) {
                String nick = parts[2].replaceAll("[^a-zA-Z0-9_]", "");
                if (!nick.isEmpty()) {
                    cfg.setLastSpyNick(nick);
                    localMsg("\u00a7e[HW] \u00a77Ник: \u00a7f" + nick);
                    HolyWorldAutoReply.LOGGER.info("[CMD] spy nick={}", nick);
                }
            }
        }

        // ======= hm spyfrz =======
        if (lower.startsWith("hm spyfrz")) {
            if (now - lastSpyfrzTime < 2000) return;
            lastSpyfrzTime = now;

            cfg.startWaiting();
            HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
            localMsg("\u00a7a[HW] \u00a77Ожидание [CHECK]...");
            HolyWorldAutoReply.LOGGER.info("[CMD] spyfrz - ожидание");

            String nick = cfg.getLastSpyNick();
            if (nick != null && !nick.isEmpty()) {
                if (cfg.isAutoReports()) {
                    String ac = "hm startcheckout " + nick + " report";
                    localMsg("\u00a7a[HW] \u00a77Авто: \u00a7f/" + ac);
                    sendOwnCommand(ac, 600);
                } else if (cfg.isAutoCheckout()) {
                    String ac = "hm startcheckout " + nick + " checkout";
                    localMsg("\u00a7a[HW] \u00a77Авто: \u00a7f/" + ac);
                    sendOwnCommand(ac, 600);
                }
            }
        }

        // ======= hm sban =======
        if (lower.startsWith("hm sban")) {
            if (now - lastSbanTime < 2000) return;
            lastSbanTime = now;
            HolyWorldAutoReply.LOGGER.info("[CMD] sban обнаружен");
            finishCheck(cfg, "sban");
        }

        // ======= banip (любые аргументы) =======
        if (lower.startsWith("banip")) {
            if (now - lastBanipTime < 2000) return;
            lastBanipTime = now;
            HolyWorldAutoReply.LOGGER.info("[CMD] banip обнаружен: {}", command);
            finishCheck(cfg, "banip");
        }

        // ======= hm unfrz / hm unfreezing =======
        if (lower.startsWith("hm unfrz") || lower.startsWith("hm unfreezing")) {
            HolyWorldAutoReply.LOGGER.info("[CMD] unfrz обнаружен");
            if (!cfg.isIdle()) {
                cfg.endCheck();
                HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                localMsg("\u00a7e[HW] \u00a77Проверка завершена (unfrz).");
            }
        }
    }

    private static void finishCheck(ModConfig cfg, String source) {
        String lastNick = cfg.getCheckedPlayerName();
        if (lastNick == null || lastNick.isEmpty()) {
            lastNick = cfg.getLastSpyNick();
        }

        if (!cfg.isIdle()) {
            cfg.endCheck();
            HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
            localMsg("\u00a7e[HW] \u00a77Проверка завершена (" + source + ").");
            HolyWorldAutoReply.LOGGER.info("[CMD] Проверка завершена ({}), ник={}", source, lastNick);
        }

        if (cfg.isAutoOut() && lastNick != null && !lastNick.isEmpty()) {
            String ec = "hm endcheckout ban " + lastNick + " false";
            localMsg("\u00a7a[HW] \u00a77Авто: \u00a7f/" + ec);
            sendOwnCommand(ec, 2500);
        }
    }

    /**
     * Отправка команды от имени мода. 
     * Устанавливает флаг ignoring чтобы миксины не перехватывали.
     */
    public static void sendOwnCommand(String cmd, long delayMs) {
        scheduler.schedule(() -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null || c.getNetworkHandler() == null) return;
            c.execute(() -> {
                ignoring.set(true);
                try {
                    c.getNetworkHandler().sendChatCommand(cmd);
                    HolyWorldAutoReply.LOGGER.info("[CMD] Отправлено: /{}", cmd);
                } catch (Exception e) {
                    HolyWorldAutoReply.LOGGER.error("[CMD] Ошибка отправки: {}", e.getMessage());
                } finally {
                    // Сбрасываем флаг через небольшую задержку
                    // чтобы mixin точно успел пропустить
                    scheduler.schedule(() -> ignoring.set(false), 100, TimeUnit.MILLISECONDS);
                }
            });
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private static void localMsg(String msg) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null) return;
        c.execute(() -> {
            if (c.player != null) {
                c.player.sendMessage(Text.literal(msg), false);
            }
        });
    }
}
