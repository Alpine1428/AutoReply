package com.holyworld.autoreply.ai;

import java.util.concurrent.ThreadLocalRandom;

public class ResponseEngine {
    private int cnt = 0;

    public void clear() { cnt = 0; }

    public String get(String msg) {
        cnt++;
        String l = msg.toLowerCase();

        if (has(l, "rustdesk", "\u0440\u0443\u0441\u0442", "\u0440\u0430\u0441\u0442", "anydesk"))
            return "\u0421\u043a\u0430\u0447\u0438\u0432\u0430\u0439 RustDesk \u0441 rustdesk com";

        if (has(l, "\u0433\u0434\u0435", "\u043a\u0430\u043a", "\u0441\u0441\u044b\u043b\u043a"))
            return "\u0412 \u0431\u0440\u0430\u0443\u0437\u0435\u0440\u0435: rustdesk com";

        if (has(l, "ds", "discord", "vk", "tg"))
            return "\u0422\u043e\u043b\u044c\u043a\u043e RustDesk";

        if (has(l, "\u0449\u0430", "\u0441\u0435\u043a", "\u0436\u0434\u0438"))
            return pick("\u0416\u0434\u0443", "\u0414\u0430\u0432\u0430\u0439");

        if (has(l, "\u0437\u0430 \u0447\u0442\u043e", "\u043f\u0440\u0438\u0447\u0438\u043d\u0430"))
            return "\u0420\u0435\u043f\u043e\u0440\u0442\u044b";

        if (has(l, "\u0447\u0438\u0441\u0442", "\u0431\u0435\u0437 \u0447\u0438\u0442"))
            return "\u041a\u0430\u0447\u0430\u0439 RustDesk, \u043f\u0440\u043e\u0432\u0435\u0440\u0438\u043c";

        if (has(l, "\u043d\u0435 \u043a\u0430\u0447\u0430\u0435\u0442", "\u043e\u0448\u0438\u0431\u043a\u0430"))
            return "\u041f\u0440\u043e\u0431\u0443\u0439 \u0441 rustdesk com";

        if (cnt <= 1 || has(l, "\u043f\u0440\u0438\u0432", "\u043a\u0443", "\u0437\u0434\u0440\u0430\u0432\u0441\u0442", "\u0445\u0430\u0439"))
            return "\u042d\u0442\u043e \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0430! \u0423 \u0432\u0430\u0441 7 \u043c\u0438\u043d. \u041a\u0430\u0447\u0430\u0439 RustDesk (rustdesk com)";

        if (msg.replaceAll("[^0-9]", "").length() > 6)
            return pick("\u041f\u0440\u0438\u043d\u0438\u043c\u0430\u0439", "\u0413\u0440\u0443\u0437\u0438\u0442");

        return null;
    }

    private boolean has(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private String pick(String... options) {
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }
}
