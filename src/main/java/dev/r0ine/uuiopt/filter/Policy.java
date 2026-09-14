package dev.r0ine.uuiopt.filter;

public interface Policy {

    boolean active();

    boolean enabled(Rule rule);

    boolean inScope(int entityId);

    long stopSoundIntervalMs();

    /** Bumped whenever the rules change; engines discard their caches when it moves. */
    int generation();
}
