import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author gongyihui
 */
public class TCPServer extends Thread {
    private static final Logger logger = LogManager.getLogger(TCPServer.class);
    private ServerSocket serversocket;
    private int port;

    public TCPServer(int port,int timeout){
        this.port = port;
        try {
            serversocket = new ServerSocket(port);
            serversocket.setSoTimeout(10000);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * It is used to connection between client and server
     * */
    public void connection(){
        while (true){
            try {
                acceptConnection();
            }catch (Exception e){
                e.getMessage();
                logger.error("connection failed in server");
            }
        }
    }
    /**
     * it is used to accept client connection

     * */

    public void acceptConnection() throws IOException {
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
