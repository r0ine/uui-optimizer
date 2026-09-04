package dev.r0ine.uuiopt.net;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public final class PacketIds {

    private PacketIds() {}

    /** Clientbound play packet name (without namespace) to numeric id, read from the running server. */
    public static Map<String, Integer> clientbound() throws ReflectiveOperationException {
        Class<?> protocols = Class.forName("net.minecraft.network.protocol.game.GameProtocols");
        Object template = protocols.getField("CLIENTBOUND_TEMPLATE").get(null);
        Class<?> unbound = Class.forName("net.minecraft.network.ProtocolInfo$Unbound");
        Class<?> visitor = Class.forName("net.minecraft.network.ProtocolInfo$Unbound$PacketVisitor");

        Map<String, Integer> ids = new HashMap<>();
        Object collector = Proxy.newProxyInstance(visitor.getClassLoader(), new Class<?>[]{visitor}, (proxy, method, args) -> {
            if (method.getName().equals("accept")) {
                Object location = args[0].getClass().getMethod("id").invoke(args[0]);
                String name = location.toString();
                ids.put(name.substring(name.indexOf(':') + 1), (Integer) args[1]);
            }
            return null;
        });
        unbound.getMethod("listPackets", visitor).invoke(template, collector);
        return ids;
    }
}
