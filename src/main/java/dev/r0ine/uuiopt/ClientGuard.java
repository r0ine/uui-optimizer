package dev.r0ine.uuiopt;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Packet ids come from the server's own protocol table. A client translated by ViaVersion
 * or ProtocolSupport sees different ids, so those connections are left alone.
 */
final class ClientGuard {

    private final int serverProtocol;
    private final Object viaApi;
    private final Method viaVersionOf;
    private final boolean protocolSupport;

    private ClientGuard(int serverProtocol, Object viaApi, Method viaVersionOf, boolean protocolSupport) {
        this.serverProtocol = serverProtocol;
        this.viaApi = viaApi;
        this.viaVersionOf = viaVersionOf;
        this.protocolSupport = protocolSupport;
    }

    static ClientGuard create(Logger log) {
        int protocol = -1;
        try {
            protocol = (int) Class.forName("net.minecraft.SharedConstants").getMethod("getProtocolVersion").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("Could not read server protocol version: " + e);
        }

        Object api = null;
        Method lookup = null;
        if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
            try {
                Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
                api = via.getMethod("getAPI").invoke(null);
                lookup = api.getClass().getMethod("getPlayerVersion", UUID.class);
                log.info("ViaVersion detected, translated clients will be skipped.");
            } catch (ReflectiveOperationException | RuntimeException e) {
                log.warning("ViaVersion present but its API is unreachable, all clients will be skipped: " + e);
                api = null;
                lookup = null;
            }
        }
        boolean ps = Bukkit.getPluginManager().isPluginEnabled("ProtocolSupport");
        if (ps) log.info("ProtocolSupport detected, all clients will be skipped.");
        return new ClientGuard(protocol, api, lookup, ps);
    }

    boolean compatible(Player player) {
        if (serverProtocol < 0 || protocolSupport) return false;
        boolean viaInstalled = Bukkit.getPluginManager().isPluginEnabled("ViaVersion");
        if (!viaInstalled) return true;
        if (viaApi == null) return false;
        try {
            return (int) viaVersionOf.invoke(viaApi, player.getUniqueId()) == serverProtocol;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
