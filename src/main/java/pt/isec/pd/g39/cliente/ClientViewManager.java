package pt.isec.pd.g39.cliente;

import pt.isec.pd.g39.cliente.UI.ConsoleUI;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ClientViewManager {
    private final ClientComms comms;

    public ClientViewManager(ClientComms comms) {
        this.comms = comms;
    }

    public void startConsole() throws IOException {
        comms.initDirectory();
        ConsoleUI consoleUI = new ConsoleUI(this);
        consoleUI.start();
    }


    public Map<String, Object> registerStudent(String nome, String email, String password, String numero) {
        return comms.registerStudent(nome, email, password, numero);
    }

    public Map<String, Object> registerTeacher(String nome, String email, String password, String secretCode) {
        return comms.registerTeacher(nome, email, password, secretCode);
    }

    public Map<String, Object> login(String email, String password) {
        return comms.login(email, password);
    }

    public Map<String, Object> listQuestionsFilter(String filtro) {
        return comms.listQuestionsFilter(filtro);
    }

    public Map<String, Object> listQuestionAnswers(int perguntaId) {
        return comms.listQuestionAnswers(perguntaId);
    }

    public Map<String, Object> createQuestion(int docenteId, String enunciado, String dataInicio, String dataFim, List<Map<String, Object>> opcoes) {
        return comms.createQuestion(docenteId, enunciado, dataInicio, dataFim, opcoes);
    }

    public Map<String, Object> editQuestion(int perguntaId, String enunciado, String dataInicio, String dataFim, List<Map<String, Object>> opcoes) {
        return comms.editQuestion(perguntaId, enunciado, dataInicio, dataFim, opcoes);
    }

    public Map<String, Object> deleteQuestion(int perguntaId) {
        return comms.deleteQuestion(perguntaId);
    }

    public Map<String, Object> getQuestionByCode(String codigo, int alunoId) {
        return comms.getQuestionByCode(codigo, alunoId);
    }

    public Map<String, Object> submitAnswer(int alunoId, int perguntaId, String opcao) {
        return comms.submitAnswer(alunoId, perguntaId, opcao);
    }

    public Map<String, Object> listAnsweredExpired(int alunoId, String filtroData) {
        return comms.listAnsweredExpired(alunoId, filtroData);
    }

    public Map<String, Object> listQuestions(int docenteId) {
        return comms.listQuestions(docenteId);
    }


    public Map<String, Object> getQuestionForEdit(int perguntaId) {
        return comms.getQuestionForEdit(perguntaId);
    }


    public Map<String, Object> editTeacher(int docenteId, String nome, String email, String password) {
        return comms.editTeacher(docenteId, nome, email, password);
    }

    public Map<String, Object> editStudent(int numeroAtual, int novoNumero,
                                           String nome, String email, String password) {
        return comms.editStudent(numeroAtual, novoNumero, nome, email, password);
    }

    public Map<String, Object> getUserData(String tipo, int id){
        return comms.getUserData(tipo, id);
    }
}
