package dev.xyat.kinetictabs;

import dev.xyat.kinetictabs.tabs.TabModule;
import dev.xyat.kinetictabs.tabs.config.TabConfigGui;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@OnlyIn(Dist.CLIENT)
public final class KineticTabsClientBootstrap {
    private KineticTabsClientBootstrap() {}

    public static void init(FMLJavaModLoadingContext context) {
        TabModule.register(context.getModEventBus());
        TabConfigGui.load();
    }
}
