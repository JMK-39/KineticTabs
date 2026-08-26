package dev.xyat.kinetictabs.tabs.jei;

import dev.xyat.kinetictabs.tabs.TabConfig;
import dev.xyat.kinetictabs.tabs.TabModule;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@JeiPlugin
public class TabJeiPlugin implements IModPlugin {

    private static IJeiRuntime jeiRuntime;

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return new ResourceLocation("kinetictabs", "tabs_jei");
    }

    @Override
    public void registerItemSubtypes(@NotNull ISubtypeRegistration registration) {
        TabConfig.load();
        Set<Item> itemsWithCustomNBT = new HashSet<>();

        for (TabConfig.TabAddition add : TabConfig.data.additions) {
            for (TabConfig.TabItem item : add.items) {
                if (item.matchNbt && !item.nbt.equals("{}")) {
                    Item mcItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(item.id));
                    if (mcItem != null) itemsWithCustomNBT.add(mcItem);
                }
            }
        }

        for (Item item : itemsWithCustomNBT) {
            registration.useNbtForSubtypes(item);
        }
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime runtime) {
        jeiRuntime = runtime;
        applyConfig(runtime);
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = null;
    }

    public static void applyConfig(IJeiRuntime runtime) {
        if (runtime == null) return;
        IIngredientManager manager = runtime.getIngredientManager();
        List<ItemStack> toHide = new ArrayList<>();

        Collection<ItemStack> allIngredients = manager.getAllIngredients(VanillaTypes.ITEM_STACK);
        for (ItemStack stack : allIngredients) {
            if (TabModule.isRemoved(stack)) toHide.add(stack);
        }

        TabModule.bypassAllModifications = true;
        Set<String> visibleItemRules = new HashSet<>();
        Set<ItemStack> itemsFromBannedTabs = new HashSet<>();

        for (Map.Entry<ResourceKey<CreativeModeTab>, CreativeModeTab> entry : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            String idStr = id.toString();
            if (idStr.equals("minecraft:search") || idStr.equals("minecraft:inventory") || idStr.equals("minecraft:hotbar") || idStr.equals("minecraft:op_blocks")) continue;

            boolean isTabBanned = TabConfig.data.hiddenTabs.contains(idStr);
            for (ItemStack stack : entry.getValue().getDisplayItems()) {
                if (stack == null || stack.isEmpty()) continue;
                String rule = TabModule.buildRule(stack);
                if (isTabBanned) itemsFromBannedTabs.add(stack);
                else visibleItemRules.add(rule);
            }
        }
        TabModule.bypassAllModifications = false;

        for (ItemStack bannedStack : itemsFromBannedTabs) {
            if (!visibleItemRules.contains(TabModule.buildRule(bannedStack))) {
                toHide.add(bannedStack);
            }
        }

        for (String rule : TabModule.INJECTED_RULES) {
            boolean currentlyAdded = false;
            for (TabConfig.TabAddition add : TabConfig.data.additions) {
                for (TabConfig.TabItem item : add.items) {
                    if (rule.equals(TabModule.buildRule(item.getStack()))) {
                        currentlyAdded = true; break;
                    }
                }
                if (currentlyAdded) break;
            }
            if (!currentlyAdded) {
                toHide.add(TabModule.parseItemStr(rule));
            }
        }

        if (!toHide.isEmpty()) {
            try { manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toHide); } catch (Exception ignored) {}
        }

        List<ItemStack> toAdd = new ArrayList<>();
        for (TabConfig.TabAddition add : TabConfig.data.additions) {
            for (TabConfig.TabItem tabItem : add.items) {
                if (tabItem != null && !tabItem.getStack().isEmpty()) {
                    toAdd.add(tabItem.getStack());
                }
            }
        }

        if (!toAdd.isEmpty()) {
            try { manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toAdd); } catch (Exception ignored) {}
        }
    }

    public static void refreshJei() {
        applyConfig(jeiRuntime);
    }
}