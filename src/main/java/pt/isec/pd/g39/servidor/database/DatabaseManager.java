package pt.isec.pd.g39.servidor.database;

import java.io.File;

public class DatabaseManager {

    public static String trataDatabaseFile(String dbFolder) {

        File folder = new File(dbFolder);

        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                System.err.println("[BD] Erro: não foi possível criar a pasta da BD: " + dbFolder);
            }
        }


        File[] dbFiles = folder.listFiles((_, name) -> name.endsWith(".db"));

        if (dbFiles == null || dbFiles.length == 0) {
            return gerarNomeBD(dbFolder, "server_v0.db");
        }

        File newest = dbFiles[0];
        for (File f : dbFiles) {
            if (f.lastModified() > newest.lastModified()) {
                newest = f;
            }
        }

        return newest.getAbsolutePath();
    }


    public static String gerarNomeBD(String dbFolder, String baseName) {

        File base = new File(dbFolder, baseName);

        if (!base.exists()) {
            return base.getAbsolutePath();
        }

        int counter = 1;
        while (true) {
            String name = baseName.replace(".db", "_" + counter + ".db");
            File f = new File(dbFolder, name);

            if (!f.exists()) {
                return f.getAbsolutePath();
            }
            counter++;
        }
    }
}
