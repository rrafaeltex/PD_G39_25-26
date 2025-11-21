package pt.isec.pd.g39.diretoria;

import java.net.InetAddress;

import static java.lang.Thread.sleep;


public class MainDiretoria {
    public static void main(String[] args) throws InterruptedException {
        int portoUDP = 4000;
        DirecaoServerList direcao = new DirecaoServerList();
        UDPReceiver receiver = new UDPReceiver(portoUDP, direcao);
        receiver.start();

        System.out.println("Diretoria ativa no porto UDP " + portoUDP);


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SHUTDOWN HOOK] Encerrando diretoria...");
            DirecaoServerList.ShutdownToAllServers();
            System.out.println("[SHUTDOWN HOOK]  Diretoria encerrada.");
        }, "DiretoriaShutdownHook"));



        while(true){
         sleep(10000);
         direcao.imprimirServidores();
        }
    }

}
