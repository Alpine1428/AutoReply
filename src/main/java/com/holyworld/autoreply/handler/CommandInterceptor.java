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

    // Дедупликация
    private static volatile String lastDetectedCmd = "";
    private static volatile long lastDetectedTime = 0;

    public static boolean isIgnoring() {
        return ignoring.get();
    }

    /**
     * Вызывается из миксинов. Команда БЕЗ слеша.
     */
    public static void onCommandDetected(String command) {
        if (command == null || command.isEmpty()) return;

        String lower = command.toLowerCase().trim();
        long now = System.currentTimeMillis();

        // Дедупликация - если та же команда за 1 секунду, пропускаем
        if (lower.equals(lastDetectedCmd) && (now - lastDetectedTime) < 1000) {
            HolyWorldAutoReply.LOGGER.info("[CMD] Дубль пропущен: {}", lower);
            return;
        }
        lastDetectedCmd = lower;
        lastDetectedTime = now;

        ModConfig cfg = HolyWorldAutoReply.getConfig();

        // ======= hm spy <nick> (не spyfrz) =======
        if (lower.startsWith("hm spy ") && !lower.startsWith("hm spyfrz")) {
            String[] parts = command.split("\\s+");
            if (parts.length >= 3) {
                String nick = parts[2].replaceAll("[^a-zA-Z0-9_]", "");
                if (!nick.isEmpty()) {
                    cfg.setLastSpyNick(nick);
                    localMsg("\u00a7e[HW] \u00a77Ник: \u00a7f" + nick);
                    HolyWorldAutoReply.LOGGER.info("[CMD] spy -> {}", nick);
                }
            }
            return;
        }

        // ======= hm spyfrz =======
        if (lower.startsWith("hm spyfrz")) {
            cfg.startWaiting();
            HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
            localMsg("\u00a7a[HW] \u00a77Ожидание [CHECK]...");
            HolyWorldAutoReply.LOGGER.info("[CMD] spyfrz");

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
            return;
        }

        // ======= hm sban =======
        if (lower.startsWith("hm sban")) {
            HolyWorldAutoReply.LOGGER.info("[CMD] sban обнаружен");
            handleBanDetected(cfg, "sban", command);
            return;
        }

        // ======= banip (ЛЮБЫЕ аргументы после) =======
        if (lower.startsWith("banip")) {
            HolyWorldAutoReply.LOGGER.info("[CMD] === BANIP ОБНАРУЖЕН === : {}", command);
            handleBanDetected(cfg, "banip", command);
            return;
        }

        // ======= hm unfrz / hm unfreezing =======
        if (lower.startsWith("hm unfrz") || lower.startsWith("hm unfreezing")) {
            HolyWorldAutoReply.LOGGER.info("[CMD] unfrz обнаружен");
            // Просто завершаем проверку без endcheckout
            if (!cfg.isIdle()) {
                cfg.endCheck();
                HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                localMsg("\u00a7e[HW] \u00a77Проверка завершена (unfrz).");
            }
            return;
        }
    }

    /**
     * Обработка бана (sban или banip).
     * ВСЕГДА пытается отправить endcheckout если есть ник.
     */
    private static void handleBanDetected(ModConfig cfg, String source, String fullCommand) {
        // Сохраняем ник ДО очистки
        String nick = cfg.getCheckedPlayerName();
        HolyWorldAutoReply.LOGGER.info("[CMD] handleBan src={} checkedPlayer='{}' lastSpy='{}' idle={} autoOut={}",
            source, nick, cfg.getLastSpyNick(), cfg.isIdle(), cfg.isAutoOut());

        if (nick == null || nick.isEmpty()) {
            nick = cfg.getLastSpyNick();
            HolyWorldAutoReply.LOGGER.info("[CMD] Используем lastSpyNick: '{}'", nick);
        }

        // Завершаем проверку если активна
        if (!cfg.isIdle()) {
            cfg.endCheck();
            HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
            localMsg("\u00a7e[HW] \u00a77Проверка завершена (" + source + ").");
            HolyWorldAutoReply.LOGGER.info("[CMD] Проверка завершена ({})", source);
        } else {
            HolyWorldAutoReply.LOGGER.info("[CMD] Проверка уже была idle, но всё равно пробуем endcheckout");
        }

        // ВСЕГДА пытаемся endcheckout если есть ник и autoOut включен
        if (cfg.isAutoOut()) {
            if (nick != null && !nick.isEmpty()) {
                final String finalNick = nick;
                String ec = "hm endcheckout ban " + finalNick + " false";
                localMsg("\u00a7a[HW] \u00a77Авто endcheckout: \u00a7f/" + ec);
                HolyWorldAutoReply.LOGGER.info("[CMD] Отправляю endcheckout: {}", ec);
                sendOwnCommand(ec, 2000);
            } else {
                localMsg("\u00a7c[HW] \u00a77Нет ника для endcheckout!");
                HolyWorldAutoReply.LOGGER.warn("[CMD] Нет ника для endcheckout! checkedPlayer='{}' lastSpy='{}'",
                    cfg.getCheckedPlayerName(), cfg.getLastSpyNick());
            }
        } else {
            HolyWorldAutoReply.LOGGER.info("[CMD] autoOut выключен, endcheckout не отправляем");
        }
    }

    /**
     * Отправка своей команды с флагом ignoring.
     */
    public static void sendOwnCommand(String cmd, long delayMs) {
        HolyWorldAutoReply.LOGGER.info("[CMD] Планирую отправку через {}мс: /{}", delayMs, cmd);
        scheduler.schedule(() -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null) {
                HolyWorldAutoReply.LOGGER.error("[CMD] MinecraftClient == null!");
                return;
            }
            if (c.player == null) {
                HolyWorldAutoReply.LOGGER.error("[CMD] player == null!");
                return;
            }
            if (c.getNetworkHandler() == null) {
                HolyWorldAutoReply.LOGGER.error("[CMD] networkHandler == null!");
                return;
            }
            c.execute(() -> {
                try {
                    HolyWorldAutoReply.LOGGER.info("[CMD] >>> Отправляю: /{}", cmd);
                    ignoring.set(true);
                    c.getNetworkHandler().sendChatCommand(cmd);
                    HolyWorldAutoReply.LOGGER.info("[CMD] >>> Отправлено успешно: /{}", cmd);
                } catch (Exception e) {
                    HolyWorldAutoReply.LOGGER.error("[CMD] >>> Ошибка отправки /{}: {}", cmd, e.getMessage());
                } finally {
                    // Сбрасываем через 200мс
                    scheduler.schedule(() -> {
                        ignoring.set(false);
                        HolyWorldAutoReply.LOGGER.info("[CMD] ignoring сброшен");
                    }, 200, TimeUnit.MILLISECONDS);
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
