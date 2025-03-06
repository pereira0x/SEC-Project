package depchain.utils;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SecureRandom;

public class CryptoUtil {
    // Signs data using SHA256withRSA.
    public static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    // Verifies the given signature for the data.
    public static boolean verify(byte[] data, byte[] sigBytes, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(data);
        return signature.verify(sigBytes);
    }
    
    public static long generateNonce() {
        // generate long random number
        SecureRandom random = new SecureRandom();
        return random.nextLong();

    }
}
