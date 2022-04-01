

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * @author gongyihui
 */
public class Controller extends TCPServer{


    private int cport;
    private int R;
    private int timeout;
    private int rebalance_period;
    private FailureHandling failureHandling;
    private TCPServer server;
    private Index index;
    private ArrayList<Integer> port = new ArrayList<>();
    private ArrayList<Integer> destorePorts = new ArrayList<>();
    /**这用来储存要被储存的文件*/
    private HashMap<String, Integer> fileShareCount = new HashMap<>();
    /** loadCount为一个文件加载的次数*/
    private HashMap<String, Integer> loadCount = new HashMap<>();
    private Set<Dstores> dstoresSet;
    private PrintWriter printWriter;
    private BufferedReader bufferedReader;
    private Dstores dstores;
    /**
     * filePort 是一个hashMap， key是文件名，value是端口，表示这个文件在哪些端口中保存
     **/
    private HashMap<String,List<Integer>> filePort = new HashMap<>(); ;
    /**
     * fileState 表示一个文件目前的状态
     * */
    private HashMap<String,IndexState> fileState = new HashMap<>();

    public Controller(int cport,int R,int timeout,int rebalance_period) throws IOException {
        this.cport = cport;
        this.R = R;
        this.timeout = timeout;
        this.rebalance_period = rebalance_period;
        server.connection(cport,timeout);
    }

    public void store(Socket client, String fileName, int fileSize){
        try {
            // 检查文件格式
            if (fileName == null|| fileName == ""||fileName.trim() == ""){
                return;

            }
            // 检查文件是否被储存
            if (filePort.containsKey(fileName)){
                printWriter.println(failureHandling.ERROR_FILE_ALREADY_EXISTS_STORE);
                return;
            }
            //检查是否有足够的 store
            if (dstoresSet.size()<=R){
                printWriter.println(failureHandling.ERROR_NOT_ENOUGH_DSTORES);
                return;
            }
            //更新index，表明处在ready状态
            if (!filePort.containsKey(fileName)){
                fileState.put(fileName,IndexState.READY);
            }
            //储存
            fileState.put(fileName,IndexState.STORE_IN_PROGRESS);
            //0为起始
            fileShareCount.put(fileName,0);
            printWriter.println(fileName+ "store");
            printWriter.close();
            client.close();
        } catch (Exception e){
            e.getMessage();
        }
    }

    public void store_ack(Socket client, String fileName) throws IOException {
        if (fileShareCount.containsKey(fileName)){
            fileShareCount.put(fileName,fileShareCount.get(fileName)+1);
            printWriter.println(failureHandling.STORE_COMPLETE);
        }else{
        }
    }

    /**
     * 这用来加入Destore
     * */
    /**public void addDestore(Dstores dstores) throws IOException {
        dstores.join(cport);
        dstoresSet.add(dstores);
    }*/
    /**
     * 这用来移除Destores
     * */
    public void removeDestore(Dstores dstores){
        dstoresSet.remove(dstores);
    }

   /**
    * 这用来加载文件
    * */

   public void load(Socket client,String fileName) throws IOException {
       client.setSoTimeout(timeout);
       printWriter = new PrintWriter(client.getOutputStream(),true);
       bufferedReader = new BufferedReader(new InputStreamReader(client.getInputStream()));
       if (!filePort.containsKey(fileName)){
           printWriter.println(failureHandling.ERROR_FILE_DOES_NOT_EXIST);
           return;
       }
       if (dstoresSet.size()<= R){
           printWriter.println(failureHandling.ERROR_NOT_ENOUGH_DSTORES);
           return;
       }

       fileState.put(fileName,IndexState.LOAD_IN_PROGRESS);
       if (!loadCount.containsKey(fileName)){
           loadCount.put(fileName,1);
       }
   }

   /**
    * 这用来向dstoresSet加入Dstores
    * @param port 加入的端口
    * */
   public void dstoresJoin(int port) throws IOException {
       port = cport;
       dstores.join(port);
       dstoresSet.add(dstores);
   }

   /**
    * 这用来remove文件
    * */
   public void remove(Socket client, String fileName) throws IOException {
       printWriter = new PrintWriter(client.getOutputStream(),true);
       if (dstoresSet.size()<R){
           printWriter.println(failureHandling.ERROR_NOT_ENOUGH_DSTORES);
           return;
       }
       if (!filePort.containsKey(fileName)){
           printWriter.println(failureHandling.ERROR_FILE_DOES_NOT_EXIST);
           return;
       }
       if (filePort.containsKey(fileName)){
           filePort.remove(fileName);
           printWriter.println(failureHandling.REMOVE_COMPLETE);
       }
   }
}
