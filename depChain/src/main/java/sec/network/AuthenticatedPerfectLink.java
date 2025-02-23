package sec.network;

import sec.network.StubbornLink;

public class AuthenticatedPerfectLink {

    private StubbornLink stubbornLink;

    public AuthenticatedPerfectLink() {
        this.stubbornLink = new StubbornLink();
    }

    public void send(String ip, int port, String data) {
        stubbornLink.send(ip, port, data);
    }
    
}
