package dev.r0ine.uuiopt.filter;

import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-connection filter state. Everything here runs on the channel's event loop,
 * so nothing is synchronized.
 */
public final class DedupeEngine {

    private static final int MAX_TRACKED_ENTITIES = 50_000;
    private static final int MAX_REMOVE_BATCH = 10_000;

    private final PacketTable table;
    private final Stats stats;
    private final Map<Integer, EntityState> entities = new HashMap<>();
    private final Map<Long, Long> sounds = new HashMap<>();
    private int generation = Integer.MIN_VALUE;

    public DedupeEngine(PacketTable table, Stats stats) {
        this.table = table;
        this.stats = stats;
    }

    public boolean shouldDrop(ByteBuf buf, Policy policy, long nowMs) {
        if (generation != policy.generation()) {
            entities.clear();
            sounds.clear();
            generation = policy.generation();
        }

        int start = buf.readerIndex();
        long head = VarInt.peek(buf, start);
        if (head < 0) return false;
        Rule rule = table.rule(VarInt.value(head));
        if (rule == null) return false;
        int body = start + VarInt.length(head);

        switch (rule.kind()) {
            case SPAWN -> forget(buf, body);
            case REMOVE -> forgetAll(buf, body);
            case RESET -> {
                entities.clear();
                sounds.clear();
            }
            case ABSOLUTE -> {
                return absolute(rule, buf, body, policy);
            }
            case MOVE_POS, MOVE_POS_ROT, MOVE_ROT -> {
                return move(rule, buf, body, policy);
            }
            case STOP_SOUND -> {
                return stopSound(rule, buf, body, policy, nowMs);
            }
        }
        return false;
    }

    public int trackedEntities() {
        return entities.size();
    }

    private boolean absolute(Rule rule, ByteBuf buf, int body, Policy policy) {
        if (!policy.enabled(rule)) return false;
        long e = VarInt.peek(buf, body);
        if (e < 0) return false;
        int eid = VarInt.value(e);
        if (!policy.inScope(eid)) return false;

        stats.recordSeen(rule.slot());
        int len = buf.writerIndex() - body;
        EntityState state = state(eid);
        byte[] last = state.last[rule.absIndex()];
        if (last != null && sameBytes(buf, body, len, last)) {
            stats.recordDrop(rule.slot(), buf.readableBytes());
            return true;
        }
        byte[] copy = new byte[len];
        buf.getBytes(body, copy);
        state.last[rule.absIndex()] = copy;
        if (rule.resetsRotation()) state.rotationKnown = false;
        return false;
    }

    private boolean move(Rule rule, ByteBuf buf, int body, Policy policy) {
        if (!policy.enabled(rule)) return false;
        long e = VarInt.peek(buf, body);
        if (e < 0) return false;
        int eid = VarInt.value(e);
        if (!policy.inScope(eid)) return false;

        boolean hasDelta = rule.kind() != Kind.MOVE_ROT;
        boolean hasRotation = rule.kind() != Kind.MOVE_POS;
        int pos = body + VarInt.length(e);
        int need = (hasDelta ? 6 : 0) + (hasRotation ? 2 : 0) + 1;
        if (buf.writerIndex() - pos < need) return false;

        boolean noDelta = !hasDelta
                || (buf.getShort(pos) == 0 && buf.getShort(pos + 2) == 0 && buf.getShort(pos + 4) == 0);
        int cursor = pos + (hasDelta ? 6 : 0);
        byte yaw = 0;
        byte pitch = 0;
        if (hasRotation) {
            yaw = buf.getByte(cursor);
            pitch = buf.getByte(cursor + 1);
            cursor += 2;
        }
        byte ground = buf.getByte(cursor);

        stats.recordSeen(rule.slot());
        EntityState state = state(eid);
        boolean sameGround = state.groundKnown && state.ground == ground;
        boolean sameRotation = !hasRotation || (state.rotationKnown && state.yaw == yaw && state.pitch == pitch);
        if (noDelta && sameGround && sameRotation) {
            stats.recordDrop(rule.slot(), buf.readableBytes());
            return true;
        }

        state.ground = ground;
        state.groundKnown = true;
        if (hasRotation) {
            state.yaw = yaw;
            state.pitch = pitch;
            state.rotationKnown = true;
        }
        return false;
    }

    private boolean stopSound(Rule rule, ByteBuf buf, int body, Policy policy, long nowMs) {
        if (!policy.enabled(rule)) return false;
        stats.recordSeen(rule.slot());
        long interval = policy.stopSoundIntervalMs();
        long key = fingerprint(buf, body, buf.writerIndex() - body);
        Long previous = sounds.get(key);
        if (previous != null && nowMs - previous < interval) {
            stats.recordDrop(rule.slot(), buf.readableBytes());
            return true;
        }
        sounds.put(key, nowMs);
        if (sounds.size() > 256) sounds.values().removeIf(t -> nowMs - t >= interval);
        return false;
    }

    private EntityState state(int eid) {
        if (entities.size() >= MAX_TRACKED_ENTITIES) entities.clear();
        return entities.computeIfAbsent(eid, k -> new EntityState(table.absoluteCount()));
    }

    private void forget(ByteBuf buf, int body) {
        long e = VarInt.peek(buf, body);
        if (e >= 0) entities.remove(VarInt.value(e));
    }

    private void forgetAll(ByteBuf buf, int body) {
        long c = VarInt.peek(buf, body);
        if (c < 0) return;
        int count = Math.min(VarInt.value(c), MAX_REMOVE_BATCH);
        int pos = body + VarInt.length(c);
        for (int i = 0; i < count; i++) {
            long e = VarInt.peek(buf, pos);
            if (e < 0) return;
            entities.remove(VarInt.value(e));
            pos += VarInt.length(e);
        }
    }

    private static boolean sameBytes(ByteBuf buf, int from, int len, byte[] cached) {
        if (cached.length != len) return false;
        for (int i = 0; i < len; i++) {
            if (buf.getByte(from + i) != cached[i]) return false;
        }
        return true;
    }

    private static long fingerprint(ByteBuf buf, int from, int len) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < len; i++) {
            h ^= buf.getByte(from + i) & 0xFF;
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static final class EntityState {
        final byte[][] last;
        byte yaw;
        byte pitch;
        byte ground;
        boolean rotationKnown;
        boolean groundKnown;

        EntityState(int absoluteKinds) {
            last = new byte[absoluteKinds][];
        }
    }
}
