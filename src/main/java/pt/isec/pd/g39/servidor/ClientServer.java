package pt.isec.pd.g39.servidor;

import java.net.ServerSocket;
import java.net.Socket;

public class ClientServer {

    public static void start(ServerSocket serverSocket) {

        new Thread(() -> {

            System.out.println("[TCP] Servidor de clientes ativo no porto " + serverSocket.getLocalPort());

            while (true) {
                try {
                    Socket client = serverSocket.accept();
                    new ClientHandler(client).start();

                } catch (Exception e) {
                    System.err.println("[TCP] Erro ao aceitar cliente: " + e.getMessage());
                }
            }

        }).start();
    }
}
