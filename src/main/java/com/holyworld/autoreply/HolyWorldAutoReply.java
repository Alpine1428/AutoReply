package com.holyworld.autoreply;

import com.holyworld.autoreply.config.ModConfig;
import com.holyworld.autoreply.handler.ChatHandler;
import com.holyworld.autoreply.handler.CommandInterceptor;
import com.holyworld.autoreply.gui.MenuScreen;
import com.holyworld.autoreply.command.AICommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HolyWorldAutoReply implements ClientModInitializer {
    public static final String MOD_ID = "holyworld-autoreply";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static ModConfig config;
    private static ChatHandler chatHandler;
    private static CommandInterceptor commandInterceptor;
    private static KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[HW] v7.0 (Stable + Menu)");
        config = new ModConfig();
        chatHandler = new ChatHandler();
        commandInterceptor = new CommandInterceptor();
        
        AICommand.register();
        
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "HolyWorld Menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "HW AutoReply"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new MenuScreen());
            }
        });
    }

    public static ModConfig getConfig() { return config; }
    public static ChatHandler getChatHandler() { return chatHandler; }
}
