package pt.isec.pd.g39.servidor.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:src/main/java/pt/isec/pd/g39/servidor/database/server.db";

    public static void init() {
        Connection conn = null;
        Statement stmt = null;

        try {

            conn = DriverManager.getConnection(DB_URL);

            stmt = conn.createStatement();

            stmt.execute("CREATE TABLE IF NOT EXISTS config (" +
                    "key TEXT PRIMARY KEY," +
                    "value TEXT NOT NULL" +
                    ");");

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

            stmt.execute("INSERT OR IGNORE INTO config (key, value) VALUES ('db_version', '0');");

            System.out.println("Base de dados criada e inicializada com sucesso!");

        } catch (SQLException e) {
            System.err.println("Erro ao inicializar BD: " + e.getMessage());
        } finally {
            try {
                if (stmt != null)
                    stmt.close();
            } catch (SQLException e) {
                System.err.println("Erro ao fechar Statement: " + e.getMessage());
            }

            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException e) {
                System.err.println("Erro ao fechar Connection: " + e.getMessage());
            }
        }
    }
}