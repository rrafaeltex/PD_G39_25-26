package pt.isec.pd.g39.servidor.database;

import pt.isec.pd.g39.servidor.HeartbeatManager;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Database {

    private static String dbPath;

    // Chamada no arranque do servidor com o diretório da BD
    public static void configure(String dbFilePath) {
        dbPath = dbFilePath;
    }

    private static String url() {
        return "jdbc:sqlite:" + dbPath;
    }

    // Inicializa BD: cria tabelas se não existirem e garante db_version
    public static void initializeIfNeeded() throws SQLException {
        File f = new File(dbPath);
        boolean createNew = !f.exists();

        try (Connection conn = DriverManager.getConnection(url());
             Statement stmt = conn.createStatement()) {

            if (createNew) {
                System.out.println("Criar nova base de dados → versão 0");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS config (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL,
                        docente_hash TEXT NOT NULL
                    )
                """);

                String codigoDocente = "DOC2025";
                String hash = HashUtil.hash(codigoDocente);

                stmt.execute("INSERT INTO config (key, value, docente_hash) VALUES ('db_version', '0', '" + hash + "');");


                createSchema(stmt);

            } else {
                System.out.println("Usar base de dados existente: " + dbPath);
            }
        }
    }

    /**
     * Cria o schema base exigido pelo enunciado.
     * Adapta as tabelas conforme o modelo ER do enunciado.
     */
    private static void createSchema(Statement stmt) throws SQLException {

        // DOCENTE
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS docente (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nome TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL
            );
        """);

        // ESTUDANTE
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS estudante (
                numero INTEGER PRIMARY KEY,
                nome TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL
            );
        """);

        // PERGUNTA
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS pergunta (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                docente_id INTEGER NOT NULL,
                enunciado TEXT NOT NULL,
                data_inicio TEXT NOT NULL,
                data_fim TEXT NOT NULL,
                codigo_acesso TEXT NOT NULL UNIQUE,
                FOREIGN KEY (docente_id) REFERENCES docente(id)
            );
        """);

        // OPÇÃO
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS opcao (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pergunta_id INTEGER NOT NULL,
                letra TEXT NOT NULL,
                texto TEXT NOT NULL,
                correta BOOLEAN NOT NULL,
                FOREIGN KEY (pergunta_id) REFERENCES pergunta(id)
            );
        """);

        // RESPOSTA
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS resposta (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                estudante_numero INTEGER NOT NULL,
                pergunta_id INTEGER NOT NULL,
                opcao_escolhida TEXT NOT NULL,
                data_resposta TEXT NOT NULL,
                FOREIGN KEY (estudante_numero) REFERENCES estudante(numero),
                FOREIGN KEY (pergunta_id) REFERENCES pergunta(id)
            );
        """);
    }

    //
// Registar estudante ou professor

    public static boolean registerEstudante(int numero, String nome, String email, String password) {
        String sql = "INSERT INTO estudante (numero, nome, email, password) VALUES (" +
                numero + ", '" + nome + "', '" + email + "', '" + password + "')";

        try {
            // Usamos executeLocalUpdate para garantir que a versão da BD sobe!
            executeLocalUpdate(sql);
            return true;
        } catch (SQLException e) {
            System.err.println("Erro ao registar estudante: " + e.getMessage());
            return false;
        }
    }

    public static boolean registerDocente(String nome, String email, String password) {
        // Nota: O enunciado diz que o registo de docente precisa de um código secreto,
        // mas isso valida-se ANTES de chamar esta função. Aqui só guardamos.
        String sql = "INSERT INTO docente (nome, email, password) VALUES ('" +
                nome + "', '" + email + "', '" + password + "')";

        try {
            executeLocalUpdate(sql);
            return true;
        } catch (SQLException e) {
            System.err.println("Erro ao registar docente: " + e.getMessage());
            return false;
        }
    }

    public static String criarPergunta(int docenteId, String enunciado,
                                       String dataInicio, String dataFim,
                                       List<Map<String, Object>> opcoes) {

        DatabaseWriteLock.waitIfLocked();

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url());
            conn.setAutoCommit(false);

            // 1) Gerar código
            String codigoAcesso;
            while (true) {
                codigoAcesso = CodeGenerator.generateCode(6);
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM pergunta WHERE codigo_acesso=?")) {
                    ps.setString(1, codigoAcesso);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next() && rs.getInt(1) == 0)
                        break;
                }
            }

            // 2) Inserir pergunta
            int perguntaId;
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO pergunta (docente_id, enunciado, data_inicio, data_fim, codigo_acesso)
                VALUES (?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, docenteId);
                ps.setString(2, enunciado);
                ps.setString(3, dataInicio);
                ps.setString(4, dataFim);
                ps.setString(5, codigoAcesso);
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                perguntaId = keys.getInt(1);
            }

            // 3) Inserir opções
            for (Map<String, Object> op : opcoes) {
                try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO opcao (pergunta_id, letra, texto, correta)
                    VALUES (?, ?, ?, ?)
                    """)) {
                    ps.setInt(1, perguntaId);
                    ps.setString(2, (String) op.get("letra"));
                    ps.setString(3, (String) op.get("texto"));
                    ps.setBoolean(4, (Boolean) op.get("correta"));
                    ps.executeUpdate();
                }
            }

            conn.commit();

            // Agora sim, incrementa versão e envia heartbeat
            int newVersion = getVersion() + 1;
            setVersion(newVersion);

            HeartbeatManager.sendHeartbeatWithSql(
                    "INSERT/UPDATE PERGUNTA", newVersion
            );

            return codigoAcesso;

        } catch (Exception e) {
            e.printStackTrace();
            try { if (conn != null) conn.rollback(); } catch (Exception ignored) {}
            return null;
        }
    }





    public static Map<String, Object> checkLogin(String email, String password) {
        // Primeiro tenta ver se é Estudante
        String sqlEstudante = "SELECT * FROM estudante WHERE email = ? AND password = ?";

        try (Connection conn = DriverManager.getConnection(url());
             PreparedStatement ps = conn.prepareStatement(sqlEstudante)) {

            ps.setString(1, email);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // Encontrou estudante!
                return Map.of(
                        "sucesso", true,
                        "nome", rs.getString("nome"),
                        "perfil", "estudante",
                        "id", rs.getInt("numero") // O ID do estudante é o número
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Se não for estudante, tenta ver se é Docente
        String sqlDocente = "SELECT * FROM docente WHERE email = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(url());
             PreparedStatement ps = conn.prepareStatement(sqlDocente)) {

            ps.setString(1, email);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // Encontrou docente!
                return Map.of(
                        "sucesso", true,
                        "nome", rs.getString("nome"),
                        "perfil", "docente",
                        "id", rs.getInt("id")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static List<Map<String, Object>> listarPerguntas(int docenteId) {
        List<Map<String, Object>> lista = new java.util.ArrayList<>();

        String sql = "SELECT id, enunciado, data_inicio, data_fim, codigo_acesso " +
                "FROM pergunta WHERE docente_id=? ORDER BY id";

        try (Connection conn = DriverManager.getConnection(url());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, docenteId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                lista.add(Map.of(
                        "id", rs.getInt("id"),
                        "enunciado", rs.getString("enunciado"),
                        "data_inicio", rs.getString("data_inicio"),
                        "data_fim", rs.getString("data_fim"),
                        "codigo_acesso", rs.getString("codigo_acesso")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return lista;
    }

    public static boolean perguntaTemRespostas(int perguntaId) {
        String sql = "SELECT COUNT(*) FROM resposta WHERE pergunta_id=?";

        try (Connection conn = DriverManager.getConnection(url());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, perguntaId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    public static Map<String, Object> getPerguntaCompleta(int perguntaId) {
        Map<String, Object> pergunta = new java.util.HashMap<>();

        String sqlPergunta =
                "SELECT id, enunciado, data_inicio, data_fim FROM pergunta WHERE id=?";

        String sqlOpcoes =
                "SELECT id, letra, texto, correta FROM opcao WHERE pergunta_id=? ORDER BY letra";

        try (Connection conn = DriverManager.getConnection(url())) {

            // Carregar dados da pergunta
            try (PreparedStatement ps = conn.prepareStatement(sqlPergunta)) {
                ps.setInt(1, perguntaId);
                ResultSet rs = ps.executeQuery();

                if (!rs.next())
                    return null;

                pergunta.put("id", rs.getInt("id"));
                pergunta.put("enunciado", rs.getString("enunciado"));
                pergunta.put("data_inicio", rs.getString("data_inicio"));
                pergunta.put("data_fim", rs.getString("data_fim"));
            }

            // Carregar opções
            List<Map<String, Object>> opcoes = new java.util.ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(sqlOpcoes)) {
                ps.setInt(1, perguntaId);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    opcoes.add(Map.of(
                            "id", rs.getInt("id"),
                            "letra", rs.getString("letra"),
                            "texto", rs.getString("texto"),
                            "correta", rs.getBoolean("correta")
                    ));
                }
            }

            pergunta.put("opcoes", opcoes);

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        return pergunta;
    }

    public static boolean editarPergunta(int perguntaId, String enunciado,
                                         String dataInicio, String dataFim,
                                         List<Map<String, Object>> novasOpcoes) {

        DatabaseWriteLock.waitIfLocked();
        try {
            // update pergunta
            String sql1 = "UPDATE pergunta SET enunciado='" + enunciado +
                    "', data_inicio='" + dataInicio +
                    "', data_fim='" + dataFim +
                    "' WHERE id=" + perguntaId;

            executeLocalUpdate(sql1);

            // apagar opções antigas
            String sql2 = "DELETE FROM opcao WHERE pergunta_id=" + perguntaId;
            executeLocalUpdate(sql2);

            // inserir novas
            for (Map<String, Object> op : novasOpcoes) {
                String sql3 = "INSERT INTO opcao (pergunta_id, letra, texto, correta) VALUES (" +
                        perguntaId + ", '" + op.get("letra") + "', '" + op.get("texto") +
                        "', " + (((Boolean) op.get("correta")) ? 1 : 0) + ")";
                executeLocalUpdate(sql3);
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public static boolean eliminarPergunta(int perguntaId) {
        if (perguntaTemRespostas(perguntaId))
            return false;

        DatabaseWriteLock.waitIfLocked();

        try {
            executeLocalUpdate("DELETE FROM opcao WHERE pergunta_id=" + perguntaId);
            executeLocalUpdate("DELETE FROM pergunta WHERE id=" + perguntaId);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public static List<Map<String, Object>> listarPerguntasFiltradas(String filtro) {
        List<Map<String, Object>> lista = new java.util.ArrayList<>();

        String sql;
        String f = filtro == null ? "" : filtro.trim().toLowerCase();
        switch (f) {
            case "a":
                sql = "SELECT id, enunciado, data_inicio, data_fim, codigo_acesso " +
                        "FROM pergunta " +
                        "WHERE datetime(data_inicio) <= datetime('now') AND datetime(data_fim) >= datetime('now') " +
                        "ORDER BY id";
                break;
            case "f":
                sql = "SELECT id, enunciado, data_inicio, data_fim, codigo_acesso " +
                        "FROM pergunta " +
                        "WHERE datetime(data_inicio) > datetime('now') " +
                        "ORDER BY id";
                break;
            case "e":
                sql = "SELECT id, enunciado, data_inicio, data_fim, codigo_acesso " +
                        "FROM pergunta " +
                        "WHERE datetime(data_fim) < datetime('now') " +
                        "ORDER BY id";
                break;
            default:
                sql = "SELECT id, enunciado, data_inicio, data_fim, codigo_acesso " +
                        "FROM pergunta ORDER BY id";
        }
        try (Connection conn = DriverManager.getConnection(url());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                lista.add(Map.of(
                        "id", rs.getInt("id"),
                        "enunciado", rs.getString("enunciado"),
                        "data_inicio", rs.getString("data_inicio"),
                        "data_fim", rs.getString("data_fim"),
                        "codigo_acesso", rs.getString("codigo_acesso")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }



    public static List<Map<String, Object>> listarPerguntasRespondidasExpiradas(int alunoId, String filtroData) {
        List<Map<String, Object>> lista = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
        SELECT 
            p.id AS pergunta_id,
            p.enunciado,
            p.data_inicio,
            p.data_fim,
            r.opcao_escolhida,
            r.data_resposta,
            o.correta AS resposta_certa
        FROM pergunta p
        JOIN resposta r ON p.id = r.pergunta_id
        JOIN opcao o 
            ON o.pergunta_id = p.id
           AND o.letra = r.opcao_escolhida
        WHERE r.estudante_numero = ?
          AND datetime(p.data_fim) < datetime('now')
    """);

        // Se o aluno quiser filtrar por data (ex: "2025-01-05")
        if (filtroData != null && !filtroData.isBlank()) {
            sql.append(" AND r.data_resposta LIKE ? ");
        }

        sql.append(" ORDER BY r.data_resposta DESC");

        try (Connection conn = DriverManager.getConnection(url());
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            ps.setInt(1, alunoId);

            if (filtroData != null && !filtroData.isBlank()) {
                ps.setString(2, "%" + filtroData + "%");
            }

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                lista.add(Map.of(
                        "id", rs.getInt("pergunta_id"),
                        "enunciado", rs.getString("enunciado"),
                        "data_inicio", rs.getString("data_inicio"),
                        "data_fim", rs.getString("data_fim"),
                        "resposta_dada", rs.getString("opcao_escolhida"),
                        "correta", rs.getBoolean("resposta_certa"),
                        "data_resposta", rs.getString("data_resposta")
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return lista;
    }


    public static Map<String, Object> listarRespostasPerguntaExpirada(int perguntaId) {

        String sqlPergunta = """
        SELECT id, enunciado, data_inicio, data_fim
        FROM pergunta
        WHERE id = ?
          AND datetime(data_fim) < datetime('now')
    """;

        String sqlOpcoes = """
        SELECT letra, texto, correta
        FROM opcao
        WHERE pergunta_id = ?
        ORDER BY letra
    """;

        String sqlRespostas = """
        SELECT r.opcao_escolhida,
               r.data_resposta,
               e.numero,
               e.nome,
               e.email
        FROM resposta r
        JOIN estudante e ON e.numero = r.estudante_numero
        WHERE r.pergunta_id = ?
        ORDER BY r.data_resposta
    """;

        try (Connection conn = DriverManager.getConnection(url())) {

            // 1. Buscar dados da pergunta
            Map<String, Object> pergunta = new HashMap<>();

            try (PreparedStatement ps = conn.prepareStatement(sqlPergunta)) {
                ps.setInt(1, perguntaId);
                ResultSet rs = ps.executeQuery();

                if (!rs.next())
                    return null; // Não existe ou não expirou

                pergunta.put("id", rs.getInt("id"));
                pergunta.put("enunciado", rs.getString("enunciado"));
                pergunta.put("data_inicio", rs.getString("data_inicio"));
                pergunta.put("data_fim", rs.getString("data_fim"));
            }

            // 2. Buscar opções
            List<Map<String, Object>> opcoes = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sqlOpcoes)) {
                ps.setInt(1, perguntaId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    opcoes.add(Map.of(
                            "letra", rs.getString("letra"),
                            "texto", rs.getString("texto"),
                            "correta", rs.getBoolean("correta")
                    ));
                }
            }

            // 3. Buscar respostas
            List<Map<String, Object>> respostas = new ArrayList<>();
            int total = 0;
            int certas = 0;

            try (PreparedStatement ps = conn.prepareStatement(sqlRespostas)) {
                ps.setInt(1, perguntaId);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    total++;

                    // Verificar se é correta
                    boolean correta = false;
                    for (Map<String, Object> op : opcoes) {
                        if (op.get("letra").equals(rs.getString("opcao_escolhida"))) {
                            correta = (Boolean) op.get("correta");
                            break;
                        }
                    }
                    if (correta) certas++;

                    respostas.add(Map.of(
                            "numero", rs.getInt("numero"),
                            "nome", rs.getString("nome"),
                            "email", rs.getString("email"),
                            "opcao_escolhida", rs.getString("opcao_escolhida"),
                            "correta", correta,
                            "data_resposta", rs.getString("data_resposta")
                    ));
                }
            }

            double percentagem = total == 0 ? 0.0 : (certas * 100.0 / total);

            return Map.of(
                    "pergunta", pergunta,
                    "opcoes", opcoes,
                    "respostas", respostas,
                    "total_respostas", total,
                    "percentagem_certas", percentagem
            );

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }



    // -------- Versionamento --------

    public static int getVersion() {
        try (Connection conn = DriverManager.getConnection(url());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT value FROM config WHERE key='db_version'")) {

            if (rs.next())
                return Integer.parseInt(rs.getString("value"));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static void setVersion(int newVersion) {
        try (Connection conn = DriverManager.getConnection(url());
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE config SET value=? WHERE key='db_version'"
             )) {

            ps.setString(1, String.valueOf(newVersion));
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // -------- Execução de queries (principal / secundário) --------

    // Usado pelo servidor principal quando um cliente faz uma operação
    public static void executeLocalUpdate(String sql) throws SQLException {

        DatabaseWriteLock.waitIfLocked();

        try (Connection conn = DriverManager.getConnection(url());
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(sql);


            int v = getVersion() + 1;
            setVersion(v);


            HeartbeatManager.sendHeartbeatWithSql(sql,v);
        }
    }

    // Usado pelos servidores secundários ao receber HBUPDATE com SQL
    public static void applyRemoteUpdate(String sql, int newVersion) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url());
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(sql);
            setVersion(newVersion);
        }
    }

    public static String getPath() {
        return dbPath;
    }

    public static boolean validarCodigoDocente(String codigoInserido) {
        String hashInserido = HashUtil.hash(codigoInserido);

        try (Connection conn = DriverManager.getConnection(url());
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT docente_hash FROM config WHERE key='db_version'")) {

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String hashGuardado = rs.getString("docente_hash");
                return hashInserido.equals(hashGuardado);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }


    public static int getId(String perfil, String email) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url())) {

            if (perfil.equalsIgnoreCase("s")) {

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT numero FROM estudante WHERE email=?")) {

                    ps.setString(1, email);
                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        return rs.getInt("numero");
                    }
                }

            } else if (perfil.equalsIgnoreCase("d")) {

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM docente WHERE email=?")) {

                    ps.setString(1, email);
                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        return rs.getInt("id"); // ID do docente
                    }
                }
            }
        }

        return -1; // não encontrado
    }



    //

    public static Map<String, Object> getPerguntaAtivaPorCodigo(String codigo) {
        // 1. Buscar APENAS pelo código (removemos o filtro de data do SQL)
        String sql = "SELECT id, enunciado, data_inicio, data_fim FROM pergunta WHERE codigo_acesso = ?";

        try (Connection conn = DriverManager.getConnection(url());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, codigo);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // Dados da Base de Dados
                int perguntaId = rs.getInt("id");
                String enunciado = rs.getString("enunciado");
                String inicioStr = rs.getString("data_inicio"); // ex: "2025-11-23 00:30"
                String fimStr = rs.getString("data_fim");       // ex: "2025-11-23 00:40"

                // 2. DEBUG: Vamos ver o que o computador está a ler!
                System.out.println("--- DEBUG HORA ---");
                System.out.println("Pergunta encontrada: " + enunciado);
                System.out.println("Início BD: " + inicioStr);
                System.out.println("Fim BD:    " + fimStr);

                // 3. Validação de Datas em JAVA (Mais seguro)
                try {
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    java.time.LocalDateTime inicio = java.time.LocalDateTime.parse(inicioStr, formatter);
                    java.time.LocalDateTime fim = java.time.LocalDateTime.parse(fimStr, formatter);
                    java.time.LocalDateTime agora = java.time.LocalDateTime.now();

                    System.out.println("Agora:     " + agora.format(formatter)); // Vê se esta hora bate certo!

                    if (agora.isBefore(inicio)) {
                        System.out.println("ERRO: Ainda não começou.");
                        return null;
                    }
                    if (agora.isAfter(fim)) {
                        System.out.println("ERRO: Já acabou.");
                        return null;
                    }

                } catch (Exception e) {
                    System.err.println("Erro ao processar datas (formato errado?): " + e.getMessage());
                    // Se der erro nas datas, deixamos passar ou retornamos null?
                    // Para teste, retornamos null para obrigar a corrigir o formato.
                    return null;
                }

                // Se passou nas datas, vamos buscar as opções
                List<Map<String, Object>> opcoes = new java.util.ArrayList<>();
                try (PreparedStatement psOp = conn.prepareStatement(
                        "SELECT letra, texto FROM opcao WHERE pergunta_id = ? ORDER BY letra")) {
                    psOp.setInt(1, perguntaId);
                    ResultSet rsOp = psOp.executeQuery();
                    while (rsOp.next()) {
                        opcoes.add(Map.of(
                                "letra", rsOp.getString("letra"),
                                "texto", rsOp.getString("texto")
                        ));
                    }
                }

                return Map.of(
                        "id", perguntaId,
                        "enunciado", enunciado,
                        "opcoes", opcoes
                );
            } else {
                System.out.println("DEBUG: Código " + codigo + " não existe na tabela pergunta.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Não encontrada
    }

    //

    public static boolean jaRespondeu(int alunoId, int perguntaId) {
        String sql = "SELECT COUNT(*) FROM resposta WHERE estudante_numero = ? AND pergunta_id = ?";

        try (Connection conn = DriverManager.getConnection(url());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, alunoId);
            ps.setInt(2, perguntaId);

            try (ResultSet rs = ps.executeQuery()) {
                // O COUNT(*) devolve sempre uma linha, mesmo que seja 0
                if (rs.next()) {
                    int count = rs.getInt(1);
                    System.out.println("DEBUG: Aluno " + alunoId + " tem " + count + " respostas na pergunta " + perguntaId);
                    return count > 0;
                }
            }

        } catch (SQLException e) {
            System.err.println("ERRO SQL em jaRespondeu: " + e.getMessage());
            e.printStackTrace();
        }

        // Se der erro técnico, assumimos FALSE para conseguires testar (mas cuidado em produção!)
        return false;
    }

    // 3. Registar a resposta
    public static boolean registarResposta(int alunoId, int perguntaId, String opcao) {

        // --- MUDANÇA AQUI ---
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String dataHora = java.time.LocalDateTime.now().format(formatter);
        // --------------------

        String sql = "INSERT INTO resposta (estudante_numero, pergunta_id, opcao_escolhida, data_resposta) " +
                "VALUES (" + alunoId + ", " + perguntaId + ", '" + opcao + "', '" + dataHora + "')";

        try {
            executeLocalUpdate(sql);
            return true;
        } catch (java.sql.SQLException e) {
            System.err.println("Erro SQL Resposta: " + e.getMessage());
            return false;
        }
    }

}