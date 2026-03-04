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
                    c.getSource().sendFeedback(Text.literal(
                        "\u00a7b\u00a7l[HW] \u00a7fНажми \u00a7eПРАВЫЙ SHIFT \u00a7fдля открытия меню.\n" +
                        "\u00a77Команды: /ai start | /ai stop | /ai status | /ai clear"
                    ));
                    return 1;
                })
                .then(ClientCommandManager.literal("start").executes(c -> {
                    HolyWorldAutoReply.getConfig().setEnabled(true);
                    c.getSource().sendFeedback(Text.literal("\u00a7a[HW] Мод включён!"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("stop").executes(c -> {
                    ModConfig cfg = HolyWorldAutoReply.getConfig();
                    cfg.setEnabled(false);
                    cfg.endCheck();
                    HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                    c.getSource().sendFeedback(Text.literal("\u00a7c[HW] Мод выключен!"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("status").executes(c -> {
                    ModConfig cfg = HolyWorldAutoReply.getConfig();
                    String state = "Свободен";
                    if (cfg.isWaiting()) state = "Ожидание [CHECK]";
                    if (cfg.isCheckActive()) state = "Проверка: " + cfg.getCheckedPlayerName();
                    String spy = cfg.getLastSpyNick();

                    c.getSource().sendFeedback(Text.literal(
                        "\u00a76\u00a7l=== HW Статус ===\n" +
                        "\u00a7eМод: " + (cfg.isEnabled() ? "\u00a7aВКЛ" : "\u00a7cВЫКЛ") + "\n" +
                        "\u00a7eСостояние: \u00a7f" + state + "\n" +
                        "\u00a7eАвто-ответы: " + (cfg.isAutoReply() ? "\u00a7aВКЛ" : "\u00a7cВЫКЛ") + "\n" +
                        "\u00a7eБан неадекват: " + (cfg.isAutoBanInsult() ? "\u00a7aВКЛ" : "\u00a7cВЫКЛ") + "\n" +
                        "\u00a7eБан отказ: " + (cfg.isAutoBanRefusal() ? "\u00a7aВКЛ" : "\u00a7cВЫКЛ") + "\n" +
                        "\u00a7eБан признание: " + (cfg.isAutoBanConfession() ? "\u00a7aВКЛ" : "\u00a7cВЫКЛ") + "\n" +
                        "\u00a7eАвто-внос репорты: " + (cfg.isAutoReports() ? "\u00a7aВКЛ" : "\u00a7cВЫКЛ") + "\n" +
                        "\u00a7eАвто-внос проверка: " + (cfg.isAutoCheckout() ? "\u00a7aВКЛ" : "\u00a7cВЫКЛ") + "\n" +
                        "\u00a7eАвто-выход: " + (cfg.isAutoOut() ? "\u00a7aВКЛ" : "\u00a7cВЫКЛ") + "\n" +
                        "\u00a7eНик: " + (spy != null ? "\u00a7f" + spy : "\u00a78нет")
                    ));
                    return 1;
                }))
                .then(ClientCommandManager.literal("clear").executes(c -> {
                    ModConfig cfg = HolyWorldAutoReply.getConfig();
                    cfg.endCheck();
                    cfg.setLastSpyNick(null);
                    HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                    c.getSource().sendFeedback(Text.literal("\u00a7e[HW] Все состояния сброшены!"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("test").executes(c -> {
                    HolyWorldAutoReply.getChatHandler().processIncoming("[CHECK] TestPlayer -> привет");
                    c.getSource().sendFeedback(Text.literal("\u00a7e[HW] Тест отправлен!"));
                    return 1;
                }))
            );
        });
    }
}
