package dev.xyat.kinetictabs.tabs.network;

import dev.xyat.kineticcore.api.NetworkCompressUtil;
import dev.xyat.kineticcore.api.client.GuiToastUtil;
import dev.xyat.kinetictabs.KineticTabs;
import dev.xyat.kinetictabs.tabs.TabConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class TabNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(KineticTabs.MODID, "tabs_sync"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncTabPacket.class, SyncTabPacket::encode, SyncTabPacket::new, SyncTabPacket::handle);
        CHANNEL.registerMessage(id++, SaveTabPacket.class, SaveTabPacket::encode, SaveTabPacket::new, SaveTabPacket::handle);
        CHANNEL.registerMessage(id++, NotifyPacket.class, NotifyPacket::encode, NotifyPacket::new, NotifyPacket::handle);
        CHANNEL.registerMessage(id++, RequestNotifyPacket.class, RequestNotifyPacket::encode, RequestNotifyPacket::new, RequestNotifyPacket::handle);
        CHANNEL.registerMessage(id++, ClearNotifyPacket.class, ClearNotifyPacket::encode, ClearNotifyPacket::new, ClearNotifyPacket::handle);
        CHANNEL.registerMessage(id++, OpenTabEditorPacket.class, OpenTabEditorPacket::encode, OpenTabEditorPacket::new, OpenTabEditorPacket::handle);
        CHANNEL.registerMessage(id, RequestOpenEditorPacket.class, RequestOpenEditorPacket::encode, RequestOpenEditorPacket::new, RequestOpenEditorPacket::handle);
    }

    public static void requestOpenEditor() {
        CHANNEL.sendToServer(new RequestOpenEditorPacket());
    }

    public static boolean sendEditorSnapshot(ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return false;
        if (!TabConfig.loadForEditor()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NotifyPacket("gui.kinetictabs.tabs.notify.load_failed"));
            return false;
        }
        String json = TabConfig.GSON.toJson(TabConfig.data);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenTabEditorPacket(json));
        return true;
    }

    public static class SyncTabPacket {
        private final String json;
        public SyncTabPacket(String json) { this.json = json; }
        public SyncTabPacket(net.minecraft.network.FriendlyByteBuf buf) { this.json = NetworkCompressUtil.decompress(buf.readByteArray()); }
        public void encode(net.minecraft.network.FriendlyByteBuf buf) { buf.writeByteArray(NetworkCompressUtil.compress(json)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                try {
                    TabConfig.Data next = TabConfig.GSON.fromJson(json, TabConfig.Data.class);
                    if (!TabConfig.isValidForServer(next)) return;
                    TabConfig.data = next;
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> TabNetworkClient::handleSync);
                } catch (RuntimeException exception) {
                    KineticTabs.LOGGER.error("Rejected invalid tabs sync payload", exception);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SaveTabPacket {
        private final String json;
        public SaveTabPacket(String json) { this.json = json; }
        public SaveTabPacket(net.minecraft.network.FriendlyByteBuf buf) { this.json = NetworkCompressUtil.decompress(buf.readByteArray()); }
        public void encode(net.minecraft.network.FriendlyByteBuf buf) { buf.writeByteArray(NetworkCompressUtil.compress(json)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender == null) return;
                if (!sender.hasPermissions(2)) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new NotifyPacket("gui.kinetictabs.tabs.notify.save_failed"));
                    return;
                }

                boolean success = false;
                String savedJson = null;
                try {
                    TabConfig.Data next = TabConfig.GSON.fromJson(json, TabConfig.Data.class);
                    if (TabConfig.isValidForServer(next) && TabConfig.save(next)) {
                        savedJson = TabConfig.GSON.toJson(TabConfig.data);
                        success = true;
                    }
                } catch (RuntimeException exception) {
                    KineticTabs.LOGGER.error("Rejected invalid tabs save payload", exception);
                }

                if (success) {
                    CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncTabPacket(savedJson));
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new NotifyPacket("gui.kinetictabs.tabs.notify.saved"));
                } else {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new NotifyPacket("gui.kinetictabs.tabs.notify.save_failed"));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class NotifyPacket {
        private final String langKey;
        public NotifyPacket(String langKey) { this.langKey = langKey; }
        public NotifyPacket(net.minecraft.network.FriendlyByteBuf buf) { this.langKey = buf.readUtf(); }
        public void encode(net.minecraft.network.FriendlyByteBuf buf) { buf.writeUtf(langKey); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GuiToastUtil.showToast(Component.translatable(langKey))));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RequestNotifyPacket {
        private final String langKey;
        public RequestNotifyPacket(String langKey) { this.langKey = langKey; }
        public RequestNotifyPacket(net.minecraft.network.FriendlyByteBuf buf) { this.langKey = buf.readUtf(); }
        public void encode(net.minecraft.network.FriendlyByteBuf buf) { buf.writeUtf(langKey); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new NotifyPacket(langKey));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ClearNotifyPacket {
        public ClearNotifyPacket() {}
        public ClearNotifyPacket(net.minecraft.network.FriendlyByteBuf buf) {}
        public void encode(net.minecraft.network.FriendlyByteBuf buf) {}
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> GuiToastUtil::clearAllToasts));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class OpenTabEditorPacket {
        private final String json;

        public OpenTabEditorPacket(String json) {
            this.json = json;
        }

        public OpenTabEditorPacket(net.minecraft.network.FriendlyByteBuf buf) {
            this.json = NetworkCompressUtil.decompress(buf.readByteArray());
        }

        public String json() {
            return json;
        }

        public void encode(net.minecraft.network.FriendlyByteBuf buf) {
            buf.writeByteArray(NetworkCompressUtil.compress(json));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> TabNetworkClient.handleOpenEditor(this)
            ));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RequestOpenEditorPacket {
        public RequestOpenEditorPacket() {}
        public RequestOpenEditorPacket(net.minecraft.network.FriendlyByteBuf buf) {}
        public void encode(net.minecraft.network.FriendlyByteBuf buf) {}
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null && sender.hasPermissions(2)) {
                    sendEditorSnapshot(sender);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
