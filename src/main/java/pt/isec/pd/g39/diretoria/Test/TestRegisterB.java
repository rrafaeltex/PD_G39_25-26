package pt.isec.pd.g39.diretoria.Test;

import java.net.*;

public class TestRegisterB {
    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket();

        String json = """
        {"type":"REGISTER_SERVER","tcp_clients":7000,"tcp_peers":7001}
        """;

        DatagramPacket packet = new DatagramPacket(
                json.getBytes(), json.length(),
                InetAddress.getByName("localhost"), 4000
        );

        System.out.println("➡ REGISTAR B (7000)");
        socket.send(packet);

        byte[] buf = new byte[1024];
        DatagramPacket resp = new DatagramPacket(buf, buf.length);
        socket.receive(resp);

        System.out.println("⬅ " + new String(resp.getData(),0,resp.getLength()));
        socket.close();
    }
}
