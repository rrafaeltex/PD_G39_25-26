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
            socket.setSoTimeout(2000);
            System.out.println("A escutar UDP...");

            byte[] buffer = new byte[1024];

            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                    processMessage(socket, packet, msg);

                } catch (SocketTimeoutException e) {
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

        // ------------------------------------------
        // REGISTAR SERVIDOR
        // ------------------------------------------
        if ("REGISTER_SERVER".equals(type)) {
            int tcpClients = ((Double) json.get("tcp_clients")).intValue();
            int tcpPeers = ((Double) json.get("tcp_peers")).intValue();

            direcao.register(packet.getAddress(), tcpClients, tcpPeers);

            ServidorInfo principal = direcao.getPrincipal();

            String reply = gson.toJson(Map.of(
                    "type", "REGISTERED",
                    "primary_ip", principal.ip.getHostAddress(),
                    "primary_tcp_clients", principal.tcpPortClients
            ));

            send(socket, reply, packet);

            return;
        }

        // ------------------------------------------
        // UNREGISTER
        // ------------------------------------------
        if ("UNREGISTER".equals(type)) {
            int tcpClients = ((Double) json.get("tcp_clients")).intValue();
            direcao.unregister(packet.getAddress(), tcpClients);
            return;
        }

        // ------------------------------------------
        // HEARTBEAT
        // ------------------------------------------
        if ("HEARTBEAT".equals(type)) {
            int tcpClients = ((Double) json.get("tcp_clients")).intValue();

            boolean ok = direcao.updateHeartbeat(packet.getAddress(), tcpClients);
            if (!ok) {
                // ignorar heartbeat de servidor não registado
                return;
            }

            // Responder ao heartbeat com informação do principal atual
            ServidorInfo principal = direcao.getPrincipal();

            String reply = gson.toJson(Map.of(
                    "type", "HEARTBEAT_REPLY",
                    "primary_ip", principal.ip.getHostAddress(),
                    "primary_tcp_clients", principal.tcpPortClients
            ));

            send(socket, reply, packet);

            return;
        }

        // ------------------------------------------
        // GET_PRIMARY → pedido do cliente
        // ------------------------------------------
        if ("GET_PRIMARY".equals(type)) {
            ServidorInfo principal = direcao.getPrincipal();

            String reply = (principal == null)
                    ? gson.toJson(Map.of("type", "NO_SERVER"))
                    : gson.toJson(Map.of(
                    "type", "PRIMARY_INFO",
                    "ip", principal.ip.getHostAddress(),
                    "tcp_port_clients", principal.tcpPortClients
            ));

            send(socket, reply, packet);
        }
    }

    private void send(DatagramSocket socket, String msg, DatagramPacket packet) throws Exception {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        DatagramPacket response = new DatagramPacket(
                data, data.length, packet.getAddress(), packet.getPort()
        );
        socket.send(response);
    }
}
