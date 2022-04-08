import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

/**
 * @author gongyihui
 */
public class TCPServer extends Thread {

    private ServerSocket serversocket;

    public TCPServer(int port) throws IOException {
        this.serversocket = new ServerSocket(port);
        this.serversocket.accept();
    }

    /**
     * It is used to get the server's address
     * */
    public InetAddress getAddress() throws UnknownHostException {
        InetAddress address = InetAddress.getLocalHost();
        return address;
    }
}
