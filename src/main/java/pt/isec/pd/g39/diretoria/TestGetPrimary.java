package pt.isec.pd.g39.diretoria;


import java.net.*;

public class TestGetPrimary {

    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket();

        String msg = "{\"type\":\"GET_PRIMARY\"}";

        DatagramPacket p = new DatagramPacket(
                msg.getBytes(), msg.length(),
                InetAddress.getByName("localhost"), 4000
        );

        System.out.println("➡ CLIENTE PEDIR PRIMARY...");
        socket.send(p);

        byte[] buf = new byte[1024];
        DatagramPacket resp = new DatagramPacket(buf, buf.length);
        socket.receive(resp);


        System.out.println("⬅ " + new String(resp.getData(),0,resp.getLength()));
        socket.close();
    }
}
