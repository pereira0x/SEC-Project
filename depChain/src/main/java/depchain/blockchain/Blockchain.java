package depchain.blockchain;

import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;

import depchain.account.EOAccount;
import depchain.account.SmartAccount;
import depchain.blockchain.block.Block;
import depchain.blockchain.block.BlockParser;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;

public class Blockchain {
    
    private int memberId;
    private List<Block> blocks;
    private EOAccount eoAccount1;
    private EOAccount eoAccount2;
    private SmartAccount smartAccount;


    public Blockchain(int memberId) throws IOException {
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

        List<Path> jsonFiles = new ArrayList<>();
        try {
            jsonFiles = Files.list(Paths.get(directoryPath))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            /* throw new RuntimeException("Error reading files from directory: " + directoryPath, e); */
        }

            if (jsonFiles.isEmpty()) {
                /* throw new IllegalArgumentException("No JSON files found in the directory."); */
            }

            Path lastBlockFile = jsonFiles.get(jsonFiles.size() - 1);
            String content = new String(Files.readAllBytes(lastBlockFile));
            JSONObject json = new JSONObject(content);
            System.out.println("JSON: " + json.toString());
    }

}
