package com.holyworld.autoreply.gui;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class MenuScreen extends Screen {
    public MenuScreen() { super(Text.literal("HW Menu")); }

    @Override
    protected void init() {
        super.init();
        ModConfig cfg = HolyWorldAutoReply.getConfig();
        int cx = width/2;
        int sy = height/2 - 70;
        int w = 220;
        int h = 20;
        int g = 24;

        addDrawableChild(ButtonWidget.builder(Text.literal(getTxt("Auto Reply", cfg.isAutoReply())), b -> {
            cfg.toggleAutoReply(); b.setMessage(Text.literal(getTxt("Auto Reply", cfg.isAutoReply())));
        }).dimensions(cx-w/2, sy, w, h).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(getTxt("Auto Ban", cfg.isAutoBan())), b -> {
            cfg.toggleAutoBan(); b.setMessage(Text.literal(getTxt("Auto Ban", cfg.isAutoBan())));
        }).dimensions(cx-w/2, sy+g, w, h).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(getTxt("Auto Reports", cfg.isAutoReports())), b -> {
            cfg.toggleAutoReports(); b.setMessage(Text.literal(getTxt("Auto Reports", cfg.isAutoReports())));
        }).dimensions(cx-w/2, sy+g*2, w, h).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(getTxt("Auto Out", cfg.isAutoOut())), b -> {
            cfg.toggleAutoOut(); b.setMessage(Text.literal(getTxt("Auto Out", cfg.isAutoOut())));
        }).dimensions(cx-w/2, sy+g*3, w, h).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7cClose"), b -> close())
            .dimensions(cx-w/2, sy+g*5, w, h).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float d) {
        renderBackground(ctx);
        ModConfig cfg = HolyWorldAutoReply.getConfig();
        
        ctx.drawCenteredTextWithShadow(textRenderer, "\u00a7b\u00a7lHolyWorld v7.0", width/2, height/2-100, -1);
        
        String st = "\u00a7c\u25CB IDLE";
        if(cfg.isWaiting()) st = "\u00a7e\u25CF WAITING for [CHECK]";
        if(cfg.isCheckActive()) st = "\u00a7a\u25CF ACTIVE: " + cfg.getCheckedPlayerName();
        
        ctx.drawCenteredTextWithShadow(textRenderer, st, width/2, height/2-85, -1);
        super.render(ctx, mx, my, d);
    }

    private String getTxt(String n, boolean v) {
        return (v ? "\u00a7aON" : "\u00a7cOFF") + " \u00a77| " + n;
    }
}
