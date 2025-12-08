package pt.isec.pd.g39.servidor.database;

import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;

public class DatabaseSync {

    public static boolean downloadDatabase(String primaryIp, int port, String dbFolder) {

        try (var socket = new java.net.Socket(InetAddress.getByName(primaryIp), port)) {

            var in = socket.getInputStream();

            byte[] sizeBytes = in.readNBytes(8);
            long fileSize = bytesToLong(sizeBytes);

            String dbFile = DatabaseManager.gerarNomeBD(dbFolder, "server_copy.db");
            File outFile = new File(dbFile);

            try (var fos = new java.io.FileOutputStream(outFile)) {

                long remaining = fileSize;
                byte[] buffer = new byte[8192];

                while (remaining > 0) {
                    int read = in.read(buffer);
                    if (read == -1) return false;

                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
            }

            Database.configure(dbFile);

            return true;

        } catch (Exception e) {
            System.err.println("[SYNC] Erro ao copiar BD: " + e.getMessage());
            return false;
        }
    }


    public static void startPeerServer(ServerSocket peerServerSocket) {

        new Thread(() -> {

            System.out.println("[Primary] Peer Server ativo (envio da BD)...");

            while (true) {
                try {

                    var socket = peerServerSocket.accept();
                    var out = socket.getOutputStream();

                    // 2) BLOQUEAR ESCRITAS ENQUANTO COPIAMOS A BD
                    DatabaseWriteLock.lock();
                    try {
                        String dbPath = Database.getPath();
                        File f = new File(dbPath);

                        out.write(longToBytes(f.length()));

                        try (var fis = new java.io.FileInputStream(f)) {
                            fis.transferTo(out);
                        }

                        System.out.println("[PRIMARY] Sincronização concluída — desbloqueando escritas.");
                    } finally {

                        DatabaseWriteLock.unlock();
                    }

                    socket.close();

                } catch (Exception e) {
                    System.err.println("[PRIMARY] Erro peerServer: " + e.getMessage());
                }
            }

        }).start();
    }



    private static byte[] longToBytes(long x) {
        byte[] buffer = new byte[8];
        for (int i = 7; i >= 0; i--) {
            buffer[i] = (byte)(x & 0xff);
            x >>= 8;
        }
        return buffer;
    }

    private static long bytesToLong(byte[] bytes) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) + (bytes[i] & 0xff);
        }
        return value;
    }
}
