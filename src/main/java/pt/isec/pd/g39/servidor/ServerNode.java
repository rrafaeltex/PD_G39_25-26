package pt.isec.pd.g39.servidor;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ServerNode {
    private final String directoryIp;
    private final int directoryPort;
    private final String dbFolder;
    private final String multicastLocalIp;
    private final Gson gson= new Gson();
    private boolean isPrimary = false;
    private  int clientPort;
    private int peerPort;

    private String primaryIp;
    private int primaryTcpClients;
    private int primaryTcpPeers;

    private ServerSocket clientServerSocket;
    private ServerSocket peerServerSocket;

    public ServerNode(String directoryIp, int directoryPort, String dbFolder, String multicastLocalIp) {
        this.directoryIp = directoryIp;
        this.directoryPort = directoryPort;
        this.dbFolder = dbFolder;
        this.multicastLocalIp = multicastLocalIp;
    }

    public void start() throws IOException {

        intializeServerSockets();

        try {
            register(directoryIp, directoryPort, clientPort, peerPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(isPrimary){
            //Thread primaryServer
        }else{
            //sincronizar base de dados
            //Thread peerServer
        }
    }


    private void intializeServerSockets() throws IOException {
        clientServerSocket = new ServerSocket(0);
        clientPort = clientServerSocket.getLocalPort();

        peerServerSocket = new ServerSocket(0);
        peerPort = peerServerSocket.getLocalPort();
    }

    public void register(String directoryIp, int directoryPort, int clientPort, int peerPort) throws IOException {
        try(DatagramSocket socket = new DatagramSocket()){
            socket.setSoTimeout(5000);

            String msg = gson.toJson(Map.of(
                    "type", "REGISTER_SERVER",
                    "primary_tcp_clients", clientPort,
                    "primary_tcp_peers", peerPort

            ));

            System.out.println("A enviar registo...");

            byte[] data = msg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            InetAddress address = InetAddress.getByName(directoryIp);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, directoryPort);
            socket.send(packet);


            byte[] buffer = new byte[1024];
            DatagramPacket resposta = new DatagramPacket(buffer, buffer.length);
            socket.receive(resposta);

            String respostaMsg = new String(resposta.getData(), 0, resposta.getLength(), StandardCharsets.UTF_8
            );

            var json = gson.fromJson(respostaMsg, Map.class);

            if("REGISTERED".equals(json.get("type"))){
                primaryIp = (String) json.get("primary_ip");
                primaryTcpClients = (int) json.get("primary_tcp_clients");
                primaryTcpPeers = (int) json.get("primary_tcp_peers");

                InetAddress myIp = InetAddress.getLocalHost();
                isPrimary = primaryIp.equals(myIp.getHostAddress()) && primaryTcpClients == clientPort;

                if(isPrimary){
                    System.out.println("Registado como servidor principal");
                } else{
                    System.out.println("Registado como servidor secundario backup");
                }

                System.out.println("Registo feito com sucesso: ");
            }
            throw new IOException("Resposta inesperada da diretoria: "+(String)json.get("type"));
            //Um servidor, que não receba qualquer resposta do serviço de diretoria durante a fase
            //de arranque, termina. >>> Se não for do tipo "REGISTERED" a resposta conta como não
            //ter obtido resposta, então termina.
        } catch (Exception e) {
            throw new IOException("Erro ao registar no diretoria: "+e.getMessage());
        }




    }
}
