package dev.xyat.kinetictabs.tabs.gui;

import dev.xyat.kineticcore.api.client.AdaptiveItemGridRenderer;
import dev.xyat.kineticcore.api.client.ItemSelectorScreen;
import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import dev.xyat.kineticcore.api.client.gui.DragStateController;
import dev.xyat.kineticcore.api.client.gui.GridScrollController;
import dev.xyat.kinetictabs.tabs.network.TabNetwork;
import dev.xyat.kinetictabs.tabs.TabClientEvents;
import dev.xyat.kinetictabs.tabs.TabConfig;
import dev.xyat.kinetictabs.tabs.TabModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TabUnifiedScreen extends ScaledScreen {
    private enum ItemState {
        NORMAL,
        ADDED,
        BANNED
    }

    private enum DragType {
        MAIN_TAB,
        MAIN_ITEM,
        RIGHT_ITEM
    }

    private record DisplayItem(
            ItemStack stack,
            ItemState state,
            TabConfig.TabItem ref,
            String ruleStr
    ) {
    }

    private static class TabInfo {
        Component name;
        ItemStack icon;
        ResourceLocation id;
    }

    private static final int MAIN_X = 10;
    private static final int MAIN_W = 360;
    private static final int RIGHT_X = 380;
    private static final int RIGHT_W = 90;

    private static final int ARROW_W = 15;
    private static final int TAB_SIZE = 22;
    private static final int TAB_INNER_PADDING = 0;
    private static final int TAB_VIEW_W = MAIN_W - ARROW_W * 2 - TAB_INNER_PADDING * 2;
    private static final int MAIN_TAB_COLS = TAB_VIEW_W / TAB_SIZE;
    private static final int TAB_CONTENT_W = MAIN_TAB_COLS * TAB_SIZE;
    private static final int TAB_CONTENT_X = MAIN_X + ARROW_W + TAB_INNER_PADDING + (TAB_VIEW_W - TAB_CONTENT_W) / 2;

    private static final int SLOT_SIZE = 18;
    private static final int MAIN_ITEM_COLS = 20;
    private static final int RIGHT_ITEM_COLS = 5;

    private static final int TAB_Y = 22;
    private static final int TAB_SCROLL_Y = 46;
    private static final int ITEM_Y = 58;
    private static final int ITEM_H = 180;
    private static final int ITEM_VISIBLE_ROWS = 10;

    private final List<TabInfo> allGameTabs =
            new ArrayList<>();

    private final List<TabInfo> mainTabs =
            new ArrayList<>();

    private final List<DisplayItem> mainItems =
            new ArrayList<>();

    private final List<DisplayItem> rightHiddenItems =
            new ArrayList<>();

    private final GridScrollController mainTabScroll =
            new GridScrollController();

    private final GridScrollController mainItemScroll =
            new GridScrollController();

    private final GridScrollController rightItemScroll =
            new GridScrollController();

    private final DragStateController<DragType> contentDrag =
            new DragStateController<>();

    private int mainSelectedTabIdx;

    private final Screen parent;
    private TabInfo hoveredTabTooltip;
    private boolean hoveredTabBanned;
    private DisplayItem hoveredItemTooltip;
    private boolean hoveredItemHiddenPane;

    public TabUnifiedScreen(Screen parent) {
        super(Component.translatable(
                "gui.kinetictabs.tabs.unified.title"
        ));

        this.parent = parent;

        configureResponsiveCanvas(
                480f,
                270f,
                6
        );
        minScale = 0f;
        renderRenderablesOnly = true;

        TabClientEvents.clearNotification();

        for (Map.Entry<ResourceKey<CreativeModeTab>, CreativeModeTab> entry
                : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            ResourceLocation id =
                    entry.getKey().location();

            String idText =
                    id.toString();

            if ("minecraft:inventory".equals(idText)
                    || "minecraft:search".equals(idText)
                    || "minecraft:hotbar".equals(idText)) {
                continue;
            }

            CreativeModeTab tab =
                    entry.getValue();

            TabInfo info =
                    new TabInfo();

            info.name =
                    tab.getDisplayName();

            info.icon =
                    tab.getIconItem();

            info.id =
                    id;

            allGameTabs.add(info);
        }

        allGameTabs.sort(
                Comparator.comparing(
                        info -> info.id.toString()
                )
        );

        refreshData();
    }

    private void refreshData() {
        mainTabs.clear();
        mainItems.clear();
        rightHiddenItems.clear();

        List<String> removalRules =
                TabConfig.currentEditing.removals;

        mainTabs.addAll(allGameTabs);

        if (mainSelectedTabIdx >= mainTabs.size()) {
            mainSelectedTabIdx =
                    Math.max(
                            0,
                            mainTabs.size() - 1
                    );
        }

        if (!mainTabs.isEmpty()) {
            buildMainItems(removalRules);
        }

        for (String rule : removalRules) {
            ItemStack stack =
                    TabModule.parseItemStr(rule);

            if (!stack.isEmpty()) {
                rightHiddenItems.add(
                        new DisplayItem(
                                stack,
                                ItemState.BANNED,
                                null,
                                rule
                        )
                );
            }
        }

        refreshScrollRanges();
    }

    private void buildMainItems(
            List<String> removalRules
    ) {
        TabInfo currentTab =
                mainTabs.get(mainSelectedTabIdx);

        Collection<ItemStack> rawItems;

        TabModule.bypassAllModifications = true;

        try {
            CreativeModeTab minecraftTab =
                    BuiltInRegistries.CREATIVE_MODE_TAB
                            .get(currentTab.id);

            rawItems =
                    minecraftTab == null
                            ? List.of()
                            : minecraftTab.getDisplayItems();
        } finally {
            TabModule.bypassAllModifications = false;
        }

        List<ItemStack> nativeItems =
                new ArrayList<>();

        for (ItemStack stack : rawItems) {
            if (stack.isEmpty()) {
                continue;
            }

            String rule =
                    TabModule.buildRule(stack);

            if (!TabModule.INJECTED_ITEMS.contains(
                    currentTab.id + "|" + rule
            )) {
                nativeItems.add(stack);
            }
        }

        List<DisplayItem> customItems =
                new ArrayList<>();

        for (TabConfig.TabAddition addition
                : TabConfig.currentEditing.additions) {
            if (!addition.tabId.equals(
                    currentTab.id.toString()
            )) {
                continue;
            }

            for (TabConfig.TabItem item : addition.items) {
                if (item == null) {
                    continue;
                }

                ItemStack stack =
                        item.getStack();

                if (!stack.isEmpty()) {
                    customItems.add(
                            new DisplayItem(
                                    stack,
                                    ItemState.ADDED,
                                    item,
                                    TabModule.buildRule(stack)
                            )
                    );
                }
            }
        }

        for (ItemStack stack : nativeItems) {
            boolean custom =
                    customItems.stream()
                            .anyMatch(item ->
                                    ItemStack.isSameItemSameTags(
                                            item.stack,
                                            stack
                                    )
                            );

            if (custom) {
                continue;
            }

            String rule =
                    TabModule.buildRule(stack);

            ItemState state =
                    removalRules.contains(rule)
                            ? ItemState.BANNED
                            : ItemState.NORMAL;

            mainItems.add(
                    new DisplayItem(
                            stack,
                            state,
                            null,
                            rule
                    )
            );
        }

        mainItems.addAll(customItems);
    }

    private void refreshScrollRanges() {
        mainTabScroll.update(
                mainTabs.size(),
                MAIN_TAB_COLS
        );

        mainItemScroll.update(
                rowCount(
                        mainItems.size(),
                        MAIN_ITEM_COLS
                ),
                ITEM_VISIBLE_ROWS
        );

        rightItemScroll.update(
                rowCount(
                        rightHiddenItems.size(),
                        RIGHT_ITEM_COLS
                ),
                ITEM_VISIBLE_ROWS
        );
    }

    private static int rowCount(
            int size,
            int columns
    ) {
        return (size + columns - 1) / columns;
    }

    @Override
    protected void initScaled() {
        int bottomY =
                vHeight - 22;

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.kinetictabs.tabs.unified.btn_add_item"
                                ),
                                button -> openItemSelector()
                        )
                        .bounds(
                                MAIN_X,
                                bottomY,
                                120,
                                18
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.kinetictabs.tabs.btn.back"
                                ),
                                button -> returnToParent()
                        )
                        .bounds(
                                vWidth / 2 - 30,
                                bottomY,
                                60,
                                18
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.kinetictabs.tabs.btn.save_apply"
                                ),
                                button -> {
                                    String json =
                                            TabConfig.GSON.toJson(
                                                    TabConfig.currentEditing
                                            );

                                    TabNetwork.CHANNEL.sendToServer(
                                            new TabNetwork.SaveTabPacket(
                                                    json
                                            )
                                    );
                                }
                        )
                        .bounds(
                                RIGHT_X + RIGHT_W - 100,
                                bottomY,
                                100,
                                18
                        )
                        .build()
        );
    }

    private void openItemSelector() {
        if (minecraft == null
                || mainTabs.isEmpty()) {
            return;
        }

        String tabId =
                mainTabs.get(
                        mainSelectedTabIdx
                ).id.toString();

        minecraft.setScreen(
                new ItemSelectorScreen(
                        this,
                        selection -> {
                            if (!selection.isItem()) {
                                return;
                            }

                            ItemStack selectedStack =
                                    selection.stack();

                            ResourceLocation selectedId =
                                    ForgeRegistries.ITEMS.getKey(
                                            selectedStack.getItem()
                                    );

                            if (selectedId == null) {
                                return;
                            }

                            String id =
                                    selectedId.toString();

                            String nbt =
                                    selectedStack.hasTag()
                                            && selectedStack.getTag() != null
                                            ? selectedStack.getTag().toString()
                                            : "{}";

                            boolean exists =
                                    mainItems.stream()
                                            .anyMatch(item -> {
                                                ResourceLocation currentId =
                                                        ForgeRegistries.ITEMS.getKey(
                                                                item.stack.getItem()
                                                        );

                                                String currentNbt =
                                                        item.stack.getTag() != null
                                                                ? item.stack.getTag().toString()
                                                                : "{}";

                                                return currentId != null
                                                        && currentId.toString().equals(id)
                                                        && currentNbt.equals(nbt);
                                            });

                            if (exists) {
                                TabNetwork.CHANNEL.sendToServer(
                                        new TabNetwork.RequestNotifyPacket(
                                                "gui.kinetictabs.tabs.notify.duplicate"
                                        )
                                );
                                return;
                            }

                            TabConfig.TabAddition addition =
                                    TabConfig.currentEditing.additions.stream()
                                            .filter(value ->
                                                    value.tabId.equals(tabId)
                                            )
                                            .findFirst()
                                            .orElseGet(() -> {
                                                TabConfig.TabAddition created =
                                                        new TabConfig.TabAddition();

                                                created.tabId =
                                                        tabId;

                                                TabConfig.currentEditing.additions.add(
                                                        created
                                                );

                                                return created;
                                            });

                            addition.items.add(
                                    new TabConfig.TabItem(
                                            id,
                                            nbt
                                    )
                            );

                            refreshData();
                        }
                )
        );
    }

    @Override
    protected void renderScaledBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        hoveredTabTooltip = null;
        hoveredItemTooltip = null;

        GuiRenderUtil.drawPanel(
                graphics,
                0,
                0,
                vWidth,
                vHeight,
                0xFF181818,
                0xFF333333
        );

        graphics.fill(
                0,
                vHeight - 30,
                vWidth,
                vHeight,
                0xFF0A0A0A
        );

        graphics.drawString(
                font,
                Component.translatable(
                        "gui.kinetictabs.tabs.unified.left_title.colored"
                ),
                MAIN_X,
                8,
                0xFFFFFF
        );

        graphics.drawString(
                font,
                Component.translatable(
                        "gui.kinetictabs.tabs.unified.right_title.colored"
                ),
                RIGHT_X,
                8,
                0xFFFFFF
        );

        renderMainPane(
                graphics,
                mouseX,
                mouseY
        );

        renderRightPane(
                graphics,
                mouseX,
                mouseY
        );
    }

    private void renderMainPane(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        GuiRenderUtil.drawPanel(
                graphics,
                MAIN_X,
                TAB_Y,
                MAIN_W,
                TAB_SIZE,
                0xFF222222,
                0xFF333333
        );

        boolean leftArrowHovered =
                GuiRenderUtil.isHovering(
                        mouseX,
                        mouseY,
                        MAIN_X,
                        TAB_Y,
                        ARROW_W - 1,
                        TAB_SIZE - 1
                );

        boolean rightArrowHovered =
                GuiRenderUtil.isHovering(
                        mouseX,
                        mouseY,
                        MAIN_X + MAIN_W - ARROW_W,
                        TAB_Y,
                        ARROW_W - 1,
                        TAB_SIZE - 1
                );

        int leftColor =
                mainTabScroll.offset() > 0
                        ? leftArrowHovered
                        ? 0xFF88FF88
                        : 0xFF33FF33
                        : 0xFF555555;

        int rightColor =
                mainTabScroll.offset()
                        < mainTabScroll.maxOffset()
                        ? rightArrowHovered
                        ? 0xFF88FF88
                        : 0xFF33FF33
                        : 0xFF555555;

        GuiRenderUtil.drawPanel(
                graphics,
                MAIN_X,
                TAB_Y,
                ARROW_W,
                TAB_SIZE,
                leftArrowHovered
                        ? 0xFF3A3A3A
                        : 0xFF282828,
                0xFF333333
        );

        graphics.drawCenteredString(
                font,
                "◀",
                MAIN_X + ARROW_W / 2,
                TAB_Y + 7,
                leftColor
        );

        GuiRenderUtil.drawPanel(
                graphics,
                MAIN_X + MAIN_W - ARROW_W,
                TAB_Y,
                ARROW_W,
                TAB_SIZE,
                rightArrowHovered
                        ? 0xFF3A3A3A
                        : 0xFF282828,
                0xFF333333
        );

        graphics.drawCenteredString(
                font,
                "➤",
                MAIN_X + MAIN_W - ARROW_W / 2,
                TAB_Y + 7,
                rightColor
        );

        for (int i = 0;
             i < MAIN_TAB_COLS;
             i++) {
            int index =
                    mainTabScroll.offset() + i;

            if (index >= mainTabs.size()) {
                break;
            }

            TabInfo info =
                    mainTabs.get(index);

            int tabX =
                    TAB_CONTENT_X + i * TAB_SIZE;

            boolean selected =
                    index == mainSelectedTabIdx;

            boolean banned =
                    TabConfig.currentEditing.hiddenTabs.contains(
                            info.id.toString()
                    );

            boolean hovered =
                    GuiRenderUtil.isHovering(
                            mouseX,
                            mouseY,
                            tabX,
                            TAB_Y,
                            TAB_SIZE - 1,
                            TAB_SIZE - 1
                    );

            AdaptiveItemGridRenderer.drawSlot(
                    graphics,
                    tabX,
                    TAB_Y,
                    TAB_SIZE,
                    4,
                    hovered && !selected
            );

            if (banned) {
                graphics.fill(
                        tabX + 1,
                        TAB_Y + 1,
                        tabX + TAB_SIZE - 1,
                        TAB_Y + TAB_SIZE - 1,
                        0x44FF0000
                );
            }

            if (selected) {
                graphics.renderOutline(
                        tabX,
                        TAB_Y,
                        TAB_SIZE,
                        TAB_SIZE,
                        0xFF55FF55
                );
            }

            AdaptiveItemGridRenderer.renderItem(
                    graphics,
                    font,
                    info.icon,
                    tabX,
                    TAB_Y,
                    TAB_SIZE,
                    1.0F,
                    false
            );

            if (hovered
                    && !contentDrag.isActive()) {
                hoveredTabTooltip = info;
                hoveredTabBanned = banned;
            }
        }

        mainTabScroll.renderHorizontal(
                graphics,
                mouseX,
                mouseY,
                TAB_CONTENT_X,
                TAB_SCROLL_Y,
                TAB_CONTENT_W,
                6,
                20
        );

        GuiRenderUtil.drawPanel(
                graphics,
                MAIN_X - 2,
                ITEM_Y - 2,
                MAIN_W + 4,
                ITEM_H + 4,
                0xFF2A2A2A,
                0xFF000000
        );

        graphics.fill(
                MAIN_X,
                ITEM_Y,
                MAIN_X + MAIN_W,
                ITEM_Y + ITEM_H,
                0xFF181818
        );

        renderItemGrid(
                graphics,
                mainItems,
                mainItemScroll,
                MAIN_X,
                MAIN_ITEM_COLS,
                false,
                mouseX,
                mouseY
        );

        mainItemScroll.render(
                graphics,
                mouseX,
                mouseY,
                MAIN_X + MAIN_W + 4,
                ITEM_Y,
                6,
                ITEM_H,
                20
        );
    }

    private void renderRightPane(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        GuiRenderUtil.drawPanel(
                graphics,
                RIGHT_X - 2,
                ITEM_Y - 2,
                RIGHT_W + 4,
                ITEM_H + 4,
                0xFF2A2A2A,
                0xFF000000
        );

        graphics.fill(
                RIGHT_X,
                ITEM_Y,
                RIGHT_X + RIGHT_W,
                ITEM_Y + ITEM_H,
                0xFF181818
        );

        renderItemGrid(
                graphics,
                rightHiddenItems,
                rightItemScroll,
                RIGHT_X,
                RIGHT_ITEM_COLS,
                true,
                mouseX,
                mouseY
        );

        rightItemScroll.render(
                graphics,
                mouseX,
                mouseY,
                RIGHT_X + RIGHT_W + 4,
                ITEM_Y,
                6,
                ITEM_H,
                20
        );
    }

    private void renderItemGrid(
            GuiGraphics graphics,
            List<DisplayItem> items,
            GridScrollController scroll,
            int gridX,
            int columns,
            boolean hiddenPane,
            int mouseX,
            int mouseY
    ) {
        int startIndex =
                scroll.offset() * columns;

        int endIndex =
                Math.min(
                        startIndex
                                + ITEM_VISIBLE_ROWS * columns,
                        items.size()
                );

        for (int i = startIndex;
             i < endIndex;
             i++) {
            DisplayItem item =
                    items.get(i);

            int localIndex =
                    i - startIndex;

            int column =
                    localIndex % columns;

            int row =
                    localIndex / columns;

            int x =
                    gridX
                            + column * SLOT_SIZE;

            int y =
                    ITEM_Y
                            + row * SLOT_SIZE;

            boolean hovered =
                    GuiRenderUtil.isHovering(
                            mouseX,
                            mouseY,
                            x,
                            y,
                            SLOT_SIZE - 1,
                            SLOT_SIZE - 1
                    );

            AdaptiveItemGridRenderer.drawSlot(
                    graphics,
                    x,
                    y,
                    SLOT_SIZE,
                    4,
                    hovered
            );

            AdaptiveItemGridRenderer.renderItem(
                    graphics,
                    font,
                    item.stack,
                    x,
                    y,
                    SLOT_SIZE,
                    1.0F,
                    !hiddenPane
            );

            int itemBarY =
                    y + SLOT_SIZE - 2;

            if (item.state == ItemState.ADDED) {
                graphics.fill(
                        x + 2,
                        itemBarY,
                        x + SLOT_SIZE - 2,
                        itemBarY + 1,
                        0xCC33FF33
                );
            } else if (item.state == ItemState.BANNED) {
                graphics.fill(
                        x + 2,
                        itemBarY,
                        x + SLOT_SIZE - 2,
                        itemBarY + 1,
                        0xCCFF3333
                );
            }

            if (hovered
                    && !contentDrag.isActive()) {
                hoveredItemTooltip = item;
                hoveredItemHiddenPane = hiddenPane;
            }
        }
    }

    private void renderTabTooltip(
            GuiGraphics graphics,
            TabInfo info,
            boolean banned,
            int rawMouseX,
            int rawMouseY
    ) {
        List<Component> tooltip =
                new ArrayList<>();

        tooltip.add(
                Component.translatable(
                        banned
                                ? "gui.kinetictabs.format.red"
                                : "gui.kinetictabs.format.white",
                        info.name.getString()
                )
        );

        if (banned) {
            tooltip.add(
                    Component.translatable(
                            "gui.kinetictabs.tabs.unified.right_tabs_title.colored"
                    )
            );

            tooltip.add(
                    Component.translatable(
                            "gui.kinetictabs.tabs.unified.tooltip.right_tab.1.colored"
                    )
            );

            tooltip.add(
                    Component.translatable(
                            "gui.kinetictabs.tabs.unified.tooltip.right_tab.2.colored"
                    )
            );

            tooltip.add(
                    Component.translatable(
                            "gui.kinetictabs.tabs.unified.tooltip.right_tab.3.colored"
                    )
            );
        } else {
            tooltip.add(
                    Component.translatable(
                            "gui.kinetictabs.tabs.unified.tooltip.left_tab.1.colored"
                    )
            );

            tooltip.add(
                    Component.translatable(
                            "gui.kinetictabs.tabs.unified.tooltip.left_tab.2.colored"
                    )
            );

            tooltip.add(
                    Component.translatable(
                            "gui.kinetictabs.tabs.unified.tooltip.left_tab.3.colored"
                    )
            );
        }

        renderRawTooltip(
                graphics,
                tooltip,
                rawMouseX,
                rawMouseY
        );
    }

    private void renderItemTooltip(
            GuiGraphics graphics,
            DisplayItem item,
            boolean hiddenPane,
            int rawMouseX,
            int rawMouseY
    ) {
        List<Component> tooltip =
                new ArrayList<>();

        tooltip.add(
                item.stack.getHoverName()
        );

        if (hiddenPane) {
            tooltip.add(
                    Component.translatable(
                            "gui.kinetictabs.tabs.unified.right_items_title.colored"
                    )
            );

            tooltip.add(
                    Component.translatable(
                            "gui.kinetictabs.tabs.unified.tooltip.right_item.1.colored"
                    )
            );

            tooltip.add(
                    Component.translatable(
                            "gui.kinetictabs.tabs.unified.tooltip.right_item.2.colored"
                    )
            );
        } else {
            if (item.state == ItemState.ADDED) {
                tooltip.add(
                        Component.translatable(
                                "gui.kinetictabs.tabs.unified.tip_added_item.colored"
                        )
                );

                tooltip.add(
                        Component.translatable(
                                item.ref.matchNbt
                                        ? "gui.kinetictabs.tabs.unified.tooltip.nbt_mode.on.colored"
                                        : "gui.kinetictabs.tabs.unified.tooltip.nbt_mode.off.colored"
                        )
                );

                tooltip.add(
                        Component.translatable(
                                "gui.kinetictabs.tabs.unified.tooltip.left_item_nbt_toggle.colored"
                        )
                );
            } else if (item.state == ItemState.BANNED) {
                tooltip.add(
                        Component.translatable(
                                "gui.kinetictabs.tabs.unified.right_items_title.colored"
                        )
                );

                tooltip.add(
                        Component.translatable(
                                "gui.kinetictabs.tabs.unified.tooltip.right_item.1.colored"
                        )
                );
            }

            if (item.state != ItemState.BANNED) {
                tooltip.add(
                        Component.translatable(
                                "gui.kinetictabs.tabs.unified.tooltip.left_item.1.colored"
                        )
                );

                tooltip.add(
                        Component.translatable(
                                "gui.kinetictabs.tabs.unified.tooltip.left_item.2.colored"
                        )
                );
            }
        }

        renderRawTooltip(
                graphics,
                tooltip,
                rawMouseX,
                rawMouseY
        );
    }

    private void renderRawTooltip(
            GuiGraphics graphics,
            List<Component> tooltip,
            int rawMouseX,
            int rawMouseY
    ) {
        graphics.renderTooltip(
                font,
                tooltip,
                Optional.empty(),
                rawMouseX,
                rawMouseY
        );
    }

    @Override
    protected void renderScaledForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (contentDrag.isActive()) {
            Object payload =
                    contentDrag.payload();

            if (payload instanceof TabInfo tabInfo) {
                AdaptiveItemGridRenderer.renderItem(
                        graphics,
                        font,
                        tabInfo.icon,
                        mouseX - 11,
                        mouseY - 11,
                        TAB_SIZE,
                        1.0F,
                        false
                );
            } else if (payload instanceof DisplayItem item) {
                AdaptiveItemGridRenderer.renderItem(
                        graphics,
                        font,
                        item.stack,
                        mouseX - 9,
                        mouseY - 9,
                        SLOT_SIZE,
                        1.0F,
                        false
                );
            }
        }

        TabClientEvents.renderNotificationScaled(
                graphics,
                vWidth,
                font
        );
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int scaledMouseX,
            int scaledMouseY,
            int rawMouseX,
            int rawMouseY
    ) {
        if (contentDrag.isActive()) {
            return;
        }

        if (hoveredItemTooltip != null) {
            renderItemTooltip(
                    graphics,
                    hoveredItemTooltip,
                    hoveredItemHiddenPane,
                    rawMouseX,
                    rawMouseY
            );
            return;
        }

        if (hoveredTabTooltip != null) {
            renderTabTooltip(
                    graphics,
                    hoveredTabTooltip,
                    hoveredTabBanned,
                    rawMouseX,
                    rawMouseY
            );
            return;
        }

        int bottomY =
                vHeight - 22;

        if (GuiRenderUtil.isHovering(
                scaledMouseX,
                scaledMouseY,
                MAIN_X,
                bottomY,
                120,
                18
        )) {
            renderRawTooltip(
                    graphics,
                    List.of(
                            Component.translatable(
                                    "gui.kinetictabs.tabs.unified.btn.add_item.tooltip"
                            )
                    ),
                    rawMouseX,
                    rawMouseY
            );
            return;
        }

        if (GuiRenderUtil.isHovering(
                scaledMouseX,
                scaledMouseY,
                RIGHT_X + RIGHT_W - 100,
                bottomY,
                100,
                18
        )) {
            renderRawTooltip(
                    graphics,
                    List.of(
                            Component.translatable(
                                    "gui.kinetictabs.tabs.unified.btn.save.tooltip"
                            )
                    ),
                    rawMouseX,
                    rawMouseY
            );
        }
    }

    private void returnToParent() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    protected boolean universalMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (super.universalMouseClicked(
                mouseX,
                mouseY,
                button
        )) {
            return true;
        }

        if (button == 0) {
            if (mainTabScroll.beginHorizontalDrag(
                    mouseX,
                    mouseY,
                    TAB_CONTENT_X,
                    TAB_SCROLL_Y,
                    TAB_CONTENT_W,
                    6,
                    20,
                    0
            )) {
                return true;
            }

            if (mainItemScroll.beginDrag(
                    mouseX,
                    mouseY,
                    MAIN_X + MAIN_W + 4,
                    ITEM_Y,
                    6,
                    ITEM_H,
                    20,
                    0
            )) {
                return true;
            }

            if (rightItemScroll.beginDrag(
                    mouseX,
                    mouseY,
                    RIGHT_X + RIGHT_W + 4,
                    ITEM_Y,
                    6,
                    ITEM_H,
                    20,
                    0
            )) {
                return true;
            }
        }

        if (mouseY >= TAB_Y
                && mouseY < TAB_Y + TAB_SIZE) {
            return handleTabClick(
                    mouseX,
                    button
            );
        }

        if (mouseY >= ITEM_Y
                && mouseY < ITEM_Y + ITEM_H) {
            return handleItemClick(
                    mouseX,
                    mouseY,
                    button
            );
        }

        return false;
    }

    private boolean handleTabClick(
            double mouseX,
            int button
    ) {
        if (button == 0
                && mouseX >= MAIN_X
                && mouseX < MAIN_X + ARROW_W) {
            mainTabScroll.setOffset(
                    mainTabScroll.offset() - 1
            );
            return true;
        }

        if (button == 0
                && mouseX >= MAIN_X + MAIN_W - ARROW_W
                && mouseX < MAIN_X + MAIN_W) {
            mainTabScroll.setOffset(
                    mainTabScroll.offset() + 1
            );
            return true;
        }

        if (mouseX < TAB_CONTENT_X
                || mouseX >= TAB_CONTENT_X + TAB_CONTENT_W) {
            return false;
        }

        int index =
                mainTabScroll.offset()
                        + (int) (
                        (mouseX - TAB_CONTENT_X)
                                / TAB_SIZE
                );

        if (index < 0
                || index >= mainTabs.size()) {
            return false;
        }

        TabInfo tabInfo =
                mainTabs.get(index);

        String tabId =
                tabInfo.id.toString();

        if (button == 0) {
            if (Screen.hasControlDown()) {
                contentDrag.start(
                        DragType.MAIN_TAB,
                        tabInfo
                );
            } else {
                mainSelectedTabIdx =
                        index;

                refreshData();
            }

            return true;
        }

        if (button == 1) {
            if (Screen.hasShiftDown()) {
                TabConfig.currentEditing.hiddenTabs.remove(
                        tabId
                );
            } else if (!TabConfig.currentEditing.hiddenTabs.contains(
                    tabId
            )) {
                TabConfig.currentEditing.hiddenTabs.add(
                        tabId
                );
            }

            refreshData();
            return true;
        }

        return false;
    }

    private boolean handleItemClick(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (mouseX >= MAIN_X
                && mouseX < MAIN_X + MAIN_W) {
            int index =
                    itemIndexAt(
                            mainItemScroll,
                            MAIN_X,
                            MAIN_ITEM_COLS,
                            mouseX,
                            mouseY
                    );

            if (index < 0
                    || index >= mainItems.size()) {
                return false;
            }

            DisplayItem item =
                    mainItems.get(index);

            if (button == 0) {
                if (item.state == ItemState.BANNED) {
                    unhideItem(item);
                    refreshData();
                } else if (Screen.hasControlDown()) {
                    contentDrag.start(
                            DragType.MAIN_ITEM,
                            item
                    );
                }

                return true;
            }

            if (button == 1) {
                if (item.state == ItemState.ADDED
                        && Screen.hasShiftDown()) {
                    item.ref.matchNbt =
                            !item.ref.matchNbt;
                } else {
                    hideItem(item);
                }

                refreshData();
                return true;
            }

            return false;
        }

        if (mouseX >= RIGHT_X
                && mouseX < RIGHT_X + RIGHT_W) {
            int index =
                    itemIndexAt(
                            rightItemScroll,
                            RIGHT_X,
                            RIGHT_ITEM_COLS,
                            mouseX,
                            mouseY
                    );

            if (index < 0
                    || index >= rightHiddenItems.size()) {
                return false;
            }

            DisplayItem item =
                    rightHiddenItems.get(index);

            if (button == 0) {
                if (Screen.hasControlDown()) {
                    contentDrag.start(
                            DragType.RIGHT_ITEM,
                            item
                    );
                } else {
                    unhideItem(item);
                    refreshData();
                }

                return true;
            }

            if (button == 1) {
                unhideItem(item);
                refreshData();
                return true;
            }
        }

        return false;
    }

    private static int itemIndexAt(
            GridScrollController scroll,
            int gridX,
            int columns,
            double mouseX,
            double mouseY
    ) {
        int row =
                (int) ((mouseY - ITEM_Y) / SLOT_SIZE);

        int column =
                (int) ((mouseX - gridX) / SLOT_SIZE);

        return scroll.offset() * columns
                + row * columns
                + column;
    }

    private void hideItem(
            DisplayItem item
    ) {
        if (item.state == ItemState.BANNED) {
            return;
        }

        if (item.state == ItemState.ADDED) {
            String currentTabId =
                    mainTabs.get(
                            mainSelectedTabIdx
                    ).id.toString();

            TabConfig.currentEditing.additions.forEach(
                    addition -> {
                        if (addition.tabId.equals(
                                currentTabId
                        )) {
                            addition.items.removeIf(
                                    candidate ->
                                            candidate.id.equals(
                                                    item.ref.id
                                            )
                                                    && candidate.nbt.equals(
                                                    item.ref.nbt
                                            )
                            );
                        }
                    }
            );

            TabConfig.currentEditing.additions.removeIf(
                    addition ->
                            addition.items.isEmpty()
            );
        } else if (!TabConfig.currentEditing.removals.contains(
                item.ruleStr
        )) {
            TabConfig.currentEditing.removals.add(
                    item.ruleStr
            );
        }
    }

    private void unhideItem(
            DisplayItem item
    ) {
        TabConfig.currentEditing.removals.remove(
                item.ruleStr
        );
    }

    @Override
    protected boolean universalMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (mainTabScroll.dragHorizontal(
                mouseX,
                MAIN_X,
                MAIN_W,
                20
        )) {
            return true;
        }

        if (mainItemScroll.drag(
                mouseY,
                ITEM_Y,
                ITEM_H,
                20
        )) {
            return true;
        }

        if (rightItemScroll.drag(
                mouseY,
                ITEM_Y,
                ITEM_H,
                20
        )) {
            return true;
        }

        if (contentDrag.isActive()) {
            return true;
        }

        return super.universalMouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    protected boolean universalMouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        boolean releasedScroll =
                mainTabScroll.release(button)
                        | mainItemScroll.release(button)
                        | rightItemScroll.release(button);

        if (releasedScroll) {
            return true;
        }

        if (button == 0
                && contentDrag.isActive()) {
            Object payload =
                    contentDrag.payload();

            boolean dropRight =
                    mouseX >= RIGHT_X
                            && mouseX <= RIGHT_X + RIGHT_W;

            boolean dropMain =
                    mouseX >= MAIN_X
                            && mouseX <= MAIN_X + MAIN_W;

            if (contentDrag.type() == DragType.MAIN_ITEM
                    && payload instanceof DisplayItem item
                    && dropRight) {
                hideItem(item);
            } else if (contentDrag.type() == DragType.RIGHT_ITEM
                    && payload instanceof DisplayItem item
                    && dropMain) {
                unhideItem(item);
            }

            contentDrag.clear();
            refreshData();
            return true;
        }

        contentDrag.clear();

        return super.universalMouseReleased(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    protected boolean universalMouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        if (mouseX >= MAIN_X
                && mouseX <= MAIN_X + MAIN_W) {
            if (mouseY >= TAB_Y
                    && mouseY <= TAB_SCROLL_Y + 8) {
                mainTabScroll.scroll(delta);
            } else {
                mainItemScroll.scroll(delta);
            }

            return true;
        }

        if (mouseX >= RIGHT_X
                && mouseX <= RIGHT_X + RIGHT_W) {
            rightItemScroll.scroll(delta);
            return true;
        }

        return super.universalMouseScrolled(
                mouseX,
                mouseY,
                delta
        );
    }
}
