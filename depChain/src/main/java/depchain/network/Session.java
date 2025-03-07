package depchain.network;

import java.net.InetSocketAddress;

import javax.crypto.SecretKey;

import depchain.utils.CryptoUtil;
import java.security.NoSuchAlgorithmException;

public class Session {
    private final int destId;
    private final InetSocketAddress destAddr;
    private final SecretKey sessionKey;
    private int ackCounter = 0;

    public Session(int destId, InetSocketAddress destAddr, SecretKey sessionKey) {
        this.destId = destId;
        this.destAddr = destAddr;
        this.sessionKey = sessionKey;
    }

    public Session(int destId, InetSocketAddress destAddr) throws NoSuchAlgorithmException {
        this.destId = destId;
        this.destAddr = destAddr;
        this.sessionKey = CryptoUtil.generateSecretKey();
    }

    public int getDestId() {
        return destId;
    }

    public SecretKey getSessionKey() {
        return sessionKey;
    }

    public InetSocketAddress getDestAddr() {
        return destAddr;
    }

    public int getAckCounter() {
        return ackCounter;
    }

    public void incrementAckCounter() {
        ackCounter++;
    }

    @Override
    public String toString() {
        return "Session{" +
                "destId=" + destId +
                ", destAddr=" + destAddr +
                ", sessionKey=" + sessionKey +
                '}';
    }
                                                     
    
}
