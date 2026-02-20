package eu.mikart.kimmoke.server;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class NioLimboServer {
    private static final int PROTOCOL_1_21_11 = 774;

    private static final int STATUS_C_SERVER_INFO = 0x00;
    private static final int STATUS_C_PING = 0x01;

    private static final int LOGIN_C_SUCCESS = 0x02;
    private static final int LOGIN_C_PLUGIN_REQUEST = 0x04;

    private static final int CONFIG_C_FINISH_CONFIGURATION = 0x03;
    private static final int CONFIG_C_REGISTRY_DATA = 0x07;
    private static final int CONFIG_C_FEATURE_FLAGS = 0x0C;
    private static final int CONFIG_C_TAGS = 0x0D;
    private static final int CONFIG_C_SELECT_KNOWN_PACKS = 0x0E;

    private static final int PLAY_C_CHUNK_BATCH_FINISHED = 0x0B;
    private static final int PLAY_C_CHUNK_BATCH_START = 0x0C;
    private static final int PLAY_C_CUSTOM_PAYLOAD = 0x18;
    private static final int PLAY_C_GAME_EVENT = 0x26;
    private static final int PLAY_C_KEEP_ALIVE = 0x2b;
    private static final int PLAY_C_MAP_CHUNK = 0x2C;
    private static final int PLAY_C_LOGIN = 0x30;
    private static final int PLAY_C_PLAYER_ABILITIES = 0x3E;
    private static final int PLAY_C_POSITION = 0x46;
    private static final int PLAY_C_UPDATE_VIEW_POSITION = 0x5C;
    private static final int PLAY_C_UPDATE_VIEW_DISTANCE = 0x5D;
    private static final int PLAY_C_SPAWN_POSITION = 0x5F;
    private static final int PLAY_C_SET_HELD_ITEM = 0x67;
    private static final int PLAY_C_SET_EXPERIENCE = 0x65;
    private static final int PLAY_C_SET_HEALTH = 0x66;
    private static final int PLAY_C_SET_TIME = 0x6F;
    private static final long KEEP_ALIVE_INTERVAL_MS = 10_000L;

    private final String host;
    private final int port;
    private final List<RegistryDataProvider.RegistryData> registryData;
    private final List<TagsDataProvider.TagRegistry> tagsData;
    private final boolean velocityModernForwarding;
    private final byte[] velocitySecret;
    private final boolean hardcore;
    private final Position spawnPosition;

    public NioLimboServer(String host, int port, boolean velocityModernForwarding, String velocitySecret, boolean hardcore, Position spawnPosition) {
        this.host = host;
        this.port = port;
        this.registryData = RegistryDataProvider.load();
        this.tagsData = TagsDataProvider.load(this.registryData);
        this.velocityModernForwarding = velocityModernForwarding;
        this.velocitySecret = velocitySecret == null ? new byte[0] : velocitySecret.getBytes(StandardCharsets.UTF_8);
        this.hardcore = hardcore;
        this.spawnPosition = spawnPosition == null ? Position.ZERO : spawnPosition;
    }

    private void accept(Selector selector, ServerSocketChannel server) throws Exception {
        SocketChannel channel = server.accept();
        if (channel == null) {
            return;
        }
        channel.configureBlocking(false);

        Connection connection = new Connection(channel);
        channel.register(selector, SelectionKey.OP_READ, connection);
    }

    private void read(SelectionKey key) throws Exception {
        Connection connection = (Connection) key.attachment();
        int bytes = connection.channel.read(connection.readBuffer);
        if (bytes <= 0) {
            closeKey(key);
            return;
        }

        connection.readBuffer.flip();
        while (true) {
            int frameStart = connection.readBuffer.position();
            int frameLength = MinecraftCodec.tryReadVarInt(connection.readBuffer);
            if (frameLength == Integer.MIN_VALUE) {
                connection.readBuffer.position(frameStart);
                break;
            }
            if (frameLength < 0 || frameLength > connection.readBuffer.remaining()) {
                connection.readBuffer.position(frameStart);
                break;
            }

            byte[] frame = new byte[frameLength];
            connection.readBuffer.get(frame);
            handlePacket(key, connection, ByteBuffer.wrap(frame));
        }
        connection.readBuffer.compact();

        if (!connection.writeQueue.isEmpty()) {
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }
    }

    private void write(SelectionKey key) throws Exception {
        Connection connection = (Connection) key.attachment();
        while (!connection.writeQueue.isEmpty()) {
            ByteBuffer current = connection.writeQueue.peek();
            connection.channel.write(current);
            if (current.hasRemaining()) {
                break;
            }
            connection.writeQueue.poll();
        }

        if (connection.writeQueue.isEmpty()) {
            if (connection.closeAfterFlush) {
                closeKey(key);
            } else {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private void handlePacket(SelectionKey key, Connection connection, ByteBuffer packet) {
        int packetId = MinecraftCodec.readVarInt(packet);

        switch (connection.state) {
            case HANDSHAKE -> handleHandshake(connection, packetId, packet);
            case STATUS -> handleStatus(key, connection, packetId, packet);
            case LOGIN -> handleLogin(connection, packetId, packet);
            case CONFIGURATION -> handleConfiguration(connection, packetId);
            case PLAY -> {
            }
        }
    }

    private void handleHandshake(Connection connection, int packetId, ByteBuffer packet) {
        if (packetId != 0x00) {
            connection.state = ConnectionState.CLOSED;
            return;
        }

        MinecraftCodec.readString(packet);
        packet.getShort();
        int nextState = MinecraftCodec.readVarInt(packet);
        connection.state = nextState == 1 ? ConnectionState.STATUS : ConnectionState.LOGIN;
    }

    private void handleStatus(SelectionKey key, Connection connection, int packetId, ByteBuffer packet) {
        if (packetId == 0x00) {
            String json = "{\"version\":{\"name\":\"1.21.11\",\"protocol\":" + PROTOCOL_1_21_11 + "},"
                + "\"players\":{\"max\":1,\"online\":0},"
                + "\"description\":{\"text\":\"Kimmoke\"},"
                + "\"enforcesSecureChat\":false}";

            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            MinecraftCodec.writeString(payload, json);
            queue(connection, STATUS_C_SERVER_INFO, payload);
            return;
        }

        if (packetId == 0x01) {
            long ping = packet.getLong();
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            MinecraftCodec.writeLong(payload, ping);
            queue(connection, STATUS_C_PING, payload);
            closeAfterFlush(key, connection);
        }
    }

    private void handleLogin(Connection connection, int packetId, ByteBuffer packet) {
        if (packetId == 0x00) {
            connection.username = MinecraftCodec.readString(packet);
            connection.uuid = MinecraftCodec.readUuid(packet);

            if (velocityModernForwarding) {
                connection.velocityQueryId = 1;

                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                MinecraftCodec.writeVarInt(payload, connection.velocityQueryId);
                MinecraftCodec.writeString(payload, "velocity:player_info");
                MinecraftCodec.writeVarInt(payload, 0);
                queue(connection, LOGIN_C_PLUGIN_REQUEST, payload);
                return;
            }

            sendLoginSuccess(connection);
            return;
        }

        if (packetId == 0x02 && velocityModernForwarding) {
            int messageId = MinecraftCodec.readVarInt(packet);
            boolean successful = packet.get() != 0;
            if (!successful || messageId != connection.velocityQueryId || velocitySecret.length == 0 || !packet.hasRemaining()) {
                closeConnection(connection);
                return;
            }

            byte[] payload = new byte[packet.remaining()];
            packet.get(payload);
            if (!applyVelocityForwarding(connection, payload)) {
                closeConnection(connection);
                return;
            }

            sendLoginSuccess(connection);
            return;
        }

        if (packetId == 0x03) {
            connection.state = ConnectionState.CONFIGURATION;
            sendConfigurationPackets(connection);
        }
    }

    private void sendLoginSuccess(Connection connection) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MinecraftCodec.writeUuid(payload, connection.uuid);
        MinecraftCodec.writeString(payload, connection.username);
        MinecraftCodec.writeVarInt(payload, 0);
        queue(connection, LOGIN_C_SUCCESS, payload);
    }

    private boolean applyVelocityForwarding(Connection connection, byte[] payload) {
        if (payload.length < 32) {
            return false;
        }

        byte[] signature = new byte[32];
        System.arraycopy(payload, 0, signature, 0, signature.length);
        byte[] data = new byte[payload.length - signature.length];
        System.arraycopy(payload, signature.length, data, 0, data.length);

        byte[] computed;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(velocitySecret, "HmacSHA256"));
            computed = mac.doFinal(data);
        } catch (Exception e) {
            return false;
        }

        if (!MessageDigest.isEqual(signature, computed)) {
            return false;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        int version = MinecraftCodec.readVarInt(buffer);
        if (version < 1) {
            return false;
        }

        MinecraftCodec.readString(buffer);
        connection.uuid = MinecraftCodec.readUuid(buffer);
        connection.username = MinecraftCodec.readString(buffer);

        int propertyCount = MinecraftCodec.readVarInt(buffer);
        for (int i = 0; i < propertyCount; i++) {
            MinecraftCodec.readString(buffer);
            MinecraftCodec.readString(buffer);
            boolean signed = buffer.get() != 0;
            if (signed) {
                MinecraftCodec.readString(buffer);
            }
        }

        return true;
    }

    private void handleConfiguration(Connection connection, int packetId) {
        if (packetId == 0x03) {
            connection.state = ConnectionState.PLAY;
            sendPlayPackets(connection);
        }
    }

    private void sendConfigurationPackets(Connection connection) {
        ByteArrayOutputStream features = new ByteArrayOutputStream();
        MinecraftCodec.writeVarInt(features, 0);
        queue(connection, CONFIG_C_FEATURE_FLAGS, features);

        ByteArrayOutputStream knownPacks = new ByteArrayOutputStream();
        MinecraftCodec.writeVarInt(knownPacks, 0);
        queue(connection, CONFIG_C_SELECT_KNOWN_PACKS, knownPacks);

        sendRegistryData(connection);

        queue(connection, CONFIG_C_TAGS, tagsPacket());

        queue(connection, CONFIG_C_FINISH_CONFIGURATION, new ByteArrayOutputStream());
    }

    private void sendRegistryData(Connection connection) {
        for (RegistryDataProvider.RegistryData registry : registryData) {
            queue(connection, CONFIG_C_REGISTRY_DATA, registryPacket(registry.id(), registry.entries()));
        }
    }

    private ByteArrayOutputStream tagsPacket() {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MinecraftCodec.writeVarInt(payload, tagsData.size());
        for (TagsDataProvider.TagRegistry registry : tagsData) {
            MinecraftCodec.writeString(payload, registry.registryId());
            MinecraftCodec.writeVarInt(payload, registry.tags().size());
            for (TagsDataProvider.TagEntry tag : registry.tags()) {
                MinecraftCodec.writeString(payload, tag.tagId());
                MinecraftCodec.writeVarInt(payload, tag.entryIds().size());
                for (Integer id : tag.entryIds()) {
                    MinecraftCodec.writeVarInt(payload, id);
                }
            }
        }
        return payload;
    }

    private ByteArrayOutputStream registryPacket(String registryId, List<RegistryDataProvider.RegistryEntry> entries) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MinecraftCodec.writeString(payload, registryId);
        MinecraftCodec.writeVarInt(payload, entries.size());
        for (RegistryDataProvider.RegistryEntry entry : entries) {
            MinecraftCodec.writeString(payload, entry.key());
            MinecraftCodec.writeBoolean(payload, entry.nbt() != null);
            if (entry.nbt() != null) {
                payload.writeBytes(entry.nbt());
            }
        }
        return payload;
    }

    private static void writeBitSet(ByteArrayOutputStream out, BitSet bitSet) {
        long[] longs = bitSet.toLongArray();
        MinecraftCodec.writeVarInt(out, longs.length);
        for (long value : longs) {
            MinecraftCodec.writeLong(out, value);
        }
    }

    public void run() throws Exception {
        Selector selector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open();
        server.configureBlocking(false);
        server.bind(new InetSocketAddress(host, port));
        server.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            selector.select(10);
            var iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                if (!key.isValid()) {
                    continue;
                }

                try {
                    if (key.isAcceptable()) {
                        accept(selector, server);
                    }
                    if (key.isReadable()) {
                        read(key);
                    }
                    if (key.isWritable()) {
                        write(key);
                    }
                } catch (Exception e) {
                    closeKey(key);
                }
            }

            sendKeepAlives(selector);
        }
    }

    private void sendKeepAlives(Selector selector) {
        long keepAliveId = System.currentTimeMillis();
        for (SelectionKey key : selector.keys()) {
            if (!key.isValid()) {
                continue;
            }
            Object attachment = key.attachment();
            if (!(attachment instanceof Connection connection)) {
                continue;
            }
            if (connection.state != ConnectionState.PLAY) {
                continue;
            }
            if (keepAliveId - connection.lastKeepAliveSentAt < KEEP_ALIVE_INTERVAL_MS) {
                continue;
            }

            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            MinecraftCodec.writeLong(payload, keepAliveId);
            queue(connection, PLAY_C_KEEP_ALIVE, payload);
            connection.lastKeepAliveSentAt = keepAliveId;
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }
    }

    private void sendPlayPackets(Connection connection) {
        ByteArrayOutputStream login = new ByteArrayOutputStream();
        MinecraftCodec.writeInt(login, 1);
        MinecraftCodec.writeBoolean(login, hardcore);

        MinecraftCodec.writeVarInt(login, 1);
        MinecraftCodec.writeString(login, "minecraft:overworld");

        MinecraftCodec.writeVarInt(login, 1);
        MinecraftCodec.writeVarInt(login, 8);
        MinecraftCodec.writeVarInt(login, 8);
        MinecraftCodec.writeBoolean(login, false);
        MinecraftCodec.writeBoolean(login, true);
        MinecraftCodec.writeBoolean(login, false);

        MinecraftCodec.writeVarInt(login, 0);
        MinecraftCodec.writeString(login, "minecraft:overworld");
        MinecraftCodec.writeLong(login, 0L);
        MinecraftCodec.writeByte(login, 0);
        MinecraftCodec.writeByte(login, 0xFF);
        MinecraftCodec.writeBoolean(login, false);
        MinecraftCodec.writeBoolean(login, true);
        MinecraftCodec.writeBoolean(login, false);
        MinecraftCodec.writeVarInt(login, 0);
        MinecraftCodec.writeVarInt(login, 63);

        MinecraftCodec.writeBoolean(login, false);

        queue(connection, PLAY_C_LOGIN, login);

        ByteArrayOutputStream abilities = new ByteArrayOutputStream();
        MinecraftCodec.writeByte(abilities, 0x00);
        MinecraftCodec.writeFloat(abilities, 0.05f);
        MinecraftCodec.writeFloat(abilities, 0.10f);
        queue(connection, PLAY_C_PLAYER_ABILITIES, abilities);

        ByteArrayOutputStream heldItem = new ByteArrayOutputStream();
        MinecraftCodec.writeVarInt(heldItem, 0);
        queue(connection, PLAY_C_SET_HELD_ITEM, heldItem);

        ByteArrayOutputStream health = new ByteArrayOutputStream();
        MinecraftCodec.writeFloat(health, 20.0f);
        MinecraftCodec.writeVarInt(health, 20);
        MinecraftCodec.writeFloat(health, 5.0f);
        queue(connection, PLAY_C_SET_HEALTH, health);

        ByteArrayOutputStream experience = new ByteArrayOutputStream();
        MinecraftCodec.writeFloat(experience, 0.0f);
        MinecraftCodec.writeVarInt(experience, 0);
        MinecraftCodec.writeVarInt(experience, 0);
        queue(connection, PLAY_C_SET_EXPERIENCE, experience);

        ByteArrayOutputStream time = new ByteArrayOutputStream();
        MinecraftCodec.writeLong(time, 0L);
        MinecraftCodec.writeLong(time, 6000L);
        MinecraftCodec.writeBoolean(time, true);
        queue(connection, PLAY_C_SET_TIME, time);

        ByteArrayOutputStream brandPayload = new ByteArrayOutputStream();
        MinecraftCodec.writeString(brandPayload, "minecraft:brand");
        ByteArrayOutputStream brandData = new ByteArrayOutputStream();
        MinecraftCodec.writeString(brandData, "Kimmoke");
        brandPayload.writeBytes(brandData.toByteArray());
        queue(connection, PLAY_C_CUSTOM_PAYLOAD, brandPayload);

        ByteArrayOutputStream viewDistance = new ByteArrayOutputStream();
        MinecraftCodec.writeVarInt(viewDistance, 8);
        queue(connection, PLAY_C_UPDATE_VIEW_DISTANCE, viewDistance);

        ByteArrayOutputStream viewPos = new ByteArrayOutputStream();
        MinecraftCodec.writeVarInt(viewPos, 0);
        MinecraftCodec.writeVarInt(viewPos, 0);
        queue(connection, PLAY_C_UPDATE_VIEW_POSITION, viewPos);

        ByteArrayOutputStream spawnPos = new ByteArrayOutputStream();
        MinecraftCodec.writeString(spawnPos, "minecraft:overworld");
        MinecraftCodec.writePosition(spawnPos, (int) Math.floor(spawnPosition.x()), (int) Math.floor(spawnPosition.y()), (int) Math.floor(spawnPosition.z()));
        MinecraftCodec.writeFloat(spawnPos, spawnPosition.yaw());
        MinecraftCodec.writeFloat(spawnPos, spawnPosition.pitch());
        queue(connection, PLAY_C_SPAWN_POSITION, spawnPos);

        ByteArrayOutputStream waitingChunks = new ByteArrayOutputStream();
        MinecraftCodec.writeByte(waitingChunks, 13);
        MinecraftCodec.writeFloat(waitingChunks, 0f);
        queue(connection, PLAY_C_GAME_EVENT, waitingChunks);

        ByteArrayOutputStream playerPos = new ByteArrayOutputStream();
        MinecraftCodec.writeVarInt(playerPos, 1);
        MinecraftCodec.writeDouble(playerPos, spawnPosition.x());
        MinecraftCodec.writeDouble(playerPos, spawnPosition.y());
        MinecraftCodec.writeDouble(playerPos, spawnPosition.z());
        MinecraftCodec.writeDouble(playerPos, 0.0);
        MinecraftCodec.writeDouble(playerPos, 0.0);
        MinecraftCodec.writeDouble(playerPos, 0.0);
        MinecraftCodec.writeFloat(playerPos, spawnPosition.yaw());
        MinecraftCodec.writeFloat(playerPos, spawnPosition.pitch());
        MinecraftCodec.writeInt(playerPos, 0);
        queue(connection, PLAY_C_POSITION, playerPos);

        queue(connection, PLAY_C_CHUNK_BATCH_START, new ByteArrayOutputStream());
        ByteArrayOutputStream finished = new ByteArrayOutputStream();
        MinecraftCodec.writeVarInt(finished, 0);
        queue(connection, PLAY_C_CHUNK_BATCH_FINISHED, finished);
    }

    private void queue(Connection connection, int packetId, ByteArrayOutputStream payload) {
        connection.writeQueue.add(MinecraftCodec.framedPacket(packetId, payload));
    }

    private void closeAfterFlush(SelectionKey key, Connection connection) {
        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        connection.closeAfterFlush = true;
    }

    private void closeConnection(Connection connection) {
        connection.state = ConnectionState.CLOSED;
        try {
            connection.channel.close();
        } catch (Exception ignored) {
        }
    }

    private void closeKey(SelectionKey key) {
        try {
            Object attachment = key.attachment();
            if (attachment instanceof Connection connection) {
                connection.channel.close();
            }
            key.cancel();
        } catch (Exception ignored) {
        }
    }

    private enum ConnectionState {
        HANDSHAKE,
        STATUS,
        LOGIN,
        CONFIGURATION,
        PLAY,
        CLOSED
    }

    private static final class Connection {
        private final SocketChannel channel;
        private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(1 << 20);
        private final Deque<ByteBuffer> writeQueue = new ArrayDeque<>();
        private final UUID fallbackUuid = new UUID(new Random().nextLong(), new Random().nextLong());

        private ConnectionState state = ConnectionState.HANDSHAKE;
        private String username = "Player";
        private UUID uuid = fallbackUuid;
        private int velocityQueryId = 0;
        private boolean closeAfterFlush = false;
        private long lastKeepAliveSentAt = 0L;

        private Connection(SocketChannel channel) {
            this.channel = channel;
        }
    }
}