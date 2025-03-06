package depchain.network;

import java.net.InetSocketAddress;

import javax.crypto.SecretKey;


public class Session {
    private final int destId;
    private final InetSocketAddress destAddr;
    private final SecretKey sessionKey;

    public Session(int destId, InetSocketAddress destAddr, SecretKey sessionKey) {
        this.destId = destId;
        this.destAddr = destAddr;
        this.sessionKey = sessionKey;
    }


    
}
