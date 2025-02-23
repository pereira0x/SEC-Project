package sec.client;

import sec.network.AuthenticatedPerfectLink;

public class Client {
    public static void main(String[] args) {
        AuthenticatedPerfectLink channel = new AuthenticatedPerfectLink();
        channel.send("127.0.0.1", 1234, "Hello, World!");

    }
}