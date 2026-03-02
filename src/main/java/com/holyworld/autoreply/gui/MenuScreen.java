package com.holyworld.autoreply.gui;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class MenuScreen extends Screen {

    public MenuScreen() {
        super(Text.literal("HW Menu"));
    }

    @Override
    protected void init() {
        super.init();
        ModConfig cfg = HolyWorldAutoReply.getConfig();
        int cx = width / 2;
        int sy = height / 2 - 110;
        int bw = 240;
        int bh = 20;
        int gap = 24;
        int row = 0;

        // === MAIN TOGGLE ===
        addDrawableChild(ButtonWidget.builder(
            Text.literal(toggleText("AutoReply (Main)", cfg.isEnabled())),
            b -> {
                cfg.toggleEnabled();
                if (!cfg.isEnabled() && HolyWorldAutoReply.getChatHandler() != null) {
                    HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();
                }
                b.setMessage(Text.literal(toggleText("AutoReply (Main)", cfg.isEnabled())));
            }
        ).dimensions(cx - bw / 2, sy + gap * row++, bw, bh).build());

        // === AUTO REPLY ===
        addDrawableChild(ButtonWidget.builder(
            Text.literal(toggleText("Auto Reply", cfg.isAutoReply())),
            b -> {
                cfg.toggleAutoReply();
                b.setMessage(Text.literal(toggleText("Auto Reply", cfg.isAutoReply())));
            }
        ).dimensions(cx - bw / 2, sy + gap * row++, bw, bh).build());

        // === BAN: INSULT ===
        addDrawableChild(ButtonWidget.builder(
            Text.literal(toggleText("Ban: Insult (30d)", cfg.isAutoBanInsult())),
            b -> {
                cfg.toggleAutoBanInsult();
                b.setMessage(Text.literal(toggleText("Ban: Insult (30d)", cfg.isAutoBanInsult())));
            }
        ).dimensions(cx - bw / 2, sy + gap * row++, bw, bh).build());

        // === BAN: REFUSAL ===
        addDrawableChild(ButtonWidget.builder(
            Text.literal(toggleText("Ban: Refusal (30d)", cfg.isAutoBanRefusal())),
            b -> {
                cfg.toggleAutoBanRefusal();
                b.setMessage(Text.literal(toggleText("Ban: Refusal (30d)", cfg.isAutoBanRefusal())));
            }
        ).dimensions(cx - bw / 2, sy + gap * row++, bw, bh).build());

        // === BAN: CONFESSION ===
        addDrawableChild(ButtonWidget.builder(
            Text.literal(toggleText("Ban: Confession (20d)", cfg.isAutoBanConfession())),
            b -> {
                cfg.toggleAutoBanConfession();
                b.setMessage(Text.literal(toggleText("Ban: Confession (20d)", cfg.isAutoBanConfession())));
            }
        ).dimensions(cx - bw / 2, sy + gap * row++, bw, bh).build());

        // === AUTO REPORTS ===
        addDrawableChild(ButtonWidget.builder(
            Text.literal(toggleText("Auto Reports", cfg.isAutoReports())),
            b -> {
                cfg.toggleAutoReports();
                b.setMessage(Text.literal(toggleText("Auto Reports", cfg.isAutoReports())));
            }
        ).dimensions(cx - bw / 2, sy + gap * row++, bw, bh).build());

        // === AUTO CHECKOUT ===
        addDrawableChild(ButtonWidget.builder(
            Text.literal(toggleText("Auto Checkout", cfg.isAutoCheckout())),
            b -> {
                cfg.toggleAutoCheckout();
                b.setMessage(Text.literal(toggleText("Auto Checkout", cfg.isAutoCheckout())));
            }
        ).dimensions(cx - bw / 2, sy + gap * row++, bw, bh).build());

        // === AUTO OUT ===
        addDrawableChild(ButtonWidget.builder(
            Text.literal(toggleText("Auto Out (endcheckout)", cfg.isAutoOut())),
            b -> {
                cfg.toggleAutoOut();
                b.setMessage(Text.literal(toggleText("Auto Out (endcheckout)", cfg.isAutoOut())));
            }
        ).dimensions(cx - bw / 2, sy + gap * row++, bw, bh).build());

        // === CLOSE ===
        row++;
        addDrawableChild(ButtonWidget.builder(
            Text.literal("\u00a7cClose"),
            b -> close()
        ).dimensions(cx - bw / 2, sy + gap * row, bw, bh).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        ModConfig cfg = HolyWorldAutoReply.getConfig();

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer,
            "\u00a7b\u00a7lHolyWorld AutoReply v2.0",
            width / 2, height / 2 - 130, -1);

        // Status line
        String status;
        if (!cfg.isEnabled()) {
            status = "\u00a7c\u25CB DISABLED";
        } else if (cfg.isIdle()) {
            status = "\u00a7e\u25CB IDLE (use /hm spyfrz)";
        } else if (cfg.isWaiting()) {
            status = "\u00a7e\u25CF WAITING for [CHECK]...";
        } else if (cfg.isCheckActive()) {
            status = "\u00a7a\u25CF ACTIVE: " + cfg.getCheckedPlayerName();
        } else {
            status = "\u00a77Unknown";
        }
        ctx.drawCenteredTextWithShadow(textRenderer, status, width / 2, height / 2 - 118, -1);

        // Spy nick
        String spy = cfg.getLastSpyNick();
        String spyText = "\u00a77Spy: " + (spy != null ? "\u00a7f" + spy : "\u00a78none");
        ctx.drawCenteredTextWithShadow(textRenderer, spyText, width / 2, height / 2 - 108, -1);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private String toggleText(String name, boolean value) {
        return (value ? "\u00a7aON" : "\u00a7cOFF") + " \u00a77| " + name;
    }
}
