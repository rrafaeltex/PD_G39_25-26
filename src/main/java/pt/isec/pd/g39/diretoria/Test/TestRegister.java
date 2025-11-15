package pt.isec.pd.g39.diretoria.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class TestRegister {
    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket();

            String msg = """
            {
              "type": "REGISTER_SERVER",
              "tcp_clients": 6000,
              "tcp_peers": 6001
            }
            """;

            byte[] data = msg.getBytes(StandardCharsets.UTF_8);

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName("localhost"),
                    4000   // Porto da diretoria
            );

            System.out.println("➡ A enviar REGISTER_SERVER para diretoria...");
            socket.send(packet);

            byte[] buffer = new byte[1024];
            DatagramPacket response =
                    new DatagramPacket(buffer, buffer.length);

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
