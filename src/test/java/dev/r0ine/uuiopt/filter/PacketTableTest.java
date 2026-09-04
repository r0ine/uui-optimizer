package dev.r0ine.uuiopt.filter;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketTableTest {

    @Test
    void resolvesKnownNamesToRules() {
        PacketTable table = Packets.table();
        assertEquals("set_entity_data", table.rule(Packets.ENTITY_DATA).packet());
        assertEquals(Kind.STOP_SOUND, table.rule(Packets.STOP_SOUND).kind());
        assertEquals(Kind.SPAWN, table.rule(Packets.ADD_ENTITY).kind());
    }

    @Test
    void unknownIdsMapToNothing() {
        PacketTable table = Packets.table();
        assertNull(table.rule(Packets.UNRELATED));
        assertNull(table.rule(-1));
        assertNull(table.rule(100_000));
    }

    @Test
    void namesTheProtocolDoesNotHaveAreReported() {
        PacketTable table = PacketTable.of(Map.of("set_entity_data", 5));
        assertEquals(1, table.rules().size());
        assertTrue(table.missing().contains("stop_sound"));
        assertTrue(table.missing().contains("teleport_entity"));
    }

    @Test
    void absoluteRulesGetDistinctCacheSlots() {
        PacketTable table = Packets.table();
        Set<Integer> seen = new HashSet<>();
        for (Rule rule : table.rules()) {
            if (rule.kind() == Kind.ABSOLUTE) assertTrue(seen.add(rule.absIndex()));
            else assertEquals(-1, rule.absIndex());
        }
        assertEquals(table.absoluteCount(), seen.size());
    }

    @Test
    void everyFilteringRuleHasAConfigKey() {
        for (Rule rule : Packets.table().rules()) {
            if (rule.filters()) assertTrue(PacketTable.configKeys().contains(rule.key()), rule.packet());
        }
    }
}
