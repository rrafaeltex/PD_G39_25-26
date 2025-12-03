package pt.isec.pd.g39.plan_API_RMI.RMI;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface StudentTeacherService extends Remote {

    // LOGIN / REGISTO
    LoginResponseDTO login(String email, String password) throws RemoteException;
    boolean registerStudent(String nome, String email, String pass, String numero) throws RemoteException;
    boolean registerTeacher(String nome, String email, String pass, String secret) throws RemoteException;

    // UTILIZADOR
    UserDTO getUserData(String token) throws RemoteException;
    UserDTO editUserData(String token, UserDTO novosDados) throws RemoteException;

    // DOCENTE
    QuestionDTO createQuestion(String token, QuestionDTO pergunta) throws RemoteException;
    List<QuestionDTO> listQuestions(String token) throws RemoteException;
    QuestionDTO getQuestionForEdit(String token, int id) throws RemoteException;
    boolean editQuestion(String token, int id, QuestionDTO edit) throws RemoteException;
    boolean deleteQuestion(String token, int id) throws RemoteException;
    List<QuestionDTO> listQuestionsFilter(String token, String estado) throws RemoteException;
    List<AnswerDTO> listQuestionAnswers(String token, int idPergunta) throws RemoteException;

    // ESTUDANTE
    QuestionDTO getQuestionByCode(String token, String codigo) throws RemoteException;
    boolean submitAnswer(String token, int perguntaId, String opcao) throws RemoteException;
    List<AnswerDTO> listAnsweredExpired(String token) throws RemoteException;
}
