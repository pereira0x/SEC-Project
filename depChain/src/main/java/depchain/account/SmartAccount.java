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

import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;


import io.github.cdimascio.dotenv.Dotenv;

public class SmartAccount {
    
    private static String deploymentBytecode = Dotenv.load().get("DEPLOYMENT_BYTECODE");
    private static String runtimeBytecode = Dotenv.load().get("RUNTIME_BYTECODE");

    public SmartAccount() {
        SimpleWorld simpleWorld = new SimpleWorld();

        // creates sender account
        Address senderAddress = Address.fromHexString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
        simpleWorld.createAccount(senderAddress,0, Wei.fromEth(100));
        MutableAccount senderAccount = (MutableAccount) simpleWorld.get(senderAddress);
        System.out.println("Sender Account");
        System.out.println("  Address: "+senderAccount.getAddress());
        System.out.println("  Balance: "+senderAccount.getBalance());
        System.out.println("  Nonce: "+senderAccount.getNonce());
        System.out.println();

        // and contract account
        Address contractAddress = Address.fromHexString("1234567891234567891234567891234567891234");
        simpleWorld.createAccount(contractAddress,0, Wei.fromEth(0));
        MutableAccount contractAccount = (MutableAccount) simpleWorld.get(contractAddress);
        System.out.println("Contract Account");
        System.out.println("  Address: "+contractAccount.getAddress());
        System.out.println("  Balance: "+contractAccount.getBalance());
        System.out.println("  Nonce: "+contractAccount.getNonce());
        System.out.println("  Storage:");
        System.out.println("    Slot 0: "+simpleWorld.get(contractAddress).getStorageValue(UInt256.valueOf(0)));
        String paddedAddress = padHexStringTo256Bit(senderAddress.toHexString());
        String stateVariableIndex = convertIntegerToHex256Bit(1);
        String storageSlotMapping = Numeric.toHexStringNoPrefix(Hash.sha3(Numeric.hexStringToByteArray(paddedAddress + stateVariableIndex)));
        System.out.println("    Slot SHA3[msg.sender||1] (mapping): "+simpleWorld.get(contractAddress).getStorageValue(UInt256.fromHexString(storageSlotMapping)));
        System.out.println();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        StandardJsonTracer tracer = new StandardJsonTracer(printStream, true, true, true, true);

        var executor = EVMExecutor.evm(EvmSpecVersion.CANCUN);
        executor.tracer(tracer);
        executor.code(Bytes.fromHexString(deploymentBytecode)); // Set contract bytecode
        executor.sender(senderAddress);
        executor.receiver(contractAddress);
        executor.worldUpdater(simpleWorld.updater());
        executor.commitWorldState();
        executor.callData(Bytes.EMPTY); // Empty callData for contract creation
        executor.execute();

        executor.code(Bytes.fromHexString(runtimeBytecode)); // Set contract bytecode
        executor.commitWorldState();
        executor.callData(Bytes.fromHexString("06fdde03")); // name()
        executor.execute();
        String name = extractStringFromReturnData(byteArrayOutputStream);
        System.out.println("ISTCoin name: " + name);


}

    
}
