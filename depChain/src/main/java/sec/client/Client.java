package main.java.sec.client;

import main.java.sec.network.MessageSender;

public class Client {
    public static void main(String[] args) {
        MessageSender sender = new MessageSender();
        sender.send("Hello World!");
    }
}