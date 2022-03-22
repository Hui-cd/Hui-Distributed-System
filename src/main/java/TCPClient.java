import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

/**
 * @author gongyihui
 */
public class TCPClient extends Thread{
    private static final Logger logger = LogManager.getLogger(TCPClient.class);
    private Socket client;
    private BufferedReader reader;
    private Socket server;
    private PrintWriter printWriter;
    /**
     * reader 从客户端得到输入的消息
     * writer 将从客户端得到的消息传入到服务端
     * * */
    public TCPClient(Socket socket) throws IOException {
        this.client = socket;
        this.reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
        this.printWriter = new PrintWriter(server.getOutputStream(), true);
    }

    /**
     * This is use to connection to server
     * */
    public void connection(InetAddress address, int port){
        try {
            client = new Socket(address,port);
        } catch (Exception e) {
            e.getMessage();
            logger.error("connection failed");

        }
    }
    public void disconnect(){
        try{
        client.close();
        }catch (Exception e){
            e.getMessage();
        }
    }


    public String await(int timeout) throws Exception{
        String message = null;
        this.client.setSoTimeout(timeout);
        message = this.reader.readLine();
        this.client.setSoTimeout(0);
        return message;
    }
    public String processMessage(String message){
        return message;
    }
    public void dispatch(String message){
        this.printWriter.println(message);
        this.printWriter.flush();
    }
}
