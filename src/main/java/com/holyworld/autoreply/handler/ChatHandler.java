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
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "HW-Chat"); t.setDaemon(true); return t; });
    }

    public ResponseEngine getResponseEngine() { return engine; }

    public void processIncoming(String raw) {
        if (raw == null || raw.isEmpty()) return;
        ModConfig cfg = HolyWorldAutoReply.getConfig();
        if (!cfg.isEnabled()) return;
        String clean = stripColors(raw);
        if (!clean.contains("[CHECK]")) return;

        int idx = clean.indexOf("[CHECK]");
        String after = clean.substring(idx + 7).trim();
        int arrow = after.indexOf("->");
        if (arrow == -1) arrow = after.indexOf(":");
        if (arrow == -1) arrow = after.indexOf("\u00BB");
        if (arrow <= 0) return;

        String nick = after.substring(0, arrow).trim().replaceAll("[^a-zA-Z0-9_]", "");
        String msg = after.substring(arrow + (after.charAt(arrow) == '-' ? 2 : 1)).trim();
        if (msg.startsWith(">")) msg = msg.substring(1).trim();
        if (nick.isEmpty() || msg.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null && nick.equalsIgnoreCase(client.player.getName().getString())) return;

        if (cfg.isIdle()) return;
        if (cfg.isWaiting()) { cfg.activateCheck(nick); HolyWorldAutoReply.LOGGER.info("[Handler] Проверка: {}", nick); }
        if (cfg.isCheckActive() && !nick.equalsIgnoreCase(cfg.getCheckedPlayerName())) return;

        long now = System.currentTimeMillis();
        Long last = cooldowns.get(nick);
        if (last != null && (now - last) < 2000) return;
        cooldowns.put(nick, now);

        ResponseEngine.Result result = engine.getResult(msg, nick);
        if (result == null) return;
        final String target = nick;

        switch (result.action) {
            case REPLY: if (cfg.isAutoReply() && result.message != null) scheduleChat(result.message, 800 + (long)(Math.random() * 1200)); break;
            case BAN_INSULT: if (cfg.isAutoBanInsult()) performBan("30d", "Неадекват", target); else scheduleChat("Оскорбления не помогут.", 1000); break;
            case BAN_REFUSAL: if (cfg.isAutoBanRefusal()) performBan("30d", "Отказ", target); else scheduleChat("Отказ = бан 30 дней.", 1000); break;
            case BAN_CONFESSION: if (cfg.isAutoBanConfession()) performBan("20d", "Признание", target); else scheduleChat("Признание принято.", 1000); break;
        }
    }

    private void performBan(String time, String reason, String nick) {
        HolyWorldAutoReply.LOGGER.info("[Handler] БАН: {} {} ({})", time, reason, nick);
        scheduleCommand("hm sban " + time + " " + reason, 500);
        scheduler.schedule(() -> {
            HolyWorldAutoReply.getConfig().endCheck();
            engine.clearPlayerState(nick);
            if (HolyWorldAutoReply.getConfig().isAutoOut()) scheduleCommand("hm endcheckout ban " + nick + " false", 1500);
        }, 700, TimeUnit.MILLISECONDS);
    }

    private void scheduleChat(String msg, long delay) {
        scheduler.schedule(() -> { MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.player != null && c.getNetworkHandler() != null) c.execute(() -> c.getNetworkHandler().sendChatMessage(msg));
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void scheduleCommand(String cmd, long delay) {
        scheduler.schedule(() -> { MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.player != null && c.getNetworkHandler() != null) c.execute(() -> { c.getNetworkHandler().sendChatCommand(cmd); HolyWorldAutoReply.LOGGER.info("[Handler] /{}", cmd); });
        }, delay, TimeUnit.MILLISECONDS);
    }

    private static String stripColors(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c == '\u00A7' && i+1 < s.length()) i++; else sb.append(c); }
        return sb.toString();
    }
}
