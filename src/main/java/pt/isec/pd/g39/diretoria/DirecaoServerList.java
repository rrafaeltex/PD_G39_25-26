package pt.isec.pd.g39.diretoria;

import com.google.gson.Gson;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class DirecaoServerList {
    private static final List<ServidorInfo> servidores = new ArrayList<>();

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
        if(!servidores.getFirst().isPrimary){servidores.getFirst().isPrimary = true;}
        System.out.println(" Servidor removido: " + ip.getHostAddress() + ":" + tcpClients);
    }

    public synchronized ServidorInfo getPrincipal() {
        if (servidores.isEmpty()) return null;
        return servidores.getFirst();
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
            servidores.getFirst().isPrimary = true;
        }

    }

    public static synchronized List<ServidorInfo> getServidores() {
        return new ArrayList<>(servidores);
    }

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

    public static synchronized void ShutdownToAllServers() {
        try (DatagramSocket socket = new DatagramSocket()) {
            Gson gson = new Gson();

            String multicastIp = "230.30.30.30";
            int multicastPort = 5000;

            String msg = gson.toJson(Map.of("type", "SHUTDOWN"));
            byte[] data = msg.getBytes();

            var servidores = getServidores();

            if (servidores.isEmpty()) {
                System.out.println("[SHUTDOWN] Nenhum servidor registado.");
                return;
            }

            System.out.println("[SHUTDOWN] A enviar comando SHUTDOWN via multicast para "
                    + servidores.size() + " servidor(es)...");


            DatagramPacket packet = new DatagramPacket(
                    data, data.length,
                    InetAddress.getByName(multicastIp), multicastPort
            );

            socket.send(packet);
            System.out.println("[SHUTDOWN] ✉️  Enviado via multicast " + multicastIp + ":" + multicastPort);

            // Dar tempo para os servidores processarem
            Thread.sleep(1500);

        } catch (Exception e) {
            System.err.println("[SHUTDOWN] Erro ao enviar comandos: " + e.getMessage());
        }
    }


}