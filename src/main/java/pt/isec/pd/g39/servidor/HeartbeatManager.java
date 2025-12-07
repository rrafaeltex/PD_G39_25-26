package pt.isec.pd.g39.servidor;

import com.google.gson.Gson;
import pt.isec.pd.g39.servidor.database.Database;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HeartbeatManager {

    private static String MULTICAST_IP;
    private static final int MULTICAST_PORT = 3030;

    private static final Gson gson = new Gson();

    private static String directoryIp;
    private static int directoryPort;
    private static String localIp;
    private static int clientPort;
    private static int peerPort;

    private static volatile String primaryIp;
    private static volatile int primaryTcpClients;

    private static volatile boolean started = false;

    public static void init(String dirIp, int dirPort, String localIpAddr, int clientP, int peerP, String multicastLocalIp) {
        directoryIp = dirIp;
        directoryPort = dirPort;
        localIp = localIpAddr;
        clientPort = clientP;
        peerPort = peerP;
        MULTICAST_IP = multicastLocalIp;
    }

    public static void start() {
        if (started)
            return;
        started = true;

        new Thread(HeartbeatManager::heartbeatLoop, "HeartbeatSender").start();

        new Thread(HeartbeatManager::multicastReceiverLoop, "HeartbeatReceiver").start();
    }

    public static void sendHeartbeatWithSql(String sql, int newVersion) {
        try {
            sendMulticastHeartbeat(sql, newVersion);
        } catch (Exception e) {
            System.err.println("[HB] Erro ao enviar heartbeat com SQL: " + e.getMessage());
        }
    }

    private static void heartbeatLoop() {
        while (true) {
            try {
                int version = Database.getVersion();

                sendHeartbeatToDirectory();

                sendMulticastHeartbeat(null, version);

                System.out.println("[DEBUG] Sou principal? " + ServerNode.isPrimary());


                Thread.sleep(5000);
            } catch (Exception e) {
                System.err.println("[ERRO] " + e.getMessage());

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private static void sendHeartbeatToDirectory() throws Exception {
        if (directoryIp == null)
            return;

        String msg = gson.toJson(Map.of(
                "type", "HEARTBEAT",
                "tcp_clients", clientPort
        ));

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(1000);

            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(
                    data, data.length,
                    InetAddress.getByName(directoryIp), directoryPort
            );
            socket.send(packet);

            byte[] buffer = new byte[1024];
            DatagramPacket resposta = new DatagramPacket(buffer, buffer.length);
            socket.receive(resposta);

            String rmsg = new String(resposta.getData(), 0, resposta.getLength(), StandardCharsets.UTF_8);
            var json = gson.fromJson(rmsg, Map.class);

            if ("HEARTBEAT_REPLY".equals(json.get("type"))) {
                primaryIp = (String) json.get("primary_ip");
                primaryTcpClients = ((Double) json.get("primary_tcp_clients")).intValue();

                System.out.println("[HB] Principal atual: " + primaryIp + ":" + primaryTcpClients);

                boolean isNowPrimary =
                        localIp.equals(primaryIp) &&
                                clientPort == primaryTcpClients;

                if (isNowPrimary && !ServerNode.isPrimary()) {
                    System.out.println("[HB] Este servidor tornou-se o novo PRINCIPAL (info da diretoria)!");
                    ServerNode.becomePrimary();
                }
            }

        } catch (SocketTimeoutException e) {
            // sem resposta da diretoria -> ignoramos por agora
        }
    }

    private static void sendMulticastHeartbeat(String sql, int version) throws Exception {

        Map<String, Object> heartbeat = new HashMap<>();
        heartbeat.put("type", "HEARTBEAT");
        heartbeat.put("server_ip", localIp);
        heartbeat.put("db_version", version);
        heartbeat.put("client_port", clientPort);
        heartbeat.put("peer_port", peerPort);

        if (sql != null)
            heartbeat.put("sql", sql);

        String msg = gson.toJson(heartbeat);

        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(
                    data, data.length,
                    InetAddress.getByName(MULTICAST_IP), MULTICAST_PORT
            );
            socket.send(packet);
        }
    }


    private static void multicastReceiverLoop() {
        try (MulticastSocket mcast = new MulticastSocket(MULTICAST_PORT)) {

            mcast.joinGroup(InetAddress.getByName(MULTICAST_IP));
            System.out.println("[HB] A escutar heartbeats multicast em " + MULTICAST_IP + ":" + MULTICAST_PORT);

            byte[] buffer = new byte[4096];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                mcast.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                processMulticastHeartbeat(msg);
            }

        } catch (Exception e) {
            System.err.println("[HB-RECV] Erro no receiver multicast: " + e.getMessage());
        }
    }

    private static void processMulticastHeartbeat(String msg) {
        try {
            Map<?, ?> json = gson.fromJson(msg, Map.class);
            if (!"HEARTBEAT".equals(json.get("type")))
                return;

            String senderIp = (String) json.get("server_ip");
            if (senderIp == null)
                return;

            int senderClientPort = ((Double) json.get("client_port")).intValue();
            int remoteVersion = ((Double) json.get("db_version")).intValue();
            String sql = (String) json.get("sql");

            if (senderIp.equals(localIp) && senderClientPort == clientPort)
                return;

            boolean isPrimaryHeartbeat =
                    senderIp.equals(primaryIp) &&
                            senderClientPort == primaryTcpClients;

            boolean isNowPrimary =
                    localIp.equals(primaryIp) &&
                            clientPort == primaryTcpClients;

            if (isNowPrimary && !ServerNode.isPrimary()) {
                System.out.println("[HB] Este servidor tornou-se o novo PRINCIPAL!");
                ServerNode.becomePrimary();
            }

            if (!isPrimaryHeartbeat)
                return;

            int localVersion = Database.getVersion();

            if (sql == null) {
                // Sem SQL: se versões diferentes -> perda de sincronização
                if (remoteVersion != localVersion) {
                    System.err.println("[HB-RECV] Versão diferente da do principal (sem SQL). A terminar.");
                    System.exit(1);
                }
            } else {
                // Com SQL: versão deve ser == local + 1
                if (remoteVersion != localVersion + 1) {
                    System.err.println("[HB-RECV] Versão remota inválida. Esperado "
                            + (localVersion + 1) + ", recebido " + remoteVersion + ". A terminar.");
                    System.exit(1);
                }

                System.out.println("[HB-RECV] A aplicar SQL do principal via heartbeat.");
                Database.applyRemoteUpdate(sql, remoteVersion);
            }

        } catch (Exception e) {
            System.err.println("[HB-RECV] Erro ao processar heartbeat multicast: " + e.getMessage());
        }
    }



}
