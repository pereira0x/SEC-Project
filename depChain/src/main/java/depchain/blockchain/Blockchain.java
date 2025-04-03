package depchain.blockchain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;

import depchain.account.EOAccount;
import depchain.account.SmartAccount;
import depchain.blockchain.block.Block;
import depchain.blockchain.block.BlockParser;
import depchain.utils.EVMUtils;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import io.github.cdimascio.dotenv.Dotenv;


public class Blockchain {
    
    private int memberId;
    private List<Block> blocks = new ArrayList<>();
    private List<EOAccount> eoAccounts = new ArrayList<>();
    private SmartAccount smartAccount;
    private final Path serverDir;


    public Blockchain(int memberId, List<PublicKey> clientPublicKeys) throws IOException {
        this.memberId = memberId;
        
        // Directory path is env variable - Dotenv
        String genesisPath = Dotenv.load().get("BLOCKS_FOLDER") + "/genesisBlock.json";
        if (genesisPath == null)
            throw new IllegalArgumentException("Environment variable BLOCKS_FOLDER is not set.");

        String serverPath = Dotenv.load().get("BLOCKS_FOLDER") + "/server_" + memberId;

        // We remove the serverPath directory if it exists and then created it again to clean it
        serverDir = Paths.get(serverPath);
        if (Files.exists(serverDir)) {
            try {
                Files.walk(serverDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> file.delete());
            } catch (IOException e) {
                throw new RuntimeException("Error deleting server directory: " + serverPath, e);
            }
        }
        Files.createDirectories(serverDir);
        Logger.log(LogLevel.INFO, "Server directory created: " + serverPath);

        // Insert genesis block
        Logger.log(LogLevel.INFO, "Fetching from genesis block.");
        Path initialBlock = Paths.get(genesisPath);
        String content = new String(Files.readAllBytes(initialBlock));

        JSONObject json = new JSONObject(content);
        /* System.out.println("JSON: " + json.toString()); */

        // Create Externally Owned Accounts (EOA)
        JSONObject state = json.getJSONObject("state");
        for(PublicKey clientKey : clientPublicKeys)
            createEOAccount(clientKey, state);

        // Create Smart Contract Account (SCA)
        getSmartContractAccount(state);

        // Create the initial block
        try {
            createInitialBlock(initialBlock);
        } catch (Exception e) {
            throw new RuntimeException("Error creating blocks:", e);
        }
    }

    public void createInitialBlock(Path initialBlock) throws IOException, Exception {
        String content = new String(Files.readAllBytes(initialBlock));

        JSONObject json = new JSONObject(content);

        Block block = BlockParser.parseBlock(json);

        blocks.add(block);
    }

    public void getSmartContractAccount(JSONObject state) {

        String deploymentBytecode = Dotenv.load().get("RUNTIME_BYTECODE");
        String smartAccountAddress;

        try {
            smartAccountAddress = EVMUtils.getSmartAccountAddress(deploymentBytecode);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating smart account address", e);
        }

        JSONObject smartContract = state.getJSONObject(smartAccountAddress);

        // String smartContractBytecode = smartContract.getString("bytecode");
        Long smartContractBalance = smartContract.getLong("balance");
        JSONObject storage = smartContract.getJSONObject("storage");
        Integer smartContractOwner = storage.getInt("owner");

        smartAccount = new SmartAccount(smartAccountAddress, smartContractBalance, smartContractOwner);

        System.out.println("Smart Contract Account: " + smartAccount.getAddress() + " Balance: " + smartAccount.getBalance() + " Owner: " + smartAccount.getOwner());
    }

    public void createEOAccount(PublicKey clientKey, JSONObject state) {
        String accountAddress;
        try {
            accountAddress = EVMUtils.getEOAccountAddress(clientKey);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating account address", e);
        }

        try {
            if (!state.has(accountAddress)) {
                Logger.log(LogLevel.ERROR, "Account address not found in state: " + accountAddress);
            }
        } catch (IllegalArgumentException e) {
            /* Handle the exception */
        }
        JSONObject account = state.getJSONObject(accountAddress);
        Long balance = account.getLong("balance");
        
        EOAccount eoAccount = new EOAccount(accountAddress, balance);

        Logger.log(LogLevel.INFO, "EOAccount: " + eoAccount.getAddress() + " Balance: " + eoAccount.getBalance());
        eoAccounts.add(eoAccount);
    }

    public void addBlock(Block block) {
        blocks.add(block);
    }

    public ArrayList<Block> getChain() {
        return new ArrayList<>(blocks);
    }

    public ArrayList<String> getHashesChain() {
        ArrayList<String> hashes = new ArrayList<>();
        for (Block block : blocks) {
            hashes.add(block.getBlockHash());
        }
        return hashes;
    }

    public Block getMostRecentBlock() {
        return blocks.get(blocks.size() - 1);
    }
}
