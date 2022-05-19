/**
 * Message contains all of the message type can be transport between nodes
 */
public class Message {
	// list
	public final static String LIST = "LIST";

	// store
	public final static String STORE = "STORE";
	public final static String STORE_TO = "STORE_TO";
	public final static String ACK = "ACK";
	public final static String STORE_ACK = "STORE_ACK";
	public final static String STORE_COMPLETE = "STORE_COMPLETE";

	// load
	public final static String LOAD = "LOAD";
	public final static String LOAD_DATA = "LOAD_DATA";
	public final static String LOAD_FROM = "LOAD_FROM";
	public final static String RELOAD = "RELOAD";

	// remove
	public final static String REMOVE = "REMOVE";
	public final static String REMOVE_ACK = "REMOVE_ACK";
	public final static String REMOVE_COMPLETE = "REMOVE_COMPLETE";

	// rebalance
	public final static String JOIN = "JOIN";
	public final static String REBALANCE = "REBALANCE";
	public final static String REBALANCE_STORE = "REBALANCE_STORE";
	public final static String REBALANCE_COMPLETE = "REBALANCE_COMPLETE";

	// error
	public final static String ERROR_LOAD = "ERROR_LOAD";
	public final static String ERROR_NOT_ENOUGH_DSTORES = "ERROR_NOT_ENOUGH_DSTORES";
	public final static String ERROR_FILE_DOES_NOT_EXIST = "ERROR_FILE_DOES_NOT_EXIST";
	public final static String ERROR_FILE_ALREADY_EXISTS = "ERROR_FILE_ALREADY_EXISTS";

	public static boolean isJoin(String message) {
		String[] messageParts = message.split(" ");
		if (messageParts[0].equals(JOIN)) {
			if (messageParts.length != 2) {
				return false;
			}

			try {
				Integer.parseInt(messageParts[1]);
				return true;
			} catch (NumberFormatException e) {
				return false;
			}
		}
		return false;
	}

	public static boolean isRebalanceStore(String message) {
		String[] messageParts = message.split(" ");
		if (messageParts[0].equals(REBALANCE_STORE)) {
			if (messageParts.length != 3) {
				return false;
			}

			try {
				Integer.parseInt(messageParts[2]);
				return true;
			} catch (NumberFormatException e) {
				return false;
			}
		}
		return false;
	}
}
