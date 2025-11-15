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
    }

    public synchronized List<ServidorInfo> getServidores() {
        return new ArrayList<>(servidores);
    }
}
