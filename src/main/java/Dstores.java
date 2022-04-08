

import java.io.File;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

/**
 * @author gongyihui
 */
public class Dstores {

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

    public Dstores(int port,int cport,int timeout,String filePath) throws UnknownHostException {
        this.filePath = filePath;
        this.port =port;
        this.cport = cport;
        this.timeout = timeout;
    }
    /**
     * 这用来向controller加入dstores
     * */
    /**
     * 储存文件
     * @param fileName
     * @param fileSize
     * */
    public void store(String fileName,int fileSize){
        File file = new File(filePath+"/"+fileName);
        Path path = Paths.get(file.getPath());
        files.put(fileName,file);
    }

    public void load(){}

    public int getPort() {
        return  port;
    }

    public void remove(String fileName) {
        files.remove(fileName);
    }
    public void sendMessage(String msg) {
        LoggerProtocol.getInstance().messageSent(port,msg);
    }
}
