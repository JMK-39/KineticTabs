# KineticTabs

[简体中文](#简体中文) | [English](#english)

## 简体中文

### 模组定位

**KineticTabs** 是 Kinetic 系列的创造模式标签页整理模块。它用于重新组织大型整合包的创造模式物品页：隐藏不需要的标签页、从标签页中移除指定物品，或把物品追加到指定标签页，同时与客户端搜索/JEI 环境保持联动。

### 主要功能

- **隐藏整个标签页**：指定的创造模式标签页不会继续显示在标签栏中。
- **从标签页移除物品**：可以按规则把不需要的物品从创造模式内容中剔除。
- **向指定标签页追加物品**：把任意注册物品添加到目标标签页。
- **NBT 物品追加**：追加条目可以保存自定义 NBT，适合药水、附魔书、枪械、饰品等特殊物品。
- **多种移除规则**：支持具体物品 ID、`@模组ID`、`#物品标签` 与 NBT 规则。
- **可视化统一编辑器**：通过实体物品列表、搜索和标签页列表直接编辑，而不是手写大型 JSON。
- **服务器配置快照**：编辑远程服务器配置时从服务器取得当前数据，避免使用本地旧副本覆盖服务器规则。
- **保存校验**：服务端会验证标签页 ID、物品 ID、NBT 和规则数量后再写入文件。
- **JEI 可选联动**：安装 JEI 时，被隐藏或移除的物品可以同步参与客户端物品浏览过滤。
- **需要重载的显示规则**：部分创造模式标签页变更需要重新构建客户端标签内容后才能完整体现。

### 配置文件

```text
config/kineticcore/creative_tabs.json
```

主要数据：

- `removals`：从创造模式内容中移除的物品规则。
- `additions`：追加到指定标签页的物品列表。
- `hiddenTabs`：隐藏的创造模式标签页 ID。

### 使用建议

通过 F6 打开 KineticTabs 页面并进入专用编辑器。大量隐藏标签页时建议分批测试，尤其是某些模组会在运行时动态创建创造模式标签页或强制追加物品。

### 运行环境

- Minecraft 1.20.1
- Minecraft Forge 47.4.2
- Java 17
- KineticCore：必须
- JEI：可选

## English

### Overview

**KineticTabs** is the creative-tab organization module for the Kinetic family. It can hide complete creative tabs, remove selected items from tab contents, append items to chosen tabs and keep optional JEI browsing synchronized with the configured view.

### Key Features

- Hide complete creative-mode tabs.
- Remove items using item, mod, tag or NBT-aware rules.
- Append items to selected tabs.
- Append custom-NBT item variants.
- Visual unified creative-tab editor.
- Server snapshot workflow for remote editing.
- Server-side validation of tab IDs, item IDs, NBT and rule limits.
- Optional JEI filtering integration.

### Configuration

```text
config/kineticcore/creative_tabs.json
```

Main sections:

- `removals`
- `additions`
- `hiddenTabs`

### Requirements

- Minecraft 1.20.1
- Minecraft Forge 47.4.2
- Java 17
- KineticCore: required
- JEI: optional

## 开源协议与版权 (License)

Copyright (C) 2024-2026 XYAT.

本项目基于 **GNU Lesser General Public License v3.0 (LGPLv3)** 协议开源。

This project is open-sourced under the **GNU Lesser General Public License v3.0 (LGPLv3)**.
