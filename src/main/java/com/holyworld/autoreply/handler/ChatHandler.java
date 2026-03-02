package com.holyworld.autoreply.handler;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.ai.ResponseEngine;
import com.holyworld.autoreply.config.ModConfig;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.*;

public class ChatHandler {

    private final ResponseEngine responseEngine;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2500;

    public ChatHandler() {
        this.responseEngine = new ResponseEngine();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HW-AutoReply");
            t.setDaemon(true);
            return t;
        });
        HolyWorldAutoReply.LOGGER.info("[ChatHandler] Ready");
    }

    public ResponseEngine getResponseEngine() {
        return responseEngine;
    }

    /**
     * Called from ChatHudMixin for every chat message
     */
    public void processIncoming(String raw) {
        if (raw == null || raw.isEmpty()) return;

        ModConfig cfg = HolyWorldAutoReply.getConfig();

        // Must be enabled
        if (!cfg.isEnabled()) return;

        String clean = stripColors(raw);
        if (!clean.contains("[CHECK]")) return;

        HolyWorldAutoReply.LOGGER.info("[Handler] Processing: '{}'", clean);

        // Parse [CHECK] Nick -> Message
        int checkIdx = clean.indexOf("[CHECK]");
        String afterCheck = clean.substring(checkIdx + 7).trim();

        int arrowIdx = afterCheck.indexOf("->");
        if (arrowIdx <= 0) {
            HolyWorldAutoReply.LOGGER.warn("[Handler] No '->' found in: '{}'", afterCheck);
            return;
        }

        String playerName = afterCheck.substring(0, arrowIdx).trim();
        String playerMessage = afterCheck.substring(arrowIdx + 2).trim();

        playerName = playerName.replaceAll("[^a-zA-Z0-9_]", "");

        if (playerName.isEmpty() || playerMessage.isEmpty()) {
            HolyWorldAutoReply.LOGGER.warn("[Handler] Empty: name='{}' msg='{}'", playerName, playerMessage);
            return;
        }

        // Don't reply to self
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            String myName = client.player.getName().getString();
            if (playerName.equalsIgnoreCase(myName)) return;
        }

        HolyWorldAutoReply.LOGGER.info("[Handler] Player='{}' Msg='{}'", playerName, playerMessage);

        // === State management ===
        // If IDLE, ignore
        if (cfg.isIdle()) {
            HolyWorldAutoReply.LOGGER.info("[Handler] State is IDLE, ignoring");
            return;
        }

        // If WAITING, activate check with this player
        if (cfg.isWaiting()) {
            cfg.activateCheck(playerName);
            HolyWorldAutoReply.LOGGER.info("[Handler] State -> CHECK_ACTIVE for '{}'", playerName);
        }

        // If CHECK_ACTIVE, only respond to the checked player
        if (cfg.isCheckActive() && !playerName.equalsIgnoreCase(cfg.getCheckedPlayerName())) {
            HolyWorldAutoReply.LOGGER.debug("[Handler] Ignoring '{}', checking '{}'", playerName, cfg.getCheckedPlayerName());
            return;
        }

        // Cooldown
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(playerName);
        if (last != null && (now - last) < COOLDOWN_MS) return;
        cooldowns.put(playerName, now);

        // === Get engine result ===
        ResponseEngine.EngineResult result = responseEngine.getResult(playerMessage, playerName);
        if (result == null) return;

        final String checkedNick = playerName;

        switch (result.action) {
            case REPLY:
                if (cfg.isAutoReply() && result.message != null && !result.message.isEmpty()) {
                    HolyWorldAutoReply.LOGGER.info("[Handler] Reply: '{}'", result.message);
                    long delay = 800 + (long)(Math.random() * 1200);
                    scheduler.schedule(() -> sendChat(result.message), delay, TimeUnit.MILLISECONDS);
                }
                break;

            case BAN_INSULT:
                if (cfg.isAutoBanInsult()) {
                    HolyWorldAutoReply.LOGGER.info("[Handler] BAN INSULT: {}", checkedNick);
                    scheduler.schedule(() -> {
                        sendCommand("hm sban " + checkedNick + " 30d Insult");
                        HolyWorldAutoReply.getConfig().endCheck();
                        responseEngine.clearPlayerState(checkedNick);
                        autoEndCheckout(checkedNick);
                    }, 500, TimeUnit.MILLISECONDS);
                } else {
                    // If ban disabled, just reply
                    String msg = "Оскорбления не помогут. Скачивай AnyDesk";
                    long delay = 800 + (long)(Math.random() * 1200);
                    scheduler.schedule(() -> sendChat(msg), delay, TimeUnit.MILLISECONDS);
                }
                break;

            case BAN_REFUSAL:
                if (cfg.isAutoBanRefusal()) {
                    HolyWorldAutoReply.LOGGER.info("[Handler] BAN REFUSAL: {}", checkedNick);
                    scheduler.schedule(() -> {
                        sendCommand("hm sban " + checkedNick + " 30d Refusal");
                        HolyWorldAutoReply.getConfig().endCheck();
                        responseEngine.clearPlayerState(checkedNick);
                        autoEndCheckout(checkedNick);
                    }, 500, TimeUnit.MILLISECONDS);
                } else {
                    String msg = "Отказ от проверки - 30 дней бана. Уверен?";
                    long delay = 800 + (long)(Math.random() * 1200);
                    scheduler.schedule(() -> sendChat(msg), delay, TimeUnit.MILLISECONDS);
                }
                break;

            case BAN_CONFESSION:
                if (cfg.isAutoBanConfession()) {
                    HolyWorldAutoReply.LOGGER.info("[Handler] BAN CONFESSION: {}", checkedNick);
                    scheduler.schedule(() -> {
                        sendCommand("hm sban " + checkedNick + " 20d Confession");
                        HolyWorldAutoReply.getConfig().endCheck();
                        responseEngine.clearPlayerState(checkedNick);
                        autoEndCheckout(checkedNick);
                    }, 500, TimeUnit.MILLISECONDS);
                } else {
                    String msg = "Признание принято. Спасибо за честность";
                    long delay = 800 + (long)(Math.random() * 1200);
                    scheduler.schedule(() -> sendChat(msg), delay, TimeUnit.MILLISECONDS);
                }
                break;
        }
    }

    private void autoEndCheckout(String nick) {
        ModConfig cfg = HolyWorldAutoReply.getConfig();
        if (cfg.isAutoOut()) {
            scheduler.schedule(() -> {
                sendCommand("hm endcheckout ban " + nick + " false");
                HolyWorldAutoReply.LOGGER.info("[Handler] AutoOut: endcheckout for {}", nick);
            }, 1500, TimeUnit.MILLISECONDS);
        }
    }

    private void sendChat(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            try {
                if (client.player == null || client.getNetworkHandler() == null) return;
                client.getNetworkHandler().sendChatMessage(message);
                HolyWorldAutoReply.LOGGER.info("[Handler] SENT: '{}'", message);
            } catch (Exception e) {
                HolyWorldAutoReply.LOGGER.error("[Handler] Send failed: {}", e.getMessage());
            }
        });
    }

    private void sendCommand(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            try {
                if (client.player == null || client.getNetworkHandler() == null) return;
                String cmd = command.startsWith("/") ? command.substring(1) : command;
                client.getNetworkHandler().sendChatCommand(cmd);
                HolyWorldAutoReply.LOGGER.info("[Handler] CMD: /{}", cmd);
            } catch (Exception e) {
                HolyWorldAutoReply.LOGGER.error("[Handler] Command failed: {}", e.getMessage());
            }
        });
    }

    private static String stripColors(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\u00A7' && i + 1 < input.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
