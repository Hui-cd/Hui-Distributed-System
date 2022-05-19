import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {

    private final String name;
    private PrintStream ps;

    public Logger(String name) {
        this.name = name;    
    }

    private synchronized PrintStream getPrintStream() {
        if (ps == null) {
            try {
                ps = new PrintStream(name + ".log");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return ps;
    }

    public void info(String message) {
        log("[INFO] " + message);
    }

    public void error(String message) {
        log("[ERROR] " + message);
    }

    private void log(String message) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss:SSS");
        String dateTime = LocalDateTime.now().format(formatter);
        String logMessage = String.format("%s %s %s",dateTime, name, message);
        System.out.println(logMessage);
        getPrintStream().println(logMessage);
    }

    public void messageSent(Socket socket, String message) {
        log("[MESSAGE] [" + socket.getLocalPort() + " --> " + socket.getPort() + "] " + message);
    }

    public void messageReceived(Socket socket, String message) {
        log("[MESSAGE] [" + socket.getLocalPort() + " <-- " + socket.getPort() + "] " + message);
    }

    public void connectionClosed(Socket socket) {
        log("[MESSAGE] [" + socket.getLocalPort() + " <--> " + socket.getPort() + "] Connection Closed");
    }

}