package depchain.account;


import static depchain.utils.EVMUtils.*;

import org.apache.tuweni.bytes.Bytes;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.*;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;

import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


import io.github.cdimascio.dotenv.Dotenv;

public class SmartAccount {
    
    private String address;
    private Long balance;
    private SimpleWorld simpleWorld;
    private Address contractAddress;
    private Address ownerAddress;
    private EVMExecutor executor;
    private ByteArrayOutputStream byteArrayOutputStream;
    private StandardJsonTracer tracer;
    private static String deploymentBytecode = Dotenv.load().get("DEPLOYMENT_BYTECODE");
    private static String runtimeBytecode = Dotenv.load().get("RUNTIME_BYTECODE");

    public SmartAccount(String address, Long balance) {
        this.address = address;
        this.balance = balance;
    }


    public String getAddress() {
        return address;
    }

    public Long getBalance() {
        return balance;
    }
 
    public Address getOwner() {
        return ownerAddress;
    }

    public void add(Long value) {
        this.balance += value;
    }

    public void remove(Long value) {
        if (this.balance >= value) {
            this.balance -= value;
        } else {
            throw new IllegalArgumentException("Insufficient balance");
        }
    }


    public SmartAccount() {
        setupEnvironment();
    }
    private void setupEnvironment() {
        this.simpleWorld = new SimpleWorld();

        // creates owner account
        String genesisPath = Dotenv.load().get("BLOCKS_FOLDER") + "/genesisBlock.json";
        if (genesisPath == null)
            throw new IllegalArgumentException("Environment variable BLOCKS_FOLDER is not set.");
  
        Path path = Paths.get(genesisPath);
        String content = null;
        try {
            content = new String(Files.readAllBytes(path));
        } catch (Exception e) {
            throw new RuntimeException("Error reading genesis block:", e);
        }
        JsonObject json = JsonParser.parseString(content).getAsJsonObject();
        JsonObject state = json.getAsJsonObject("state");
        String contractAddressKey = state.keySet().stream().reduce((first, second) -> second).orElseThrow(() -> new RuntimeException("State is empty"));
        JsonObject contractAccount1 = state.getAsJsonObject(contractAddressKey);
        String ownerAddressHex = contractAccount1.getAsJsonObject("storage").get("owner").getAsString();

        this.ownerAddress = Address.fromHexString(ownerAddressHex);

        // and contract account
        this.contractAddress = Address.fromHexString(contractAddressKey);
        simpleWorld.createAccount(contractAddress,0, Wei.fromEth(0));
        MutableAccount contractAccount = (MutableAccount) simpleWorld.get(contractAddress);
        String paddedAddress = padHexStringTo256Bit(ownerAddress.toHexString());
        String stateVariableIndex = convertIntegerToHex256Bit(1);
        String storageSlotMapping = Numeric.toHexStringNoPrefix(Hash.sha3(Numeric.hexStringToByteArray(paddedAddress + stateVariableIndex)));

        this.byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        this.tracer = new StandardJsonTracer(printStream, true, true, true, true);
        this.executor = EVMExecutor.evm(EvmSpecVersion.CANCUN);
        executor.tracer(tracer);
        executor.code(Bytes.fromHexString(deploymentBytecode)); // Set contract bytecode
        executor.sender(ownerAddress);
        executor.receiver(contractAddress);
        executor.worldUpdater(simpleWorld.updater());
        executor.commitWorldState();
        executor.callData(Bytes.EMPTY); // Empty callData for contract creation
        executor.execute();
        executor.code(Bytes.fromHexString(runtimeBytecode)); // Set contract bytecode
        executor.commitWorldState();
}

// ISTCoin methods implementation
    public String name(String senderAddress) {
        byteArrayOutputStream.reset();
        executor.sender(Address.fromHexString(senderAddress));
        executor.callData(Bytes.fromHexString("06fdde03")); // name()
        executor.execute();
        return extractStringFromReturnData(byteArrayOutputStream);
    }
    
    public String symbol(String senderAddress) {
        byteArrayOutputStream.reset();
        executor.sender(Address.fromHexString(senderAddress));
        executor.callData(Bytes.fromHexString("95d89b41")); // symbol()
        executor.execute();
        return extractStringFromReturnData(byteArrayOutputStream);
    }
    
    public int decimals(String senderAddress) {
        byteArrayOutputStream.reset();
        executor.sender(Address.fromHexString(senderAddress));
        executor.callData(Bytes.fromHexString("313ce567")); // decimals()
        executor.execute();
        return extractIntegerFromReturnData(byteArrayOutputStream);
    }
    
    public BigInteger totalSupply(String senderAddress) {
        byteArrayOutputStream.reset();
        executor.sender(Address.fromHexString(senderAddress));
        executor.callData(Bytes.fromHexString("18160ddd")); // totalSupply()
        executor.execute();
        return extractBigIntegerFromReturnData(byteArrayOutputStream);
    }

    public BigInteger balanceOf(String senderAddress, String accountAddress) {
        byteArrayOutputStream.reset();
        executor.sender(Address.fromHexString(senderAddress));
        // Format: function selector + account address (padded to 32 bytes)
        String paddedAddress = padHexStringTo256Bit(accountAddress);
        executor.callData(Bytes.fromHexString("70a08231" + paddedAddress)); // balanceOf(address)
        executor.execute();
        return extractBigIntegerFromReturnData(byteArrayOutputStream);
    }
    
    public boolean transfer(String senderAddress, String recipientAddress, BigInteger amount) {
        byteArrayOutputStream.reset();
        executor.sender(Address.fromHexString(senderAddress));
        // Format: function selector + recipient address (padded to 32 bytes) + amount (padded to 32 bytes)
        String paddedAddress = padHexStringTo256Bit(recipientAddress);
        String paddedAmount = padHexStringTo256Bit(amount.toString(16));
        executor.callData(Bytes.fromHexString("a9059cbb" + paddedAddress + paddedAmount)); // transfer(address,uint256)
        executor.execute();
        try {
            return extractIntegerFromReturnData(byteArrayOutputStream) == 1;
        } catch (Exception e) {
            return false;
        }
    }
    public BigInteger allowance(String senderAddress, String sourceAddress, String spenderAddress) {
        byteArrayOutputStream.reset();
        executor.sender(Address.fromHexString(senderAddress));
        // Format: function selector + owner address (padded to 32 bytes) + spender address (padded to 32 bytes)
        String paddedSourceAddress = padHexStringTo256Bit(sourceAddress);
        String paddedSpenderAddress = padHexStringTo256Bit(spenderAddress);
        executor.callData(Bytes.fromHexString("dd62ed3e" + paddedSourceAddress + paddedSpenderAddress)); // allowance(address,address)
        executor.execute();
        return extractBigIntegerFromReturnData(byteArrayOutputStream);
    }
    
    public boolean approve(String senderAddress, String spenderAddress, BigInteger amount) {
        byteArrayOutputStream.reset();
        executor.sender(Address.fromHexString(senderAddress));
        // Format: function selector + spender address (padded to 32 bytes) + amount (padded to 32 bytes)
        String paddedAddress = padHexStringTo256Bit(spenderAddress);
        String paddedAmount = padHexStringTo256Bit(amount.toString(16));
        executor.callData(Bytes.fromHexString("095ea7b3" + paddedAddress + paddedAmount)); // approve(address,uint256)
        executor.execute();
        try {
            return extractIntegerFromReturnData(byteArrayOutputStream) == 1;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean transferFrom(String senderAddress, String sourceAddress, String recipientAddress, BigInteger amount) {
        byteArrayOutputStream.reset();
        executor.sender(Address.fromHexString(senderAddress));
        // Format: function selector + sender address + recipient address + amount (all padded to 32 bytes)
        String paddedSourceAddress = padHexStringTo256Bit(sourceAddress);
        String paddedRecipientAddress = padHexStringTo256Bit(recipientAddress);
        String paddedAmount = padHexStringTo256Bit(amount.toString(16));
        executor.callData(Bytes.fromHexString("23b872dd" + paddedSourceAddress + paddedRecipientAddress + paddedAmount)); // transferFrom(address,address,uint256)
        executor.execute();
        try {
            return extractIntegerFromReturnData(byteArrayOutputStream) == 1;
        } catch (Exception e) {
            return false;
        }
    }
    
    // AccessControl methods
    public String owner(String senderAddress) {
        executor.sender(Address.fromHexString(senderAddress));
        byteArrayOutputStream.reset();
        executor.callData(Bytes.fromHexString("8da5cb5b")); // owner()
        executor.execute();
        // This needs to be handled differently as it returns an address
        return extractAddressFromReturnData(byteArrayOutputStream);
    }
    
    public boolean addToBlacklist(String senderAddress, String accountAddress) {
        byteArrayOutputStream.reset();
        executor.sender(Address.fromHexString(senderAddress));
        String paddedAddress = padHexStringTo256Bit(accountAddress);
        executor.callData(Bytes.fromHexString("44337ea1" + paddedAddress)); // addToBlacklist(address)
        executor.execute();
        try {
          return extractIntegerFromReturnData(byteArrayOutputStream) == 1;
        } catch (Exception e) {
          return false;
        }
    }
    
    public boolean removeFromBlacklist(String senderAddress, String accountAddress) {
        byteArrayOutputStream.reset();
        executor.sender(Address.fromHexString(senderAddress));
        String paddedAddress = padHexStringTo256Bit(accountAddress);
        executor.callData(Bytes.fromHexString("537df3b6" + paddedAddress)); // removeFromBlacklist(address)
        executor.execute();
        try {
          return extractIntegerFromReturnData(byteArrayOutputStream) == 1;
        } catch (Exception e) {
          return false;
        }
    }
    
    public boolean isBlacklisted(String senderAddress, String accountAddress) {
        byteArrayOutputStream.reset();
        executor.sender(Address.fromHexString(senderAddress));
        String paddedAddress = padHexStringTo256Bit(accountAddress);
        executor.callData(Bytes.fromHexString("fe575a87" + paddedAddress)); // isBlacklisted(address)
        executor.execute();
        try {
          return extractIntegerFromReturnData(byteArrayOutputStream) == 1;
        } catch (Exception e) {
          return false;
        }
    }
}


