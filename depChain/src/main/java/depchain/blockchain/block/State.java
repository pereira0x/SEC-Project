package depchain.blockchain.block;

import java.util.Map;


public class State {
  private Map<String, Long> accounts;
  private AccessControl accessControl;
  private Map<String, String> storage;

  // Getters and setters
  public Map<String, Long> getAccounts() { return accounts; }
  public void setAccounts(Map<String, Long> accounts) { this.accounts = accounts; }

  public AccessControl getAccessControl() { return accessControl; }
  public void setAccessControl(AccessControl accessControl) { this.accessControl = accessControl; }

  public Map<String, String> getStorage() { return storage; }
  public void setStorage(Map<String, String> storage) { this.storage = storage; }
}