package depchain;

import java.math.BigInteger;

import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import depchain.account.SmartAccount;

import org.junit.jupiter.api.Test;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SmartContractsTest {

    @Test
    public void testSmartContracts() {
        SmartAccount smartAccount = new SmartAccount();
        //name
        System.out.println("ISTCoin name: " + smartAccount.name());
        //symbol
        System.out.println("ISTCoin symbol: " + smartAccount.symbol());
        //decimals
        System.out.println("ISTCoin decimals: " + smartAccount.decimals());
        //totalSupply
        System.out.println("ISTCoin totalSupply: " + smartAccount.totalSupply());
        //balanceOf
        System.out.println("ISTCoin owner balanceOf: " + smartAccount.balanceOf("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
        System.out.println("ISTCoin mock balanceOf: " + smartAccount.balanceOf("1234abcd1234abcd1234abcd1234abcd1234abcd"));
        //transfer from owner to mock
        System.out.println("ISTCoin transfer: " + smartAccount.transfer("1234abcd1234abcd1234abcd1234abcd1234abcd", BigInteger.valueOf(1)));
        // verify balance
        System.out.println("ISTCoin balanceOf mock: " + smartAccount.balanceOf("1234abcd1234abcd1234abcd1234abcd1234abcd"));
        System.out.println("ISTCoin balanceOf owner: " + smartAccount.balanceOf("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
        //approve
        System.out.println("ISTCoin approve: " + smartAccount.approve("1234abcd1234abcd1234abcd1234abcd1234abcd", BigInteger.valueOf(1)));
        //verify allowance
        System.out.println("ISTCoin allowance: " + smartAccount.allowance("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef", "1234abcd1234abcd1234abcd1234abcd1234abcd"));
        //transferFrom
        System.out.println("ISTCoin transferFrom: " + smartAccount.transferFrom("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef", "6664abcd6664abcd6664abcd6664abcd6664abcd", BigInteger.valueOf(1)));
        // verify balances after transferFrom
        System.out.println("ISTCoin balanceOf mock: " + smartAccount.balanceOf("1234abcd1234abcd1234abcd1234abcd1234abcd"));
        System.out.println("ISTCoin balanceOf another: " + smartAccount.balanceOf("6664abcd6664abcd6664abcd6664abcd6664abcd"));
        //owner
        System.out.println("ISTCoin owner: " + smartAccount.owner());
        //isBlacklisted
        System.out.println("ISTCoin [1] isBlacklisted: " + smartAccount.isBlacklisted("1234abcd1234abcd1234abcd1234abcd1234abcd"));
        //addToBlacklist
        System.out.println("ISTCoin [1] addToBlacklist: " + smartAccount.addToBlacklist("1234abcd1234abcd1234abcd1234abcd1234abcd"));
        //isBlacklisted
        System.out.println("ISTCoin [1] isBlacklisted: " + smartAccount.isBlacklisted("1234abcd1234abcd1234abcd1234abcd1234abcd"));
        //removeFromBlacklist
        System.out.println("ISTCoin [1] removeFromBlacklist: " + smartAccount.removeFromBlacklist("1234abcd1234abcd1234abcd1234abcd1234abcd"));
        //isBlacklisted
        System.out.println("ISTCoin [1] isBlacklisted: " + smartAccount.isBlacklisted("1234abcd1234abcd1234abcd1234abcd1234abcd"));
        
        // test transfer to blacklisted account
        // set up blacklisted account
        System.out.println("ISTCoin [1] addToBlacklist: " + smartAccount.addToBlacklist("1234abcd1234abcd1234abcd1234abcd1234abcd"));
        // try to transfer to blacklisted account
        System.out.println("ISTCoin [1] transfer to blacklisted account: " + smartAccount.transfer("1234abcd1234abcd1234abcd1234abcd1234abcd", BigInteger.valueOf(1)));
        // verify balance
        System.out.println("ISTCoin [1] balanceOf mock: " + smartAccount.balanceOf("1234abcd1234abcd1234abcd1234abcd1234abcd"));
        // verify balance
        System.out.println("ISTCoin [1] balanceOf owner: " + smartAccount.balanceOf("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    }
    
}
