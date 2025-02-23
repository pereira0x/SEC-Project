package sec.network;

import sec.network.FairLossLink;

public class StubbornLink {

    private FairLossLink fairLossLink;

    public StubbornLink() {
        this.fairLossLink = new FairLossLink();
    }

    public void send(String ip, int port, String message) {
        fairLossLink.send(ip, port, message);
    }

}
