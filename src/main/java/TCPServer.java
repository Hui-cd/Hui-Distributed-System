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
    private int port;
    private BufferedReader reader;


    /**
     * It is used to connection between client and server
     * */
    public void connection(int port, int timeout){
        while (true){
            try {
                serversocket = new ServerSocket(port);
                this.serversocket.accept();
                reader = new BufferedReader(new InputStreamReader(serversocket.accept().getInputStream()));
                String line = "waiting...";
                line = reader.readLine();
                System.out.println(line);
            }catch (Exception e){
                e.getMessage();
            }
        }
    }
    /**
     * it is used to accept client connection

     * */

    /**
     * It is used to get the server's address
     * */
    public InetAddress getAddress() throws UnknownHostException {
        InetAddress address = InetAddress.getLocalHost();
        return address;
    }

}
