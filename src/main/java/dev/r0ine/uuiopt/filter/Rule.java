package dev.r0ine.uuiopt.filter;

public record Rule(String packet, String key, Kind kind, boolean resetsRotation, int id, int slot, int absIndex) {

    public boolean filters() {
        return kind != Kind.SPAWN && kind != Kind.REMOVE && kind != Kind.RESET;
    }
}
