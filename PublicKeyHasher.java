import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class PublicKeyHasher {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java PublicKeyHasher <publicKeyFile1> <publicKeyFile2> ...");
            return;
        }

        for (String filePath : args) {
            try {
                PublicKey publicKey = loadPublicKey(filePath);
                String accountAddress = getAccountAddress(publicKey);
                System.out.println("Account Address for " + filePath + ": " + accountAddress);
            } catch (Exception e) {
                System.err.println("Error processing " + filePath + ": " + e.getMessage());
            }
        }
    }

    public static PublicKey loadPublicKey(String filePath) throws Exception {
        String pemContent = new String(Files.readAllBytes(Paths.get(filePath)));
        pemContent = pemContent
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", ""); // Remove whitespace and newlines

        byte[] keyBytes = Base64.getDecoder().decode(pemContent);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA"); // Change this if needed
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return keyFactory.generatePublic(spec);
    }

    public static String getAccountAddress(PublicKey publicKey) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getEncoded());
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}