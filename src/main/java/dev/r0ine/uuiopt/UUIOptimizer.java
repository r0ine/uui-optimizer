package dev.r0ine.uuiopt;

import dev.r0ine.uuiopt.filter.PacketTable;
import dev.r0ine.uuiopt.filter.Rule;
import dev.r0ine.uuiopt.filter.Stats;
import dev.r0ine.uuiopt.net.ChannelLocator;
import dev.r0ine.uuiopt.net.PacketIds;
import dev.r0ine.uuiopt.net.PipelineHook;
import io.netty.channel.Channel;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UUIOptimizer extends JavaPlugin implements Listener {

    private static final long HOOK_DELAY_TICKS = 5L;
    private static final long ENSURE_PERIOD_TICKS = 100L;

    private final Map<UUID, Channel> hooked = new ConcurrentHashMap<>();
    private volatile Settings settings;
    private PacketTable table;
    private Stats stats;
    private ChannelLocator locator;
    private ClientGuard guard;
    private PipelineHook hook;
    private BukkitTask logTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig(), false, 1);

        try {
            locator = ChannelLocator.bind();
            table = PacketTable.of(PacketIds.clientbound());
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().severe("Could not bind to the server internals, disabling: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!table.missing().isEmpty()) {
            getLogger().warning("Packets not present in this protocol, skipped: " + String.join(", ", table.missing()));
        }
        getLogger().info("Tracking " + table.rules().size() + " clientbound packet types.");

        stats = new Stats(table.rules().size());
        guard = ClientGuard.create(getLogger());
        hook = new PipelineHook(table, stats, () -> settings, getLogger());

        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand("uuiopt");
        if (command != null) {
            UuiCommand handler = new UuiCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        getServer().getOnlinePlayers().forEach(this::attach);
        getServer().getScheduler().runTaskTimer(this, this::repairPipelines, ENSURE_PERIOD_TICKS, ENSURE_PERIOD_TICKS);
        scheduleLog();
    }

    @Override
    public void onDisable() {
        if (hook == null) return;
        hooked.values().forEach(hook::remove);
        hooked.clear();
    }

    void reload() {
        reloadConfig();
        settings = Settings.load(getConfig(), settings.paused(), settings.generation() + 1);
        scheduleLog();
    }

    void setPaused(boolean paused) {
        settings = settings.withPaused(paused);
    }

    Settings settings() {
        return settings;
    }

    PacketTable table() {
        return table;
    }

    Stats stats() {
        return stats;
    }

    int hookedCount() {
        return hooked.size();
    }

    Channel channelOf(Player player) {
        return locator.channelOf(player);
    }

    private void attach(Player player) {
        if (hooked.containsKey(player.getUniqueId())) return;
        if (settings.skipMismatchedClients() && !guard.compatible(player)) return;
        Channel channel = locator.channelOf(player);
        if (channel == null) return;
        hooked.put(player.getUniqueId(), channel);
        hook.install(channel);
    }

    private void repairPipelines() {
        hooked.values().forEach(hook::ensure);
    }

    private void scheduleLog() {
        if (logTask != null) logTask.cancel();
        int seconds = settings.statsLogSeconds();
        if (seconds <= 0) {
            logTask = null;
            return;
        }
        long ticks = seconds * 20L;
        logTask = getServer().getScheduler().runTaskTimer(this, this::logSummary, ticks, ticks);
    }

    private void logSummary() {
        long seen = 0;
        long dropped = 0;
        long saved = 0;
        for (Rule rule : table.rules()) {
            if (!rule.filters()) continue;
            seen += stats.seenCount(rule.slot());
            dropped += stats.droppedCount(rule.slot());
            saved += stats.savedBytes(rule.slot());
        }
        if (seen == 0) return;
        getLogger().info(String.format("dropped %,d of %,d inspected packets (%.1f%%), %,d KiB saved, %d players",
                dropped, seen, 100.0 * dropped / seen, saved / 1024, hooked.size()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) attach(player);
        }, HOOK_DELAY_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Channel channel = hooked.remove(event.getPlayer().getUniqueId());
        if (channel != null) hook.remove(channel);
    }
}
