package dev.r0ine.uuiopt.filter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PacketTable {

    private record Def(String packet, String key, Kind kind, boolean resetsRotation) {}

    private static final List<Def> DEFS = List.of(
            new Def("set_entity_data", "entity-metadata", Kind.ABSOLUTE, false),
            new Def("rotate_head", "head-rotation", Kind.ABSOLUTE, false),
            new Def("set_entity_motion", "entity-velocity", Kind.ABSOLUTE, false),
            new Def("set_equipment", "equipment", Kind.ABSOLUTE, false),
            new Def("update_attributes", "attributes", Kind.ABSOLUTE, false),
            new Def("entity_position_sync", "position-sync", Kind.ABSOLUTE, true),
            new Def("teleport_entity", "teleport", Kind.ABSOLUTE, true),
            new Def("move_entity_pos", "relative-move", Kind.MOVE_POS, false),
            new Def("move_entity_pos_rot", "relative-move", Kind.MOVE_POS_ROT, false),
            new Def("move_entity_rot", "relative-move", Kind.MOVE_ROT, false),
            new Def("stop_sound", "stop-sound", Kind.STOP_SOUND, false),
            new Def("add_entity", null, Kind.SPAWN, false),
            new Def("remove_entities", null, Kind.REMOVE, false),
            new Def("respawn", null, Kind.RESET, false),
            new Def("login", null, Kind.RESET, false),
            new Def("start_configuration", null, Kind.RESET, false));

    private final Rule[] byId;
    private final List<Rule> rules;
    private final List<String> missing;
    private final int absoluteCount;

    private PacketTable(Rule[] byId, List<Rule> rules, List<String> missing, int absoluteCount) {
        this.byId = byId;
        this.rules = rules;
        this.missing = missing;
        this.absoluteCount = absoluteCount;
    }

    public static PacketTable of(Map<String, Integer> ids) {
        List<Rule> rules = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int abs = 0;
        int maxId = -1;
        for (Def def : DEFS) {
            Integer id = ids.get(def.packet());
            if (id == null) {
                missing.add(def.packet());
                continue;
            }
            int absIndex = def.kind() == Kind.ABSOLUTE ? abs++ : -1;
            rules.add(new Rule(def.packet(), def.key(), def.kind(), def.resetsRotation(), id, rules.size(), absIndex));
            maxId = Math.max(maxId, id);
        }
        Rule[] byId = new Rule[maxId + 1];
        for (Rule r : rules) byId[r.id()] = r;
        return new PacketTable(byId, List.copyOf(rules), List.copyOf(missing), abs);
    }

    public static Set<String> configKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Def def : DEFS) if (def.key() != null) keys.add(def.key());
        return keys;
    }

    public Rule rule(int id) {
        return id >= 0 && id < byId.length ? byId[id] : null;
    }

    public List<Rule> rules() {
        return rules;
    }

    public List<String> missing() {
        return missing;
    }

    public int absoluteCount() {
        return absoluteCount;
    }
}
