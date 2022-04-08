/**
 * @author gongyihui
 */
public class LoggerProtocol extends Logger {
    private static LoggerProtocol instance = null;

    protected LoggerProtocol(LoggingType loggingType) {
        super(loggingType);
    }

    public static LoggerProtocol getInstance() {
        if (instance == null) {
            throw new RuntimeException("ControllerLogger has not been initialised yet");
        }
        return instance;
    }

    @Override
    protected String getLogFileSuffix() {
        return null;
    }
}
