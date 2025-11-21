package pt.isec.pd.g39.servidor;


public class MainServer {
    public static void main(String[] args) throws Exception {

        if (args.length != 4) {
            System.out.println("Uso: java pt.isec.pd.g39.servidor.MainServer <dirIp> <dirPort> <dbFolder> <multicastLocalIp>");
            System.exit(1);
        }


        String dirIp = args[0];
        int port = Integer.parseInt(args[1]);
        String dbPath = args[2];
        String multicastLocalIp = args[3];


        ServerNode node = new ServerNode(dirIp, port, dbPath, multicastLocalIp);
        ShutDownHandle shutdownHandler = new ShutDownHandle();
        System.out.println("Servidor ativo no porto " + port);
        node.start();
        shutdownHandler.start();

    }
}