package com.holyworld.autoreply.command;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.config.ModConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.text.Text;

public class AICommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("ai")

                    // /ai start
                    .then(ClientCommandManager.literal("start")
                        .executes(ctx -> {
                            HolyWorldAutoReply.getConfig().setEnabled(true);
                            ctx.getSource().sendFeedback(Text.literal("\u00a7a\u00a7l[HW] \u00a7eAutoReply ON!"));
                            HolyWorldAutoReply.LOGGER.info("[AI] ENABLED");
                            return 1;
                        })
                    )

                    // /ai stop
                    .then(ClientCommandManager.literal("stop")
                        .executes(ctx -> {
                            ModConfig cfg = HolyWorldAutoReply.getConfig();
                            cfg.setEnabled(false);
                            cfg.endCheck();
                            if (HolyWorldAutoReply.getChatHandler() != null)
                                HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                            ctx.getSource().sendFeedback(Text.literal("\u00a7c\u00a7l[HW] \u00a7eAutoReply OFF!"));
                            HolyWorldAutoReply.LOGGER.info("[AI] DISABLED");
                            return 1;
                        })
                    )

                    // /ai status
                    .then(ClientCommandManager.literal("status")
                        .executes(ctx -> {
                            ModConfig cfg = HolyWorldAutoReply.getConfig();
                            String spy = cfg.getLastSpyNick();
                            String state = "IDLE";
                            if (cfg.isWaiting()) state = "WAITING";
                            if (cfg.isCheckActive()) state = "ACTIVE: " + cfg.getCheckedPlayerName();

                            ctx.getSource().sendFeedback(Text.literal(
                                "\u00a76\u00a7l=== HW Status ===\n" +
                                "\u00a7eEnabled: " + (cfg.isEnabled() ? "\u00a7aON" : "\u00a7cOFF") + "\n" +
                                "\u00a7eState: \u00a7f" + state + "\n" +
                                "\u00a7eAutoReply: " + (cfg.isAutoReply() ? "\u00a7aON" : "\u00a7cOFF") + "\n" +
                                "\u00a7eBan Insult: " + (cfg.isAutoBanInsult() ? "\u00a7aON" : "\u00a7cOFF") + "\n" +
                                "\u00a7eBan Refusal: " + (cfg.isAutoBanRefusal() ? "\u00a7aON" : "\u00a7cOFF") + "\n" +
                                "\u00a7eBan Confession: " + (cfg.isAutoBanConfession() ? "\u00a7aON" : "\u00a7cOFF") + "\n" +
                                "\u00a7eAuto Reports: " + (cfg.isAutoReports() ? "\u00a7aON" : "\u00a7cOFF") + "\n" +
                                "\u00a7eAuto Checkout: " + (cfg.isAutoCheckout() ? "\u00a7aON" : "\u00a7cOFF") + "\n" +
                                "\u00a7eAuto Out: " + (cfg.isAutoOut() ? "\u00a7aON" : "\u00a7cOFF") + "\n" +
                                "\u00a7eSpy: " + (spy != null ? "\u00a7f" + spy : "\u00a77none")
                            ));
                            return 1;
                        })
                    )

                    // /ai clear
                    .then(ClientCommandManager.literal("clear")
                        .executes(ctx -> {
                            ModConfig cfg = HolyWorldAutoReply.getConfig();
                            cfg.endCheck();
                            cfg.setLastSpyNick(null);
                            if (HolyWorldAutoReply.getChatHandler() != null)
                                HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                            ctx.getSource().sendFeedback(Text.literal("\u00a7e[HW] Cleared all states!"));
                            return 1;
                        })
                    )

                    // /ai test
                    .then(ClientCommandManager.literal("test")
                        .executes(ctx -> {
                            if (HolyWorldAutoReply.getChatHandler() != null) {
                                HolyWorldAutoReply.getChatHandler().processIncoming(
                                    "[CHECK] TestPlayer -> привет"
                                );
                                ctx.getSource().sendFeedback(Text.literal("\u00a7e[HW] Test sent!"));
                            }
                            return 1;
                        })
                    )

                    // /ai (no args)
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(Text.literal(
                            "\u00a7e[HW] Press RIGHT SHIFT for menu\n" +
                            "\u00a77/ai start | stop | status | clear | test"
                        ));
                        return 1;
                    })
            );
        });
    }
}
