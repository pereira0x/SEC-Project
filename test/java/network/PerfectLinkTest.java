package network;

import model.APLMessage;
import crypto.CryptoUtils;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

public class PerfectLinkTest {
    @Test
    public void testMessageDelivery() throws Exception {
        // Setup keys
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        Map<Integer, PublicKey> keys = new HashMap<>();
        keys.put(1, keyPair.getPublic());

        AtomicBoolean delivered = new AtomicBoolean(false);
        PerfectLink link = new PerfectLink(5000, keys, msg -> delivered.set(true));
        
        APLMessage message = new APLMessage(1, 1, APLMessage.MessageType.PROPOSE, "test".getBytes(), null);
        link.send(InetAddress.getLocalHost(), 5000, message);
        
        Thread.sleep(2000); // Wait for delivery
        assertTrue(delivered.get());
    }
}
