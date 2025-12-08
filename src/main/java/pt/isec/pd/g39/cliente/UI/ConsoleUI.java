package pt.isec.pd.g39.cliente.UI;

import pt.isec.pd.g39.cliente.ClientViewManager;

import java.util.*;

public class ConsoleUI {
    private final ClientViewManager manager;
    private final Scanner scanner = new Scanner(System.in);

    private int userId;

    public ConsoleUI(ClientViewManager manager) {
        this.manager = manager;
    }

    public void start() {
        String choice;
        while (true) {
            System.out.println("Registo ou Login?");
            choice = scanner.nextLine().trim().toLowerCase();
            if (choice.equals("login") || choice.equals("registo")) break;
            System.out.println("Opção inválida. Por favor, escolha 'Registo' ou 'Login'.");
        }
        userId = -1;
        boolean isDocente;

        if (choice.equals("registo")) {
            while (true) {
                System.out.print("Email: ");
                String email = scanner.nextLine().trim();
                System.out.print("Password: ");
                String password = scanner.nextLine().trim();
                System.out.print("Nome: ");
                String nome = scanner.nextLine().trim();
                String role;
                while (true) {
                    System.out.print("Estudante (S) ou Docente (D)? ");
                    role = scanner.nextLine().trim().toLowerCase();
                    if (role.equals("s") || role.equals("d")) break;
                    System.out.println("Opção inválida. Introduza 'S' para Estudante ou 'D' para Docente.");
                }

                Map<String, Object> resp;
                if (role.equals("s")) {
                    String numero;
                    while (true) {
                        System.out.print("Numero de estudante? ");
                        numero = scanner.nextLine().trim();
                        if (!numero.isBlank() && numero.matches("\\d+")) break;
                        System.out.println("Número inválido. Introduza apenas dígitos.");
                    }
                    resp = manager.registerStudent(nome, email, password, numero);
                } else {
                    System.out.print("Secret Code: ");
                    String secret = scanner.nextLine().trim();
                    resp = manager.registerTeacher(nome, email, password, secret);
                }

                System.out.println(resp.getOrDefault("message", resp.get("type")));

                String type = (String) resp.getOrDefault("type", "");
                if ("REGISTER_OK".equals(type)) {
                    System.out.println("Prossiga a fazer login.\n");
                    break;
                } else {
                    String retryChoice;
                    while (true) {
                        System.out.print("Registo falhou. Deseja tentar novamente? (s/n): ");
                        retryChoice = scanner.nextLine().trim().toLowerCase();
                        if (retryChoice.equals("s") || retryChoice.equals("n")) break;
                        System.out.println("Opção inválida. Introduza 's' para sim ou 'n' para não.");
                    }
                    if (retryChoice.equals("n")) {
                        return;
                    }
                }
            }
        }

        System.out.print("Email: ");
        String email = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine().trim();
        var loginResp = manager.login(email, password);
        String type = (String) loginResp.get("type");
        if (!"LOGIN_OK".equals(type)) {
            System.out.println("Login failed: " + loginResp.getOrDefault("message", ""));
            return;
        }
        Object idObj = loginResp.get("id");
        if (idObj instanceof Number) userId = ((Number) idObj).intValue();
        String perfil = (String) loginResp.getOrDefault("perfil", "");
        isDocente = "docente".equalsIgnoreCase(perfil);
        System.out.println("Login efetuado com sucesso. -> userId= " + userId + " -> perfil= " + perfil);

        boolean done = false;
        while (!done) {
            System.out.println("O que fazer:");
            if (isDocente) {
                System.out.println("1 -> Editar dados do docente");
                System.out.println("2 -> Criar uma pergunta");
                System.out.println("3 -> Editar Pergunta");
                System.out.println("4 -> Eliminar Perguntas");
                System.out.println("5 -> Listar Perguntas c/Filtro (Ativas/Futuras/Expiradas)");
                System.out.println("6 -> Ver respostas de perguntas expiradas");
                System.out.println("7 -> Log Out");

                String ch = scanner.nextLine().trim();
                switch (ch) {
                    case "1": handleEditDataUser(isDocente); break;
                    case "2": handleCreateQuestion(); break;
                    case "3": handleEditQuestion(); break;
                    case "4": handleDeleteQuestion(); break;
                    case "5": handleListQuestionsFilter(); break;
                    case "6": handleViewExpiredAnswers(); break;
                    case "7": done = true; break;
                    default: System.out.println("Opção inválida."); break;
                }
            } else {
                System.out.println("1 -> Editar dados do estudante");
                System.out.println("2 -> Responder a uma pergunta");
                System.out.println("3 -> Consultar perguntas respondidas(expiradas)");
                System.out.println("4 -> LogOut");

                String ch = scanner.nextLine().trim();
                switch (ch) {
                    case "1": handleEditDataUser( isDocente); break;
                    case "2": handleAnswerQuestion(); break;
                    case "3": handleListAnsweredExpired(); break;
                    case "4": done = true; break;
                    default: System.out.println("Opção inválida."); break;
                }
            }
        }
        System.out.println("A terminar.");
    }

    private void handleCreateQuestion() {
        System.out.print("Enunciado: ");
        String enunciado = scanner.nextLine();
        System.out.print("Data início (yyyy-MM-dd HH:mm): ");
        String di = scanner.nextLine();
        System.out.print("Data fim (yyyy-MM-dd HH:mm): ");
        String df = scanner.nextLine();
        int n;
        while (true) {
            System.out.print("Quantas opções? ");
            String line = scanner.nextLine().trim();
            try {
                n = Integer.parseInt(line);
                if (n <= 0) {
                    System.out.println("Por favor insira um inteiro positivo.");
                    continue;
                }
                break;
            } catch (NumberFormatException e) {
                System.out.println("Numero invalido!");
            }
        }
        List<Map<String, Object>> opcoes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String letra = String.valueOf((char) ('A' + i));
            System.out.println("Opção " + letra);
            System.out.print("Texto: ");
            String texto = scanner.nextLine();
            System.out.print("É a correta? (s/n): ");
            boolean correta = scanner.nextLine().trim().equalsIgnoreCase("s");
            opcoes.add(Map.of("letra", letra, "texto", texto, "correta", correta));
        }
        var resp = manager.createQuestion(userId, enunciado, di, df, opcoes);
        System.out.println(resp.getOrDefault("message", resp.get("type")));
        System.out.println("Código de acesso da pergunta: " + resp.get("codigo_acesso"));
    }

    private void handleEditQuestion() {
        var resp = manager.listQuestions(userId);
        if (!"LIST_QUESTIONS_OK".equals(resp.get("type"))) {
            System.out.println("Erro: " + resp.getOrDefault("message", ""));
            return;
        }
        List<Map<String, Object>> perguntas = (List<Map<String, Object>>) resp.get("perguntas");
        if (perguntas.isEmpty()) {
            System.out.println("Sem perguntas.");
            return;
        }
        for (int i = 0; i < perguntas.size(); i++) {
            System.out.println((i + 1) + " -> " + perguntas.get(i).get("enunciado"));
        }
        System.out.print("Escolha número: ");
        int idx = Integer.parseInt(scanner.nextLine()) - 1;
        int perguntaId = ((Number) perguntas.get(idx).get("id")).intValue();

        var getResp = manager.getQuestionForEdit(perguntaId);
        if (!"GET_QUESTION_OK".equals(getResp.get("type"))) {
            System.out.println("Erro ao obter pergunta.");
            return;
        }
        String enunciado = (String) getResp.get("enunciado");
        String dataInicio = (String) getResp.get("data_inicio");
        String dataFim = (String) getResp.get("data_fim");
        List<Map<String, Object>> opcoes = (List<Map<String, Object>>) getResp.get("opcoes");

        System.out.println("Novo enunciado (ENTER mantém): " + enunciado);
        String novoEn = scanner.nextLine(); if (novoEn.isBlank()) novoEn = enunciado;
        System.out.println("Nova data início (yyyy-MM-dd HH:mm)(ENTER mantém): " + dataInicio);
        String novaDi = scanner.nextLine(); if (novaDi.isBlank()) novaDi = dataInicio;
        System.out.println("Nova data fim (yyyy-MM-dd HH:mm)(ENTER mantém): " + dataFim);
        String novaDf = scanner.nextLine(); if (novaDf.isBlank()) novaDf = dataFim;

        for (Map<String, Object> op : opcoes) {
            System.out.println("Opção " + op.get("letra") + ": " + op.get("texto"));
            System.out.print("Novo texto (ENTER mantém): ");
            String nt = scanner.nextLine();
            if (!nt.isBlank()) op.put("texto", nt);
            System.out.print("É a correta? (s/n, ENTER mantém): ");
            String cor = scanner.nextLine().trim();
            if (cor.equalsIgnoreCase("s")) op.put("correta", true);
            else if (cor.equalsIgnoreCase("n")) op.put("correta", false);
        }

        var editResp = manager.editQuestion(perguntaId, novoEn, novaDi, novaDf, opcoes);
        System.out.println(editResp.getOrDefault("message", editResp.get("type")));
    }

    private void handleDeleteQuestion() {
        var resp = manager.listQuestions(userId);
        if (!"LIST_QUESTIONS_OK".equals(resp.get("type"))) {
            System.out.println("Erro: " + resp.getOrDefault("message", ""));
            return;
        }
        List<Map<String, Object>> perguntas = (List<Map<String, Object>>) resp.get("perguntas");
        for (int i = 0; i < perguntas.size(); i++) {
            System.out.println(i + " -> " + perguntas.get(i).get("enunciado"));
        }
        System.out.print("Escolha a pergunta a eliminar: ");
        int escolha = Integer.parseInt(scanner.nextLine());
        int perguntaId = ((Number) perguntas.get(escolha).get("id")).intValue();
        var del = manager.deleteQuestion(perguntaId);
        System.out.println(del.getOrDefault("message", del.get("type")));
    }

    private void handleListQuestionsFilter() {
        System.out.print("Deseja consultar perguntas ativas, futuras ou expiradas? (A/F/E): ");
        String choice = scanner.nextLine().trim().toLowerCase();
        var resp = manager.listQuestionsFilter(choice);
        if (!"LIST_QUESTIONS_FILTER_OK".equals(resp.get("type"))) {
            System.out.println("Erro: " + resp.getOrDefault("message", ""));
            return;
        }
        List<Map<String, Object>> perguntas = (List<Map<String, Object>>) resp.get("perguntas");
        for (int i = 0; i < perguntas.size(); i++) {
            var p = perguntas.get(i);
            System.out.println(i + " -> " + p.get("enunciado") + " // " + p.get("data_inicio") + " até " + p.get("data_fim") + "// Código: " + p.get("codigo_acesso"));
        }
    }

    private void handleViewExpiredAnswers() {
        Scanner sc = new Scanner(System.in);

        var resp = manager.listQuestionsFilter("e");
        if (!"LIST_QUESTIONS_FILTER_OK".equals(resp.get("type"))) {
            System.out.println("Erro: " + resp.getOrDefault("message", "Erro ao obter perguntas expiradas."));
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> perguntas = (List<Map<String, Object>>) resp.get("perguntas");
        if (perguntas == null || perguntas.isEmpty()) {
            System.out.println("Não existem perguntas expiradas.");
            return;
        }

        System.out.println("\nPerguntas expiradas:");
        for (int i = 0; i < perguntas.size(); i++) {
            System.out.println(i + " -> " + perguntas.get(i).get("enunciado"));
        }

        System.out.print("Escolha a pergunta: ");
        int escolha;
        try {
            escolha = Integer.parseInt(sc.nextLine().trim());
        } catch (NumberFormatException ex) {
            System.out.println("Escolha inválida.");
            return;
        }
        Object idObj = perguntas.get(escolha).get("id");
        int perguntaId = (idObj instanceof Number) ? ((Number) idObj).intValue() : Integer.parseInt(String.valueOf(idObj));

        var detalheResp = manager.listQuestionAnswers(perguntaId);
        if (!"LIST_QUESTION_ANSWERS_OK".equals(detalheResp.get("type"))) {
            System.out.println("Erro: " + detalheResp.getOrDefault("message", "Erro ao obter respostas."));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> pergunta = (Map<String, Object>) detalheResp.get("pergunta");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> opcoes = (List<Map<String, Object>>) detalheResp.get("opcoes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> respostas = (List<Map<String, Object>>) detalheResp.get("respostas");
        Object pctObj = detalheResp.get("percentagem_certas");
        double percentagem = (pctObj instanceof Number) ? ((Number) pctObj).doubleValue() : 0.0;

        System.out.println("\n============================");
        System.out.println("Enunciado: " + pergunta.get("enunciado"));
        System.out.println("Início: " + pergunta.get("data_inicio"));
        System.out.println("Fim: " + pergunta.get("data_fim"));

        System.out.println("\nOpções:");
        for (Map<String, Object> op : opcoes) {
            boolean correta = Boolean.TRUE.equals(op.get("correta"));
            System.out.println(op.get("letra") + ") " + op.get("texto") + (correta ? " (correta)" : ""));
        }

        System.out.println("\nRespostas submetidas:");
        if (respostas != null) {
            for (Map<String, Object> r : respostas) {
                System.out.println("---------------------");
                System.out.println("Estudante: " + r.get("numero") + " | " + r.get("nome"));
                System.out.println("Email: " + r.get("email"));
                System.out.println("Resposta: " + r.get("opcao_escolhida"));
                System.out.println("Correta?: " + (((Boolean) r.get("correta")) ? "SIM" : "NÃO"));
                System.out.println("Data: " + r.get("data_resposta"));
            }
        }
        System.out.println("\nPercentagem de corretas: " + percentagem + "%");
        System.out.println("============================\n");

        System.out.print("Deseja exportar a pergunta para um ficheiro CSV? (s/n): ");
        String exportChoice = sc.nextLine().trim().toLowerCase();
        while (!exportChoice.equals("s") && !exportChoice.equals("n")) {
            System.out.print("Opção inválida. Introduza 's' para sim ou 'n' para não: ");
            exportChoice = sc.nextLine().trim().toLowerCase();
        }

        if (exportChoice.equals("s")) {
            try {
                java.nio.file.Path resourcesDir = java.nio.file.Paths.get("src", "main", "resources");
                java.nio.file.Files.createDirectories(resourcesDir);

                String perguntaIdStr = String.valueOf(pergunta.get("id"));
                java.nio.file.Path file = resourcesDir.resolve("pergunta_" + perguntaIdStr + ".csv");

                java.util.function.Function<String, String> esc = s -> {
                    if (s == null) return "";
                    return s.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ");
                };

                try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(
                        file,
                        java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {

                    writer.write("\"enunciado\";\"" + esc.apply((String) pergunta.get("enunciado")) + "\"");
                    writer.newLine();
                    writer.write("\"data_inicio\";\"" + esc.apply((String) pergunta.get("data_inicio")) + "\"");
                    writer.newLine();
                    writer.write("\"data_fim\";\"" + esc.apply((String) pergunta.get("data_fim")) + "\"");
                    writer.newLine();
                    writer.newLine();

                    writer.write("\"opção\";\"texto da opção\"");
                    writer.newLine();
                    for (Map<String, Object> op : opcoes) {
                        String letra = String.valueOf(op.get("letra"));
                        String texto = esc.apply(String.valueOf(op.get("texto")));
                        writer.write("\"" + letra + "\";\"" + texto + "\"");
                        writer.newLine();
                    }
                    writer.newLine();

                    writer.write("\"número de estudante\";\"nome\";\"e-mail\";\"resposta\"");
                    writer.newLine();
                    if (respostas != null) {
                        for (Map<String, Object> r : respostas) {
                            writer.write("\"" + esc.apply(String.valueOf(r.get("numero"))) + "\";\"" +
                                    esc.apply(String.valueOf(r.get("nome"))) + "\";\"" +
                                    esc.apply(String.valueOf(r.get("email"))) + "\";\"" +
                                    esc.apply(String.valueOf(r.get("opcao_escolhida"))) + "\"");
                            writer.newLine();
                        }
                    }
                }

                System.out.println("Pergunta exportada para: " + file.toAbsolutePath());
            } catch (Exception e) {
                System.err.println("Erro ao exportar a pergunta: " + e.getMessage());
            }
        }
    }


    private void handleAnswerQuestion() {
        System.out.print("\nIntroduza o código da pergunta (ex: 9H45G1): ");
        String codigo = scanner.nextLine().trim();
        if (codigo.isEmpty()) return;

        var resp = manager.getQuestionByCode(codigo, userId);
        if (!"GET_QUESTION_OK".equals(resp.get("type"))) {
            System.out.println("Erro: " + resp.getOrDefault("message", ""));
            return;
        }

        String enunciado = (String) resp.get("enunciado");
        int perguntaId = ((Number) resp.get("id")).intValue();
        List<Map<String, String>> opcoes = (List<Map<String, String>>) resp.get("opcoes");

        System.out.println("\nPERGUNTA: " + enunciado);
        for (Map<String, String> op : opcoes) {
            System.out.println("[" + op.get("letra") + "] " + op.get("texto"));
        }

        String escolha;
        while (true) {
            System.out.print("\nA sua resposta (letra): ");
            String attempt = scanner.nextLine().trim().toUpperCase();
            boolean valida = opcoes.stream().anyMatch(o -> o.get("letra").equalsIgnoreCase(attempt));
            if (valida) {
                escolha = attempt;
                break;
            }
            System.out.println("Opção inválida. Tente novamente.");
        }

        var submit = manager.submitAnswer(userId, perguntaId, escolha);
        System.out.println(submit.getOrDefault("message", submit.get("type")));
    }

    private void handleListAnsweredExpired() {
        System.out.print("Filtrar por data (ENTER para ignorar): ");
        String filtro = scanner.nextLine();
        var resp = manager.listAnsweredExpired(userId, filtro);
        if (!"LIST_ANSWERED_EXPIRED_OK".equals(resp.get("type"))) {
            System.out.println("Erro: " + resp.getOrDefault("message", ""));
            return;
        }
        List<Map<String, Object>> perguntas = (List<Map<String, Object>>) resp.get("perguntas");
        for (Map<String, Object> p : perguntas) {
            System.out.println("-----");
            System.out.println("Pergunta: " + p.get("enunciado"));
            System.out.println("Data fim: " + p.get("data_fim"));
            System.out.println("Resposta dada: " + p.get("resposta_dada"));
            System.out.println("Correta?: " + (((Boolean) p.get("correta")) ? "SIM" : "Não"));
            System.out.println("Data resposta: " + p.get("data_resposta"));
        }
    }

    private void handleEditDataUser(boolean isDocente) {
        System.out.println("\n=== Editar dados de utilizador ===");

        if (isDocente) {
            System.out.println("Editar dados de DOCENTE.");

            var dadosResp = manager.getUserData("docente", userId);
            if (!"GET_USER_DATA_OK".equals(dadosResp.get("type"))) {
                System.out.println("Erro ao obter dados do docente.");
                return;
            }

            String oldNome = (String) dadosResp.get("nome");
            String oldEmail = (String) dadosResp.get("email");
            String oldPassword = (String) dadosResp.get("password");

            System.out.println("Nome atual: " + oldNome);
            System.out.print("Novo nome (ENTER mantém): ");
            String nome = scanner.nextLine().trim();
            if (nome.isBlank()) nome = oldNome;

            System.out.println("E-mail atual: " + oldEmail);
            System.out.print("Novo e-mail (ENTER mantém): ");
            String email = scanner.nextLine().trim();
            if (email.isBlank()) email = oldEmail;

            System.out.println("Password atual: (oculta)");
            System.out.print("Nova password (ENTER mantém): ");
            String password = scanner.nextLine().trim();
            if (password.isBlank()) password = oldPassword;

            var resp = manager.editTeacher(userId, nome, email, password);
            System.out.println(resp.getOrDefault("message", resp.get("type")));
        }
        else {
            System.out.println("Editar dados de ESTUDANTE.");

            var dadosResp = manager.getUserData("estudante", userId);
            if (!"GET_USER_DATA_OK".equals(dadosResp.get("type"))) {
                System.out.println("Erro ao obter dados do estudante.");
                return;
            }

            String oldNome = (String) dadosResp.get("nome");
            String oldEmail = (String) dadosResp.get("email");
            String oldPassword = (String) dadosResp.get("password");
            Object numObj = dadosResp.get("id");
            int oldNumero;
            if (numObj instanceof Double d) {
                oldNumero = d.intValue();
            } else if (numObj instanceof Number n) {
                oldNumero = n.intValue();
            } else {
                throw new IllegalStateException("Valor 'numero' inválido no JSON: " + numObj);
            }

            System.out.println("Número atual: " + oldNumero);
            System.out.print("Novo número (ENTER mantém): ");
            String numStr = scanner.nextLine().trim();
            int novoNumero = numStr.isBlank() ? oldNumero : Integer.parseInt(numStr);

            System.out.println("Nome atual: " + oldNome);
            System.out.print("Novo nome (ENTER mantém): ");
            String nome = scanner.nextLine().trim();
            if (nome.isBlank()) nome = oldNome;

            System.out.println("E-mail atual: " + oldEmail);
            System.out.print("Novo e-mail (ENTER mantém): ");
            String email = scanner.nextLine().trim();
            if (email.isBlank()) email = oldEmail;

            System.out.println("Password atual: (oculta)");
            System.out.print("Nova password (ENTER mantém): ");
            String password = scanner.nextLine().trim();
            if (password.isBlank()) password = oldPassword;

            var resp = manager.editStudent(userId, novoNumero, nome, email, password);
            System.out.println(resp.getOrDefault("message", resp.get("type")));
            if(resp.getOrDefault("type", "").equals("EDIT_USER_OK")){
                userId = novoNumero;
            }
        }

        System.out.println("=================================\n");
    }

}
