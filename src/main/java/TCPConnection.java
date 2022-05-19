import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class TCPConnection {
    private final Socket socket;

    private PrintWriter writer;
    private BufferedReader reader;
    private volatile boolean closed;
    private final Logger logger;
    
    public TCPConnection(final Socket socket, Logger logger) {
        this.socket = socket;
        this.logger = logger;
        try {
            this.closed = false;
            this.writer = new PrintWriter(this.socket.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
        } catch (IOException e) {
            this.closed = true;
        }
    }

    public void send(String string) {
        if (!socket.isClosed() && socket.isConnected()) {
            logger.messageSent(socket, string);
            writer.println(string);
        }
    }

    public String receive() throws IOException {
        String message = reader.readLine();
        if (message != null) {
            logger.messageReceived(socket, message);
        } else {
            logger.connectionClosed(socket);
        }
        return message;
    }

    public void close() {
        try {
            reader.close();
            writer.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    public int getPort() {
        return socket.getPort();
    }

    public byte[] readNBytes(int len) throws IOException {
        return socket.getInputStream().readNBytes(len);
    }

    public void writeBytes(byte[] bytes) throws IOException {
        socket.getOutputStream().write(bytes);
    }
}