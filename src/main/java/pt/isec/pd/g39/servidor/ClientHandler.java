package pt.isec.pd.g39.servidor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
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
                    case "CREATE_QUESTION":
                        handleCreateQuestion(request);
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
    private void handleRegisterStudent(Map<String, Object> request) throws SQLException {
        String nome = (String) request.get("nome");
        String email = (String) request.get("email");
        String password = (String) request.get("password");
        int numero = (int) request.get("numero");

        System.out.println("A registar estudante: " + nome);

        boolean sucesso = Database.registerEstudante(numero, nome, email, password);

        if (sucesso) {
            sendResponse("REGISTER_OK", Map.of("message", "Registo efetuado!" , "id" , Database.getId("s", email)));
        } else {
            sendResponse("REGISTER_FAIL", Map.of("message", "Erro: Email ou número já existem."));
        }
    }
    /**
     * Trata de um pedido de Registo de Docente.
     */
    //
    private void handleRegisterTeacher(Map<String, Object> request) throws SQLException {
        // 1. Extrair dados do JSON
        String secretCode = (String) request.get("secret_code");
        String nome = (String) request.get("nome");
        String email = (String) request.get("email");
        String password = (String) request.get("password");

        System.out.println("A verificar registo de docente: " + nome);

        // Nota: O enunciado pede que isto esteja na BD, podes adicionar à tabela 'config' mais tarde.
        if (Database.validarCodigoDocente(secretCode)) {

            // 3. Registar na Base de Dados
            // Chama a função que cria o INSERT INTO docente...
            boolean sucesso = Database.registerDocente(nome, email, password);

            if (sucesso) {
                sendResponse("REGISTER_OK", Map.of("message", "Docente registado com sucesso!" , "id" , Database.getId("d",email)));
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

    private void handleCreateQuestion(Map<String, Object> request) {
        int docenteId = ((Double) request.get("docente_id")).intValue();
        String enunciado = (String) request.get("enunciado");
        String dataInicio = (String) request.get("data_inicio");
        String dataFim = (String) request.get("data_fim");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> opcoes =
                (List<Map<String, Object>>) request.get("opcoes");

        String codigo = Database.criarPergunta(docenteId, enunciado, dataInicio, dataFim, opcoes);

        if (codigo != null) {
            sendResponse("CREATE_QUESTION_OK", Map.of(
                    "message", "Pergunta criada com sucesso!",
                    "codigo_acesso", codigo
            ));
        } else {
            sendResponse("CREATE_QUESTION_FAIL", Map.of(
                    "message", "Falha ao criar pergunta."
            ));
        }
    }
}

