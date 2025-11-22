package pt.isec.pd.g39.servidor.database;

import java.io.File;
import java.sql.*;
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
                                       java.util.List<Map<String, Object>> opcoes) {

        String codigoAcesso;

        // 1. Gerar código único
        while (true) {
            codigoAcesso = CodeGenerator.generateCode(6);
            try (Connection conn = DriverManager.getConnection(url());
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT COUNT(*) FROM pergunta WHERE codigo_acesso=?")) {

                ps.setString(1, codigoAcesso);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) == 0)
                    break;

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        try (Connection conn = DriverManager.getConnection(url())) {
            conn.setAutoCommit(false);

            // 2. Inserir pergunta
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

            // 3. Inserir opções
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
            return codigoAcesso;

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    //
// Adiciona isto à classe Database

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

        try (Connection conn = DriverManager.getConnection(url())) {
            conn.setAutoCommit(false);

            // Atualizar os dados base da pergunta
            try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE pergunta
                SET enunciado=?, data_inicio=?, data_fim=?
                WHERE id=?
        """)) {
                ps.setString(1, enunciado);
                ps.setString(2, dataInicio);
                ps.setString(3, dataFim);
                ps.setInt(4, perguntaId);
                ps.executeUpdate();
            }

            // Apagar opções antigas
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM opcao WHERE pergunta_id=?")) {
                ps.setInt(1, perguntaId);
                ps.executeUpdate();
            }

            // Inserir opções novas
            for (Map<String, Object> op : novasOpcoes) {
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
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean eliminarPergunta(int perguntaId) {

        // 1. Verificar se já existem respostas
        if (perguntaTemRespostas(perguntaId)) {
            return false; // não pode apagar
        }

        try (Connection conn = DriverManager.getConnection(url())) {
            conn.setAutoCommit(false);

            // 2. Apagar opções primeiro (FK)
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM opcao WHERE pergunta_id=?")) {
                ps.setInt(1, perguntaId);
                ps.executeUpdate();
            }

            // 3. Apagar pergunta
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM pergunta WHERE id=?")) {
                ps.setInt(1, perguntaId);
                ps.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
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
        try (Connection conn = DriverManager.getConnection(url());
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(sql);

            int v = getVersion() + 1;
            setVersion(v);
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
}