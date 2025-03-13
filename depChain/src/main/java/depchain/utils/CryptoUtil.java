package depchain.utils;

import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.InvalidKeyException;
import java.security.MessageDigest;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import java.security.SecureRandom;

public class CryptoUtil {

    private static final int keySize = 128;
    private static final String algorithm = "AES";

    // Signs data using SHA256withRSA.
    public static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    public static byte[] getHMACHmacSHA256(byte[] data, SecretKey secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Verifies the given signature for the data.
    public static boolean verify(byte[] data, byte[] sigBytes, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(data);
        return signature.verify(sigBytes);
    }

    public static boolean checkHMACHmacSHA256(byte[] data, byte[] hmac, SecretKey secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            byte[] computedHmac = mac.doFinal(data);
            return MessageDigest.isEqual(computedHmac, hmac);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static int generateNonce() {
        // generate int random number
        SecureRandom random = new SecureRandom();
        return random.nextInt();
    }

    public static SecretKey generateSecretKey() {
        try {

            KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm);
            keyGenerator.init(keySize);
            return keyGenerator.generateKey();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }

    }

    public static byte[] encryptSecretKey(SecretKey secretKey, PublicKey publicKey) {
        try {
            // Create a cipher instance for the specified algorithm
            Cipher cipher = Cipher.getInstance("RSA");

            cipher.init(Cipher.WRAP_MODE, publicKey);

            return cipher.wrap(secretKey);

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException
                | InvalidKeyException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static SecretKey decryptSecretKey(byte[] encryptedSecretKey, PrivateKey privateKey) {
        try {
            // Create a cipher instance for the specified algorithm

            Cipher cipher = Cipher.getInstance("RSA");

            cipher.init(Cipher.UNWRAP_MODE, privateKey);
            if (encryptedSecretKey == null) {
                throw new IllegalArgumentException("The encrypted secret key cannot be null");
            }
            SecretKey secretKey = (SecretKey) cipher.unwrap(encryptedSecretKey, algorithm, Cipher.SECRET_KEY);

            return secretKey;

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            e.printStackTrace();
            return null;
        }
    }
}
