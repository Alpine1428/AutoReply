package com.holyworld.autoreply.command;
import com.holyworld.autoreply.HolyWorldAutoReply;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.text.Text;

public class AICommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, r) -> {
            d.register(ClientCommandManager.literal("ai")
                .executes(c -> {
                    c.getSource().sendFeedback(Text.literal("\u00a7e[HW] Press RIGHT SHIFT to open menu."));
                    return 1;
                })
            );
        });
    }
}
