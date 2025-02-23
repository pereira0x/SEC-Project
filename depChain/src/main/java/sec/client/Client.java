package sec.client;

import sec.network.AuthenticatedPerfectLink;

public class Client {
    public static void main(String[] args) {
        AuthenticatedPerfectLink channel = new AuthenticatedPerfectLink();
        // if arg = '1' then send, else receive
        if (args[0].equals("1")) {
            channel.send("127.0.0.1", 1234, "Hello World!");
        } else {
            channel.receive(1234);
        }

    }
}