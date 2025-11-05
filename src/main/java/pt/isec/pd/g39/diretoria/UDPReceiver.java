package pt.isec.pd.g39.diretoria;

import com.google.gson.Gson;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class UDPReceiver extends Thread {
    private final int porto;
    private final DirecaoServerList direcao;
    private final Gson gson = new Gson();

    public UDPReceiver(int porto, DirecaoServerList direcao) {
        this.porto = porto;
        this.direcao = direcao;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(porto)) {
            socket.setSoTimeout(2000); // 2 s
            System.out.println("📥 A escutar UDP...");

            byte[] buffer = new byte[1024];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                    processMessage(socket, packet, msg);
                } catch (SocketTimeoutException e) {
                    // verifica servidores mortos periodicamente
                    direcao.removeDeadServers();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processMessage(DatagramSocket socket, DatagramPacket packet, String msg) throws Exception {
        var json = gson.fromJson(msg, java.util.Map.class);
        String type = (String) json.get("type");

        if ("REGISTER_SERVER".equals(type)) {
            int tcpClients = ((Double) json.get("tcp_clients")).intValue();
            int tcpPeers = ((Double) json.get("tcp_peers")).intValue();
            direcao.register(packet.getAddress(), tcpClients, tcpPeers);

            ServidorInfo principal = direcao.getPrincipal();
            if (principal != null) {
                String reply = gson.toJson(Map.of(
                        "type", "REGISTERED",
                        "primary_ip", principal.ip.getHostAddress(),
                        "primary_tcp_clients", principal.tcpPortClients
                ));
                byte[] data = reply.getBytes(StandardCharsets.UTF_8);
                DatagramPacket response = new DatagramPacket(
                        data, data.length, packet.getAddress(), packet.getPort());
                socket.send(response);
            }
        }
        else if ("HEARTBEAT".equals(type)) {
            int tcpClients = ((Double) json.get("tcp_clients")).intValue();
            direcao.updateHeartbeat(packet.getAddress(), tcpClients);
        }
        else if ("GET_PRIMARY".equals(type)) {
            ServidorInfo principal = direcao.getPrincipal();
            String reply = (principal == null)
                    ? gson.toJson(Map.of("type", "NO_SERVER"))
                    : gson.toJson(Map.of(
                    "type", "PRIMARY_INFO",
                    "ip", principal.ip.getHostAddress(),
                    "tcp_port_clients", principal.tcpPortClients));
            byte[] data = reply.getBytes(StandardCharsets.UTF_8);
            DatagramPacket response = new DatagramPacket(
                    data, data.length, packet.getAddress(), packet.getPort());
            socket.send(response);
        }
    }
}
