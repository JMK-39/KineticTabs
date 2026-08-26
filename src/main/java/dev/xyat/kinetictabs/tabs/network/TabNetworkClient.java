package dev.xyat.kinetictabs.tabs.network;

import dev.xyat.kinetictabs.tabs.TabConfig;
import dev.xyat.kinetictabs.tabs.TabModule;
import dev.xyat.kinetictabs.tabs.TabClientEvents;
import dev.xyat.kinetictabs.tabs.gui.TabUnifiedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TabNetworkClient {

    public static void handleSync() {
        TabModule.refreshTabs();
    }

    public static void handleNotify(String langKey) {
        TabClientEvents.showNotification(langKey);
    }

    public static void handleClearNotify() {
        TabClientEvents.clearNotification();
    }

    public static void handleOpenEditor(TabNetwork.OpenTabEditorPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;

        if (TabConfig.beginEdit(packet.json())) {
            minecraft.setScreen(new TabUnifiedScreen(parent));
        }
    }
}
