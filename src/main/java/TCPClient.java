

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

/**
 * @author gongyihui
 */
public class TCPClient extends Thread{
    private Socket client;
    private BufferedReader reader;
    private Socket server;
    private PrintWriter printWriter;

    /**
     * This is use to connection to server
     * */
    public void connection(InetAddress address, int port){
        try {
            client = new Socket(address,port);
            printWriter = new PrintWriter(client.getOutputStream(),true);
            printWriter.println("Connection...");
            System.out.println("connection");

        } catch (Exception e) {
            e.getMessage();

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
