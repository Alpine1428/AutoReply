package com.holyworld.autoreply.handler;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.ai.ResponseEngine;
import com.holyworld.autoreply.config.ModConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import java.util.concurrent.*;
import java.util.regex.*;

public class ChatHandler {
    private final ResponseEngine engine = new ResponseEngine();
    private final ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();

    private final Pattern P = Pattern.compile(
        "\\[CHECK\\]\\s*([a-zA-Z0-9_]{2,16})\\s*(?:->|:|>>)\\s*(.+)"
    );

    private final ConcurrentHashMap<String, Long> cd = new ConcurrentHashMap<>();
    private boolean warned = false;

    public ChatHandler() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            try {
                String raw = message.getString();
                HolyWorldAutoReply.LOGGER.debug("[HW-GAME] overlay={} raw: {}", overlay, raw);
                process(raw);
            } catch (Exception e) {
                HolyWorldAutoReply.LOGGER.error("[HW] Error in GAME handler", e);
            }
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            try {
                String raw = message.getString();
                HolyWorldAutoReply.LOGGER.debug("[HW-CHAT] raw: {}", raw);
                process(raw);
            } catch (Exception e) {
                HolyWorldAutoReply.LOGGER.error("[HW] Error in CHAT handler", e);
            }
        });
    }

    public void resetState() {
        warned = false;
        engine.clear();
    }

    private void process(String raw) {
        ModConfig cfg = HolyWorldAutoReply.getConfig();

        if (raw == null || raw.isEmpty()) return;

        String clean = stripColors(raw).trim();

        if (!clean.contains("[CHECK]")) return;

        HolyWorldAutoReply.LOGGER.info("[HW] Detected [CHECK] message: '{}'", clean);

        if (cfg.isIdle()) {
            HolyWorldAutoReply.LOGGER.info("[HW] State is IDLE, ignoring [CHECK]");
            return;
        }

        Matcher m = P.matcher(clean);
        if (!m.find()) {
            HolyWorldAutoReply.LOGGER.warn("[HW] Pattern did NOT match! clean='{}'", clean);
            return;
        }

        String nick = m.group(1);
        String msg = m.group(2).trim();

        HolyWorldAutoReply.LOGGER.info("[HW] Parsed: nick='{}', msg='{}'", nick, msg);

        if (cfg.isWaiting()) {
            cfg.activateCheck(nick);
            HolyWorldAutoReply.LOGGER.info("[HW] State -> CHECK_ACTIVE for '{}'", nick);
        }

        if (cfg.isCheckActive() && nick.equalsIgnoreCase(cfg.getCheckedPlayerName())) {
            handle(nick, msg);
        }
    }

    private void handle(String n, String m) {
        long now = System.currentTimeMillis();
        Long last = cd.get(n);
        if (last != null && now - last < 2000) return;
        cd.put(n, now);

        String l = m.toLowerCase();
        ModConfig cfg = HolyWorldAutoReply.getConfig();

        HolyWorldAutoReply.LOGGER.info("[HW] Processing msg from '{}': '{}'", n, m);

        if (cfg.isAutoBan()) {
            if (isInsult(l)) {
                HolyWorldAutoReply.LOGGER.info("[HW] Insult -> banning {}", n);
                ban(n, "30d Insult");
                return;
            }
            String trimmed = l.trim();
            if (trimmed.equals("\u043e\u0442\u043a\u0430\u0437") || trimmed.equals("\u043e\u0442\u043a\u0430\u0437\u044b\u0432\u0430\u044e\u0441\u044c")) {
                HolyWorldAutoReply.LOGGER.info("[HW] Refusal -> banning {}", n);
                ban(n, "30d Refusal");
                return;
            }
        }

        if (cfg.isAutoReply()) {
            if (cfg.isAutoBan() && isRefuse(l) && !warned) {
                warned = true;
                chat("\u0415\u0441\u043b\u0438 \u0432\u044b \u043e\u0442\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442\u0435\u0441\u044c, \u043d\u0430\u043f\u0438\u0448\u0438\u0442\u0435 \"\u043e\u0442\u043a\u0430\u0437\".", 1000);
                return;
            }
            String r = engine.get(m);
            if (r != null) {
                HolyWorldAutoReply.LOGGER.info("[HW] Auto-reply: {}", r);
                chat(r, 1200);
            } else {
                HolyWorldAutoReply.LOGGER.info("[HW] No auto-reply for: {}", m);
            }
        }
    }

    private void chat(String m, int d) {
        ex.schedule(() -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.player != null && c.getNetworkHandler() != null) {
                c.execute(() -> {
                    c.getNetworkHandler().sendChatMessage(m);
                    HolyWorldAutoReply.LOGGER.info("[HW] Sent: {}", m);
                });
            }
        }, d, TimeUnit.MILLISECONDS);
    }

    private void ban(String n, String r) {
        ex.schedule(() -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.player != null && c.getNetworkHandler() != null) {
                c.execute(() -> {
                    c.getNetworkHandler().sendChatCommand("hm sban " + n + " " + r);
                    HolyWorldAutoReply.LOGGER.info("[HW] Executed ban: {} {}", n, r);
                    HolyWorldAutoReply.getConfig().endCheck();
                    resetState();
                });
            }
        }, 500, TimeUnit.MILLISECONDS);
    }

    private boolean isInsult(String s) {
        return s.contains("\u043d\u0430\u0445\u0443\u0439") || s.contains("\u043f\u0438\u0434\u043e\u0440") ||
               s.contains("\u0445\u0443\u0439") || s.contains("\u0435\u0431\u0430\u043b") ||
               s.contains("\u0441\u0443\u043a\u0430") || s.contains("\u0431\u043b\u044f");
    }

    private boolean isRefuse(String s) {
        return s.contains("\u043d\u0435 \u0431\u0443\u0434\u0443") || s.contains("\u043d\u0435 \u0445\u043e\u0447\u0443") || s.contains("\u043d\u0435 \u0441\u0442\u0430\u043d\u0443");
    }

    private String stripColors(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7' && i + 1 < s.length()) {
                i++;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
