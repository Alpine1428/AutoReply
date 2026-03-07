package com.holyworld.autoreply.command;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.config.ModConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.text.Text;

public class AICommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, r) -> {
            d.register(ClientCommandManager.literal("ai")
                .executes(c -> {
                    c.getSource().sendFeedback(Text.literal("\u00a7e[HW] ПРАВЫЙ SHIFT = меню"));
                    return 1;
                })
                .then(ClientCommandManager.literal("start").executes(c -> {
                    HolyWorldAutoReply.getConfig().setEnabled(true);
                    c.getSource().sendFeedback(Text.literal("\u00a7a[HW] Включено!"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("stop").executes(c -> {
                    ModConfig cfg = HolyWorldAutoReply.getConfig();
                    cfg.setEnabled(false);
                    cfg.endCheck();
                    HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                    c.getSource().sendFeedback(Text.literal("\u00a7c[HW] Выключено!"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("status").executes(c -> {
                    ModConfig cfg = HolyWorldAutoReply.getConfig();
                    String state = cfg.isIdle() ? "Свободен" : cfg.isWaiting() ? "Ожидание" : "Проверка: " + cfg.getCheckedPlayerName();
                    c.getSource().sendFeedback(Text.literal("\u00a76=== HW v2.4 ===\n\u00a7eМод: " + (cfg.isEnabled() ? "\u00a7aВКЛ" : "\u00a7cВЫКЛ") + "\n\u00a7eСостояние: \u00a7f" + state));
                    return 1;
                }))
                .then(ClientCommandManager.literal("clear").executes(c -> {
                    ModConfig cfg = HolyWorldAutoReply.getConfig();
                    cfg.endCheck();
                    cfg.setLastSpyNick(null);
                    HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                    c.getSource().sendFeedback(Text.literal("\u00a7e[HW] Сброшено!"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("test").executes(c -> {
                    HolyWorldAutoReply.getChatHandler().processIncoming("[CHECK] TestPlayer -> привет");
                    c.getSource().sendFeedback(Text.literal("\u00a7e[HW] Тест!"));
                    return 1;
                }))
            );
        });
    }
}
