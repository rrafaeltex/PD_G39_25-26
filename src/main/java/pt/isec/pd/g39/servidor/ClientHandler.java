package pt.isec.pd.g39.servidor;

import java.net.Socket;

public class ClientHandler extends Thread {

    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            System.out.println("[CLIENT] Cliente ligado: " + socket.getInetAddress());

            // Aqui processas pedidos do cliente (ex: SQL GET, etc)
            // InputStream in = socket.getInputStream();
            // OutputStream out = socket.getOutputStream();

        } catch (Exception e) {
            System.err.println("[CLIENT] Erro no handler: " + e.getMessage());
        }
    }
}
