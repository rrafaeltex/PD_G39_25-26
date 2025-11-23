package pt.isec.pd.g39.servidor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Collections;
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
            socket.setSoTimeout(30_000);
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
                        return;

                    case "REGISTER_STUDENT":
                        handleRegisterStudent(request);
                        break;

                    case "REGISTER_TEACHER":
                        handleRegisterTeacher(request);
                        break;
                    case "CREATE_QUESTION":
                        handleCreateQuestion(request);
                        break;

                    case "LIST_QUESTIONS":
                        handleListQuestions(request);
                        break;

                    case "GET_QUESTION_FOR_EDIT":
                        handleGetQuestionForEdit(request);
                        break;

                    case "EDIT_QUESTION":
                        handleEditQuestion(request);
                        break;

                    case "DELETE_QUESTION":
                        handleDeleteQuestion(request);
                        break;

                    case "LIST_QUESTIONS_FILTER":
                        handleListFilterQuestions(request);
                        break;

                    case "GET_QUESTION_BY_CODE":
                        handleGetQuestionByCode(request);
                        break;

                    case "SUBMIT_ANSWER":
                        handleSubmitAnswer(request);
                        break;

                    case"LIST_ANSWERED_EXPIRED":
                        handleListAnsweredExpired(request);
                        break;

                    case"LIST_QUESTION_ANSWERS":
                        handleListQuestionAnswers(request);
                        break;

                    default:
                        sendResponse("ERROR", Map.of("message", "Tipo de pedido desconhecido: " + type));
                        break;
                }
            }

        } catch (SocketTimeoutException e) {
            System.out.println("[SERVER] Cliente não enviou credenciais em 30 segundos. Ligação encerrada.");
        } catch (Exception e) {
            System.err.println("[CLIENT " + socket.getPort() + "] Cliente desligou-se: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void handleListQuestionAnswers(Map<String, Object> request) {
        int perguntaId = ((Double) request.get("pergunta_id")).intValue();

        Map<String, Object> dados = Database.listarRespostasPerguntaExpirada(perguntaId);

        if (dados == null) {
            sendResponse("LIST_QUESTION_ANSWERS_FAIL", Map.of(
                    "message", "Pergunta não encontrada ou ainda não expirou."
            ));
            return;
        }

        sendResponse("LIST_QUESTION_ANSWERS_OK", dados);
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

            try { socket.close(); } catch (Exception ignored) {}
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
        int numero = Integer.parseInt((String) request.get("numero"));

        System.out.println("A registar estudante: " + nome);

        boolean sucesso = Database.registerEstudante(numero, nome, email, password);

        if (sucesso) {
            sendResponse("REGISTER_OK", Map.of("message", "Registo efetuado!" , "id" , Database.getId("s", email)));
        } else {
            sendResponse("REGISTER_FAIL", Map.of("message", "Erro: Email ou número já existem."));

            try { socket.close(); } catch (Exception ignored) {}
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

                try { socket.close(); } catch (Exception ignored) {}
            }

        } else {
            sendResponse("REGISTER_FAIL", Map.of("message", "Código de acesso de docente inválido!"));

            try { socket.close(); } catch (Exception ignored) {}
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


    //

    private void handleGetQuestionByCode(Map<String, Object> request) {
        String codigo = (String) request.get("codigo");

        // O Gson converte números para Double por defeito, por isso fazemos o cast
        int alunoIdCheck = ((Double) request.get("aluno_id")).intValue();

        System.out.println("Aluno " + alunoIdCheck + " a pedir pergunta: " + codigo);

        // 1. Buscar pergunta ativa
        Map<String, Object> perguntaAtiva = Database.getPerguntaAtivaPorCodigo(codigo);

        if (perguntaAtiva == null) {
            sendResponse("GET_QUESTION_FAIL", Map.of("message", "Pergunta não encontrada ou fora do horário."));
            return;
        }

        // 2. Verificar se já respondeu
        int pId = (int) perguntaAtiva.get("id");
        if (Database.jaRespondeu(alunoIdCheck, pId)) {
            sendResponse("GET_QUESTION_FAIL", Map.of("message", "Já respondeste a esta pergunta!"));
        } else {
            sendResponse("GET_QUESTION_OK", perguntaAtiva);
        }
    }

    private void handleSubmitAnswer(Map<String, Object> request) {
        int alunoId = ((Double) request.get("aluno_id")).intValue();
        int perguntaId = ((Double) request.get("pergunta_id")).intValue();
        String opcao = (String) request.get("opcao");

        System.out.println("Aluno " + alunoId + " submeteu resposta: " + opcao);

        boolean registado = Database.registarResposta(alunoId, perguntaId, opcao);

        if (registado) {
            sendResponse("SUBMIT_ANSWER_OK", Map.of("message", "Resposta submetida com sucesso!"));
        } else {
            sendResponse("SUBMIT_ANSWER_FAIL", Map.of("message", "Erro ao gravar resposta."));
        }
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

    private void handleListQuestions(Map<String, Object> request) {
        int docenteId = ((Double) request.get("docente_id")).intValue();

        List<Map<String, Object>> perguntas = Database.listarPerguntas(docenteId);

        sendResponse("LIST_QUESTIONS_OK", Map.of("perguntas", perguntas));
    }

    private void handleGetQuestionForEdit(Map<String, Object> request) {
        int perguntaId = ((Double) request.get("pergunta_id")).intValue();

        if (Database.perguntaTemRespostas(perguntaId)) {
            sendResponse("QUESTION_HAS_ANSWERS", Map.of());
            return;
        }

        Map<String, Object> pergunta = Database.getPerguntaCompleta(perguntaId);

        sendResponse("GET_QUESTION_OK", pergunta);
    }

    private void handleEditQuestion(Map<String, Object> request) {
        int perguntaId = ((Double) request.get("pergunta_id")).intValue();

        String enunciado = (String) request.get("enunciado");
        String di = (String) request.get("data_inicio");
        String df = (String) request.get("data_fim");
        List<Map<String, Object>> opcoes =
                (List<Map<String, Object>>) request.get("opcoes");

        boolean ok = Database.editarPergunta(perguntaId, enunciado, di, df, opcoes);

        if (ok)
            sendResponse("EDIT_QUESTION_OK", Map.of("message", "Atualizada"));
        else
            sendResponse("EDIT_QUESTION_FAIL", Map.of("message", "Erro ao atualizar"));
    }

    private void handleDeleteQuestion(Map<String, Object> request) {
        int perguntaId = ((Double) request.get("pergunta_id")).intValue();

        if (Database.perguntaTemRespostas(perguntaId)) {
            sendResponse("DELETE_QUESTION_FAIL",
                    Map.of("message", "A pergunta já tem respostas. Não pode ser eliminada."));
            return;
        }

        boolean ok = Database.eliminarPergunta(perguntaId);

        if (ok)
            sendResponse("DELETE_QUESTION_OK", Map.of("message", "Pergunta eliminada com sucesso!"));
        else
            sendResponse("DELETE_QUESTION_FAIL", Map.of("message", "Erro ao eliminar pergunta."));
    }
    private void handleListFilterQuestions(Map<String, Object> request) {
        String filtro = (String) request.get("filtro");

        List<Map<String, Object>> perguntas = Database.listarPerguntasFiltradas(filtro);

        sendResponse("LIST_QUESTIONS_FILTER_OK", Map.of("perguntas", perguntas));
    }


    private void handleListAnsweredExpired(Map<String, Object> request) {
        int alunoId = ((Double) request.get("aluno_id")).intValue();
        String filtroData = (String) request.getOrDefault("filtro_data", null);

        List<Map<String, Object>> lista = Database.listarPerguntasRespondidasExpiradas(alunoId, filtroData);

        sendResponse("LIST_ANSWERED_EXPIRED_OK", Map.of("perguntas", lista));

        System.out.println("RESPONDI EXPIRADAS -> " + gson.toJson(lista));
    }

}