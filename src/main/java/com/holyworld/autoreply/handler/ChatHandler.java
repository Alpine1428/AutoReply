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
    
    // Robust pattern for [CHECK] Nick -> Msg
    private final Pattern P = Pattern.compile("\\[CHECK\\]\\s*([a-zA-Z0-9_]+)\\s*(?:->|:)\\s*(.+)");
    
    private final ConcurrentHashMap<String, Long> cd = new ConcurrentHashMap<>();
    private boolean warned = false;

    public ChatHandler() {
        // Listen to BOTH Game and Chat to catch server messages
        ClientReceiveMessageEvents.GAME.register((m, o) -> { if(!o) process(m.getString()); });
        ClientReceiveMessageEvents.CHAT.register((m, s, sender, p, t) -> process(m.getString()));
    }

    public void resetState() {
        warned = false;
        engine.clear();
    }

    private void process(String raw) {
        ModConfig cfg = HolyWorldAutoReply.getConfig();
        if (cfg.isIdle() || raw == null || !raw.contains("[CHECK]")) return;

        String clean = stripColors(raw);
        Matcher m = P.matcher(clean);
        if (!m.find()) return;
        
        String nick = m.group(1);
        String msg = m.group(2).trim();
        
        // Waiting -> Active transition
        if (cfg.isWaiting()) {
            cfg.activateCheck(nick);
            HolyWorldAutoReply.LOGGER.info("[HW] Active Check: " + nick);
        }
        
        // Process only if active and nick matches
        if (cfg.isCheckActive() && nick.equalsIgnoreCase(cfg.getCheckedPlayerName())) {
            handle(nick, msg);
        }
    }

    private void handle(String n, String m) {
        // Anti-spam
        long now = System.currentTimeMillis();
        if (cd.containsKey(n) && now - cd.get(n) < 2000) return;
        cd.put(n, now);

        String l = m.toLowerCase();
        ModConfig cfg = HolyWorldAutoReply.getConfig();

        // 1. Auto Ban
        if (cfg.isAutoBan()) {
            if (isInsult(l)) {
                ban(n, "30d \u041d\u0435\u0430\u0434\u0435\u043a\u0432\u0430\u0442");
                return;
            }
            if (l.equals("\u043e\u0442\u043a\u0430\u0437") || l.equals("\u043e\u0442\u043a\u0430\u0437\u044b\u0432\u0430\u044e\u0441\u044c")) {
                ban(n, "30d \u041e\u0442\u043a\u0430\u0437");
                return;
            }
        }

        // 2. Auto Reply
        if (cfg.isAutoReply()) {
            if (cfg.isAutoBan() && isRefuse(l) && !warned) {
                warned = true;
                chat("\u0415\u0441\u043b\u0438 \u0432\u044b \u043e\u0442\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442\u0435\u0441\u044c, \u043d\u0430\u043f\u0438\u0448\u0438\u0442\u0435 \"\u043e\u0442\u043a\u0430\u0437\".", 1000);
                return;
            }
            String r = engine.get(m);
            if (r != null) chat(r, 1200);
        }
    }

    private void chat(String m, int d) {
        ex.schedule(() -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.player != null) c.execute(() -> c.getNetworkHandler().sendChatMessage(m));
        }, d, TimeUnit.MILLISECONDS);
    }

    private void ban(String n, String r) {
        ex.schedule(() -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.player != null) {
                c.execute(() -> c.getNetworkHandler().sendChatCommand("hm sban " + n + " " + r));
                HolyWorldAutoReply.getConfig().endCheck();
            }
        }, 500, TimeUnit.MILLISECONDS);
    }

    private boolean isInsult(String s) {
        return s.contains("\u043d\u0430\u0445\u0443\u0439") || s.contains("\u043f\u0438\u0434\u043e\u0440") || 
               s.contains("\u0445\u0443\u0439") || s.contains("\u0435\u0431\u0430\u043b");
    }
    
    private boolean isRefuse(String s) {
        return s.contains("\u043d\u0435 \u0431\u0443\u0434\u0443") || s.contains("\u043d\u0435 \u0445\u043e\u0447\u0443");
    }

    private String stripColors(String s) {
        StringBuilder b = new StringBuilder();
        for(int i=0; i<s.length(); i++) {
            if(s.charAt(i) == '\u00A7' && i+1 < s.length()) i++;
            else b.append(s.charAt(i));
        }
        return b.toString();
    }
}
