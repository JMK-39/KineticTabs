package dev.xyat.kinetictabs.tabs.command;

import dev.xyat.kineticcore.command.KTCommandApi;
import dev.xyat.kineticcore.command.KTCommandExtension;
import dev.xyat.kinetictabs.KineticTabs;
import dev.xyat.kinetictabs.tabs.TabConfig;
import net.minecraft.commands.CommandSourceStack;

public final class TabsCommandExtension implements KTCommandExtension {
    private TabsCommandExtension() {}

    public static void install() {
        KTCommandApi.register(KineticTabs.MODID, new TabsCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        TabConfig.load();
    }
}
