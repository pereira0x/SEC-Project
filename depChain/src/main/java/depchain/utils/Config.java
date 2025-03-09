package depchain.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class Config {

    // Mapping from blockchain member IDs to their network addresses (servers).
    public static ConcurrentHashMap<Integer, InetSocketAddress> processAddresses = new ConcurrentHashMap<>();

    // Mapping from client IDs to their network addresses.
    public static ConcurrentHashMap<Integer, InetSocketAddress> clientAddresses = new ConcurrentHashMap<>();

    // Mapping from process IDs to their behavior (e.g., "correct" or "byzantine").
    public static ConcurrentHashMap<Integer, String> processBehaviors = new ConcurrentHashMap<>();

    // Mapping from process (or client) IDs to their public keys.
    public static ConcurrentHashMap<Integer, PublicKey> publicKeys = new ConcurrentHashMap<>();

    // (If you need private keys in code, add them here:)
    public static ConcurrentHashMap<Integer, PrivateKey> privateKeys = new ConcurrentHashMap<>();

    /**
     * Call this once at startup to read: - server/client host:port info from config.txt - private/public keys from PEM
     * files in resources
     */
    public static void loadConfiguration(String configFilePath, String resourcesFolder) throws Exception {
        loadAddresses(configFilePath);
        loadKeys(resourcesFolder);
    }

    private static void loadAddresses(String configFilePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(new File(configFilePath));

        for (JsonNode node : rootNode) {
            String type = node.get("type").asText();
            int id = node.get("id").asInt();
            String host = node.get("host").asText();
            int port = node.get("port").asInt();
            String behavior = node.get("behavior").asText();
            processBehaviors.put(id, behavior);
            Logger.log(LogLevel.DEBUG, "Loaded: " + type + " " + id + " " + host + ":" + port + " " + behavior);

            InetSocketAddress address = new InetSocketAddress(host, port);

            if (type.equalsIgnoreCase("server")) {
                processAddresses.put(id, address);
            } else if (type.equalsIgnoreCase("client")) {
                clientAddresses.put(id, address);
            }
        }
    }

    private static void loadKeys(String resourcesFolder) throws Exception {
        // For servers 1..4:
        for (int serverId = 1; serverId <= 4; serverId++) {
            String privKeyPath = resourcesFolder + "/priv_key_" + serverId + ".pem";
            String pubKeyPath = resourcesFolder + "/pub_key_" + serverId + ".pem";

            PrivateKey priv = loadPrivateKey(privKeyPath);
            PublicKey pub = loadPublicKey(pubKeyPath);

            privateKeys.put(serverId, priv);
            publicKeys.put(serverId, pub);
        }

        // For the single client (ID=1 in config.txt)
        String privKeyClientPath = resourcesFolder + "/priv_key_client.pem";
        String pubKeyClientPath = resourcesFolder + "/pub_key_client.pem";

        PrivateKey privClient = loadPrivateKey(privKeyClientPath);
        PublicKey pubClient = loadPublicKey(pubKeyClientPath);

        privateKeys.put(5, privClient);
        publicKeys.put(5, pubClient);
    }

    /**
     * Utility: Load a PEM-encoded private key file (PKCS#8).
     */
    private static PrivateKey loadPrivateKey(String pemFilePath) throws Exception {
        String keyPem = new String(Files.readAllBytes(Paths.get(pemFilePath)), StandardCharsets.UTF_8);
        // Strip off headers/footers
        keyPem = keyPem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(keyPem);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * Utility: Load a PEM-encoded public key file (X.509).
     */
    private static PublicKey loadPublicKey(String pemFilePath) throws Exception {
        String keyPem = new String(Files.readAllBytes(Paths.get(pemFilePath)), StandardCharsets.UTF_8);
        // Strip off headers/footers
        keyPem = keyPem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(keyPem);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    public static PrivateKey getPrivateKey(int id) {
        return privateKeys.get(id);
    }

    public static PublicKey getPublicKey(int id) {
        return publicKeys.get(id);
    }
}
