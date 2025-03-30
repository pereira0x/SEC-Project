package depchain.account;

public class EOAccount {
    
    private String address;
    private Long balance;

    public EOAccount(String address, String publicKey, String privateKey) {
        this.address = address;
        this.balance = 0L;
    }

    public String getAddress() {
        return address;
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
}
