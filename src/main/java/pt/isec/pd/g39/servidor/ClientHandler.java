package pt.isec.pd.g39.servidor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import com.google.gson.Gson;


public class ClientHandler extends Thread {

    private final Socket socket;
    private PrintWriter out; //falar com o cliente
    private BufferedReader in; // escutar o cliente
    private final Gson gson = new Gson();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // 1. Preparar os canais de comunicação (Texto)
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // 2. Ciclo principal: Ficar à escuta de mensagens do cliente
            String clientMessage;
            while ((clientMessage = in.readLine()) != null) {

                System.out.println("[CLIENT " + socket.getPort() + " ➡] " + clientMessage);

                Map<String, Object> request = gson.fromJson(clientMessage, Map.class);
                String type = (String) request.get("type");


                switch (type) {
                    case "LOGIN":
                        handleLogin(request);
                        break;

                    case "REGISTER_STUDENT":
                        handleRegisterStudent(request);
                        break;

                    case "REGISTER_TEACHER":
                        handleRegisterTeacher(request);
                        break;

                    // Adicionar mais 'cases' para as outras funções
                    // ex: "CREATE_QUESTION", "GET_QUESTIONS", "SUBMIT_ANSWER"

                    default:
                        sendResponse("ERROR", Map.of("message", "Tipo de pedido desconhecido: " + type));
                        break;
                }
            }

        } catch (Exception e) {
            System.err.println("[CLIENT " + socket.getPort() + "] Cliente desligou-se: " + e.getMessage());
        } finally {
            // Limpar (fechar o socket)
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Trata de um pedido de Login.
     */
    private void handleLogin(Map<String, Object> request) {
        String email = (String) request.get("email");
        String password = (String) request.get("password");

        // ---LÓGICA DA DATABASE ---
        // User user = Database.checkLogin(email, password);
        // if (user != null) { ... }

        System.out.println("PLACEHOLDER: A processar LOGIN para " + email);

        // Simular uma resposta de sucesso (temporário)
        if (password.equals("123")) {
            sendResponse("LOGIN_OK", Map.of(
                    "nome", "Utilizador Fictício",
                    "profile", "student" // ou "teacher"
            ));
        } else {
            sendResponse("LOGIN_FAIL", Map.of("message", "Email ou password incorretos"));
        }
    }

    /**
     * Trata de um pedido de Registo de Estudante.
     */
    private void handleRegisterStudent(Map<String, Object> request) {
        String nome = (String) request.get("nome");
        String email = (String) request.get("email");
        // ... apanhar os outros campos ...

        // --- AQUI ENTRARIA A LÓGICA DA DATABASE ---
        // Exemplo:
        // boolean success = Database.registerStudent(...);
        // if (success) { ... }

        System.out.println("PLACEHOLDER: A tentar registar estudante " + nome);

        // Simular uma resposta
        sendResponse("REGISTER_OK", null);
        // ou: sendResponse("REGISTER_FAIL", Map.of("message", "Email já existe"));
    }

    /**
     * Trata de um pedido de Registo de Docente.
     */
    private void handleRegisterTeacher(Map<String, Object> request) {
        String secretCode = (String) request.get("secret_code");

        // LÓGICA DA DATABASE ---
        // Exemplo:
        // boolean goodCode = Database.checkTeacherCode(secretCode);

        System.out.println("PLACEHOLDER: A verificar código de docente...");
        // tem de ser o codigo que esta mna DATABASE
        if ("codigo123".equals(secretCode)) {
            // ... (aqui farias o resto do registo, tal como o de estudante)
            sendResponse("REGISTER_OK", null);
        } else {
            sendResponse("REGISTER_FAIL", Map.of("message", "Código de docente inválido"));
        }
    }


    // =======================================================
    // FUNÇÃO DE RESPOSTA (O "Tradutor" de saída)
    // =======================================================

    /**
     * Envia uma resposta JSON para o cliente.
     * @param type O tipo de resposta (ex: "LOGIN_OK")
     * @param data O "corpo" da mensagem (pode ser null)
     */
    private void sendResponse(String type, Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", type);

        if (data != null) {
            response.putAll(data);
        }

        // GSON: Traduz o Mapa "response" para um texto JSON
        String jsonResponse = gson.toJson(response);

        System.out.println("[CLIENT " + socket.getPort() + " ⬅] " + jsonResponse);

        // Envia o texto JSON para o cliente
        out.println(jsonResponse);
    }
}

