# Planeamento da API REST

Este documento define uma API REST equivalente às operações já implementadas no sistema
(tal como existem no `ClientHandler`), mas expressas em estilo REST.
Não existe implementação REST — apenas planeamento.

Todas as operações autenticadas usam:

```
Authorization: Bearer <token>
```

---

# 1. Autenticação e Registo

## POST /api/auth/login
Autentica um estudante ou docente.

### Request JSON
```json
{
  "email": "user@example.com",
  "password": "1234"
}
```

### Response JSON
```json
{
  "type": "LOGIN_OK",
  "id": 15,
  "perfil": "estudante",
  "token": "abc.def.ghi"
}
```

---

## POST /api/students
Registo de estudante.

### Request JSON
```json
{
  "nome": "João Silva",
  "email": "joao@gmail.com",
  "password": "1234",
  "numero": "20201234"
}
```

---

## POST /api/teachers
Registo de docente.

### Request JSON
```json
{
  "nome": "Ana Costa",
  "email": "ana@isec.pt",
  "password": "abcd",
  "secret": "CODIGO_DOCENTE"
}
```

---

# 2. Dados do Utilizador

## GET /api/users/me
Obtém dados do utilizador autenticado.

### Response JSON
```json
{
  "id": 15,
  "nome": "João Silva",
  "email": "joao@gmail.com",
  "perfil": "estudante"
}
```

---

## PUT /api/users/me
Edita dados do utilizador.

### Request JSON
```json
{
  "nome": "João S. Pereira",
  "email": "joaop@gmail.com"
}
```

---

# 3. Operações do Docente

## POST /api/questions
Cria uma pergunta nova.

### Request JSON
```json
{
  "enunciado": "Que classe Java envia dados TCP?",
  "data_inicio": "2025-11-23 10:00",
  "data_fim": "2025-11-23 10:05",
  "opcoes": [
    {"letra": "a", "texto": "Socket", "correta": true},
    {"letra": "b", "texto": "ServerSocket"},
    {"letra": "c", "texto": "DatagramSocket"}
  ]
}
```

---

## GET /api/questions
Lista perguntas criadas pelo docente.

---

## GET /api/questions/{id}
Obtém dados de uma pergunta para edição.

---

## PUT /api/questions/{id}
Edita uma pergunta existente.

---

## DELETE /api/questions/{id}
Elimina uma pergunta, se não tiver respostas.

---

## GET /api/questions/filter?estado=a|f|e
Lista perguntas por estado:
- `a` → ativas
- `f` → futuras
- `e` → expiradas

---

## GET /api/questions/{id}/answers
Lista todas as respostas submetidas a uma pergunta expirada.

---

# 4. Operações do Estudante

## GET /api/questions/code/{codigo}
Obtém uma pergunta ativa usando o código de acesso.

### Response JSON
```json
{
  "id": 31,
  "enunciado": "Que classe usa TCP?",
  "opcoes": [
    {"letra": "a", "texto": "Socket"},
    {"letra": "b", "texto": "ServerSocket"},
    {"letra": "c", "texto": "DatagramSocket"}
  ]
}
```

---

## POST /api/questions/{id}/answers
Submete uma resposta.

### Request JSON
```json
{
  "opcao": "a"
}
```

---

## GET /api/students/me/answers?estado=expirada
Lista perguntas respondidas cujo período já expirou.

---

# 5. Estrutura Geral das Respostas
Todas as respostas seguem o padrão interno usado no sistema real:

```json
{
  "type": "XXX_OK",
  "message": "Mensagem opcional",
  "data": { ... }
}
```

---
