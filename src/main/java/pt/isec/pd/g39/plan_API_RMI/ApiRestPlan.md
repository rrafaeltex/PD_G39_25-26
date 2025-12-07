# Planeamento da API REST e Interface RMI — Estudantes
_Trabalho Prático — Programação Distribuída 2025/2026_

Este documento apresenta o planeamento da **API REST** e da **interface RMI** destinadas exclusivamente aos **estudantes**, tal como pedido no enunciado do trabalho.  
Este planeamento define apenas a estrutura dos serviços — **não inclui implementação**.

As funcionalidades previstas para estudantes incluem:
- Registo e autenticação
- Obtenção de dados pessoais
- Edição de dados pessoais
- Obtenção de perguntas ativas via código
- Submissão de respostas
- Consulta do histórico de perguntas expiradas respondidas

A autenticação segue o modelo baseado em **token**:
- **REST:** `Authorization: Bearer <token>`
- **RMI:** o token é sempre o **primeiro parâmetro** nos métodos autenticados.

---

# 1. API REST – Endpoints para Estudantes

## Tabela Resumo

| Método | Endpoint | Descrição | Autenticação |
|--------|----------|-----------|--------------|
| POST | `/api/auth/login` | Autenticação do estudante | ❌ |
| POST | `/api/students` | Registo de estudante | ❌ |
| GET | `/api/users/me` | Obter dados do estudante autenticado | ✅ |
| PUT | `/api/users/me` | Editar dados pessoais | ✅ |
| GET | `/api/questions/code/{codigo}` | Obter pergunta ativa via código | ✅ |
| POST | `/api/questions/{id}/answers` | Submeter resposta | ✅ |
| GET | `/api/students/me/answers` | Listar respostas a perguntas expiradas | ✅ |
| POST | `/api/auth/logout` | Terminar sessão | ✅ |

---

# 2. Detalhe dos Endpoints REST

---

## 2.1 POST `/api/auth/login`

### Request:
```json
{
  "email": "aluno@example.com",
  "password": "1234"
}
```

### Response:
```json
{
  "type": "LOGIN_OK",
  "data": {
    "id": 12,
    "nome": "Maria Silva",
    "perfil": "estudante",
    "token": "abc.def.ghi"
  }
}
```

---

## 2.2 POST `/api/students`
Registo de estudante.

### Request:
```json
{
  "nome": "João Santos",
  "email": "joao@example.com",
  "password": "1234",
  "numero": "20201234"
}
```

---

## 2.3 GET `/api/users/me`
Obtém dados do estudante autenticado.

### Response:
```json
{
  "id": 12,
  "nome": "Maria Silva",
  "email": "maria@example.com",
  "numero": "20201234"
}
```

---

## 2.4 PUT `/api/users/me`
Edita os dados pessoais do estudante.

### Request:
```json
{
  "nome": "Maria S. Silva",
  "email": "maria.silva@example.com"
}
```

---

## 2.5 GET `/api/questions/code/{codigo}`

### Response:
```json
{
  "type": "QUESTION_OK",
  "data": {
    "id": 31,
    "enunciado": "Qual destas classes utiliza TCP em Java?",
    "opcoes": [
      { "letra": "a", "texto": "Socket" },
      { "letra": "b", "texto": "ServerSocket" },
      { "letra": "c", "texto": "DatagramSocket" }
    ]
  }
}
```

---

## 2.6 POST `/api/questions/{id}/answers`

### Request:
```json
{
  "opcao": "a"
}
```

### Response:
```json
{
  "type": "ANSWER_OK",
  "message": "Resposta submetida com sucesso."
}
```

---

## 2.7 GET `/api/students/me/answers?estado=expirada`

### Response:
```json
{
  "type": "ANSWERS_LIST_OK",
  "data": [
    {
      "id": 31,
      "enunciado": "Qual destas classes utiliza TCP em Java?",
      "resposta_estudante": "a",
      "correta": true,
      "data_resposta": "2025-11-23 10:02"
    }
  ]
}
```

---

## 2.8 POST `/api/auth/logout`

### Response:
```json
{
  "type": "LOGOUT_OK",
  "message": "Sessão terminada."
}
```

---

# 3. Interface RMI – Métodos Remotos para Estudantes

A interface RMI mantém as mesmas funcionalidades.  
Nos métodos autenticados, o token é sempre o **primeiro argumento**.

```java
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
```

---

# 4. Descrição dos Métodos RMI

### `login(email, password)`
Autentica o estudante e devolve o token.

### `registerStudent(nome, email, pass, numero)`
Regista um novo estudante no sistema.

---

### `getUserData(token)`
Devolve os dados do estudante autenticado.

### `editUserData(token, novosDados)`
Atualiza os dados pessoais do estudante.

---

### `getQuestionByCode(token, codigo)`
Obtém a pergunta ativa associada ao código.

### `submitAnswer(token, perguntaId, opcao)`
Submete a resposta do estudante à pergunta indicada.

### `listAnsweredExpired(token)`
Lista as perguntas expiradas já respondidas pelo estudante.

---


