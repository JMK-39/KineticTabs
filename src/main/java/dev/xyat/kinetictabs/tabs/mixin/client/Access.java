package dev.xyat.kinetictabs.tabs.mixin.client;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Collection;
import java.util.Set;

@Mixin(CreativeModeTab.class)
public interface Access {
    @Accessor("displayItems")
    void setDisplayItems(Collection<ItemStack> items);

    @Accessor("displayItemsSearchTab")
    void setDisplayItemsSearchTab(Set<ItemStack> items);
}