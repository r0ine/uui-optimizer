package dev.r0ine.uuiopt.net;

import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ChannelLocator {

    private final Method getHandle;
    private final Field listener;
    private final Field connection;
    private final Field channel;

    private ChannelLocator(Method getHandle, Field listener, Field connection, Field channel) {
        this.getHandle = getHandle;
        this.listener = listener;
        this.connection = connection;
        this.channel = channel;
    }

    public static ChannelLocator bind() throws ReflectiveOperationException {
        Method handle = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");
        Field listener = field(handle.getReturnType(), "ServerGamePacketListenerImpl");
        Field connection = field(listener.getType(), "Connection");
        Field channel = field(connection.getType(), "Channel");
        return new ChannelLocator(handle, listener, connection, channel);
    }

    public Channel channelOf(Player player) {
        try {
            Object handle = getHandle.invoke(player);
            return (Channel) channel.get(connection.get(listener.get(handle)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Field field(Class<?> from, String typeName) throws NoSuchFieldException {
        for (Class<?> c = from; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().getSimpleName().equals(typeName)) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        throw new NoSuchFieldException(typeName + " in " + from.getName());
    }
}
