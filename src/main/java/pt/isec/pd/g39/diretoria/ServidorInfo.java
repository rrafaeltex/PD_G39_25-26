package pt.isec.pd.g39.diretoria;

import java.net.InetAddress;

public class ServidorInfo {
    public InetAddress ip;
    public int tcpPortClients;
    public int tcpPortPeers;
    public long lastHeartbeat;
    public boolean isPrimary = false;

    public ServidorInfo(InetAddress ip, int tcpPortClients, int tcpPortPeers) {
        this.ip = ip;
        this.tcpPortClients = tcpPortClients;
        this.tcpPortPeers = tcpPortPeers;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return ip.getHostAddress() + ":" + tcpPortClients + " (peers:" + tcpPortPeers + ")";
    }
}
