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
import java.util.List;
import java.util.Map;


public class ClientComms {
    private final String directoryIp;
    private final int directoryPort;

    private String ipServer;
    private int tcpPortServer;

    private final Gson gson = new Gson();
    private static final long LOGIN_TIME = 30_000L;

    boolean isDocente = false;
    int idUser;

    private String lastLoginEmail = null;
    private String lastLoginPassword = null;
    private long loginDeadlineMs = 0L;

    public ClientComms(String directoryIp, int directoryPort) {
        this.directoryIp = directoryIp;
        this.directoryPort = directoryPort;
    }

    private void getTCP() throws IOException {
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
            System.err.println("[ERRO] " + e.getMessage());
            System.exit(1);
        }
    }

    private Socket connectToCurrentServer() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(ipServer, tcpPortServer), 5000);
        socket.setSoTimeout(30_000);
        return socket;
    }

    private Map<String, Object> sendSingleMessage(String msg) {
        boolean waitedOnce = false;

        for (int attempt = 0; attempt < 2; attempt++) {
            try (Socket socket = connectToCurrentServer();
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                out.write(msg);
                out.write("\n");
                out.flush();

                String reply = in.readLine();
                if (reply == null || reply.isBlank()) {
                    return Map.of("type", "ERROR", "message", "Empty or closed reply from server");
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> json = gson.fromJson(reply, Map.class);

                Object idObj = json.get("id");
                if (idObj instanceof Double) {
                    idUser = ((Double) idObj).intValue();
                } else if (idObj instanceof Number) {
                    idUser = ((Number) idObj).intValue();
                }
                return json;
            } catch (IOException e) {
                System.err.println("Erro na comunicação TCP: " + e.getMessage());
                boolean reauthed = attemptRecoveryLogin(loginDeadlineMs, waitedOnce);
                if (reauthed) {
                    System.out.println("Servidor e autenticação recuperados; a tentar reenviar automaticamente.");
                } else {
                    if (!waitedOnce) {
                        waitedOnce = true;
                        continue;
                    }
                    System.out.println("Operação de envio falhou após tentativas. A terminar.");
                    System.exit(1);
                }
            }
        }

        System.out.println("Operação de envio falhou após tentativas. A terminar.");
        System.exit(1);
        return Map.of("type", "ERROR", "message", "unreachable");
    }

    private Map<String, Object> sendAndReceive(String msg) {
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
                boolean reauthed = attemptRecoveryLogin(loginDeadlineMs, waitedOnce);
                if (reauthed) {
                    System.out.println("Servidor e autenticação recuperados; a tentar reenviar automaticamente.");
                } else {
                    if (!waitedOnce) {
                        waitedOnce = true;
                        continue;
                    }
                    return Map.of("type", "ERROR", "message", e.getMessage());
                }
            } catch (Exception e) {
                System.err.println("Erro: " + e.getMessage());
                return Map.of("type", "ERROR", "message", e.getMessage());
            }
        }

        return Map.of("type", "ERROR", "message", "Operação de envio falhou após tentativas.");
    }



    private enum RecoveryAction {
        RETRY_IMMEDIATE,
        RETRY_AFTER_WAIT,
        GIVE_UP
    }

    private RecoveryAction attemptRecovery(String previousIp, int previousPort,
                                           boolean alreadyWaitedOnce, long maxWaitMs) {
        try {
            getTCP();
        } catch (IOException e) {
            System.err.println("Erro ao contactar diretoria durante recuperação: " + e.getMessage());
            return RecoveryAction.GIVE_UP;
        }

        boolean changed = !(ipServer.equals(previousIp) && tcpPortServer == previousPort);

        if (changed) {
            return RecoveryAction.RETRY_IMMEDIATE;
        }

        if (alreadyWaitedOnce) {
            System.out.println("Mesmo servidor principal e já foi tentado aguardar. A terminar.");
            return RecoveryAction.GIVE_UP;
        }

        if (maxWaitMs <= 0) {
            System.out.println("Janela de login expirada durante espera.");
            return RecoveryAction.GIVE_UP;
        }
        long wait = Math.min(20_000L, maxWaitMs);

        try {
            Thread.sleep(wait);
        } catch (InterruptedException ignored) {}

        return RecoveryAction.RETRY_AFTER_WAIT;
    }


    private boolean attemptRecoveryLogin(long deadlineMs, boolean alreadyWaitedOnce) {
        String previousIp = ipServer;
        int previousPort = tcpPortServer;
        RecoveryAction action = attemptRecovery(previousIp, previousPort, alreadyWaitedOnce, Math.max(0, deadlineMs - System.currentTimeMillis()));
        switch (action) {
            case RETRY_IMMEDIATE:
            case RETRY_AFTER_WAIT:
                long now = System.currentTimeMillis();
                long effectiveDeadline = Math.min(loginDeadlineMs, deadlineMs);
                if (lastLoginEmail == null || lastLoginPassword == null || now > effectiveDeadline) {
                    System.out.println("No stored credentials or login window expired; cannot re-authenticate automatically.");
                    return false;
                }

                System.out.println("Attempting automatic re-login to " + ipServer + ":" + tcpPortServer + " ...");
                String loginMsg = gson.toJson(Map.of(
                        "type", "LOGIN",
                        "email", lastLoginEmail,
                        "password", lastLoginPassword
                ));
                Map<String, Object> resp = sendAndReceive(loginMsg);
                String type = (String) resp.get("type");
                if ("LOGIN_OK".equals(type)) {
                    Object idObj = resp.get("id");
                    if (idObj instanceof Double) {
                        idUser = ((Double) idObj).intValue();
                    } else if (idObj instanceof Number) {
                        idUser = ((Number) idObj).intValue();
                    }
                    String perfil = (String) resp.getOrDefault("perfil", "");
                    isDocente = "docente".equalsIgnoreCase(perfil);

                    System.out.println("Re-login successful.");
                    return true;
                } else {
                    System.out.println("Automatic re-login failed: " + resp.getOrDefault("message", "no message"));
                    return false;
                }

            case GIVE_UP:
            default:
                return false;
        }
    }

    public void initDirectory() throws IOException {
        getTCP();
    }

    public Map<String, Object> registerStudent(String nome, String email, String password, String numero) {
        String msg = gson.toJson(Map.of(
                "type", "REGISTER_STUDENT",
                "nome", nome,
                "email", email,
                "password", password,
                "numero", numero
        ));
        return sendSingleMessage(msg);
    }

    public Map<String, Object> registerTeacher(String nome, String email, String password, String secretCode) {
        String msg = gson.toJson(Map.of(
                "type", "REGISTER_TEACHER",
                "nome", nome,
                "email", email,
                "password", password,
                "secret_code", secretCode
        ));
        return sendSingleMessage(msg);
    }

    public Map<String, Object> login(String email, String password) {
        this.lastLoginEmail = email;
        this.lastLoginPassword = password;
        this.loginDeadlineMs = System.currentTimeMillis() + LOGIN_TIME;

        String msg = gson.toJson(Map.of(
                "type", "LOGIN",
                "email", email,
                "password", password
        ));
        Map<String, Object> resp = sendAndReceive(msg);

        String type = (String) resp.get("type");
        if ("LOGIN_OK".equals(type)) {
            Object idObj = resp.get("id");
            if (idObj instanceof Double) {
                idUser = ((Double) idObj).intValue();
            } else if (idObj instanceof Number) {
                idUser = ((Number) idObj).intValue();
            }
            String perfil = (String) resp.getOrDefault("perfil", "");
            isDocente = "docente".equalsIgnoreCase(perfil);
        }
        return resp;
    }

    public Map<String, Object> listQuestionsFilter(String filtro) {
        String msg = gson.toJson(Map.of(
                "type", "LIST_QUESTIONS_FILTER",
                "filtro", filtro
        ));
        return sendAndReceive(msg);
    }

    public Map<String, Object> listQuestionAnswers(int perguntaId) {
        String msg = gson.toJson(Map.of(
                "type", "LIST_QUESTION_ANSWERS",
                "pergunta_id", perguntaId
        ));
        return sendAndReceive(msg);
    }

    public Map<String, Object> createQuestion(int docenteId, String enunciado, String dataInicio, String dataFim, List<Map<String, Object>> opcoes) {
        String msg = gson.toJson(Map.of(
                "type", "CREATE_QUESTION",
                "docente_id", docenteId,
                "enunciado", enunciado,
                "data_inicio", dataInicio,
                "data_fim", dataFim,
                "opcoes", opcoes
        ));
        return sendAndReceive(msg);
    }

    public Map<String, Object> editQuestion(int perguntaId, String enunciado, String dataInicio, String dataFim, List<Map<String, Object>> opcoes) {
        String msg = gson.toJson(Map.of(
                "type", "EDIT_QUESTION",
                "pergunta_id", perguntaId,
                "enunciado", enunciado,
                "data_inicio", dataInicio,
                "data_fim", dataFim,
                "opcoes", opcoes
        ));
        return sendAndReceive(msg);
    }

    public Map<String, Object> deleteQuestion(int perguntaId) {
        String msg = gson.toJson(Map.of(
                "type", "DELETE_QUESTION",
                "pergunta_id", perguntaId
        ));
        return sendAndReceive(msg);
    }

    public Map<String, Object> getQuestionByCode(String codigo, int alunoId) {
        String msg = gson.toJson(Map.of(
                "type", "GET_QUESTION_BY_CODE",
                "codigo", codigo,
                "aluno_id", alunoId
        ));
        return sendAndReceive(msg);
    }

    public Map<String, Object> submitAnswer(int alunoId, int perguntaId, String opcao) {
        String msg = gson.toJson(Map.of(
                "type", "SUBMIT_ANSWER",
                "aluno_id", alunoId,
                "pergunta_id", perguntaId,
                "opcao", opcao
        ));
        return sendAndReceive(msg);
    }

    public Map<String, Object> listAnsweredExpired(int alunoId, String filtroData) {
        String msg = gson.toJson(Map.of(
                "type", "LIST_ANSWERED_EXPIRED",
                "aluno_id", alunoId,
                "filtro_data", filtroData
        ));
        return sendAndReceive(msg);
    }

    public Map<String, Object> listQuestions(int docenteId) {
        String msg = gson.toJson(Map.of(
                "type", "LIST_QUESTIONS",
                "docente_id", docenteId
        ));
        return sendAndReceive(msg);
    }

    public Map<String, Object> getQuestionForEdit(int perguntaId) {
        String msg = gson.toJson(Map.of(
                "type", "GET_QUESTION_FOR_EDIT",
                "pergunta_id", perguntaId
        ));
        return sendAndReceive(msg);
    }


    public Map<String, Object> editTeacher(int docenteId, String nome, String email, String password) {
        String msg = gson.toJson(Map.of(
                "type", "EDIT_USER_DATA",
                "perfil", "docente",
                "id", docenteId,
                "nome", nome,
                "email", email,
                "password", password
        ));
        return sendAndReceive(msg);
    }

    public Map<String, Object> editStudent(int numeroAtual, int novoNumero,
                                           String nome, String email, String password) {
        String msg = gson.toJson(Map.of(
                "type", "EDIT_USER_DATA",
                "perfil", "estudante",
                "id", numeroAtual,
                "novo_numero", novoNumero,
                "nome", nome,
                "email", email,
                "password", password
        ));
        return sendAndReceive(msg);
    }

    public Map<String, Object> getUserData(String tipo, int id) {
        String msg = gson.toJson(Map.of(
                "type", "GET_USER_DATA",
                "perfil", tipo,
                "id", id
        ));
        return sendAndReceive(msg);
    }


}
