package dev.r0ine.uuiopt.filter;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static dev.r0ine.uuiopt.filter.Packets.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedupeEngineTest {

    private static final class TestPolicy implements Policy {
        boolean active = true;
        boolean uuiOnly = true;
        boolean metadataEnabled = true;
        long interval = 500;
        int generation = 1;

        @Override public boolean active() { return active; }
        @Override public boolean enabled(Rule rule) { return rule.id() != ENTITY_DATA || metadataEnabled; }
        @Override public boolean inScope(int eid) { return !uuiOnly || eid >= 2_000_000_000; }
        @Override public long stopSoundIntervalMs() { return interval; }
        @Override public int generation() { return generation; }
    }

    private TestPolicy policy;
    private Stats stats;
    private DedupeEngine engine;
    private long now;

    @BeforeEach
    void setUp() {
        PacketTable table = table();
        policy = new TestPolicy();
        stats = new Stats(table.rules().size());
        engine = new DedupeEngine(table, stats);
        now = 10_000;
    }

    private boolean drops(ByteBuf buf) {
        return engine.shouldDrop(buf, policy, now);
    }

    @Test
    void identicalMetadataIsDroppedAfterTheFirstSend() {
        assertFalse(drops(entity(ENTITY_DATA, UUI_ENTITY, 1, 2, 3)));
        assertTrue(drops(entity(ENTITY_DATA, UUI_ENTITY, 1, 2, 3)));
        assertTrue(drops(entity(ENTITY_DATA, UUI_ENTITY, 1, 2, 3)));
    }

    @Test
    void changedMetadataGoesThroughAndBecomesTheNewBaseline() {
        drops(entity(ENTITY_DATA, UUI_ENTITY, 1, 2, 3));
        assertFalse(drops(entity(ENTITY_DATA, UUI_ENTITY, 9, 9, 9)));
        assertTrue(drops(entity(ENTITY_DATA, UUI_ENTITY, 9, 9, 9)));
        assertFalse(drops(entity(ENTITY_DATA, UUI_ENTITY, 1, 2, 3)));
    }

    @Test
    void entitiesDoNotShareState() {
        drops(entity(ENTITY_DATA, UUI_ENTITY, 1));
        assertFalse(drops(entity(ENTITY_DATA, UUI_ENTITY + 1, 1)));
    }

    @Test
    void packetKindsDoNotShareState() {
        drops(entity(ENTITY_DATA, UUI_ENTITY, 5));
        assertFalse(drops(entity(HEAD_ROTATION, UUI_ENTITY, 5)));
    }

    @Test
    void spawnInvalidatesCachedState() {
        drops(entity(ENTITY_DATA, UUI_ENTITY, 4, 4));
        assertFalse(drops(entity(ADD_ENTITY, UUI_ENTITY, 0)));
        assertFalse(drops(entity(ENTITY_DATA, UUI_ENTITY, 4, 4)));
    }

    @Test
    void removalInvalidatesEveryListedEntity() {
        drops(entity(ENTITY_DATA, UUI_ENTITY, 4));
        drops(entity(ENTITY_DATA, UUI_ENTITY + 1, 4));
        drops(entity(ENTITY_DATA, UUI_ENTITY + 2, 4));
        assertFalse(drops(removal(UUI_ENTITY, UUI_ENTITY + 1)));
        assertFalse(drops(entity(ENTITY_DATA, UUI_ENTITY, 4)));
        assertFalse(drops(entity(ENTITY_DATA, UUI_ENTITY + 1, 4)));
        assertTrue(drops(entity(ENTITY_DATA, UUI_ENTITY + 2, 4)));
    }

    @Test
    void respawnClearsEverything() {
        drops(entity(ENTITY_DATA, UUI_ENTITY, 4));
        assertFalse(drops(bare(RESPAWN, 0, 0)));
        assertFalse(drops(entity(ENTITY_DATA, UUI_ENTITY, 4)));
    }

    @Test
    void vanillaEntitiesAreLeftAloneInUuiScope() {
        assertFalse(drops(entity(ENTITY_DATA, VANILLA_ENTITY, 1)));
        assertFalse(drops(entity(ENTITY_DATA, VANILLA_ENTITY, 1)));
    }

    @Test
    void vanillaEntitiesAreDeduplicatedInAllScope() {
        policy.uuiOnly = false;
        assertFalse(drops(entity(ENTITY_DATA, VANILLA_ENTITY, 1)));
        assertTrue(drops(entity(ENTITY_DATA, VANILLA_ENTITY, 1)));
    }

    @Test
    void disabledRuleNeverDrops() {
        policy.metadataEnabled = false;
        drops(entity(ENTITY_DATA, UUI_ENTITY, 1));
        assertFalse(drops(entity(ENTITY_DATA, UUI_ENTITY, 1)));
    }

    @Test
    void generationChangeForgetsWhatWasSent() {
        drops(entity(ENTITY_DATA, UUI_ENTITY, 1));
        policy.generation++;
        assertFalse(drops(entity(ENTITY_DATA, UUI_ENTITY, 1)));
        assertTrue(drops(entity(ENTITY_DATA, UUI_ENTITY, 1)));
    }

    @Test
    void unrelatedAndMalformedPacketsPassUntouched() {
        assertFalse(drops(bare(UNRELATED, 1, 2)));
        assertFalse(drops(bare(UNRELATED, 1, 2)));
        assertFalse(drops(io.netty.buffer.Unpooled.buffer()));
        ByteBuf truncated = io.netty.buffer.Unpooled.buffer();
        truncated.writeByte(ENTITY_DATA);
        truncated.writeByte(0x80);
        assertFalse(drops(truncated));
    }

    @Test
    void firstZeroDeltaMoveIsSentBecauseTheClientStateIsUnknown() {
        assertFalse(drops(move(MOVE_POS, UUI_ENTITY, 0, 0, 0, 1)));
        assertTrue(drops(move(MOVE_POS, UUI_ENTITY, 0, 0, 0, 1)));
    }

    @Test
    void movesThatActuallyMoveAreNeverDropped() {
        drops(move(MOVE_POS, UUI_ENTITY, 0, 0, 0, 1));
        assertFalse(drops(move(MOVE_POS, UUI_ENTITY, 0, 5, 0, 1)));
        assertFalse(drops(move(MOVE_POS, UUI_ENTITY, 0, 5, 0, 1)));
        assertFalse(drops(move(MOVE_POS, UUI_ENTITY, 0, 0, -1, 1)));
    }

    @Test
    void groundFlagChangeCountsAsInformation() {
        drops(move(MOVE_POS, UUI_ENTITY, 0, 0, 0, 1));
        assertFalse(drops(move(MOVE_POS, UUI_ENTITY, 0, 0, 0, 0)));
        assertTrue(drops(move(MOVE_POS, UUI_ENTITY, 0, 0, 0, 0)));
    }

    @Test
    void rotationOnlyMoveIsDroppedWhenNothingChanged() {
        assertFalse(drops(move(MOVE_ROT, UUI_ENTITY, 0, 0, 0, 10, 20, 1)));
        assertTrue(drops(move(MOVE_ROT, UUI_ENTITY, 0, 0, 0, 10, 20, 1)));
        assertFalse(drops(move(MOVE_ROT, UUI_ENTITY, 0, 0, 0, 11, 20, 1)));
    }

    @Test
    void zeroDeltaMoveWithNewRotationIsKept() {
        drops(move(MOVE_POS_ROT, UUI_ENTITY, 0, 0, 0, 10, 20, 1));
        assertFalse(drops(move(MOVE_POS_ROT, UUI_ENTITY, 0, 0, 0, 30, 20, 1)));
        assertTrue(drops(move(MOVE_POS_ROT, UUI_ENTITY, 0, 0, 0, 30, 20, 1)));
    }

    @Test
    void rotationLearnedFromOneMoveKindCarriesToTheOther() {
        drops(move(MOVE_ROT, UUI_ENTITY, 0, 0, 0, 10, 20, 1));
        assertTrue(drops(move(MOVE_POS_ROT, UUI_ENTITY, 0, 0, 0, 10, 20, 1)));
    }

    @Test
    void teleportForgetsTheKnownRotation() {
        drops(move(MOVE_POS_ROT, UUI_ENTITY, 0, 0, 0, 10, 20, 1));
        assertFalse(drops(entity(TELEPORT, UUI_ENTITY, 7, 7)));
        assertFalse(drops(move(MOVE_POS_ROT, UUI_ENTITY, 0, 0, 0, 10, 20, 1)));
    }

    @Test
    void truncatedMovePassesThrough() {
        assertFalse(drops(move(MOVE_POS, UUI_ENTITY, 0, 0, 0)));
    }

    @Test
    void stopSoundIsThrottledPerPayload() {
        assertFalse(drops(bare(STOP_SOUND, 3, 1, 2)));
        now += 100;
        assertTrue(drops(bare(STOP_SOUND, 3, 1, 2)));
        assertFalse(drops(bare(STOP_SOUND, 3, 9, 9)));
    }

    @Test
    void stopSoundPassesAgainOnceTheWindowHasElapsed() {
        drops(bare(STOP_SOUND, 3, 1, 2));
        now += 499;
        assertTrue(drops(bare(STOP_SOUND, 3, 1, 2)));
        now += 1;
        assertFalse(drops(bare(STOP_SOUND, 3, 1, 2)));
    }

    @Test
    void countersTrackSeenAndDropped() {
        PacketTable table = table();
        int slot = table.rule(ENTITY_DATA).slot();
        ByteBuf first = entity(ENTITY_DATA, UUI_ENTITY, 1, 2, 3);
        int size = first.readableBytes();
        drops(first);
        drops(entity(ENTITY_DATA, UUI_ENTITY, 1, 2, 3));
        drops(entity(ENTITY_DATA, UUI_ENTITY, 1, 2, 3));
        assertEquals(3, stats.seenCount(slot));
        assertEquals(2, stats.droppedCount(slot));
        assertEquals(2L * size, stats.savedBytes(slot));
    }

    @Test
    void engineDoesNotConsumeTheBuffer() {
        ByteBuf buf = entity(ENTITY_DATA, UUI_ENTITY, 1, 2, 3);
        int readable = buf.readableBytes();
        drops(buf);
        assertEquals(0, buf.readerIndex());
        assertEquals(readable, buf.readableBytes());
    }
}
