import java.io.IOException;
import java.util.HashSet;
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
    private Set<Dstores> dstoresSet ;
    private Set<TCPClient> clientsSet;
    private Index index;
    public Controller(int cport, int R, int timeout, int rebalance_period) throws IOException {
        super(cport);
        this.cport = cport;
        this.R = R;
        this.timeout = timeout;
        this.rebalance_period = rebalance_period;
        this.dstoresSet = new HashSet<>();
        this.clientsSet = new HashSet<>();
    }
    /**
     * this is used to add dstores
     * @param dstores
     * */
    public void addDstores(Dstores dstores){
        dstoresSet.add(dstores);
    }

    /**
     * this is used to remove dstores
     * @param dstores
     * */
    public void removeDstores(Dstores dstores){
        dstoresSet.remove(dstores);
    }

    /**
     * this is used to add clients
     * @param client
     * */

    public void addClients(TCPClient client){
        clientsSet.add(client);
    }

    /**
     * this is used to remove client
     * @param client
     * */
    public void removeClients(TCPClient client){
        clientsSet.remove(client);
    }

    /**
     * this is used to sendMessage store requests
     * @param client client
     * @param filename file's name
     * @param fileSize file's szie
     * */
    public void store(TCPClient client, String filename, int fileSize){
        // check if there are enough dstores or not
        if (checkDstoreNum()){
            client.sendMessage(failureHandling.ERROR_NOT_ENOUGH_DSTORES);
            return;
        }
       // check fileName
        if (checkFile(filename)){
            return;
        }

        // check file is exist or not
        if (!index.beginStore(filename,fileSize)){
            client.sendMessage(failureHandling.ERROR_FILE_ALREADY_EXISTS_STORE);
        }

        // begin store
        String token = failureHandling.STORE_TOKEN;

        for ( Dstores dstore : dstoresSet){

            token += " " + dstore.getPort();

            client.sendMessage(token);
        }
        boolean complete = index.awaitStore(filename);
        if (complete){
            client.sendMessage(failureHandling.STORE_COMPLETE);
        }

        // store complete
        index.endStore(filename,fileSize,dstoresSet,complete);
    }

    /**
     * it is used to load file
     * @param client
     * @param filename
     * @param i how many times
     */

    public void load(TCPClient client, String filename, int i){
        try {
            if (checkDstoreNum()){
                client.sendMessage(failureHandling.ERROR_NOT_ENOUGH_DSTORES);
                return;
            }
            dstoresSet = index.getFileDstores(filename);
            if (dstoresSet==null){
                client.sendMessage(failureHandling.ERROR_FILE_DOES_NOT_EXIST);
            }else {
                if (i>dstoresSet.size()){
                    client.sendMessage(failureHandling.ERROR_LOAD);
                }else {
                    client.sendMessage(failureHandling.LOAD_TOKEN + " "+ dstoresSet.iterator().next().getPort()+" "+index.getFileDstores(filename));
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    /**
     * it is used to get R
     * @return R
     */
    public int getR() {
        return  R;
    }

    /**
     * it is used to get time out
     * @return timeout
     */
    public int getTimeout() {
        return  timeout;
    }
    public boolean checkFile(String filename){
        if (filename == null || filename == ""|| filename.trim() == ""){
            return true;
        }else{
            return false;
        }
    }
    public boolean checkDstoreNum(){
        if (dstoresSet.size()<R){
            return true;
        }else{
            return false;
        }
    }
}