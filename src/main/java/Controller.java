import java.io.File;

public class Controller {
    private int cport;
    private int R;
    private int timeout;
    private int rebalance_period;
    private TCPServer server;
    private Index index;

    public Controller(int cport,int R,int timeout,int rebalance_period){
        this.cport = cport;
        this.R = R;
        this.timeout = timeout;
        this.rebalance_period = rebalance_period;
        server = new TCPServer(cport,timeout);
        server.run();
    }

    public void store(TCPClient client,String fileSame,int fileSize){

    }
}
