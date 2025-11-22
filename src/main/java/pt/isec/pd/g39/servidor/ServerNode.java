package pt.isec.pd.g39.servidor;

import com.google.gson.Gson;
import pt.isec.pd.g39.servidor.database.Database;
import pt.isec.pd.g39.servidor.database.DatabaseManager;
import pt.isec.pd.g39.servidor.database.DatabaseSync;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;

public class ServerNode {

    private final String directoryIp;
    private final int directoryPort;
    private final String dbFolder;
    private final String multicastLocalIp;

    private final Gson gson = new Gson();

    private static volatile boolean isPrimary = false;

    private int clientPort;
    private int peerPort;

    private String primaryIp;
    private int primaryTcpClients;
    private int primaryTcpPeers;

    private ServerSocket clientServerSocket;
    private static ServerSocket peerServerSocket;

    public ServerNode(String directoryIp, int directoryPort, String dbFolder, String multicastLocalIp) {
        this.directoryIp = directoryIp;
        this.directoryPort = directoryPort;
        this.dbFolder = dbFolder;
        this.multicastLocalIp = multicastLocalIp;
    }

    public static boolean isPrimary() {
        return isPrimary;
    }

    public static synchronized void becomePrimary() {
        if (!isPrimary) {
            isPrimary = true;
            System.out.println("🌟 Agora sou o servidor PRINCIPAL!");


            DatabaseSync.startPeerServer(peerServerSocket);

            // Notificar os clientes se necessário (opcional aqui)
        }
    }

    public void  start() throws IOException {

        initializeServerSockets();

        try {
            register(directoryIp, directoryPort, clientPort, peerPort);
        } catch (IOException e) {
            System.err.println("[ERRO] Falha no registo: " + e.getMessage());
            return;
        }

        if (isPrimary) {

            String dbFile = DatabaseManager.trataDatabaseFile(dbFolder);
            Database.configure(dbFile);

            try {
                Database.initializeIfNeeded();
            } catch (SQLException e) {
                System.err.println("Erro ao inicializar BD: " + e.getMessage());
                return;
            }

            System.out.println("Servidor principal com BD: " + dbFile);

            DatabaseSync.startPeerServer(peerServerSocket);

        } else {

            System.out.println("Sou secundário — sincronizando BD via TCP...");

            boolean ok = DatabaseSync.downloadDatabase(primaryIp, primaryTcpPeers, dbFolder);

            if (!ok) {
                System.err.println("Falha ao sincronizar BD. Informando diretoria...");
                informarDiretoriaFalha();
                return;
            }

            ClientServer.start(clientServerSocket);

            System.out.println("BD sincronizada com sucesso.");
        }


        ClientServer.start(clientServerSocket);

        try {
            String myIp = "127.0.0.1";
            HeartbeatManager.init(directoryIp, directoryPort, myIp, clientPort, peerPort);
            HeartbeatManager.start();
        } catch (Exception e) {
            System.err.println("[HB] Erro ao iniciar HeartbeatManager: " + e.getMessage());
        }
    }


    private void initializeServerSockets() throws IOException {
        clientServerSocket = new ServerSocket(0);
        clientPort = clientServerSocket.getLocalPort();

        peerServerSocket = new ServerSocket(0);
        peerPort = peerServerSocket.getLocalPort();
    }


    public void register(String directoryIp, int directoryPort, int clientPort, int peerPort) throws IOException {

        try (DatagramSocket socket = new DatagramSocket()) {

            socket.setSoTimeout(5000);

            String msg = gson.toJson(Map.of(
                    "type", "REGISTER_SERVER",
                    "tcp_clients", clientPort,
                    "tcp_peers", peerPort
            ));

            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName(directoryIp);

            DatagramPacket packet = new DatagramPacket(data, data.length, addr, directoryPort);
            socket.send(packet);

            byte[] buffer = new byte[1024];
            DatagramPacket resposta = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(resposta);
            } catch (SocketTimeoutException e) {
                System.err.println("[ERRO] Diretoria não respondeu ao registo.");
                System.exit(1);
                return;
            }

            String respostaMsg = new String(resposta.getData(), 0, resposta.getLength(), StandardCharsets.UTF_8);

            var json = gson.fromJson(respostaMsg, Map.class);

            if ("REGISTERED".equals(json.get("type"))) {

                primaryIp = (String) json.get("primary_ip");
                primaryTcpClients = ((Double) json.get("primary_tcp_clients")).intValue();
                primaryTcpPeers = ((Double) json.get("primary_tcp_peers")).intValue();

                if (primaryIp.equals(directoryIp) && primaryTcpClients == clientPort) {
                    becomePrimary();
                } else {
                    isPrimary = false;
                }

                System.out.println(isPrimary ?
                        "Registado como servidor PRINCIPAL" :
                        "Registado como servidor SECUNDÁRIO");

                return;
            }

            System.err.println("[ERRO] Resposta inesperada da diretoria.");
            System.exit(1);

        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao registar no serviço de diretoria: " + e.getMessage());
            System.exit(1);
        }
    }



    private void informarDiretoriaFalha() {

        try (DatagramSocket socket = new DatagramSocket()) {

            String msg = gson.toJson(Map.of(
                    "type", "SERVER_FAIL",
                    "server_ip", InetAddress.getLocalHost().getHostAddress(),
                    "reason", "sync_failed"
            ));

            byte[] data = msg.getBytes(StandardCharsets.UTF_8);

            DatagramPacket packet = new DatagramPacket(
                    data, data.length,
                    InetAddress.getByName(directoryIp), directoryPort
            );

            socket.send(packet);

        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao informar diretoria: " + e.getMessage());
        }
    }

}
