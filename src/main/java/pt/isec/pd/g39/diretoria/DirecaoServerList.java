package pt.isec.pd.g39.diretoria;

import java.net.InetAddress;
import java.util.*;

public class DirecaoServerList {
    private final List<ServidorInfo> servidores = new ArrayList<>();

    public synchronized void register(InetAddress ip, int tcpClients, int tcpPeers) {

        for(ServidorInfo s : servidores){
            if(s.ip.equals(ip) && s.tcpPortClients == tcpClients){
                System.out.println("Servidor já registado, ignorado: "+s);
                return;
            }
        }

        ServidorInfo s = new ServidorInfo(ip, tcpClients, tcpPeers);
        if(servidores.isEmpty()){
            s.isPrimary = true;
        }
        servidores.add(s);
        System.out.println("Servidor registado: " + s);

    }


    public synchronized void unregister(InetAddress ip, int tcpClients) {
        servidores.removeIf(s -> s.ip.equals(ip) && s.tcpPortClients == tcpClients);
        if(servidores.get(0).isPrimary == false){servidores.get(0).isPrimary = true;}
        System.out.println(" Servidor removido: " + ip.getHostAddress() + ":" + tcpClients);
    }

    public synchronized ServidorInfo getPrincipal() {
        if (servidores.isEmpty()) return null;
        return servidores.get(0);
    }

    public synchronized boolean updateHeartbeat(InetAddress ip, int tcpClients) {
        for (ServidorInfo s : servidores) {
            if (s.ip.equals(ip) && s.tcpPortClients == tcpClients) {
                s.lastHeartbeat = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    public synchronized void removeDeadServers() {
        long now = System.currentTimeMillis();
        servidores.removeIf(s -> now - s.lastHeartbeat > 17000);
        if(!servidores.isEmpty()){
            servidores.get(0).isPrimary = true;
        }

    }

    public synchronized List<ServidorInfo> getServidores() {
        return new ArrayList<>(servidores);
    }

    // Imprime todos os dados atuais dos servidores registados
    public synchronized void imprimirServidores() {
        if (servidores.isEmpty()) {
            System.out.println("[Direcao] Nao existem servidores registados.");
            return;
        }
        System.out.println("[Direcao] ===== Lista de Servidores (" + servidores.size() + ") =====");
        long now = System.currentTimeMillis();
        int idx = 1;
        for (ServidorInfo s : servidores) {
            long agoMs = now - s.lastHeartbeat;
            System.out.printf(
                    Locale.ROOT,
                    "%d) IP=%s | TCP_Clients=%d | TCP_Peers=%d | Primary=%s | Heartbeat=%d ms atras%n",
                    idx++,
                    s.ip.getHostAddress(),
                    s.tcpPortClients,
                    s.tcpPortPeers,
                    s.isPrimary,
                    agoMs
            );
        }
        System.out.println("[Direcao] =====================================");
    }
}
