
package pt.isec.pd.g39.servidor;

import com.google.gson.Gson;


import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ShutDownHandle extends Thread {

    private final String MULTICAST_IP ;
    private static final int SHUTDOWN_PORT = 5000;
    private final Gson gson = new Gson();
    private volatile boolean running = true;

    public ShutDownHandle(String multicastLocalIp) {
        this.MULTICAST_IP = multicastLocalIp;
        super("ShutdownHandler");
        setDaemon(false);
    }

    @Override
    public void run() {
        try {

            MulticastSocket socket = new MulticastSocket(SHUTDOWN_PORT);
            socket.joinGroup(InetAddress.getByName(MULTICAST_IP));
            socket.setSoTimeout(2000);

            System.out.println("[SHUTDOWN-HANDLE] A escutar comandos SHUTDOWN via multicast "
                    + MULTICAST_IP + ":" + SHUTDOWN_PORT);

            byte[] buffer = new byte[1024];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    processMessage(msg, packet.getAddress().getHostAddress());

                } catch (SocketTimeoutException e) {
                    // Timeout normal - continua a esperar
                }
            }

            socket.leaveGroup(InetAddress.getByName(MULTICAST_IP));
            socket.close();

        } catch (Exception e) {
            System.err.println("[SHUTDOWN-HANDLE] Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processMessage(String msg, String senderIp) {
        try {
            Map<?, ?> json = gson.fromJson(msg, Map.class);
            String type = (String) json.get("type");

            if ("SHUTDOWN".equals(type)) {
                System.out.println("[SHUTDOWN-HANDLE] ⚠️ Comando SHUTDOWN recebido de " + senderIp);
                handleShutdown();
            }
            // Ignorar outros tipos (como HEARTBEAT do HeartbeatManager)

        } catch (Exception e) {
            // Ignorar mensagens mal formatadas
        }
    }

    private void handleShutdown() {
        // Parar de receber mensagens
        running = false;

        // Executar shutdown em thread separada
        new Thread(() -> {
            try {
                System.out.println("[SHUTDOWN] A fechar recursos do servidor...");

                // Pequeno delay para garantir que a mensagem foi impressa
                Thread.sleep(100);

                System.out.println("[SHUTDOWN] Servidor encerrado com sucesso.");
                System.exit(0);

            } catch (Exception e) {
                System.err.println("[SHUTDOWN] Erro durante shutdown: " + e.getMessage());
                System.exit(1);
            }
        }, "ShutdownExecutor").start();
    }

    public void stopHandler() {
        running = false;
    }
}