import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Dstore {
    
    private final Integer port;
    private final Integer cport;
    private final Integer timeout;
    private final File directory;
    private final TCPServer server;
    private final Logger logger;
    private TCPConnection controller;
    private final ExecutorService timeoutService;
    private final ConcurrentHashMap<Integer, TCPConnection> otherDstores;
    private final ConcurrentHashMap<TCPConnection, Integer> connectionsToPort;

    // rebalance
    private ConcurrentHashMap<Integer, String> rebalanceSend;

    private final Handler clientHandler = new Handler() {
        @Override
        public void handle(TCPConnection connection, String message) {
            String[] messageParts = message.split(" ");
            String command = messageParts[0];
            try {
                switch (command) {
                    case Message.STORE -> store(connection, messageParts);
                    case Message.LOAD_DATA -> loadData(connection, messageParts);
                    default -> logger.error("Invalid message : " + message);
                }
            } catch (IOException e) {
                // maybe connection is closed;
                if (controller.isClosed()) {
                    // do something
                }
            } catch (NumberFormatException e) {
                // ignore malformed message
                logger.error("Invalid message : " + message);
            }
        }

        public void store(TCPConnection connection, String[] messageParts) throws IOException {
            if (messageParts.length == 3) {
                // meta file
                String fileName = messageParts[1];
                int fileSize = Integer.parseInt(messageParts[2]);
                // send ACK to client to receive file
                connection.send(Message.ACK);
                // true file
                File newFile = new File(directory, fileName);
                // try to receivie file
                if (!timeoutTask(new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        try {
                            Files.write(newFile.toPath(), connection.readNBytes(fileSize));
                        } catch (IOException e) {
                            e.printStackTrace();
                            logger.error("Failed to read from socket or failed to write to file");
                            return false;
                        }
                        return true;
                    }
                }, new Callable<Void>() {
                    @Override
                    public Void call() {
                        newFile.delete();
                        return null;
                    }
                }, "Timeout, failed to receive file from client[" + connection.getPort() + "]")) {
                    return;
                }

                // send store ACK to controller
                controller.send(Message.STORE_ACK + ' ' + fileName);
            }
        }

        public void loadData(TCPConnection connection, String[] messageParts) throws IOException {
            if (messageParts.length == 2) {
                File file = new File(directory, messageParts[1]);
                if (!file.exists()) {
                    logger.error("File" + messageParts[1] + "does not exist");
                    connection.close();
                } else {
                    connection.writeBytes(Files.readAllBytes(file.toPath()));
                }
            }
        }
    };

    private final Handler dstoreHandler = new Handler() {
        @Override
        public void handle(TCPConnection connection, String message) {
            String[] messageParts = message.split(" ");
            String command = messageParts[0];
            try {
                if (command.equals(Message.REBALANCE_STORE)) {
                    rebalanceStore(connection, messageParts);
                }
            } catch (IOException e) {
                // maybe connection is closed;
                if (controller.isClosed()) {
                    // do something
                }
            } catch (NumberFormatException e) {
                // ignore malformed message
                logger.error("Invalid message : " + message);
            }
        }

        public void rebalanceStore(TCPConnection connection, String[] messageParts)
                throws NumberFormatException, IOException {
            if (messageParts.length == 3) {
                String fileName = messageParts[1];
                int fileSize = Integer.parseInt(messageParts[2]);
                File newFile = new File(directory, fileName);
                connection.send(Message.ACK);
                // waiting for file data
                if (timeoutTask(new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        try {
                            Files.write(newFile.toPath(), connection.readNBytes(fileSize));
                        } catch (IOException e) {
                            e.printStackTrace();
                            logger.error("Failed to read from socket or failed to write to file");
                            return false;
                        }
                        return true;
                    }
                }, new Callable<Void>() {
                    @Override
                    public Void call() {
                        newFile.delete();
                        return null;
                    }
                }, "Timeout, failed to receive file data from other dstore [" + connection.getPort() + "]")) {
                    logger.info("Receive file " + newFile.getName() + " from dstore [" + connection.getPort() + "]");
                }
            }
        }
    };

    private final Runnable controllerRoutine = new Runnable() {
        @Override
        public void run() {
            String message;
            try {
                while ((message = controller.receive()) != null) {
                    handle(message);
                }
            } catch (IOException e) {
                logger.info(
                        String.format("Connection of Controller is closed", controller.getPort(), port));
            } finally {
                controller.close();
            }

        }

        public void handle(String message) {
            String[] messageParts = message.split(" ");
            String command = messageParts[0];
            try {
                switch (command) {
                    case Message.REMOVE -> remove(messageParts);
                    // rebalance operation
                    case Message.LIST -> list(messageParts);
                    case Message.REBALANCE -> rebalance(messageParts);
                    default -> logger.error("Invalid message : " + message);
                }
            } catch (IOException e) {
                // maybe connection is closed;
                if (controller.isClosed()) {
                    // do something
                }
            } catch (NumberFormatException e) {
                // ignore malformed message
                logger.error("Invalid message : " + message);
            }
        }

        public void remove(String[] messageParts) throws IOException {
            if (messageParts.length == 2) {
                File file = new File(directory, messageParts[1]);
                if (!file.exists()) {
                    controller.send(Message.ERROR_FILE_DOES_NOT_EXIST + " " + messageParts[1]);
                    return;
                }
                logger.info("Delete file " + file.getName());
                file.delete();
                controller.send(Message.REMOVE_ACK + ' ' + file.getName());
            }
        }

        public void list(String[] messageParts) throws IOException {
            if (messageParts.length == 1) {
                StringBuilder respBuilder = new StringBuilder(Message.LIST);
                for (String file : directory.list()) {
                    respBuilder.append(' ').append(file);
                }
                controller.send(respBuilder.toString());
            }
        }

        public void rebalance(String[] messageParts) throws NumberFormatException {
            rebalanceSend = new ConcurrentHashMap<>();
            int index = 1;
            boolean success = true;
            // send
            int sendNum = Integer.parseInt(messageParts[index++]);
            for (int i = 0; i < sendNum; i++) {
                String fileName = messageParts[index++];
                int nodeNum = Integer.parseInt(messageParts[index++]);
                for (int j = 0; j < nodeNum; j++) {
                    int node = Integer.parseInt(messageParts[index++]);
                    rebalanceSend.put(node, fileName);
                    if (!rebalanceStore(node, fileName)) {
                        success = false;
                    }
                }
            }
            // remove
            int removeNum = Integer.parseInt(messageParts[index++]);
            for (int i = 0; i < removeNum; i++) {
                String fileName = messageParts[index++];
                File file = new File(directory, fileName);
                file.delete();
            }

            if (success) {
                logger.info("Finish rebalance");
                controller.send(Message.REBALANCE_COMPLETE);
            }
        }

        public boolean rebalanceStore(int node, String fileName) {
            if (!otherDstores.containsKey(node) && !connectToOtherDstore(node)) {
                // remote dstore may fail
                return false;
            }

            // send rebalance request to other dstore
            File file = new File(directory, fileName);
            TCPConnection connection = otherDstores.get(node);
            connection.send(Message.REBALANCE_STORE + ' ' + fileName + ' ' + file.length());
            // waiting for ACK
            if (!timeoutTask(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    String msg;
                    try {
                        msg = connection.receive();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                    return msg.equals(Message.ACK);
                }
            }, null, "Timeout, failed to receive ACK from dstore [" + node + "]")) {
                return false;
            }
            // send file content
            try {
                connection.writeBytes(Files.readAllBytes(file.toPath()));
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }
    };

    public boolean timeoutTask(Callable<Boolean> task, Callable<Void> failureHandler, String timeoutMessage) {
        Future<Boolean> future = timeoutService.submit(task);
        try {
            if (!future.get(timeout, TimeUnit.MILLISECONDS)) {
                failureHandler.call();
                return false;
            }
        } catch (TimeoutException e) {
            // timeout
            logger.error(timeoutMessage);
            return false;
        } catch (Exception e) {
            // other error
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean connectToOtherDstore(int port) {
        try {
            Socket socket = new Socket(InetAddress.getByName("localhost"), port);
            otherDstores.put(port, new TCPConnection(socket, logger));
            connectionsToPort.put(otherDstores.get(port), port);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Cannot connect to Dstore [" + port + "]");
            return false;
        }
        return true;
    }

    public boolean connectToController() {
        try {
            Socket socket = new Socket(InetAddress.getByName("localhost"), cport);
            controller = new TCPConnection(socket, logger);
            controller.send(Message.JOIN + ' ' + port); // join
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Cannot connect to controller");
            return false;
        }
        return true;
    }

    public void startDstore() {
        if (server.normal()) {
            logger.info(String.format(
                    "Start Dstore  [port %d] with arguments: [timeout %d] [file folder %s]", port, timeout,
                    directory.getName()));
            // join to the group
            if (connectToController()) {
                new Thread(controllerRoutine).start();
            }

            server.run();
        }
    }

    public Dstore(int port, int cport, int timeout, String fileFolder) {
        this.port = port;
        this.cport = cport;
        this.timeout = timeout;
        this.logger = new Logger("Dstore [" + port + "]");
        this.otherDstores = new ConcurrentHashMap<>();
        this.connectionsToPort = new ConcurrentHashMap<>();
        this.timeoutService = Executors.newSingleThreadExecutor();
        // file directory
        this.directory = new File(fileFolder);
        if (!directory.exists() && !directory.mkdir()) {
            throw new RuntimeException("Cannot create new directory in " + directory.getAbsolutePath());
        }
        // clean the existing files
        for (File file : directory.listFiles()) {
            file.delete();
        }
        this.server = new TCPServer(port, timeout, new Handler() {
            @Override
            public void handle(TCPConnection connection, String message) {
                // dispatch the message to specific handler
                if (Message.isRebalanceStore(message) || otherDstores.containsValue(connection)) {
                    dstoreHandler.handle(connection, message);
                } else {
                    clientHandler.handle(connection, message);
                }
            }
        }, logger);
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        int cport = Integer.parseInt(args[1]);
        int timeout = Integer.parseInt(args[2]);
        String fileFolder = args[3];
        Dstore dstore = new Dstore(port, cport, timeout, fileFolder);
        dstore.startDstore();
    }
}
