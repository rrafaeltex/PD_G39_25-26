package pt.isec.pd.g39.servidor;

import pt.isec.pd.g39.servidor.database.Database;

public class MainServer {
    public static void main(String[] args) {
        Database.init();
    }
}
