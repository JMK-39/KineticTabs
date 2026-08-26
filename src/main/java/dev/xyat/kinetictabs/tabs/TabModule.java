package dev.xyat.kinetictabs.tabs;

import dev.xyat.kinetictabs.tabs.jei.TabJeiPlugin;
import dev.xyat.kinetictabs.tabs.mixin.client.Access;
import net.minecraft.client.Minecraft;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class TabModule {

    public static boolean bypassAllModifications = false;
    public static final Set<String> INJECTED_ITEMS = new HashSet<>();
    public static final Set<String> INJECTED_RULES = new HashSet<>();

    public static void load() { TabConfig.load(); }
    public static void register(IEventBus bus) { bus.addListener(TabModule::onBuildContents); }

    /**
     * 初次加载时的注入逻辑（保持原样，用于兼容模组初始化）
     */
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (TabConfig.data == null) return;
        String currentTab = event.getTabKey().location().toString();
        if (currentTab.equals("minecraft:search")) return;

        for (TabConfig.TabAddition add : TabConfig.data.additions) {
            if (!currentTab.equals(add.tabId)) continue;
            for (TabConfig.TabItem tabItem : add.items) {
                if (tabItem == null) continue;
                ItemStack stack = tabItem.getStack();
                if (stack.isEmpty()) continue;
                event.accept(stack);

                String rule = buildRule(stack);
                INJECTED_ITEMS.add(currentTab + "|" + rule);
                INJECTED_RULES.add(rule);
            }
        }
    }

    /**
     * [内存修改法] 强制刷新
     * 直接操作内存里的展示列表，不触发原版 buildContents，防止原版物品丢失
     */
    public static void refreshTabs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        INJECTED_ITEMS.clear();
        INJECTED_RULES.clear();

        mc.execute(() -> {
            List<ItemStack> allSearchableItems = new ArrayList<>();

            for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
                // 修改此处：使用独立的 Access 接口
                if (!(tab instanceof Access accessor)) continue;
                String tabId = Objects.requireNonNull(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab)).toString();

                // 1. 从内存中提取当前已有的物品列表 (如果是第一次打开，系统会自动触发一次 buildContents)
                Collection<ItemStack> currentItems = tab.getDisplayItems();

                // 2. 创建一个新的集合进行增删操作 (使用 ItemStackLinkedSet 保持 Minecraft 的去重逻辑)
                Collection<ItemStack> nextDisplayItems = ItemStackLinkedSet.createTypeAndTagSet();
                Set<ItemStack> nextSearchItems = ItemStackLinkedSet.createTypeAndTagSet();

                // 把内存中非移除名单的物品搬运到新列表
                for (ItemStack stack : currentItems) {
                    if (!isRemoved(stack)) {
                        nextDisplayItems.add(stack);
                        nextSearchItems.add(stack);
                    }
                }

                // 处理追加物品：直接注入到新列表
                for (TabConfig.TabAddition add : TabConfig.data.additions) {
                    if (add.tabId.equals(tabId)) {
                        for (TabConfig.TabItem item : add.items) {
                            ItemStack addStack = item.getStack();
                            if (!addStack.isEmpty()) {
                                nextDisplayItems.add(addStack);
                                nextSearchItems.add(addStack);
                                // 记录，以便 GUI 区分
                                String rule = buildRule(addStack);
                                INJECTED_ITEMS.add(tabId + "|" + rule);
                                INJECTED_RULES.add(rule);
                            }
                        }
                    }
                }

                // 3. [内存写回] 将修改后的结果强制塞回 Tab 对象的缓存字段
                accessor.setDisplayItems(nextDisplayItems);
                accessor.setDisplayItemsSearchTab(nextSearchItems instanceof Set ? nextSearchItems : new HashSet<>(nextSearchItems));

                // 收集可见标签页的物品用于刷新搜索索引
                if (tab.getType() != CreativeModeTab.Type.SEARCH && !TabConfig.data.hiddenTabs.contains(tabId)) {
                    allSearchableItems.addAll(nextSearchItems);
                }
            }

            // 4. 更新搜索索引树
            mc.populateSearchTree(SearchRegistry.CREATIVE_NAMES, allSearchableItems);
            mc.populateSearchTree(SearchRegistry.CREATIVE_TAGS, allSearchableItems);

            if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
                TabJeiPlugin.refreshJei();
            }
        });
    }

    public static boolean isRemoved(ItemStack stack) {
        if (TabConfig.data == null) return false;
        return matchesAnyRule(stack, TabConfig.data.removals);
    }

    public static boolean matchesAnyRule(ItemStack stack, List<String> rules) {
        if (rules == null || rules.isEmpty() || stack == null || stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        String idStr = id.toString();

        for (String rule : rules) {
            if (rule == null || rule.isEmpty()) continue;
            if (rule.startsWith("@")) {
                if (id.getNamespace().equals(rule.substring(1))) return true;
            } else if (rule.startsWith("#")) {
                String tag = rule.substring(1);
                if (stack.getTags().anyMatch(t -> t.location().toString().equals(tag))) return true;
            } else if (rule.contains("{")) {
                ItemStack ruleStack = parseItemStr(rule);
                if (!ruleStack.isEmpty() && ItemStack.isSameItemSameTags(stack, ruleStack)) return true;
            } else {
                if (idStr.equals(rule)) return true;
            }
        }
        return false;
    }

    public static ItemStack parseItemStr(String str) {
        if (str == null || str.isEmpty()) return ItemStack.EMPTY;
        try {
            int brace = str.indexOf('{');
            if (brace == -1) {
                net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(str));
                return item == null ? ItemStack.EMPTY : new ItemStack(item);
            }
            String idPart = str.substring(0, brace);
            net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(idPart));
            if (item == null) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(item);
            String nbt = str.substring(brace);
            if (!nbt.equals("{}")) stack.setTag(net.minecraft.nbt.TagParser.parseTag(nbt));
            return stack;
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    public static String buildRule(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return "";
        String nbt = (stack.hasTag() && stack.getTag() != null) ? stack.getTag().toString() : "{}";
        return nbt.equals("{}") ? id.toString() : id + nbt;
    }
}