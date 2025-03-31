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
import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class Config {

    // Mapping from blockchain member IDs to their network addresses (servers and clients).
    public static ConcurrentHashMap<Integer, InetSocketAddress> processAddresses = new ConcurrentHashMap<>();

    // Mapping from process (or client) IDs to their public keys.
    public static ConcurrentHashMap<Integer, PublicKey> publicKeys = new ConcurrentHashMap<>();

    // Mapping from process IDs to their behavior (e.g., "correct" or "byzantine").
    public static ConcurrentHashMap<Integer, String> processBehaviors = new ConcurrentHashMap<>();

    // (If you need private keys in code, add them here:)
    public static ConcurrentHashMap<Integer, PrivateKey> privateKeys = new ConcurrentHashMap<>();

    public static List<Integer> clientIds = new ArrayList<>();

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
        mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);   // Allow comments in JSON
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

            if(type.equals("client")) {
                clientIds.add(id);
            }

            processAddresses.put(id, address);
        }
      /*   for (int clientId : clientIds) {
            Logger.log(LogLevel.DEBUG, "Client ID: " + clientId);
        } */
    }

    private static void loadKeys(String resourcesFolder) throws Exception {
        final int totalServers = processAddresses.size() - clientIds.size();
        System.out.println("Total servers: " + totalServers);
        for (int serverId = 1; serverId <= totalServers; serverId++) {
            String privKeyPath = resourcesFolder + "/priv_key_" + serverId + ".pem";
            String pubKeyPath = resourcesFolder + "/pub_key_" + serverId + ".pem";

            PrivateKey priv = loadPrivateKey(privKeyPath);
            PublicKey pub = loadPublicKey(pubKeyPath);

            privateKeys.put(serverId, priv);
            publicKeys.put(serverId, pub);
        }


        // For the client 1 (ID=1 in config.txt)
        String privKeyClientPath = resourcesFolder + "/priv_key_client1.pem";
        String pubKeyClientPath = resourcesFolder + "/pub_key_client1.pem";

        PrivateKey privClient = loadPrivateKey(privKeyClientPath);
        PublicKey pubClient = loadPublicKey(pubKeyClientPath);

        privateKeys.put(5, privClient);
        publicKeys.put(5, pubClient);

        // For the client 2 (ID=2 in config.txt)
        String privKeyClient2Path = resourcesFolder + "/priv_key_client2.pem";
        String pubKeyClient2Path = resourcesFolder + "/pub_key_client2.pem";

        PrivateKey privClient2 = loadPrivateKey(privKeyClient2Path);
        PublicKey pubClient2 = loadPublicKey(pubKeyClient2Path);

        privateKeys.put(6, privClient2);
        publicKeys.put(6, pubClient2);

        // For the client 3 (ID=3 in config.txt)
        String privKeyClient3Path = resourcesFolder + "/priv_key_client3.pem";
        String pubKeyClient3Path = resourcesFolder + "/pub_key_client3.pem";

        PrivateKey privClient3 = loadPrivateKey(privKeyClient3Path);
        PublicKey pubClient3 = loadPublicKey(pubKeyClient3Path);

        privateKeys.put(7, privClient3);
        publicKeys.put(7, pubClient3);

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

    public static List<PublicKey> getClientPublicKeys() {
        List<PublicKey> clientKeys = new ArrayList<>();
        for (int clientId : clientIds) {
            PublicKey clientKey = publicKeys.get(clientId);
            if (clientKey != null) {
                clientKeys.add(clientKey);
            }
        }

        return clientKeys;  
    }
}
