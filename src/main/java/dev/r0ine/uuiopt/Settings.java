package dev.r0ine.uuiopt;

import dev.r0ine.uuiopt.filter.PacketTable;
import dev.r0ine.uuiopt.filter.Policy;
import dev.r0ine.uuiopt.filter.Rule;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public final class Settings implements Policy {

    private final boolean enabled;
    private final boolean paused;
    private final boolean uuiOnly;
    private final int minEntityId;
    private final long stopSoundIntervalMs;
    private final boolean skipMismatchedClients;
    private final int statsLogSeconds;
    private final Map<String, Boolean> rules;
    private final int generation;

    private Settings(boolean enabled, boolean paused, boolean uuiOnly, int minEntityId, long stopSoundIntervalMs,
                     boolean skipMismatchedClients, int statsLogSeconds, Map<String, Boolean> rules, int generation) {
        this.enabled = enabled;
        this.paused = paused;
        this.uuiOnly = uuiOnly;
        this.minEntityId = minEntityId;
        this.stopSoundIntervalMs = stopSoundIntervalMs;
        this.skipMismatchedClients = skipMismatchedClients;
        this.statsLogSeconds = statsLogSeconds;
        this.rules = rules;
        this.generation = generation;
    }

    public static Settings load(FileConfiguration cfg, boolean paused, int generation) {
        Map<String, Boolean> rules = new HashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("rules");
        for (String key : PacketTable.configKeys()) {
            rules.put(key, section == null || section.getBoolean(key, true));
        }
        return new Settings(
                cfg.getBoolean("enabled", true),
                paused,
                !"all".equalsIgnoreCase(cfg.getString("entity-scope", "uui")),
                cfg.getInt("min-entity-id", 2_000_000_000),
                Math.max(0, cfg.getLong("stop-sound-interval-ms", 500)),
                cfg.getBoolean("skip-mismatched-clients", true),
                Math.max(0, cfg.getInt("stats-log-interval", 60)),
                Map.copyOf(rules),
                generation);
    }

    public Settings withPaused(boolean value) {
        return new Settings(enabled, value, uuiOnly, minEntityId, stopSoundIntervalMs,
                skipMismatchedClients, statsLogSeconds, rules, generation + 1);
    }

    public boolean paused() {
        return paused;
    }

    public boolean skipMismatchedClients() {
        return skipMismatchedClients;
    }

    public int statsLogSeconds() {
        return statsLogSeconds;
    }

    public boolean uuiOnly() {
        return uuiOnly;
    }

    @Override
    public boolean active() {
        return enabled && !paused;
    }

    @Override
    public boolean enabled(Rule rule) {
        return rule.key() == null || rules.getOrDefault(rule.key(), true);
    }

    @Override
    public boolean inScope(int entityId) {
        return !uuiOnly || entityId >= minEntityId;
    }

    @Override
    public long stopSoundIntervalMs() {
        return stopSoundIntervalMs;
    }

    @Override
    public int generation() {
        return generation;
    }
}
