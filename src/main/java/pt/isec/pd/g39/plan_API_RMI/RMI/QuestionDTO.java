package pt.isec.pd.g39.plan_API_RMI.RMI;

import java.io.Serializable;
import java.util.List;

public class QuestionDTO implements Serializable {
    public int id;
    public String enunciado;
    public String dataInicio;
    public String dataFim;
    public List<OptionDTO> opcoes;
}