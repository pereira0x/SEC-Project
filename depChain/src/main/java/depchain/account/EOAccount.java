package depchain.account;

public class EOAccount {

    private String address;
    private Long balance;

    public EOAccount(String address, Long balance) {
        this.address = address;
        this.balance = balance;
    }

    public String getAddress() {
        return address;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long value) {
        this.balance = value;
    }

    public void remove(Long value) {
        if (this.balance >= value) {
            this.balance -= value;
        } else {
            throw new IllegalArgumentException("Insufficient balance");
        }
    }
}
