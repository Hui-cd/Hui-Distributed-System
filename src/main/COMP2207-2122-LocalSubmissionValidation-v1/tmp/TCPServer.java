import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {
    private ServerSocket serverSocket;
    private final Handler handler;
    private final int port;
    private final int timeout;
    private final Logger logger;

    public TCPServer(int port, int timeout, Handler handler, Logger logger) {
        this.port = port;
        this.timeout = timeout;
        this.logger = logger;
        try {
            this.serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("ERROR: failed to create socket");
            this.serverSocket = null;
        }
        this.handler = handler;
    }

    public void run() {
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                TCPConnection connection = new TCPConnection(client, logger);
                // create a worker receive and handle message
                new Thread(() -> {
                    String message;
                    try {
                        while ((message = connection.receive()) != null) {
                            handler.handle(connection, message);
                        }
                    } catch (IOException e) {
                        logger.info(
                                String.format("Connection from [%d] to [%d] is closed", connection.getPort(), port));
                    } finally {
                        connection.close();
                    }
                }).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        logger.info("server exit");
    }

    public int getPort() {
        return port;
    }

    public int getTimeout() {
        return timeout;
    }

    public boolean normal() {
        return serverSocket != null;
    }
}
