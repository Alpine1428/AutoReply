package com.holyworld.autoreply.gui;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class MenuScreen extends Screen {

    public MenuScreen() { super(Text.literal("HW")); }

    @Override
    protected void init() {
        super.init();
        ModConfig cfg = HolyWorldAutoReply.getConfig();

        int cx = width / 2;
        int sy = height / 2 - 105;
        int w = 250;
        int h = 20;
        int g = 24;
        int row = 0;

        // Главный
        addBtn(cx, sy, g, row++, w, h, "Главный переключатель", cfg.isEnabled(), b -> {
            cfg.toggleEnabled();
            if (!cfg.isEnabled()) {
                cfg.endCheck();
                HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
            }
            refresh();
        });

        // Авто-ответы
        addBtn(cx, sy, g, row++, w, h, "Авто-ответы", cfg.isAutoReply(), b -> {
            cfg.toggleAutoReply();
            refresh();
        });

        // Бан: Неадекват
        addBtn(cx, sy, g, row++, w, h, "Бан: Неадекват (30 дней)", cfg.isAutoBanInsult(), b -> {
            cfg.toggleAutoBanInsult();
            refresh();
        });

        // Бан: Отказ
        addBtn(cx, sy, g, row++, w, h, "Бан: Отказ (30 дней)", cfg.isAutoBanRefusal(), b -> {
            cfg.toggleAutoBanRefusal();
            refresh();
        });

        // Бан: Признание
        addBtn(cx, sy, g, row++, w, h, "Бан: Признание (20 дней)", cfg.isAutoBanConfession(), b -> {
            cfg.toggleAutoBanConfession();
            refresh();
        });

        // Авто-внос: Репорты
        addBtn(cx, sy, g, row++, w, h, "Авто-внос: Репорты", cfg.isAutoReports(), b -> {
            cfg.toggleAutoReports();
            refresh();
        });

        // Авто-внос: Проверка
        addBtn(cx, sy, g, row++, w, h, "Авто-внос: Проверка", cfg.isAutoCheckout(), b -> {
            cfg.toggleAutoCheckout();
            refresh();
        });

        // Авто-выход
        addBtn(cx, sy, g, row++, w, h, "Авто-выход (endcheckout)", cfg.isAutoOut(), b -> {
            cfg.toggleAutoOut();
            refresh();
        });

        // Закрыть
        row++;
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7cЗакрыть"), b -> close())
            .dimensions(cx - w / 2, sy + g * row, w, h).build());
    }

    private void addBtn(int cx, int sy, int g, int row, int w, int h, String label, boolean val, ButtonWidget.PressAction act) {
        addDrawableChild(ButtonWidget.builder(togText(label, val), act)
            .dimensions(cx - w / 2, sy + g * row, w, h).build());
    }

    private Text togText(String label, boolean val) {
        return Text.literal((val ? "\u00a7aВКЛ" : "\u00a7cВЫКЛ") + " \u00a77| " + label);
    }

    private void refresh() {
        clearChildren();
        init();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ModConfig cfg = HolyWorldAutoReply.getConfig();

        ctx.drawCenteredTextWithShadow(textRenderer,
            "\u00a7b\u00a7lHolyWorld AutoReply v2.2", width / 2, height / 2 - 135, -1);

        // Статус
        String status;
        if (!cfg.isEnabled()) status = "\u00a7c\u2716 ОТКЛЮЧЕН";
        else if (cfg.isIdle()) status = "\u00a7e\u25CB Свободен";
        else if (cfg.isWaiting()) status = "\u00a76\u25CF Ожидание [CHECK]...";
        else status = "\u00a7a\u25CF Проверка: " + cfg.getCheckedPlayerName();

        ctx.drawCenteredTextWithShadow(textRenderer, status, width / 2, height / 2 - 122, -1);

        // Ник
        String spy = cfg.getLastSpyNick();
        ctx.drawCenteredTextWithShadow(textRenderer,
            "\u00a77Ник: " + (spy != null ? "\u00a7f" + spy : "\u00a78нет"),
            width / 2, height / 2 - 112, -1);

        super.render(ctx, mx, my, delta);
    }
}
