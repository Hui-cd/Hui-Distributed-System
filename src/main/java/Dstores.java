import java.net.UnknownHostException;

public class Dstores {
    private int port;
    private int cport;
    private int timeout;
    private String file_folder;
    private TCPServer server;
    private TCPClient client;

    public Dstores(int port,int cport,int timeout) throws UnknownHostException {
        this.port =port;
        this.cport = cport;
        this.timeout = timeout;
        this.server.start();
        this.client.connection(server.getAddress(),port);
    }
}