package pt.isec.pd.g39.servidor.database;


public class DatabaseWriteLock {
    public static volatile boolean locked = false;

    public static synchronized void lock() {
        locked = true;
    }

    public static synchronized void unlock() {
        locked = false;
    }

    public static void waitIfLocked() {
        while (locked) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}
        }
    }
}