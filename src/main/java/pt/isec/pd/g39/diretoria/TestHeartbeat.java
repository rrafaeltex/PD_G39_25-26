package pt.isec.pd.g39.diretoria;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class TestHeartbeat {

    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket();

            String msg = """
            {
              "type": "HEARTBEAT",
              "tcp_clients": 6000
            }
            """;

            byte[] data = msg.getBytes(StandardCharsets.UTF_8);

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName("localhost"),
                    4000   // usa o porto da diretoria
            );

            System.out.println("➡ A enviar HEARTBEAT para a diretoria...");
            socket.send(packet);

            byte[] buf = new byte[1024];
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);

            System.out.println("⬅ Resposta recebida:");
            System.out.println(
                    new String(response.getData(), 0, response.getLength())
            );

            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
