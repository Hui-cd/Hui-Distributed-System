import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author gongyihui
 */
public class Controller extends TCPServer{
    private int cport;
    private int R;
    private int timeout;
    private int rebalance_period;
    private Protocol protocol;
    private TCPServer server;
    private Index index;
    private ArrayList<Integer> port = new ArrayList<>();
    private ArrayList<Integer> destorePorts = new ArrayList<>();
    private Set<Dstores> dstoresSet;
    /**
     * filePort 是一个hashMap， key是文件名，value是端口，表示这个文件在哪些端口中保存
     **/
    private HashMap<String,List<Integer>> filePort ;
    /**
     * fileState 表示一个文件目前的状态
     * */
    private HashMap<String,IndexState> fileState;

    public Controller(int cport,int R,int timeout,int rebalance_period) throws IOException {
        super(cport,timeout);
        this.cport = cport;
        this.R = R;
        this.timeout = timeout;
        this.rebalance_period = rebalance_period;
        server = new TCPServer(cport,timeout);
        server.connection();
    }

    public void store(Socket client, String fileName, int fileSize){
        filePort = new HashMap<>();
        try {
            PrintWriter printWriter = new PrintWriter(client.getOutputStream(),true);

            if (dstoresSet.size()<R){
                printWriter.println(protocol.NOT_ENOUGH_DSTORES);
            }

            if (fileName ==null || fileName == ""|| fileName.trim() =="" ){
                return;
            }

            /**
             * 检查文件是否以及被储存
             * */
            if (filePort.containsKey(fileName)){
                printWriter.println(protocol.FILE_IS_Already_Exists);
                return;
            }
            /**
             *
             * */
            if (fileState.get(fileName)== null){
                fileState.put(fileName,IndexState.READY);

            }else if (fileState.get(fileName) != null){
                printWriter.println(protocol.File_IS_Already_Stored);
                return;
            }
            /***/
            fileState.put(fileName,IndexState.STORE_IN_PROGRESS);

            String storeToMsg = protocol.STORE_TO_TOKEN + " ";
            storeToMsg += String.join("",getRports(port,destorePorts).stream().map(p -> String.valueOf(p)).collect(Collectors.toList()));
            printWriter.println(storeToMsg);

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public List<Integer> getRports(ArrayList port, ArrayList destorePorts){
       port = (ArrayList) destorePorts.subList(0,R);
       return port;
    }
    /**
     * 这用来加入Destore
     * */
    public void addDestore(Dstores dstores){
        dstoresSet.add(dstores);
    }
    /**
     * 这用来移除Destores
     * */
    public void removeDestore(Dstores dstores){
        dstoresSet.remove(dstores);
    }

}
