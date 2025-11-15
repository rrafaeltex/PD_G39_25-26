package pt.isec.pd.g39.diretoria;

public class MainDiretoria {
    public static void main(String[] args) {
        int portoUDP = 4000; // podes mudar via argumento
        DirecaoServerList direcao = new DirecaoServerList();
        UDPReceiver receiver = new UDPReceiver(portoUDP, direcao);
        receiver.start();

        System.out.println("Diretoria ativa no porto UDP " + portoUDP);
    }
}
