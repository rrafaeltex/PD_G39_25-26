// java
package pt.isec.pd.g39.cliente;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class ClientComms {
    private final String directoryIp;
    private final int directoryPort;

    private String ipServer;
    private int tcpPortServer;

    private final Gson gson = new Gson();
    private static final long LOGIN_TIME = 30_000L;

    boolean isDocente = false;
    int idUser;

    public ClientComms(String directoryIp, int directoryPort) {
        this.directoryIp = directoryIp;
        this.directoryPort = directoryPort;
    }

    public void start() throws IOException {
        getTCP();

        boolean done = false;
        Scanner scanner = new Scanner(System.in);

        String choice;
        while (true) {
            System.out.println("Registo ou Login?");
            choice = scanner.nextLine().trim().toLowerCase();
            if (choice.equals("login") || choice.equals("registo")) {
                break;
            }
            System.out.println("Opção inválida. Por favor, escolha 'Registo' ou 'Login'.");
        }

        if (choice.equals("login")) {
            runLoginSession(scanner);
        } else {
            System.out.print("Email: ");
            String email = scanner.nextLine().trim();

            System.out.print("Password: ");
            String password = scanner.nextLine().trim();

            System.out.print("Nome: ");
            String nome = scanner.nextLine().trim();

            String role;
            while (true) {
                System.out.print("Estudante (S) ou Docente (D)? ");
                role = scanner.nextLine().trim().toLowerCase();
                if (role.equals("s") || role.equals("d")) break;
                System.out.println("Opção inválida. Introduza 'S' para Estudante ou 'D' para Docente.");
            }
            String msg = "";
            if (role.equals("s")) {
                String n;
                while (true) {
                    System.out.print("Numero de estudante? ");
                    n = scanner.nextLine().trim();
                    if (!n.isBlank() && n.matches("\\d+")) break;
                    System.out.println("Número inválido. Introduza apenas dígitos.");
                }
                msg = gson.toJson(Map.of(
                        "type", "REGISTER_STUDENT",
                        "nome", nome,
                        "email", email,
                        "password", password,
                        "numero", n
                ));
            } else {
                System.out.print("Secret Code: ");
                String secretCode = scanner.nextLine().trim();
                msg = gson.toJson(Map.of(
                        "type", "REGISTER_TEACHER",
                        "nome", nome,
                        "email", email,
                        "password", password,
                        "secret_code", secretCode
                ));
                isDocente = true;
            }
            sendSingleMessage(msg);
        }
        while (!done) {
            if (isDocente) {
                System.out.println("O que fazer:");
                System.out.println("1 -> Sair");
                System.out.println("2 -> Criar uma pergunta");
                System.out.println("3 -> Editar Pergunta");
                System.out.println("4 -> Eliminar Perguntas");
                System.out.println("5 -> Listar Perguntas c/Filtro (Ativas/Futuras/Expiradas)");

                String choice2 = scanner.nextLine().trim();
                switch (choice2) {
                    case "1":
                        done = true;
                        break;
                    case "2":
                        criarPergunta();
                        break;
                    case "3":
                        editarPergunta();
                        break;
                    case "4":
                        eliminarPergunta();
                        break;
                    case "5":
                        consultarPerguntas();
                        break;
                    default:
                        System.out.println("Opção inválida. A voltar ao menu.");
                }
            } else {
                System.out.println("O que fazer:");
                System.out.println("1 -> Sair");

                String choice2 = scanner.nextLine().trim();
                switch (choice2) {
                    case "1":
                        done = true;
                        break;
                    default:
                        System.out.println("Opção inválida. A voltar ao menu.");
                }
            }
        }
        System.out.println("A terminar.");
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
                String message = (String) json.get("message");
                String perfil = (String) json.get("perfil");
                Object idObj = json.get("id");
                if (idObj instanceof Double) {
                    idUser = ((Double) idObj).intValue();
                } else if (idObj instanceof Number) {
                    idUser = ((Number) idObj).intValue();
                }
                if (perfil != null && perfil.equals("docente")) {
                    isDocente = true;
                }
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
                Object idObj = json.get("id");

                if (idObj instanceof Double) {
                    idUser = ((Double) idObj).intValue();
                } else if (idObj instanceof Number) {
                    idUser = ((Number) idObj).intValue();
                }

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
                RecoveryAction action = attemptRecovery(previousIp, previousPort, waitedOnce, -1);
                switch (action) {
                    case RETRY_IMMEDIATE:
                        System.out.println("Servidor principal mudou para " + ipServer + ":" + tcpPortServer + " — a tentar reenviar automaticamente.");
                        previousIp = ipServer;
                        previousPort = tcpPortServer;
                        break;
                    case RETRY_AFTER_WAIT:
                        System.out.println("Mesmo servidor principal; aguardar 20s e tentar novamente...");
                        waitedOnce = true;
                        break;
                    case GIVE_UP:
                    default:
                        System.out.println("Operação de envio falhou após tentativas. A terminar.");
                        System.exit(1);
                }
            }
        }

        System.out.println("Operação de envio falhou após tentativas. A terminar.");
        System.exit(1);
    }

    private void criarPergunta() {
        Scanner sc = new Scanner(System.in);

        System.out.print("Enunciado: ");
        String enunciado = sc.nextLine();

        System.out.print("Data início (yyyy-MM-dd HH:mm): ");
        String di = sc.nextLine();

        System.out.print("Data fim (yyyy-MM-dd HH:mm): ");
        String df = sc.nextLine();

        System.out.print("Quantas opções? ");
        int n = Integer.parseInt(sc.nextLine());

        List<Map<String, Object>> opcoes = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            System.out.println("Opção " + (char)('A' + i));
            System.out.print("Texto: ");
            String texto = sc.nextLine();

            System.out.print("É a correta? (s/n): ");
            boolean correta = sc.nextLine().trim().equalsIgnoreCase("s");

            opcoes.add(Map.of(
                    "letra", String.valueOf((char)('A' + i)),
                    "texto", texto,
                    "correta", correta
            ));
        }

        String msg = gson.toJson(Map.of(
                "type", "CREATE_QUESTION",
                "docente_id", idUser,
                "enunciado", enunciado,
                "data_inicio", di,
                "data_fim", df,
                "opcoes", opcoes
        ));

        sendSingleMessage(msg);
    }

    @SuppressWarnings("unchecked")
    private void editarPergunta(){
        Scanner sc = new Scanner(System.in);

        String msg = gson.toJson(Map.of(
                "type", "LIST_QUESTIONS",
                "docente_id", idUser
        ));

        Map<String, Object> resposta = sendAndReceive(msg);
        if(!"LIST_QUESTIONS_OK".equals(resposta.get("type"))){
            System.out.println("Erro ao obter perguntas.");
            return;
        }

        List<Map<String, Object>> perguntas =
                (List<Map<String, Object>>) resposta.get("perguntas");

        if (perguntas.isEmpty()) {
            System.out.println("Não tem perguntas para editar.");
            return;
        }

        // 2. Mostrar lista com índice
        System.out.println("\n=== PERGUNTAS CRIADAS ===");
        for (int i = 0; i < perguntas.size(); i++) {
            System.out.println((i + 1) + " -> " +
                    perguntas.get(i).get("enunciado") +
                    " (Código: " + perguntas.get(i).get("codigo_acesso") + ")");
        }

        System.out.print("Escolha o número da pergunta: ");
        int idx = Integer.parseInt(sc.nextLine()) - 1;

        int perguntaId = ((Double) perguntas.get(idx).get("id")).intValue();

        // 3. Pedir detalhes para edição
        msg = gson.toJson(Map.of(
                "type", "GET_QUESTION_FOR_EDIT",
                "pergunta_id", perguntaId
        ));

        resposta = sendAndReceive(msg);

        if ("QUESTION_HAS_ANSWERS".equals(resposta.get("type"))) {
            System.out.println("Não é possível editar — já existem respostas.");
            return;
        }

        if (!"GET_QUESTION_OK".equals(resposta.get("type"))) {
            System.out.println("Erro ao obter detalhes da pergunta.");
            return;
        }

        // Dados recebidos
        String enunciado = (String) resposta.get("enunciado");
        String dataInicio = (String) resposta.get("data_inicio");
        String dataFim = (String) resposta.get("data_fim");
        List<Map<String, Object>> opcoes =
                (List<Map<String, Object>>) resposta.get("opcoes");

        // 4. EDITAR (perguntas ao utilizador)
        System.out.println("\nNovo enunciado (ENTER mantém): " + enunciado);
        String novoEnunciado = sc.nextLine();
        if (novoEnunciado.isBlank()) novoEnunciado = enunciado;

        System.out.println("Nova data início (ENTER mantém): " + dataInicio);
        String novaDI = sc.nextLine();
        if (novaDI.isBlank()) novaDI = dataInicio;

        System.out.println("Nova data fim (ENTER mantém): " + dataFim);
        String novaDF = sc.nextLine();
        if (novaDF.isBlank()) novaDF = dataFim;

        // 5. Editar opções
        for (int i = 0; i < opcoes.size(); i++) {
            Map<String, Object> op = opcoes.get(i);
            System.out.println("Opção " + op.get("letra") + ": " + op.get("texto"));

            System.out.print("Novo texto (ENTER mantém): ");
            String novoTexto = sc.nextLine();
            if (!novoTexto.isBlank())
                op.put("texto", novoTexto);

            System.out.print("É a correta? (s/n, ENTER mantém): ");
            String cor = sc.nextLine().trim();
            if (cor.equalsIgnoreCase("s")) op.put("correta", true);
            else if (cor.equalsIgnoreCase("n")) op.put("correta", false);
        }

        // 6. Enviar alterações
        msg = gson.toJson(Map.of(
                "type", "EDIT_QUESTION",
                "pergunta_id", perguntaId,
                "enunciado", novoEnunciado,
                "data_inicio", novaDI,
                "data_fim", novaDF,
                "opcoes", opcoes
        ));

        Map<String, Object> respostaFinal = sendAndReceive(msg);

        if ("EDIT_QUESTION_OK".equals(respostaFinal.get("type")))
            System.out.println("Pergunta editada com sucesso!");
        else
            System.out.println("Erro ao editar pergunta.");
    }

    private Map<String, Object> sendAndReceive(String msg) {
        String previousIp = ipServer;
        int previousPort = tcpPortServer;
        boolean waitedOnce = false;

        for (int attempt = 0; attempt < 2; attempt++) {
            try (Socket socket = connectToCurrentServer();
                 BufferedWriter out = new BufferedWriter(
                         new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                out.write(msg + "\n");
                out.flush();

                String reply = in.readLine();
                if (reply == null || reply.isBlank()) {
                    return Map.of("type", "ERROR", "message", "Empty or closed reply from server");
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = gson.fromJson(reply, Map.class);
                return parsed;

            } catch (IOException e) {
                System.err.println("Erro na comunicação TCP: " + e.getMessage());
                RecoveryAction action = attemptRecovery(previousIp, previousPort, waitedOnce, -1);
                switch (action) {
                    case RETRY_IMMEDIATE:
                        System.out.println("Servidor principal mudou para " + ipServer + ":" + tcpPortServer + " — a tentar reenviar automaticamente.");
                        previousIp = ipServer;
                        previousPort = tcpPortServer;
                        break;
                    case RETRY_AFTER_WAIT:
                        waitedOnce = true;
                        break;
                    case GIVE_UP:
                    default:
                        return Map.of("type", "ERROR", "message", e.getMessage());
                }
            } catch (Exception e) {
                System.err.println("Erro: " + e.getMessage());
                return Map.of("type", "ERROR", "message", e.getMessage());
            }
        }

        return Map.of("type", "ERROR", "message", "Operação de envio falhou após tentativas.");
    }

    private void eliminarPergunta() {
        Scanner sc = new Scanner(System.in);

        // 1. Pedir lista ao servidor
        String msg = gson.toJson(Map.of(
                "type", "LIST_QUESTIONS",
                "docente_id", idUser
        ));

        Map<String, Object> resposta = sendAndReceive(msg);

        if (!"LIST_QUESTIONS_OK".equals(resposta.get("type"))) {
            System.out.println("Erro ao obter perguntas.");
            return;
        }

        List<Map<String, Object>> perguntas = (List<Map<String, Object>>) resposta.get("perguntas");

        if (perguntas.isEmpty()) {
            System.out.println("Não tem perguntas criadas.");
            return;
        }

        System.out.println("Perguntas disponíveis:");
        for (int i = 0; i < perguntas.size(); i++) {
            System.out.println(i + " -> " + perguntas.get(i).get("enunciado"));
        }

        System.out.print("Escolha o número da pergunta a eliminar: ");
        int escolha = Integer.parseInt(sc.nextLine());

        int perguntaId = ((Double) perguntas.get(escolha).get("id")).intValue();

        // 3. Enviar pedido DELETE
        msg = gson.toJson(Map.of(
                "type", "DELETE_QUESTION",
                "pergunta_id", perguntaId
        ));

        Map<String, Object> respostaDelete = sendAndReceive(msg);

        System.out.println(respostaDelete.get("message"));
    }

    private void consultarPerguntas() {
        Scanner scanner = new Scanner(System.in);
        String choice = "";
        while (true) {
            System.out.print("Deseja consultar perguntas ativas, futuras ou expiradas? (A/F/E): ");
            choice = scanner.nextLine().trim().toLowerCase();
            if (choice.equals("a") || choice.equals("f") || choice.equals("e")) break;
            System.out.println("Opção inválida. Introduza A para ativas, F para futuras ou E para expiradas.");
        }

        String msg = gson.toJson(Map.of(
                "type", "LIST_QUESTIONS_FILTER",
                "filtro", choice
        ));

        Map<String, Object> resposta = sendAndReceive(msg);

        if (!"LIST_QUESTIONS_FILTER_OK".equals(resposta.get("type"))) {
            System.out.println("Erro ao obter perguntas: " + resposta.getOrDefault("message", "unknown"));
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> perguntas = (List<Map<String, Object>>) resposta.get("perguntas");

        if (perguntas == null || perguntas.isEmpty()) {
            System.out.println("Não existem perguntas para este filtro.");
            return;
        }

        String lista;
        switch (choice) {
            case "a": lista = "ativas"; break;
            case "f": lista = "futuras"; break;
            default:  lista = "expiradas"; break;
        }

        System.out.println("Perguntas " + lista + ":");
        for (int i = 0; i < perguntas.size(); i++) {
            Map<String, Object> p = perguntas.get(i);
            System.out.println(i + " -> " + p.get("enunciado")
                    + " // " + p.get("data_inicio") + " até " + p.get("data_fim")
                    + " // Codigo de acesso: " + p.get("codigo_acesso")
                    + " // ID pergunta: " + p.get("id"));
        }
    }

    private enum RecoveryAction {
        RETRY_IMMEDIATE,
        RETRY_AFTER_WAIT,
        GIVE_UP
    }

    private RecoveryAction attemptRecovery(String previousIp, int previousPort, boolean alreadyWaitedOnce, long maxWaitMs) {
        try {
            getTCP();
        } catch (IOException e) {
            System.err.println("Erro ao contactar diretoria durante recuperação: " + e.getMessage());
            return RecoveryAction.GIVE_UP;
        }

        boolean changed = !(ipServer.equals(previousIp) && tcpPortServer == previousPort);
        if (changed) {
            return RecoveryAction.RETRY_IMMEDIATE;
        } else {
            if (alreadyWaitedOnce) {
                System.out.println("Mesmo servidor principal e já foi tentado aguardar. A terminar.");
                return RecoveryAction.GIVE_UP;
            }
            long wait;
            if (maxWaitMs > 0) {
                wait = Math.min(20_000L, maxWaitMs);
            } else {
                wait = 20_000L;
            }
            if (wait <= 0) {
                System.out.println("Janela de login expirada durante espera.");
                return RecoveryAction.GIVE_UP;
            }
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ignored) {}
            return RecoveryAction.RETRY_AFTER_WAIT;
        }
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
        RecoveryAction action = attemptRecovery(previousIp, previousPort, alreadyWaitedOnce, Math.max(0, deadlineMs - System.currentTimeMillis()));
        switch (action) {
            case RETRY_IMMEDIATE:
                System.out.println("Servidor principal mudou para " + ipServer + ":" + tcpPortServer + " — a tentar reconectar automaticamente.");
                return true;
            case RETRY_AFTER_WAIT:
                return true;
            case GIVE_UP:
            default:
                return false;
        }
    }
}
