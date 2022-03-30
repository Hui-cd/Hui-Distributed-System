import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

/**
 * @author gongyihui
 */
public class Dstores {
    private Logger logger = LogManager.getLogger(Dstores.class);

    private int port;
    private int cport;
    private int timeout;
    private String filePath;
    private String file_folder;
    private  TCPServer server;
    private TCPClient client;
    private PrintWriter printWriter;
    private Controller controller;
    private FailureHandling failureHandling;
    private HashMap<String, File> files = new HashMap<>();

    public Dstores(int port,int cport,int timeout) throws UnknownHostException {
        this.port =port;
        this.cport = cport;
        this.timeout = timeout;
        this.server.start();
    }
    /**
     * 这用来向controller加入dstores
     * */
    public void join(int port) throws IOException {
        Socket socket = new Socket(server.getAddress(),this.cport);
        printWriter = new PrintWriter(socket.getOutputStream(),true);
        printWriter.println("join"+String.valueOf(port));
        socket.close();
        printWriter.close();
    }
    /**
     * 储存文件
     * */
    public void store(Socket client, String fileName, int fileSize){
        try {
            client.setSoTimeout(this.timeout);
            printWriter = new PrintWriter(client.getOutputStream(),true);
            printWriter.println(FailureHandling.ACK_TOKEN);
            byte[] fileContent = client.getInputStream().readNBytes(fileSize);
            File file = new File(filePath+"/"+fileName);
            Path path = Paths.get(file.getPath());
            files.put(fileName,file);
            printWriter.close();
            client.close();
        }catch (Exception e){
            e.getMessage();
        }
    }

    public void loadData(Socket client ,String fileName){
        try {
            client.setSoTimeout(this.timeout);
            printWriter = new PrintWriter(client.getOutputStream(),true);
            if (files.containsKey(fileName)){
                byte[] fileContent = Files.readAllBytes(files.get(fileName).toPath());
                client.getOutputStream().write(fileContent);
                printWriter.close();
                client.close();
            }
        }catch (Exception e){
            e.getMessage();
        }
    }

    public void removeFile(String fileName){
        files.get(fileName).delete();
        files.remove(fileName);
    }
}
