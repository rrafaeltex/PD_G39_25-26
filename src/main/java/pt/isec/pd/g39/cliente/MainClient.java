package pt.isec.pd.g39.cliente;


import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

public class MainClient {

    // --- Configuração da Diretoria ---
    private static String directoryIp;
    private static int directoryPort;

    // --- Info do Servidor (descoberto) ---
    private static String serverIp;
    private static int serverPort;

    // --- Ferramentas de Comunicação ---
    private static Socket socket; // O "Telefone" TCP para o servidor
    private static PrintWriter out; // "Boca de Texto"
    private static BufferedReader in; // "Ouvido de Texto"
    private static final Gson gson = new Gson();
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        // 1. Ler os argumentos de arranque
        if (args.length != 2) {
            System.out.println("Uso: java MainClient <dirIp> <dirPort>");
            System.exit(1);
        }
        directoryIp = args[0];
        directoryPort = Integer.parseInt(args[1]);

        // 2. Tentar ligar-se ao sistema (ciclo de failover)
        while (true) {
            if (connectToSystem()) {
                // 3. Se a ligação for bem-sucedida, mostrar o menu principal
                runUI();
            }

            // Se o runUI() terminar (porque a ligação falhou),
            // esperamos 5 segundos e tentamos reconectar.
            System.err.println("Ligação perdida. A tentar reconectar em 5 segundos...");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Passo 1: Falar com a Diretoria (UDP) para encontrar o Servidor.
     * Tenta descobrir o IP/Porto do Servidor Principal.
     */
    private static boolean getServerAddress() {
        // Esta lógica é baseada no teu TestGetPrimary.java
        try (DatagramSocket udpSocket = new DatagramSocket()) {
            udpSocket.setSoTimeout(3000); // 3 segundos de timeout

            String msg = gson.toJson(Map.of("type", "GET_PRIMARY"));
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);

            DatagramPacket packet = new DatagramPacket(
                    data, data.length,
                    InetAddress.getByName(directoryIp), directoryPort
            );

            System.out.println("A contactar diretoria (" + directoryIp + ":" + directoryPort + ") para encontrar servidor...");
            udpSocket.send(packet);

            byte[] buffer = new byte[1024];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(response);

            String rmsg = new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8);
            Map<String, Object> json = gson.fromJson(rmsg, Map.class);

            if ("PRIMARY_INFO".equals(json.get("type"))) {
                serverIp = (String) json.get("ip");
                serverPort = ((Double) json.get("tcp_port_clients")).intValue();
                System.out.println("Servidor principal encontrado em: " + serverIp + ":" + serverPort);
                return true;
            } else {
                System.err.println("Nenhum servidor principal disponível.");
                return false;
            }

        } catch (Exception e) {
            System.err.println("Erro ao contactar diretoria: " + e.getMessage());
            return false;
        }
    }

    /**
     * Passo 2: Ligar-se ao Servidor (TCP).
     * Estabelece a ligação TCP e prepara os "assistentes de texto".
     */
    private static boolean connectToSystem() {
        try {
            // 1. Encontrar o servidor (UDP)
            if (!getServerAddress()) {
                return false;
            }

            // 2. Ligar-se ao servidor (TCP)
            System.out.println("A ligar ao servidor " + serverIp + ":" + serverPort + "...");
            socket = new Socket(serverIp, serverPort);

            // 3. Preparar os "assistentes de texto" (igual ao ClientHandler)
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            System.out.println("Ligado!");
            return true;

        } catch (Exception e) {
            System.err.println("Falha ao ligar ao servidor: " + e.getMessage());
            return false;
        }
    }

    /**
     * Passo 3: Correr a Interface de Utilizador (UI).
     * Mostra os menus e trata do input do utilizador.
     */
    private static void runUI() {
        try {
            // TODO: Aqui irás criar os menus (Login, Registo, etc.)
            // Por agora, vamos só testar o LOGIN

            System.out.println("--- BEM-VINDO ---");
            System.out.print("Email: ");
            String email = scanner.nextLine();
            System.out.print("Password: ");
            String pass = scanner.nextLine();

            // 1. Criar o Mapa (Objeto Java) para o pedido
            Map<String, Object> loginRequest = Map.of(
                    "type", "LOGIN", // O "type" que o ClientHandler espera
                    "email", email,
                    "password", pass
            );

            // 2. Traduzir para JSON e ENVIAR pela "Boca de Texto"
            String jsonRequest = gson.toJson(loginRequest);
            out.println(jsonRequest);
            System.out.println("➡ Pedido de LOGIN enviado.");

            // 3. ESPERAR pela resposta do servidor (usar o "Ouvido de Texto")
            String jsonResponse = in.readLine();
            System.out.println("⬅ Resposta recebida: " + jsonResponse);

            if (jsonResponse == null) {
                throw new IOException("Servidor desligou-se.");
            }

            // 4. Traduzir a resposta JSON de volta para um Mapa
            Map<String, Object> response = gson.fromJson(jsonResponse, Map.class);
            String type = (String) response.get("type");

            if ("LOGIN_OK".equals(type)) {
                System.out.println("SUCESSO! Logado como " + response.get("nome"));
                // TODO: Chamar o menu principal (do docente ou estudante)
                // showMainMenu();
            } else {
                System.err.println("FALHA: " + response.get("message"));
            }

            // ... (Aqui continuaria o resto da UI)
            // Por agora, forçamos o fecho para o loop de failover
            // (Remove esta linha quando a UI estiver completa)
            throw new IOException("A simular fecho para testar failover");


        } catch (IOException e) {
            // Se houver um erro de leitura/escrita, o 'catch' é ativado.
            // O 'runUI' termina, e o 'main' vai tentar reconectar.
            // Isto implementa o failover!
            System.err.println("Erro na comunicação com o servidor: " + e.getMessage());
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
        }
    }
}