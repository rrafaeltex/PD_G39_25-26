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
import pt.isec.pd.g39.servidor.database.Database;


public class ClientHandler extends Thread {

    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
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
//
    private void handleLogin(Map<String, Object> request) {
        String email = (String) request.get("email");
        String password = (String) request.get("password");

        System.out.println("A verificar login para: " + email);

        // CHAMADA REAL À BASE DE DADOS
        Map<String, Object> user = Database.checkLogin(email, password);

        if (user != null) {
            // Login OK! Devolvemos os dados do utilizador ao cliente
            Map<String, Object> responseData = new HashMap<>(user);
            responseData.remove("sucesso"); // Não precisamos enviar este flag
            sendResponse("LOGIN_OK", responseData);
        } else {
            sendResponse("LOGIN_FAIL", Map.of("message", "Email ou password errados"));
        }
    }

    /**
     * Trata de um pedido de Registo de Estudante.
     */
    //
    private void handleRegisterStudent(Map<String, Object> request) {
        String nome = (String) request.get("nome");
        String email = (String) request.get("email");
        String password = (String) request.get("password");
        // O cliente tem de enviar o numero de estudante também!
        // Se o teu cliente ainda não pede número, assume um aleatório ou altera o cliente.
        // Vamos assumir que vem no JSON como "numero":
        // int numero = ((Double) request.get("numero")).intValue();

        // Para testar rápido, vou usar o hashCode do email como número (só para não dar erro agora)
        int numero = Math.abs(email.hashCode());

        System.out.println("A registar estudante: " + nome);

        boolean sucesso = Database.registerEstudante(numero, nome, email, password);

        if (sucesso) {
            sendResponse("REGISTER_OK", Map.of("message", "Registo efetuado!"));
        } else {
            sendResponse("REGISTER_FAIL", Map.of("message", "Erro: Email ou número já existem."));
        }
    }
    /**
     * Trata de um pedido de Registo de Docente.
     */
    //
    private void handleRegisterTeacher(Map<String, Object> request) {
        // 1. Extrair dados do JSON
        String secretCode = (String) request.get("secret_code");
        String nome = (String) request.get("nome");
        String email = (String) request.get("email");
        String password = (String) request.get("password");

        System.out.println("A verificar registo de docente: " + nome);

        // 2. Validar Código Secreto (Hardcoded "codigo123" para já)
        // Nota: O enunciado pede que isto esteja na BD, podes adicionar à tabela 'config' mais tarde.
        if ("codigo123".equals(secretCode)) {

            // 3. Registar na Base de Dados
            // Chama a função que cria o INSERT INTO docente...
            boolean sucesso = Database.registerDocente(nome, email, password);

            if (sucesso) {
                sendResponse("REGISTER_OK", Map.of("message", "Docente registado com sucesso!"));
            } else {
                // Falha geralmente se o email já existir (UNIQUE constraint)
                sendResponse("REGISTER_FAIL", Map.of("message", "Erro: Email já está em uso."));
            }

        } else {
            sendResponse("REGISTER_FAIL", Map.of("message", "Código de acesso de docente inválido!"));
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

