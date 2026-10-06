package dev.r0ine.uuiopt;

import dev.r0ine.uuiopt.filter.Rule;
import dev.r0ine.uuiopt.net.PipelineHook;
import io.netty.channel.Channel;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class UuiCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("stats", "ids", "pipeline", "reload", "reset", "toggle");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final UUIOptimizer plugin;

    UuiCommand(UUIOptimizer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "stats";
        switch (sub) {
            case "stats" -> stats(sender);
            case "ids" -> ids(sender);
            case "pipeline" -> pipeline(sender, args.length > 1 ? args[1] : null);
            case "reload" -> {
                plugin.reload();
                send(sender, "<green>Config reloaded.");
            }
            case "reset" -> {
                plugin.stats().reset();
                send(sender, "<green>Counters reset.");
            }
            case "toggle" -> {
                boolean nowPaused = !plugin.settings().paused();
                plugin.setPaused(nowPaused);
                send(sender, nowPaused ? "<yellow>Filtering paused." : "<green>Filtering resumed.");
            }
            default -> send(sender, "<yellow>/" + label + " [stats|ids|pipeline [player]|reload|reset|toggle]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBCOMMANDS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pipeline")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }

    private void stats(CommandSender sender) {
        Settings settings = plugin.settings();
        String state = !settings.active() ? "<red>off" : "<green>on";
        send(sender, "<gold>UUIOptimizer <gray>filtering " + state + "<gray>, " + plugin.hookedCount()
                + " players hooked, scope <white>" + (settings.uuiOnly() ? "uui" : "all"));
        send(sender, "<dark_gray>packet                    seen     dropped  saved");
        for (Rule rule : plugin.table().rules()) {
            if (!rule.filters()) continue;
            long seen = plugin.stats().seenCount(rule.slot());
            long dropped = plugin.stats().droppedCount(rule.slot());
            long saved = plugin.stats().savedBytes(rule.slot());
            String percent = seen == 0 ? "-" : String.format("%.0f%%", 100.0 * dropped / seen);
            send(sender, String.format("<white>%-24s <gray>%,9d %,9d <green>%4s <dark_gray>%,d KiB",
                    rule.packet(), seen, dropped, percent, saved / 1024));
        }
    }

    private void ids(CommandSender sender) {
        for (Rule rule : plugin.table().rules()) {
            send(sender, String.format("<white>%-24s <gray>id <white>%d <dark_gray>%s", rule.packet(), rule.id(), rule.kind()));
        }
        for (String name : plugin.table().missing()) send(sender, "<red>missing: " + name);
    }

    private void pipeline(CommandSender sender, String name) {
        Player target = name != null ? Bukkit.getPlayerExact(name) : sender instanceof Player p ? p : null;
        if (target == null) {
            send(sender, "<red>Name an online player.");
            return;
        }
        Channel channel = plugin.channelOf(target);
        if (channel == null) {
            send(sender, "<red>No channel for " + target.getName());
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String handler : PipelineHook.names(channel)) {
            parts.add(handler.equals(PipelineHook.NAME) ? "<green>" + handler + "</green>" : handler);
        }
        send(sender, "<gray>head <dark_gray>></dark_gray> " + String.join(" <dark_gray>></dark_gray> ", parts) + " <dark_gray>></dark_gray> tail");
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(MINI.deserialize(message));
    }
}
