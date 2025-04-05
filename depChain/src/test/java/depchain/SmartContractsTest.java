package depchain;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.hyperledger.besu.datatypes.Address;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import depchain.account.SmartAccount;
import io.github.cdimascio.dotenv.Dotenv;

import org.junit.jupiter.api.Test;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SmartContractsTest {

    @Test
    public void testSmartContracts() {
        SmartAccount smartAccount = new SmartAccount();
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

        String address = ownerAddressHex;
        //name
        System.out.println("ISTCoin name: " + smartAccount.name(address));
        //symbol
        System.out.println("ISTCoin symbol: " + smartAccount.symbol(address));
        //decimals
        System.out.println("ISTCoin decimals: " + smartAccount.decimals(address));
        //totalSupply
        System.out.println("ISTCoin totalSupply: " + smartAccount.totalSupply(address));
        //balanceOf
        System.out.println("ISTCoin owner balanceOf: " + smartAccount.balanceOf(address,"deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
        System.out.println("ISTCoin idontexsit balanceOf: " + smartAccount.balanceOf(address,"dead2222dead2222dead2222dead2222dead2222"));
        System.out.println("ISTCoin mock balanceOf: " + smartAccount.balanceOf(address,"1234abcd1234abcd1234abcd1234abcd1234abcd"));
        //transfer from owner to mock
        System.out.println("ISTCoin transfer: " + smartAccount.transfer(address,"1234abcd1234abcd1234abcd1234abcd1234abcd", BigInteger.valueOf(1)));
        // verify balance
        System.out.println("ISTCoin balanceOf mock: " + smartAccount.balanceOf(address,"1234abcd1234abcd1234abcd1234abcd1234abcd"));
        System.out.println("ISTCoin balanceOf owner: " + smartAccount.balanceOf(address,"deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
        //approve
        System.out.println("ISTCoin approve: " + smartAccount.approve(address,"1234abcd1234abcd1234abcd1234abcd1234abcd", BigInteger.valueOf(1)));
        //verify allowance
        System.out.println("ISTCoin allowance: " + smartAccount.allowance(address,"deadbeefdeadbeefdeadbeefdeadbeefdeadbeef", "1234abcd1234abcd1234abcd1234abcd1234abcd"));
        //transferFrom
        System.out.println("ISTCoin transferFrom: " + smartAccount.transferFrom(address,"deadbeefdeadbeefdeadbeefdeadbeefdeadbeef", "6664abcd6664abcd6664abcd6664abcd6664abcd", BigInteger.valueOf(1)));
        // verify balances after transferFrom
        System.out.println("ISTCoin balanceOf mock: " + smartAccount.balanceOf(address,"1234abcd1234abcd1234abcd1234abcd1234abcd"));
        System.out.println("ISTCoin balanceOf another: " + smartAccount.balanceOf(address,"6664abcd6664abcd6664abcd6664abcd6664abcd"));
        //owner
        System.out.println("ISTCoin owner: " + smartAccount.owner(address));
        //isBlacklisted
        System.out.println("ISTCoin [1] isBlacklisted: " + smartAccount.isBlacklisted(address,"1234abcd1234abcd1234abcd1234abcd1234abcd"));
        //addToBlacklist
        System.out.println("ISTCoin [1] addToBlacklist: " + smartAccount.addToBlacklist(address,"1234abcd1234abcd1234abcd1234abcd1234abcd"));
        //isBlacklisted
        System.out.println("ISTCoin [1] isBlacklisted: " + smartAccount.isBlacklisted(address,"1234abcd1234abcd1234abcd1234abcd1234abcd"));
        //removeFromBlacklist
        System.out.println("ISTCoin [1] removeFromBlacklist: " + smartAccount.removeFromBlacklist(address,"1234abcd1234abcd1234abcd1234abcd1234abcd"));
        //isBlacklisted
        System.out.println("ISTCoin [1] isBlacklisted: " + smartAccount.isBlacklisted(address,"1234abcd1234abcd1234abcd1234abcd1234abcd"));
        
        // test transfer to blacklisted account
        // set up blacklisted account
        System.out.println("ISTCoin [1] addToBlacklist: " + smartAccount.addToBlacklist(address,"1234abcd1234abcd1234abcd1234abcd1234abcd"));
        // try to transfer to blacklisted account
        System.out.println("ISTCoin [1] transfer to blacklisted account: " + smartAccount.transfer(address,"1234abcd1234abcd1234abcd1234abcd1234abcd", BigInteger.valueOf(1)));
        // verify balance
        System.out.println("ISTCoin [1] balanceOf mock: " + smartAccount.balanceOf(address,"1234abcd1234abcd1234abcd1234abcd1234abcd"));
        // verify balance
        System.out.println("ISTCoin [1] balanceOf owner: " + smartAccount.balanceOf(address,"deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    }
    
}
