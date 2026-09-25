package com.solidandshot.listener;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Server side of the optional Listener client-mod bridge.
 *
 * <p>The bridge deliberately uses Bukkit's native plugin messaging instead of a
 * second socket. A client mod only needs to register the {@value #CHANNEL}
 * channel and implement the small binary protocol documented in README.md.</p>
 */
public final class ClientBridge implements PluginMessageListener {

    public static final String CHANNEL = "listener:main";
    public static final int PROTOCOL_VERSION = 1;

    private static final int OP_HELLO = 1;
    private static final int OP_HELLO_ACK = 2;
    private static final int OP_ACTION = 3;
    private static final int OP_EVENT = 4;
    private static final int MAX_MESSAGE_BYTES = 32 * 1024;
    private static final int MAX_STRING_CHARS = 4096;
    private static final int MAX_FIELDS = 64;
    private static final Pattern NAME = Pattern.compile("[a-z0-9_:.\\-]{1,64}");

    private final Plugin plugin;
    private final Consumer<EventContext> eventSink;
    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();

    public ClientBridge(Plugin plugin, Consumer<EventContext> eventSink) {
        this.plugin = plugin;
        this.eventSink = eventSink;
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    /** Stops accepting messages and releases Bukkit messenger registrations. */
    public void close() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        clients.clear();
    }

    public void remove(Player player) {
        if (player != null) clients.remove(player.getUniqueId());
    }

    /** Returns a snapshot of clients that completed a protocol handshake. */
    public Map<UUID, ClientInfo> clients() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(clients));
    }

    /** Sends an arbitrary client action. Unknown action names are left to the mod. */
    public boolean sendAction(Player player, String action, String value) {
        if (player == null || !player.isOnline() || !validName(action)) return false;
        try {
            byte[] payload = frame(OP_ACTION, out -> {
                out.writeUTF(action);
                out.writeUTF(limit(value));
            });
            player.sendPluginMessage(plugin, CHANNEL, payload);
            return true;
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().warning("发送客户端动作失败: " + ex.getMessage());
            return false;
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || player == null || message == null || message.length > MAX_MESSAGE_BYTES) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            int version = in.readUnsignedByte();
            int op = in.readUnsignedByte();
            if (version != PROTOCOL_VERSION) {
                sendHelloAck(player, false, "unsupported_protocol");
                return;
            }
            switch (op) {
                case OP_HELLO -> receiveHello(player, in);
                case OP_EVENT -> receiveEvent(player, in);
                default -> plugin.getLogger().fine("忽略未知客户端消息操作码: " + op);
            }
        } catch (EOFException ex) {
            plugin.getLogger().fine("忽略截断的客户端消息。");
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().fine("忽略无效客户端消息: " + ex.getMessage());
        }
    }

    private void receiveHello(Player player, DataInputStream in) throws IOException {
        int clientVersion = in.readUnsignedByte();
        String modVersion = readString(in);
        int count = Math.min(in.readUnsignedByte(), MAX_FIELDS);
        List<String> features = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String feature = readString(in);
            if (validName(feature)) features.add(feature);
        }
        if (clientVersion != PROTOCOL_VERSION) {
            sendHelloAck(player, false, "unsupported_protocol");
            return;
        }
        clients.put(player.getUniqueId(), new ClientInfo(clientVersion, modVersion, Set.copyOf(features)));
        sendHelloAck(player, true, "ok");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("mod_version", modVersion);
        values.put("features", String.join(",", features));
        eventSink.accept(new EventContext("client_connect", player, values));
    }

    private void receiveEvent(Player player, DataInputStream in) throws IOException {
        // Require a successful HELLO before accepting client-originated events.
        // This avoids processing stale or malformed payloads sent immediately after
        // a reconnect; the handshake is not an anti-cheat/authentication mechanism.
        if (!clients.containsKey(player.getUniqueId())) return;
        String rawEvent = readString(in).toLowerCase();
        if (!validName(rawEvent)) return;
        String event = rawEvent.startsWith("client_") ? rawEvent : "client_" + rawEvent;
        int count = Math.min(in.readUnsignedByte(), MAX_FIELDS);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("source", "client");
        for (int i = 0; i < count; i++) {
            String key = readString(in).toLowerCase();
            String value = readString(in);
            if (validName(key)) values.put(key, value);
        }
        eventSink.accept(new EventContext(event, player, values));
    }

    private void sendHelloAck(Player player, boolean accepted, String reason) throws IOException {
        byte[] payload = frame(OP_HELLO_ACK, out -> {
            out.writeBoolean(accepted);
            out.writeUTF(reason);
            out.writeUTF("action,event");
        });
        player.sendPluginMessage(plugin, CHANNEL, payload);
    }

    private byte[] frame(int op, Writer writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(PROTOCOL_VERSION);
            out.writeByte(op);
            writer.write(out);
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length > MAX_MESSAGE_BYTES) throw new IOException("消息超过 32 KiB 限制");
        return payload;
    }

    private static String readString(DataInputStream in) throws IOException {
        String value = in.readUTF();
        return limit(value);
    }

    private static String limit(String value) {
        if (value == null) return "";
        return value.length() <= MAX_STRING_CHARS ? value : value.substring(0, MAX_STRING_CHARS);
    }

    private static boolean validName(String value) {
        return value != null && NAME.matcher(value).matches();
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    public record ClientInfo(int protocolVersion, String modVersion, Set<String> features) {
        public ClientInfo {
            features = Set.copyOf(features);
        }
    }
}
