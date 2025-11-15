package pt.isec.pd.g39.servidor;


public class MainServer {
    public void main(String[] args) throws Exception {

        if (args.length != 4) {
            System.out.println("Uso: java pd.server.ServerMain <dirIp> <dirPort> <dbFolder> <multicastLocalIp>");
            System.exit(1);
        }


        String dirIp = args[0];
        int port = Integer.parseInt(args[1]);
        String dbPath = args[2];
        String multicastLocalIp = args[3];

       ServerNode node = new ServerNode(dirIp, port, dbPath, multicastLocalIp);
       System.out.println("Servidor ativo no porto " + port);
       node.start();

    }
}