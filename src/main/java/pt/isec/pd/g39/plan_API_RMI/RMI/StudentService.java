package pt.isec.pd.g39.plan_API_RMI.RMI;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface StudentService extends Remote {


    LoginResponseDTO login(String email, String password) throws RemoteException;
    boolean registerStudent(String nome, String email, String pass, String numero) throws RemoteException;

    UserDTO getUserData(String token) throws RemoteException;
    UserDTO editUserData(String token, UserDTO novosDados) throws RemoteException;

    QuestionDTO getQuestionByCode(String token, String codigo) throws RemoteException;
    boolean submitAnswer(String token, int perguntaId, String opcao) throws RemoteException;
    List<AnswerDTO> listAnsweredExpired(String token) throws RemoteException;
}
