package pt.isec.pd.g39.cliente;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

public class ClientComms {
    private final String directoryIp;
    private final int directoryPort;

    private String ipServer;
    private int tcpPortServer;

    private final Gson gson = new Gson();
    private static final long LOGIN_WINDOW_MS = 30_000L;

    public ClientComms(String directoryIp, int directoryPort) {
        this.directoryIp = directoryIp;
        this.directoryPort = directoryPort;
    }

    public void start() throws IOException {
        getTCP();
        /*
        Depois, começam por solicitar o email e a password ao utilizador para efeitos de
        autenticação, ou o conjunto de dados necessários ao registo de um novo utilizador.
         */
        boolean done = false;
        Scanner scanner = new Scanner(System.in);
        while (!done) {
            System.out.println("Registo ou Login?");
            String choice = scanner.nextLine().trim().toLowerCase();

        /*
        Depois, começam por solicitar o email e a password ao utilizador para efeitos de
        autenticação, ou o conjunto de dados necessários ao registo de um novo utilizador. Posteriomente,
        a aplicação ira retirar estes dados da UI
         */
            if (choice.equals("login")) {
                runLoginSession(scanner);
                done = true;
            } else if (choice.equals("registo")) {
                System.out.print("Email: ");
                String email = scanner.nextLine().trim();
                System.out.print("Password: ");
                String password = scanner.nextLine().trim();
                System.out.print("Nome: ");
                String nome = scanner.nextLine().trim();
                System.out.print("Estudante (S) ou Docente (D)? ");
                String role = scanner.nextLine().trim().toLowerCase();
                String msg;
                if (role.equals("s")) {
                    msg = gson.toJson(Map.of(
                            "type", "REGISTER_STUDENT",
                            "nome", nome,
                            "email", email,
                            "password", password
                    ));
                } else {
                    System.out.println("Secret Code: ");
                    String secretCode = scanner.nextLine().trim();
                    msg = gson.toJson(Map.of(
                            "type", "REGISTER_TEACHER",
                            "nome", nome,
                            "email", email,
                            "password", password,
                            "secret_code", secretCode
                    ));
                }
                sendSingleMessage(msg);
                done = true;
            } else {
                System.out.println("Opção inválida. Por favor, escolha 'Registo' ou 'Login'.");
            }
        }
    }

    private void getTCP() throws IOException {
        /*
        Solicitam ao serviço de diretoria o endereço IP e porto de escuta TCP do servidor ao
        qual se devem ligar. Se esta operação não for bem-sucedida, a aplicação terminar.
         */
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);

            String msg = gson.toJson(Map.of("type", "GET_PRIMARY"));
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName(directoryIp);

            DatagramPacket packet = new DatagramPacket(data, data.length, addr, directoryPort);
            socket.send(packet);

            byte[] buffer = new byte[1024];
            DatagramPacket resposta = new DatagramPacket(buffer, buffer.length);
            socket.receive(resposta);

            String respostaMsg = new String(resposta.getData(), 0, resposta.getLength(), StandardCharsets.UTF_8);

            var json = gson.fromJson(respostaMsg, Map.class);

            if ("PRIMARY_INFO".equals(json.get("type"))) {
                ipServer = (String) json.get("ip");
                tcpPortServer = ((Double) json.get("tcp_port_clients")).intValue();
                return;
            }

            System.out.println("Resposta inesperada do servico de diretoria.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println(e);
            System.exit(1);
        }
    }

    private void runLoginSession(Scanner scanner) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipServer, tcpPortServer), 5000);
            socket.setSoTimeout(30_000);
            /*
            Quando a autenticação falha ou as credenciais não são enviadas no espaço de 30
            segundos, o servidor encerra a ligação TCP com o cliente.
             */

            try (BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                long deadlineMs = 0L;
                boolean firstAttempt = true;

                while (true) {
                    System.out.print("Email: ");
                    String email = scanner.nextLine().trim();
                    System.out.print("Password: ");
                    String password = scanner.nextLine().trim();

                    String msg = gson.toJson(Map.of(
                            "type", "LOGIN",
                            "email", email,
                            "password", password
                    ));

                    out.write(msg);
                    out.write("\n");
                    out.flush();

                    if (firstAttempt) {
                        deadlineMs = System.currentTimeMillis() + LOGIN_WINDOW_MS;
                        firstAttempt = false;
                    }

                    String reply;
                    try {
                        reply = in.readLine();
                    } catch (SocketTimeoutException e) {
                        System.err.println("Timeout à espera de resposta. Servidor pode ter encerrado.");
                        break;
                    }

                    if (reply == null || reply.isBlank()) {
                        System.out.println("Ligação encerrada pelo servidor.");
                        break;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> json = gson.fromJson(reply, Map.class);
                    String type = (String) json.get("type");
                    String message = (String) json.getOrDefault("message", "");

                    if ("LOGIN_OK".equals(type)) {
                        System.out.println("Login bem-sucedido!");
                        break;
                    } else if ("LOGIN_FAIL".equals(type)) {
                        long remainingSec = Math.max(0, (deadlineMs - System.currentTimeMillis()) / 1000);
                        if (remainingSec <= 0) {
                            System.out.println("Janela de 30s expirada.");
                            break;
                        }
                        System.out.println("Falha no login: " + message + " | Tente novamente (" + remainingSec + "s restantes)");
                    } else {
                        System.out.println("Resposta inesperada do servidor: " + type);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erro na comunicação TCP: " + e.getMessage());
        }
    }

    private void sendSingleMessage(String msg) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipServer, tcpPortServer), 5000);
            socket.setSoTimeout(5000);

            try (BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                out.write(msg);
                out.write("\n");
                out.flush();

                String reply = in.readLine();
                if (reply == null || reply.isBlank()) {
                    System.out.println("Sem resposta do servidor.");
                    return;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> json = gson.fromJson(reply, Map.class);
                String type = (String) json.get("type");
                String message = (String) json.getOrDefault("message", "");

                switch (type) {
                    case "REGISTER_OK":
                        System.out.println("Registo bem-sucedido!");
                        break;
                    case "REGISTER_FAIL":
                        System.out.println("Falha no registo: " + message);
                        break;
                    case "LOGIN_OK":
                        System.out.println("Login bem-sucedido!");
                        break;
                    case "LOGIN_FAIL":
                        System.out.println("Falha no login: " + message);
                        break;
                    default:
                        System.out.println("Resposta inesperada do servidor: " + type);
                }
            }
        } catch (IOException e) {
            System.err.println("Erro na comunicação TCP: " + e.getMessage());
        }
    }
}
