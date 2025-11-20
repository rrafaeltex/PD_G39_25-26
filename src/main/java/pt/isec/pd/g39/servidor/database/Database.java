package pt.isec.pd.g39.servidor.database;

import java.io.File;
import java.sql.*;
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
                        value TEXT NOT NULL
                    )
                """);

                stmt.execute("INSERT INTO config (key, value) VALUES ('db_version', '0');");

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

        return null; // Não encontrou ninguém
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
}
