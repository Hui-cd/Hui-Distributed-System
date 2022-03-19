import java.io.File;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;

public class Controller extends TCPServer{
    private int cport;
    private int R;
    private int timeout;
    private int rebalance_period;
    private TCPServer server;
    private Index index;
    private HashMap<String,List<Integer>> file ;


    public Controller(int cport,int R,int timeout,int rebalance_period){
        super(cport,timeout);
        this.cport = cport;
        this.R = R;
        this.timeout = timeout;
        this.rebalance_period = rebalance_period;
        server = new TCPServer(cport,timeout);
        server.run();
    }

    public void store(Socket client, String fileName, int fileSize){
        file = new HashMap<>();
        if (fileName ==null){
            return;
        }
        try {
            PrintWriter printWriter = new PrintWriter(client.getOutputStream(),true);

            if (file.containsKey(fileName)){
                printWriter.println("error, file is already exists");
            }

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
