package dev.xyat.kinetictabs;

import com.mojang.logging.LogUtils;
import dev.xyat.kinetictabs.tabs.TabConfig;
import dev.xyat.kinetictabs.tabs.command.TabsCommandExtension;
import dev.xyat.kinetictabs.tabs.network.TabNetwork;
import dev.xyat.kineticcore.config.server.KTServerConfigApi;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(KineticTabs.MODID)
public final class KineticTabs {
    public static final String MODID = "kinetictabs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KineticTabs(FMLJavaModLoadingContext context) {
        TabConfig.load();
        KTServerConfigApi.registerActionPage("kinetictabs:tabs");
        TabNetwork.register();
        TabsCommandExtension.install();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KineticTabsClientBootstrap.init(context));
    }
}
