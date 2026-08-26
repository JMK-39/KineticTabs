package dev.xyat.kinetictabs.tabs.mixin.client;

import dev.xyat.kinetictabs.tabs.TabConfig;
import dev.xyat.kinetictabs.tabs.TabModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixins {

    @Inject(method = "shouldDisplay", at = @At("HEAD"), cancellable = true)
    private void kinetictabs$shouldHideTab(CallbackInfoReturnable<Boolean> cir) {
        if (TabModule.bypassAllModifications) return;
        ResourceLocation tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey((CreativeModeTab) (Object) this);
        if (tabId != null && TabConfig.data != null && TabConfig.data.hiddenTabs.contains(tabId.toString())) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 持久化过滤器：
     * 就算我们通过内存改好了列表，但如果玩家切换语言或触发了原版的 reload，
     * 原版逻辑会再次生成列表。这里作为最后的保险拦截。
     */
    @Inject(method = { "getDisplayItems", "getSearchTabDisplayItems" }, at = @At("RETURN"), cancellable = true)
    private void kinetictabs$filterItems(CallbackInfoReturnable<Collection<ItemStack>> cir) {
        if (TabConfig.data == null || TabModule.bypassAllModifications) return;

        ResourceLocation tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey((CreativeModeTab) (Object) this);
        if (tabId == null) return;
        String currentTabStr = tabId.toString();

        if (currentTabStr.equals("minecraft:search")) return;

        Collection<ItemStack> original = cir.getReturnValue();
        if (original == null) return;

        boolean needFiltering = original.stream().anyMatch(TabModule::isRemoved);

        // 查找是否需要追加物品
        boolean hasAddition = false;
        for (TabConfig.TabAddition add : TabConfig.data.additions) {
            if (add.tabId.equals(currentTabStr)) {
                hasAddition = true; break;
            }
        }

        if (!needFiltering && !hasAddition) return;

        List<ItemStack> result = new ArrayList<>();
        // 1. 过滤
        for (ItemStack stack : original) {
            if (!TabModule.isRemoved(stack)) {
                result.add(stack);
            }
        }
        // 2. 追加
        for (TabConfig.TabAddition add : TabConfig.data.additions) {
            if (add.tabId.equals(currentTabStr)) {
                for (TabConfig.TabItem item : add.items) {
                    ItemStack addStack = item.getStack();
                    if (!addStack.isEmpty() && result.stream().noneMatch(s -> ItemStack.isSameItemSameTags(s, addStack))) {
                        result.add(addStack);
                    }
                }
            }
        }
        cir.setReturnValue(result);
    }
}