import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

/**
 * @author gongyihui
 */
public class TCPClient{
    private Socket client;
    private BufferedReader reader;
    private Socket server;
    private PrintWriter printWriter;

    public TCPClient(InetAddress address, int port) throws IOException {
        this(new Socket(address,port));
    }

    public TCPClient(Socket socket) throws IOException {
        this(socket,socket);
    }

    public TCPClient(Socket input, Socket output) throws IOException {
        this.client = input;
        this.server = output;
        reader = new BufferedReader(new InputStreamReader(input.getInputStream()));
        printWriter = new PrintWriter(output.getOutputStream());
    }

    public void disconnect(){
        try{
        client.close();
        }catch (Exception e){
            e.getMessage();
        }
    }

    public void processMessage(String msg){

    }


    public String await(int timeout) throws Exception{
        String message = null;
        this.client.setSoTimeout(timeout);
        message = this.reader.readLine();
        this.client.setSoTimeout(0);
        return message;
    }


    public void sendMessage(String message){
        this.printWriter.println(message);
        this.printWriter.flush();
    }
}
