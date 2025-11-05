package pt.isec.pd.g39.diretoria;

import java.net.InetAddress;
import java.util.*;

public class DirecaoServerList {
    private final List<ServidorInfo> servidores = new ArrayList<>();

    public synchronized void register(InetAddress ip, int tcpClients, int tcpPeers) {
        ServidorInfo s = new ServidorInfo(ip, tcpClients, tcpPeers);
        servidores.add(s);
        System.out.println("Servidor registado: " + s);
    }

    public synchronized ServidorInfo getPrincipal() {
        if (servidores.isEmpty()) return null;
        return servidores.get(0);
    }

    public synchronized void updateHeartbeat(InetAddress ip, int tcpClients) {
        for (ServidorInfo s : servidores) {
            if (s.ip.equals(ip) && s.tcpPortClients == tcpClients) {
                s.lastHeartbeat = System.currentTimeMillis();
                return;
            }
        }
    }

    public synchronized void removeDeadServers() {
        long now = System.currentTimeMillis();
        servidores.removeIf(s -> now - s.lastHeartbeat > 17000);
    }

    public synchronized List<ServidorInfo> getServidores() {
        return new ArrayList<>(servidores);
    }
}
