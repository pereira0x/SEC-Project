package depchain.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


import depchain.blockchain.block.Block;

public class EVMUtils {



    public static int extractIntegerFromReturnData(ByteArrayOutputStream byteArrayOutputStream) {
        String[] lines = byteArrayOutputStream.toString().split("\\r?\\n");
        JsonObject jsonObject = JsonParser.parseString(lines[lines.length - 1]).getAsJsonObject();

        String memory = jsonObject.get("memory").getAsString();
        JsonArray stack = jsonObject.get("stack").getAsJsonArray();
        int offset = Integer.decode(stack.get(stack.size() - 1).getAsString());
        int size = Integer.decode(stack.get(stack.size() - 2).getAsString());

        String returnData = memory.substring(2 + offset * 2, 2 + offset * 2 + size * 2);
        return Integer.decode("0x" + returnData);
    }

    public static String convertIntegerToHex256Bit(int number) {
        return String.format("%064x", BigInteger.valueOf(number));
    }

    public static byte[] hexStringToByteArray(String hexString) {
        int length = hexString.length();
        byte[] byteArray = new byte[length / 2];

        for (int i = 0; i < length; i += 2) {
            int value = Integer.parseInt(hexString.substring(i, i + 2), 16);
            byteArray[i / 2] = (byte) value;
        }

        return byteArray;
    }

    public static String extractStringFromReturnData(ByteArrayOutputStream byteArrayOutputStream) {
        /* System.out.println(byteArrayOutputStream.toString()); */
        String[] lines = byteArrayOutputStream.toString().split("\\r?\\n");
        JsonObject jsonObject = JsonParser.parseString(lines[lines.length-1]).getAsJsonObject();

        String memory = jsonObject.get("memory").getAsString();

        JsonArray stack = jsonObject.get("stack").getAsJsonArray();
        int offset = Integer.decode(stack.get(stack.size()-1).getAsString());
        int size = Integer.decode(stack.get(stack.size()-2).getAsString());

        String returnData = memory.substring(2 + offset * 2, 2 + offset * 2 + size * 2);

        int stringOffset = Integer.decode("0x"+returnData.substring(0, 32 * 2));
        int stringLength = Integer.decode("0x"+returnData.substring(stringOffset * 2, stringOffset * 2 + 32 * 2));
        String hexString = returnData.substring(stringOffset * 2 + 32 * 2, stringOffset * 2 + 32 * 2 + stringLength * 2);

        return new String(hexStringToByteArray(hexString), StandardCharsets.UTF_8);
    }

    public static String padHexStringTo256Bit(String hexString) {
        if (hexString.startsWith("0x")) {
            hexString = hexString.substring(2);
        }
        return "0".repeat(64 - hexString.length()) + hexString;
    }

    public static String generateBlockHash(Block block) throws NoSuchAlgorithmException {
        String data = block.getPreviousBlockHash() + block.getTransactions().toString() + block.getState().toString();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    // account address is an hash pof the onwer's public key
    public static String generateAccountHash(String publicKey) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}