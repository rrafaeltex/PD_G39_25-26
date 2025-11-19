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
    private static final long LOGIN_TIME = 30_000L;

    public ClientComms(String directoryIp, int directoryPort) {
        this.directoryIp = directoryIp;
        this.directoryPort = directoryPort;
    }

    public void start() throws IOException {
        getTCP();
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

    private Socket connectToCurrentServer() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(ipServer, tcpPortServer), 5000);
        socket.setSoTimeout(30_000);
        return socket;
    }

    private void runLoginSession(Scanner scanner) {
        String lastEmail;
        String lastPassword;

        System.out.print("Email: ");
        lastEmail = scanner.nextLine().trim();
        System.out.print("Password: ");
        lastPassword = scanner.nextLine().trim();

        long deadlineMs = System.currentTimeMillis() + LOGIN_TIME;
        boolean retriedAfterSame = false;

        while (System.currentTimeMillis() <= deadlineMs) {
            try (Socket socket = connectToCurrentServer();
                 BufferedWriter out = new BufferedWriter(
                         new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                String msg = gson.toJson(Map.of(
                        "type", "LOGIN",
                        "email", lastEmail,
                        "password", lastPassword
                ));
                out.write(msg);
                out.write("\n");
                out.flush();

                String reply = in.readLine();
                if (reply == null || reply.isBlank()) {
                    throw new IOException("Ligação encerrada pelo servidor.");
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> json = gson.fromJson(reply, Map.class);
                String type = (String) json.get("type");
                String message = (String) json.getOrDefault("message", "");

                if ("LOGIN_OK".equals(type)) {
                    System.out.println("Login bem-sucedido!");
                    return;
                } else if ("LOGIN_FAIL".equals(type)) {
                    long remainingSec = Math.max(0, (deadlineMs - System.currentTimeMillis()) / 1000);
                    if (remainingSec <= 0) {
                        System.out.println("Janela de 30s expirada.");
                        break;
                    }
                    System.out.println("Falha no login: " + message + " | Tente novamente (" + remainingSec + "s restantes)");
                    System.out.print("Email: ");
                    lastEmail = scanner.nextLine().trim();
                    System.out.print("Password: ");
                    lastPassword = scanner.nextLine().trim();
                } else {
                    System.out.println("Resposta inesperada do servidor: " + type);
                    return;
                }

            } catch (SocketTimeoutException e) {
                System.err.println("Timeout à espera de resposta. Servidor pode ter encerrado.");
                if (!attemptRecoveryLogin(deadlineMs, retriedAfterSame)) {
                    System.exit(1);
                } else {
                    retriedAfterSame = true;
                }
            } catch (IOException e) {
                System.err.println("Erro na comunicação TCP: " + e.getMessage());
                if (!attemptRecoveryLogin(deadlineMs, retriedAfterSame)) {
                    System.exit(1);
                } else {
                    retriedAfterSame = true;
                }
            }
        }

        System.out.println("Não foi possível completar o login. A terminar.");
        System.exit(1);
    }

    private boolean attemptRecoveryLogin(long deadlineMs, boolean alreadyWaitedOnce) {
        /*
        Quando a conexão TCP com o servidor atual deixa de estar operacional, a aplicação
        cliente volta a solicitar ao serviço de diretoria os dados sobre o servidor principal atual.
        Se for diferente do anterior (que deixou de estar acessível), volta a ligar-se e a
        autenticar-se, sem envolver o utilizador e tentando passar esta situação de
        recuperação de falha o mais despercebida possível. Se os dados corresponderem ao
        mesmo servidor, volta a tentar uma segunda vez 20 segundos depois. Caso a operação
        não seja bem-sucedida, a aplicação termina.
        */
        String previousIp = ipServer;
        int previousPort = tcpPortServer;
        try {
            getTCP();
        } catch (IOException e) {
            System.err.println("Erro ao contactar diretoria durante recuperação: " + e.getMessage());
            return false;
        }

        boolean changed = !(ipServer.equals(previousIp) && tcpPortServer == previousPort);
        if (changed) {
            System.out.println("Servidor principal mudou para " + ipServer + ":" + tcpPortServer + " — a tentar reconectar automaticamente.");
            return true;
        } else {
            if (alreadyWaitedOnce) {
                System.out.println("Mesmo servidor principal e já foi tentado aguardar. A terminar.");
                return false;
            }
            System.out.println("Mesmo servidor principal; aguardar 20s e tentar novamente...");
            try {
                long remaining = Math.max(0, deadlineMs - System.currentTimeMillis());
                long wait = Math.min(20_000L, remaining);
                if (wait <= 0) {
                    System.out.println("Janela de login expirada durante espera.");
                    return false;
                }
                Thread.sleep(wait);
            } catch (InterruptedException ignored) {}
            return true;
        }
    }

    private void sendSingleMessage(String msg) {
        String previousIp = ipServer;
        int previousPort = tcpPortServer;
        boolean waitedOnce = false;

        for (int attempt = 0; attempt < 2; attempt++) {
            try (Socket socket = connectToCurrentServer();
                 BufferedWriter out = new BufferedWriter(
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

                Map<String, Object> json = gson.fromJson(reply, Map.class);
                String type = (String) json.get("type");
                String message = (String) json.getOrDefault("message", "");

                switch (type) {
                    case "REGISTER_OK":
                        System.out.println("Registo bem-sucedido!");
                        return;
                    case "REGISTER_FAIL":
                        System.out.println("Falha no registo: " + message);
                        return;
                    case "LOGIN_OK":
                        System.out.println("Login bem-sucedido!");
                        return;
                    case "LOGIN_FAIL":
                        System.out.println("Falha no login: " + message);
                        return;
                    default:
                        System.out.println("Resposta inesperada do servidor: " + type);
                        return;
                }

            } catch (IOException e) {
                System.err.println("Erro na comunicação TCP: " + e.getMessage());
                try {
                    getTCP();
                } catch (IOException ex) {
                    System.err.println("Erro ao contactar diretoria durante recuperação: " + ex.getMessage());
                    System.exit(1);
                }

                boolean changed = !(ipServer.equals(previousIp) && tcpPortServer == previousPort);
                if (changed) {
                    System.out.println("Servidor principal mudou para " + ipServer + ":" + tcpPortServer + " — a tentar reenviar automaticamente.");
                    previousIp = ipServer;
                    previousPort = tcpPortServer;
                } else {
                    if (waitedOnce) {
                        System.out.println("Mesmo servidor principal e já foi tentado aguardar. A terminar.");
                        System.exit(1);
                    }
                    System.out.println("Mesmo servidor principal; aguardar 20s e tentar novamente...");
                    try {
                        Thread.sleep(20_000L);
                    } catch (InterruptedException ignored) {}
                    waitedOnce = true;
                }
            }
        }

        System.out.println("Operação de envio falhou após tentativas. A terminar.");
        System.exit(1);
    }
}
