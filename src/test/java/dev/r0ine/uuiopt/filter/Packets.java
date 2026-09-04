package dev.r0ine.uuiopt.filter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.Map;

final class Packets {

    static final int ENTITY_DATA = 1;
    static final int HEAD_ROTATION = 2;
    static final int MOVE_POS = 3;
    static final int MOVE_POS_ROT = 4;
    static final int MOVE_ROT = 5;
    static final int STOP_SOUND = 6;
    static final int ADD_ENTITY = 7;
    static final int REMOVE_ENTITIES = 8;
    static final int RESPAWN = 9;
    static final int TELEPORT = 10;
    static final int UNRELATED = 40;

    static final int UUI_ENTITY = 2_000_000_123;
    static final int VANILLA_ENTITY = 77;

    private Packets() {}

    static PacketTable table() {
        return PacketTable.of(Map.of(
                "set_entity_data", ENTITY_DATA,
                "rotate_head", HEAD_ROTATION,
                "move_entity_pos", MOVE_POS,
                "move_entity_pos_rot", MOVE_POS_ROT,
                "move_entity_rot", MOVE_ROT,
                "stop_sound", STOP_SOUND,
                "add_entity", ADD_ENTITY,
                "remove_entities", REMOVE_ENTITIES,
                "respawn", RESPAWN,
                "teleport_entity", TELEPORT));
    }

    static ByteBuf entity(int id, int eid, int... payload) {
        ByteBuf buf = Unpooled.buffer();
        varInt(buf, id);
        varInt(buf, eid);
        for (int b : payload) buf.writeByte(b);
        return buf;
    }

    static ByteBuf move(int id, int eid, int dx, int dy, int dz, int... tail) {
        ByteBuf buf = Unpooled.buffer();
        varInt(buf, id);
        varInt(buf, eid);
        if (id != MOVE_ROT) {
            buf.writeShort(dx).writeShort(dy).writeShort(dz);
        }
        for (int b : tail) buf.writeByte(b);
        return buf;
    }

    static ByteBuf removal(int... eids) {
        ByteBuf buf = Unpooled.buffer();
        varInt(buf, REMOVE_ENTITIES);
        varInt(buf, eids.length);
        for (int eid : eids) varInt(buf, eid);
        return buf;
    }

    static ByteBuf bare(int id, int... payload) {
        ByteBuf buf = Unpooled.buffer();
        varInt(buf, id);
        for (int b : payload) buf.writeByte(b);
        return buf;
    }

    static void varInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }
}
