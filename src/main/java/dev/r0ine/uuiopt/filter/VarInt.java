package dev.r0ine.uuiopt.filter;

import io.netty.buffer.ByteBuf;

public final class VarInt {

    private VarInt() {}

    /**
     * Reads a VarInt without moving the reader index.
     * Low 32 bits hold the value, high 32 bits the encoded length; negative when truncated.
     */
    public static long peek(ByteBuf buf, int index) {
        int end = buf.writerIndex();
        int value = 0;
        for (int i = 0; i < 5; i++) {
            if (index + i >= end) return -1;
            byte b = buf.getByte(index + i);
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) return ((long) (i + 1) << 32) | (value & 0xFFFFFFFFL);
        }
        return -1;
    }

    public static int value(long packed) {
        return (int) packed;
    }

    public static int length(long packed) {
        return (int) (packed >>> 32);
    }
}
