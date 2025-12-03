package pt.isec.pd.g39.plan_API_RMI.RMI;

import java.io.Serializable;

public class AnswerDTO implements Serializable {
    public int perguntaId;
    public String respostaDada;
    public boolean correta;
    public String dataRealizacao;
}