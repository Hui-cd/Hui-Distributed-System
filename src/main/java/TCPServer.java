import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TCPServer extends Thread {
    private ServerSocket serversocket;
    private int port;
    private static final Logger logger = LogManager.getLogger(TCPServer.class);

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
@Override
    public void run(){
        while (true){
            try{
                Socket server = serversocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(server.getInputStream()));
                String line;
                while((line = in.readLine())!= null){
                    System.out.println(line+ "received");
                }
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * It is used to get the server's address
     * */
    public InetAddress getAddress() throws UnknownHostException {
        InetAddress address = InetAddress.getLocalHost();
        return address;
    }

}
