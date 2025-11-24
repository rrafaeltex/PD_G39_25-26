package pt.isec.pd.g39.cliente;

import java.net.InetAddress;

public class MainClient {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Uso: java MainClient <dirIp> <dirPort>");
            System.exit(1);
        }
        InetAddress addr = InetAddress.getByName(args[0]);
        String directoryIp = addr.getHostAddress();
        int directoryPort = Integer.parseInt(args[1]);

        ClientComms clientComms = new ClientComms(directoryIp, directoryPort);
        ClientViewManager manager = new ClientViewManager(clientComms);
        try {
            // start console UI via manager (future: choose GUI)
            manager.startConsole();
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            System.exit(1);
        }
    }
}
