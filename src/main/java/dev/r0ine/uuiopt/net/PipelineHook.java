package dev.r0ine.uuiopt.net;

import dev.r0ine.uuiopt.Settings;
import dev.r0ine.uuiopt.filter.DedupeEngine;
import dev.r0ine.uuiopt.filter.PacketTable;
import dev.r0ine.uuiopt.filter.Stats;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Keeps the raw filter between the compression/framing handlers and whatever writes
 * encoded packets into the pipeline. PacketEvents' silent sends start at its own encoder's
 * context, so the filter has to sit on the head side of that encoder to see them.
 */
public final class PipelineHook {

    public static final String NAME = "uuiopt-raw";

    private static final String PACKETEVENTS_PREFIX = "pe-encoder";
    private static final String[] HEAD_SIDE_LIMITS = {"encrypt", "prepender", "compress"};
    private static final String[] TAIL_SIDE_FALLBACK = {"encoder", "outbound_config", "packet_handler"};

    private final PacketTable table;
    private final Stats stats;
    private final Supplier<Settings> settings;
    private final Logger log;

    public PipelineHook(PacketTable table, Stats stats, Supplier<Settings> settings, Logger log) {
        this.table = table;
        this.stats = stats;
        this.settings = settings;
        this.log = log;
    }

    public void install(Channel ch) {
        ch.eventLoop().execute(() -> {
            ChannelPipeline pipeline = ch.pipeline();
            if (pipeline.get(NAME) != null) return;
            place(ch, new RawFilter(new DedupeEngine(table, stats), settings, log));
        });
    }

    public void remove(Channel ch) {
        ch.eventLoop().execute(() -> {
            if (ch.pipeline().get(NAME) != null) ch.pipeline().remove(NAME);
        });
    }

    public void ensure(Channel ch) {
        ch.eventLoop().execute(() -> {
            if (!(ch.pipeline().get(NAME) instanceof RawFilter current)) return;
            List<String> names = ch.pipeline().names();
            int self = names.indexOf(NAME);
            int ceiling = ceiling(names);
            if (self > floor(names) && (ceiling < 0 || self < ceiling)) return;
            ch.pipeline().remove(NAME);
            place(ch, current.copy());
        });
    }

    public static List<String> names(Channel ch) {
        return ch.pipeline().names();
    }

    private void place(Channel ch, RawFilter filter) {
        List<String> names = ch.pipeline().names();
        int ceiling = ceiling(names);
        if (ceiling < 0 || ceiling <= floor(names)) {
            log.warning("No usable pipeline slot for " + ch.remoteAddress() + ": " + names);
            return;
        }
        ch.pipeline().addBefore(names.get(ceiling), NAME, filter);
    }

    private static int ceiling(List<String> names) {
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).startsWith(PACKETEVENTS_PREFIX)) return i;
        }
        for (String candidate : TAIL_SIDE_FALLBACK) {
            int i = names.indexOf(candidate);
            if (i >= 0) return i;
        }
        return -1;
    }

    private static int floor(List<String> names) {
        int floor = -1;
        for (String limit : HEAD_SIDE_LIMITS) floor = Math.max(floor, names.indexOf(limit));
        return floor;
    }
}
