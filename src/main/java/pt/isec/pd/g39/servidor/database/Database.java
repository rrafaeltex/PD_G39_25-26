package pt.isec.pd.g39.servidor.database;

import java.sql.*;

public class Database {

    private static String dbPath;

    // Chamada no arranque do servidor com o diretório da BD
    public static void configure(String dbFolder) {
        dbPath = dbFolder + "/server.db";
    }

    private static String url() {
        return "jdbc:sqlite:" + dbPath;
    }

    // Inicializa BD: cria tabelas se não existirem e garante db_version
    public static void init() {
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = DriverManager.getConnection(url());
            stmt = conn.createStatement();

            stmt.execute("CREATE TABLE IF NOT EXISTS config (" +
                    "key TEXT PRIMARY KEY," +
                    "value TEXT NOT NULL" +
                    ");");

            stmt.execute("INSERT OR IGNORE INTO config (key, value) VALUES ('db_version', '0');");

            stmt.execute("CREATE TABLE IF NOT EXISTS docente (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "nome TEXT NOT NULL," +
                    "email TEXT NOT NULL UNIQUE," +
                    "pass_hash TEXT NOT NULL," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS estudante (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "numero INTEGER NOT NULL UNIQUE," +
                    "nome TEXT NOT NULL," +
                    "email TEXT NOT NULL UNIQUE," +
                    "pass_hash TEXT NOT NULL," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS pergunta (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "id_docente INTEGER NOT NULL," +
                    "enunciado TEXT NOT NULL," +
                    "inicio DATETIME NOT NULL," +
                    "fim DATETIME NOT NULL," +
                    "codigo_acesso TEXT NOT NULL UNIQUE," +
                    "correta CHAR(1) NOT NULL," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (id_docente) REFERENCES docente(id)" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS opcao (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "id_pergunta INTEGER NOT NULL," +
                    "letra CHAR(1) NOT NULL," +
                    "texto TEXT NOT NULL," +
                    "UNIQUE(id_pergunta, letra)," +
                    "FOREIGN KEY (id_pergunta) REFERENCES pergunta(id)" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS resposta (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "id_pergunta INTEGER NOT NULL," +
                    "id_estudante INTEGER NOT NULL," +
                    "letra CHAR(1) NOT NULL," +
                    "responded_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(id_pergunta, id_estudante)," +
                    "FOREIGN KEY (id_pergunta) REFERENCES pergunta(id)," +
                    "FOREIGN KEY (id_estudante) REFERENCES estudante(id)" +
                    ");");

            System.out.println("[BD] Base de dados inicializada em: " + dbPath);

        } catch (SQLException e) {
            System.err.println("[BD] Erro ao inicializar BD: " + e.getMessage());
        } finally {
            try { if (stmt != null) stmt.close(); } catch (SQLException ignored) {}
            try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
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
}
