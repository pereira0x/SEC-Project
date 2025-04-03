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
    private List<Block> blocks;
    private List<EOAccount> eoAccounts = new ArrayList<>();
    private SmartAccount smartAccount;


    public Blockchain(int memberId, List<PublicKey> clientPublicKeys) throws IOException {
        this.memberId = memberId;
/*         this.blocks = new ArrayList<>();
        this.eoAccount1 = new EOAccount("EOAccount1", "EOAccount1PublicKey", "EOAccount1PrivateKey");
        this.eoAccount2 = new EOAccount("EOAccount2", "EOAccount2PublicKey", "EOAccount2PrivateKey");
        this.smartAccount = new SmartAccount("SmartContract1", "SmartContract1PublicKey", "SmartContract1PrivateKey"); */
        
        // directory path is env variable - Dotenv
        String directoryPath = Dotenv.load().get("BLOCKCHAIN_DIR");
        

        if (directoryPath == null) {
            throw new IllegalArgumentException("Environment variable BLOCKCHAIN_DIR is not set.");
        }
        // apend /memberId to directory path
       String  directoryPathMember = directoryPath + "/member" + memberId;

        List<Path> jsonFiles = new ArrayList<>();
        String content = null; // Declare content variable here
        try {
            jsonFiles = Files.list(Paths.get(directoryPathMember))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());

        } catch (IOException e) {
            /* throw new RuntimeException("Error reading files from directory: " + directoryPath, e); */
        }

        if (jsonFiles.isEmpty()) {
            // insert genesis block
            System.out.println("No JSON files found in the directory. Fetching from genesis block.");
            jsonFiles.add(Paths.get(directoryPath + "/genesisBlock.json"));
            content = new String(Files.readAllBytes(jsonFiles.get(0)));
        } else{
            Path lastBlockFile = jsonFiles.get(jsonFiles.size() - 1);
            content = new String(Files.readAllBytes(lastBlockFile));
        }

        JSONObject json = new JSONObject(content);
        /* System.out.println("JSON: " + json.toString()); */


        // Create Externally Owned Accounts (EOA)
        JSONObject state = json.getJSONObject("state");
        for(PublicKey clientKey : clientPublicKeys) {
            createEOAccount(clientKey, state);
        }

        // Create Smart Contract Account (SCA)
        getSmartContractAccount(state);


        // Create blocks
        try {
            createBlocks(jsonFiles);
        } catch (Exception e) {
            throw new RuntimeException("Error creating blocks", e);
        }

/*         for (Block block : blocks) {
            System.out.println(block.toString());
        } */

    }

    public void createBlocks(List<Path> jsonFiles) throws IOException, Exception {
        blocks = new ArrayList<>();
        for (Path jsonFile : jsonFiles) {
            String content = new String(Files.readAllBytes(jsonFile));
            JSONObject json = new JSONObject(content);
            Block block = BlockParser.parseBlock(json);
            blocks.add(block);
        }
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

        System.out.println("EOAccount: " + eoAccount.getAddress() + " Balance: " + eoAccount.getBalance());
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
