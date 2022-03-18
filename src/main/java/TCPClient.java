import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

public class TCPClient {
    private Socket client;

    public TCPClient(Socket socket){
        this.client = socket;
    }

    /**
     * This is use to connection to server
     * */
    public void connection(InetAddress address, int port){
        try {
            client = new Socket(address,port);
            PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
            for (int i =0 ;i<10;i++){
                writer.println("TCP" + i);
                Thread.sleep(1000);
            }
            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
