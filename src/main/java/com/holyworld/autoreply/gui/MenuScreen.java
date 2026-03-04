package com.holyworld.autoreply.gui;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class MenuScreen extends Screen {
    public MenuScreen() { super(Text.literal("HW")); }

    @Override protected void init() {
        super.init();
        ModConfig cfg = HolyWorldAutoReply.getConfig();
        int cx = width/2, sy = height/2-105, w = 250, h = 20, g = 24, row = 0;
        btn(cx,sy,g,row++,w,h,"Главный переключатель",cfg.isEnabled(),b->{cfg.toggleEnabled();if(!cfg.isEnabled()){cfg.endCheck();HolyWorldAutoReply.getChatHandler().getResponseEngine().clearAllStates();}refresh();});
        btn(cx,sy,g,row++,w,h,"Авто-ответы",cfg.isAutoReply(),b->{cfg.toggleAutoReply();refresh();});
        btn(cx,sy,g,row++,w,h,"Бан: Неадекват (30д)",cfg.isAutoBanInsult(),b->{cfg.toggleAutoBanInsult();refresh();});
        btn(cx,sy,g,row++,w,h,"Бан: Отказ (30д)",cfg.isAutoBanRefusal(),b->{cfg.toggleAutoBanRefusal();refresh();});
        btn(cx,sy,g,row++,w,h,"Бан: Признание (20д)",cfg.isAutoBanConfession(),b->{cfg.toggleAutoBanConfession();refresh();});
        btn(cx,sy,g,row++,w,h,"Авто-внос: Репорты",cfg.isAutoReports(),b->{cfg.toggleAutoReports();refresh();});
        btn(cx,sy,g,row++,w,h,"Авто-внос: Проверка",cfg.isAutoCheckout(),b->{cfg.toggleAutoCheckout();refresh();});
        btn(cx,sy,g,row++,w,h,"Авто-выход (endcheckout)",cfg.isAutoOut(),b->{cfg.toggleAutoOut();refresh();});
        row++;
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7cЗакрыть"),b->close()).dimensions(cx-w/2,sy+g*row,w,h).build());
    }

    private void btn(int cx,int sy,int g,int row,int w,int h,String l,boolean v,ButtonWidget.PressAction a){addDrawableChild(ButtonWidget.builder(tog(l,v),a).dimensions(cx-w/2,sy+g*row,w,h).build());}
    private Text tog(String l,boolean v){return Text.literal((v?"\u00a7aВКЛ":"\u00a7cВЫКЛ")+" \u00a77| "+l);}
    private void refresh(){clearChildren();init();}

    @Override public void render(DrawContext ctx,int mx,int my,float d){
        renderBackground(ctx);ModConfig cfg=HolyWorldAutoReply.getConfig();
        ctx.drawCenteredTextWithShadow(textRenderer,"\u00a7b\u00a7lHolyWorld AutoReply v2.4",width/2,height/2-135,-1);
        String st;if(!cfg.isEnabled())st="\u00a7c\u2716 ОТКЛЮЧЕН";else if(cfg.isIdle())st="\u00a7e\u25CB Свободен";else if(cfg.isWaiting())st="\u00a76\u25CF Ожидание [CHECK]...";else st="\u00a7a\u25CF Проверка: "+cfg.getCheckedPlayerName();
        ctx.drawCenteredTextWithShadow(textRenderer,st,width/2,height/2-122,-1);
        String spy=cfg.getLastSpyNick();ctx.drawCenteredTextWithShadow(textRenderer,"\u00a77Ник: "+(spy!=null?"\u00a7f"+spy:"\u00a78нет"),width/2,height/2-112,-1);
        super.render(ctx,mx,my,d);
    }
}
