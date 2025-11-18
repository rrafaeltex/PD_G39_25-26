package pt.isec.pd.g39.cliente;


import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

public class MainClient {

    private static String directoryIp;
    private static int directoryPort;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Uso: java MainClient <dirIp> <dirPort>");
            System.exit(1);
        }
        /*
        São lançados fornecendo o endereço e o porto de escuta UDP do serviço de diretoria
        através da linha de comando.
        */
        directoryIp = args[0];
        directoryPort = Integer.parseInt(args[1]);

        ClientComms clientComms = new ClientComms(directoryIp, directoryPort);
        try {
            clientComms.start();
            //ClientUI clientUI = new ClientUI(clientComms);
            //clientUI.start();
        } catch (IOException e) {
            System.err.println("Erro na comunicação com o serviço de diretoria: " + e.getMessage());
            System.exit(1);
        }
    }
}