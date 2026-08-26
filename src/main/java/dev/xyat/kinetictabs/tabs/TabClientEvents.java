package dev.xyat.kinetictabs.tabs;

import dev.xyat.kinetictabs.KineticTabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = KineticTabs.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TabClientEvents {

    // ===== 通知系统逻辑 =====
    private static Component message = null;
    private static long expireTime = 0;

    public static void showNotification(String langKey) {
        message = Component.translatable(langKey);
        expireTime = System.currentTimeMillis() + 4000;
    }

    public static void clearNotification() {
        message = null;
        expireTime = 0;
    }

    public static void renderNotificationScaled(GuiGraphics g, int vWidth, Font font) {
        if (message != null && System.currentTimeMillis() < expireTime) {
            int textWidth = font.width(message);
            int boxWidth = textWidth + 30;
            int x = (vWidth - boxWidth) / 2;
            int y = 5;

            g.pose().pushPose();
            g.pose().translate(0, 0, 1000);
            g.fill(x, y, x + boxWidth, y + 20, 0xEE222222);
            g.renderOutline(x, y, boxWidth, 20, 0xFF00AAFF);
            g.drawCenteredString(font, message, vWidth / 2, y + 6, 0xFFFFFF);
            g.pose().popPose();
        }
    }

    // ===== 事件订阅逻辑 =====
    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay().id().getPath().equals("hotbar")) {
            Minecraft mc = Minecraft.getInstance();
            renderNotificationScaled(
                    event.getGuiGraphics(),
                    event.getWindow().getGuiScaledWidth(),
                    mc.font
            );
        }
    }
}