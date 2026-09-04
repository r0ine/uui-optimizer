package dev.r0ine.uuiopt.filter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VarIntTest {

    @Test
    void readsValuesAcrossLengths() {
        int[] values = {0, 1, 127, 128, 300, 16_384, 2_000_000_000, Integer.MAX_VALUE, -1};
        int[] lengths = {1, 1, 1, 2, 2, 3, 5, 5, 5};
        for (int i = 0; i < values.length; i++) {
            ByteBuf buf = Unpooled.buffer();
            Packets.varInt(buf, values[i]);
            long packed = VarInt.peek(buf, 0);
            assertEquals(values[i], VarInt.value(packed));
            assertEquals(lengths[i], VarInt.length(packed));
        }
    }

    @Test
    void peekLeavesReaderIndexAlone() {
        ByteBuf buf = Unpooled.buffer();
        Packets.varInt(buf, 300);
        VarInt.peek(buf, 0);
        assertEquals(0, buf.readerIndex());
    }

    @Test
    void honoursTheStartOffset() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(0x55);
        Packets.varInt(buf, 129);
        assertEquals(129, VarInt.value(VarInt.peek(buf, 1)));
    }

    @Test
    void truncatedInputIsRejected() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(0x80);
        buf.writeByte(0x80);
        assertTrue(VarInt.peek(buf, 0) < 0);
        assertTrue(VarInt.peek(Unpooled.buffer(), 0) < 0);
    }

    @Test
    void overlongInputIsRejected() {
        ByteBuf buf = Unpooled.buffer();
        for (int i = 0; i < 6; i++) buf.writeByte(0x80);
        assertTrue(VarInt.peek(buf, 0) < 0);
    }
}
