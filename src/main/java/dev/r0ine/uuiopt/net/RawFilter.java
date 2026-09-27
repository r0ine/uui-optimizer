package dev.r0ine.uuiopt.net;

import dev.r0ine.uuiopt.Settings;
import dev.r0ine.uuiopt.filter.DedupeEngine;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.function.Supplier;
import java.util.logging.Logger;

final class RawFilter extends ChannelDuplexHandler {

    private final DedupeEngine engine;
    private final Supplier<Settings> settings;
    private final Logger log;
    private boolean failed;

    RawFilter(DedupeEngine engine, Supplier<Settings> settings, Logger log) {
        this.engine = engine;
        this.settings = settings;
        this.log = log;
    }

    RawFilter copy() {
        return new RawFilter(engine, settings, log);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!failed && msg instanceof ByteBuf buf && buf.isReadable()) {
            Settings current = settings.get();
            if (current.active()) {
                try {
                    if (engine.shouldDrop(buf, current, System.currentTimeMillis())) {
                        buf.release();
                        promise.setSuccess();
                        return;
                    }
                } catch (RuntimeException e) {
                    failed = true;
                    log.warning("Filter disabled for " + ctx.channel().remoteAddress() + ": " + e);
                }
            }
        }
        ctx.write(msg, promise);
    }
}
