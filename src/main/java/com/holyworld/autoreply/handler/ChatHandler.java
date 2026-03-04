package com.holyworld.autoreply.handler;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.ai.ResponseEngine;
import com.holyworld.autoreply.config.ModConfig;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.*;

public class ChatHandler {

    private final ResponseEngine engine;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    public ChatHandler() {
        this.engine = new ResponseEngine();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HW-Chat");
            t.setDaemon(true);
            return t;
        });
    }

    public ResponseEngine getResponseEngine() { return engine; }

    /**
     * Вызывается из ChatHudMixin при каждом сообщении в чат
     */
    public void processIncoming(String raw) {
        if (raw == null || raw.isEmpty()) return;

        ModConfig cfg = HolyWorldAutoReply.getConfig();
        if (!cfg.isEnabled()) return;

        String clean = stripColors(raw);
        if (!clean.contains("[CHECK]")) return;

        HolyWorldAutoReply.LOGGER.info("[Handler] Получено: '{}'", clean);

        // Парсинг [CHECK] Ник -> Сообщение
        int idx = clean.indexOf("[CHECK]");
        String after = clean.substring(idx + 7).trim();

        // Ищем разделитель: -> или : или »
        int arrow = after.indexOf("->");
        if (arrow == -1) arrow = after.indexOf(":");
        if (arrow == -1) arrow = after.indexOf("»");
        if (arrow <= 0) {
            HolyWorldAutoReply.LOGGER.warn("[Handler] Разделитель не найден в: '{}'", after);
            return;
        }

        String nick = after.substring(0, arrow).trim().replaceAll("[^a-zA-Z0-9_]", "");
        String msg = after.substring(arrow + (after.charAt(arrow) == '-' ? 2 : 1)).trim();
        if (msg.startsWith(">")) msg = msg.substring(1).trim();

        if (nick.isEmpty() || msg.isEmpty()) return;

        // Не отвечаем себе
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            if (nick.equalsIgnoreCase(client.player.getName().getString())) return;
        }

        HolyWorldAutoReply.LOGGER.info("[Handler] Ник='{}' Сообщение='{}'", nick, msg);

        // === ЛОГИКА СОСТОЯНИЙ ===

        // IDLE - игнорируем
        if (cfg.isIdle()) {
            HolyWorldAutoReply.LOGGER.info("[Handler] Состояние IDLE, пропускаем");
            return;
        }

        // WAITING -> ACTIVE (первое сообщение активирует проверку)
        if (cfg.isWaiting()) {
            cfg.activateCheck(nick);
            HolyWorldAutoReply.LOGGER.info("[Handler] Проверка активирована: {}", nick);
        }

        // ACTIVE - отвечаем только проверяемому игроку
        if (cfg.isCheckActive() && !nick.equalsIgnoreCase(cfg.getCheckedPlayerName())) {
            return;
        }

        // Анти-спам (2 сек между ответами)
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(nick);
        if (last != null && (now - last) < 2000) return;
        cooldowns.put(nick, now);

        // === ПОЛУЧАЕМ ОТВЕТ ОТ AI ===
        ResponseEngine.Result result = engine.getResult(msg, nick);
        if (result == null) return;

        switch (result.action) {
            case REPLY:
                if (cfg.isAutoReply() && result.message != null) {
                    long delay = 800 + (long)(Math.random() * 1200);
                    scheduleChat(result.message, delay);
                }
                break;

            case BAN_INSULT:
                if (cfg.isAutoBanInsult()) {
                    // /hm sban 30d Неадекват (БЕЗ НИКА)
                    performBan("30d", "Неадекват", nick);
                } else {
                    scheduleChat("Оскорбления не помогут. Качай AnyDesk.", 1000);
                }
                break;

            case BAN_REFUSAL:
                if (cfg.isAutoBanRefusal()) {
                    // /hm sban 30d Отказ (БЕЗ НИКА)
                    performBan("30d", "Отказ", nick);
                } else {
                    scheduleChat("Отказ = бан 30 дней. Уверен?", 1000);
                }
                break;

            case BAN_CONFESSION:
                if (cfg.isAutoBanConfession()) {
                    // /hm sban 20d Признание (БЕЗ НИКА)
                    performBan("20d", "Признание", nick);
                } else {
                    scheduleChat("Признание принято.", 1000);
                }
                break;
        }
    }

    /**
     * Бан БЕЗ НИКА - другой плагин сам подставляет игрока на проверке
     * Формат: /hm sban <время> <причина>
     */
    private void performBan(String time, String reason, String nick) {
        HolyWorldAutoReply.LOGGER.info("[Handler] БАН: {} {} (игрок: {})", time, reason, nick);

        // Команда бана БЕЗ ника
        scheduleCommand("hm sban " + time + " " + reason, 500);

        // Завершаем проверку
        scheduler.schedule(() -> {
            HolyWorldAutoReply.getConfig().endCheck();
            engine.clearPlayerState(nick);
            HolyWorldAutoReply.LOGGER.info("[Handler] Проверка завершена");
        }, 600, TimeUnit.MILLISECONDS);

        // Auto Out
        if (HolyWorldAutoReply.getConfig().isAutoOut()) {
            scheduleCommand("hm endcheckout ban " + nick + " false", 2500);
        }
    }

    private void scheduleChat(String msg, long delay) {
        scheduler.schedule(() -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.player != null && c.getNetworkHandler() != null) {
                c.execute(() -> {
                    c.getNetworkHandler().sendChatMessage(msg);
                    HolyWorldAutoReply.LOGGER.info("[Handler] Отправлено: '{}'", msg);
                });
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void scheduleCommand(String cmd, long delay) {
        scheduler.schedule(() -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.player != null && c.getNetworkHandler() != null) {
                c.execute(() -> {
                    c.getNetworkHandler().sendChatCommand(cmd);
                    HolyWorldAutoReply.LOGGER.info("[Handler] Команда: /{}", cmd);
                });
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private static String stripColors(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7' && i + 1 < s.length()) { i++; }
            else sb.append(c);
        }
        return sb.toString();
    }
}
