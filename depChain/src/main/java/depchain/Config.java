package depchain;

import java.net.*;
import java.util.concurrent.*;
import java.security.PublicKey;

public class Config {
    // Mapping from blockchain member IDs to their network addresses.
    public static ConcurrentMap<Integer, InetSocketAddress> processAddresses = new ConcurrentHashMap<>();
    // Mapping from process IDs to their public keys.
    public static ConcurrentMap<Integer, PublicKey> publicKeys = new ConcurrentHashMap<>();
    // Mapping from client IDs to their network addresses.
    public static ConcurrentMap<Integer, InetSocketAddress> clientAddresses = new ConcurrentHashMap<>();
}
