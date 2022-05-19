/**
 * Handler define the action after receiving a message
 */
public interface Handler {
    public void handle(TCPConnection connection, String message);
}
