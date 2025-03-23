package depchain;

import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import depchain.account.SmartContract;

import org.junit.jupiter.api.Test;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SmartContractsTest {

    @Test
    public void testSmartContracts() {
        SmartContract smartContract = new SmartContract();
    }
    
}
