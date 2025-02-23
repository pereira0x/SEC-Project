package depchain;

import java.util.concurrent.atomic.AtomicInteger;

public class ClientIdGenerator {
    private static AtomicInteger clientIdCounter = new AtomicInteger(1000);
    public static int getNextClientId() {
        return clientIdCounter.getAndIncrement();
    }
}
