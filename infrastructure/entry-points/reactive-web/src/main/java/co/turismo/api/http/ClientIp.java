package co.turismo.api.http;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetSocketAddress;
import java.util.regex.Pattern;

public final class ClientIp {

    private ClientIp() {}

    private static final Pattern COMMA_SPLIT = Pattern.compile("\\s*,\\s*");

    /** Resuelve la IP del cliente respetando X-Forwarded-For / X-Real-IP y evitando NPE. */
    public static String resolve(ServerHttpRequest req) {
        // 1) Cabeceras de proxy (primer hop)
        String xff = headerFirst(req, "X-Forwarded-For");
        if (isUsable(xff)) return xff;

        String xri = headerFirst(req, "X-Real-IP");
        if (isUsable(xri)) return xri;

        // 2) RemoteAddress seguro (sin forzar InetAddress)
        InetSocketAddress ra = req.getRemoteAddress();
        if (ra == null) return "0.0.0.0";

        if (ra.getAddress() != null) {
            return sanitize(ra.getAddress().getHostAddress());
        }
        // hostString no requiere resolución y evita: getAddress() == null
        return sanitize(ra.getHostString());
    }

    private static String headerFirst(ServerHttpRequest req, String name) {
        String v = req.getHeaders().getFirst(name);
        if (v == null || v.isBlank()) return null;
        // XFF puede venir como "cliente, proxy1, proxy2"
        String first = COMMA_SPLIT.split(v)[0].trim();
        return sanitize(first);
    }

    private static boolean isUsable(String ip) {
        return ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip);
    }

    /** Limpia brackets IPv6 y zona (%eth0) si apareciera. */
    private static String sanitize(String ip) {
        if (ip == null) return null;
        if (ip.startsWith("[") && ip.endsWith("]")) {
            ip = ip.substring(1, ip.length() - 1);
        }
        int zone = ip.indexOf('%'); // ej: fe80::1%eth0
        if (zone > 0) ip = ip.substring(0, zone);
        return ip;
    }
}
