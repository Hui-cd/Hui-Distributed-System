import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Controller {
    private final int cport; // port of Controller
    private final int R; // Replication factor - number of Dstores
    private final int timeout; // Timeout period in ms
    private final int rebalance_period; // period to call Rebalance in ms
    private final TCPServer server;
    private final Logger logger;
    private final FileIndex fileIndex;

    private final ConcurrentHashMap<Integer, TCPConnection> connections; // node port -> connection
    private final ConcurrentHashMap<TCPConnection, Integer> connectionsToPort;

    // rebalance relative
    private volatile Boolean firstRebalance = true;
    private AtomicLong rebalanceTime = new AtomicLong(); // time for rebalance
    private final Object rebalanceLock = new Object();
    private volatile int workers = 0; // how many client request is processing
    private volatile boolean needRebalance = false; // prevent starvation
    private Map<Integer, Boolean> liveness;
    private Map<Integer, Set<String>> filesToRemove;
    private Map<Integer, Map<String, Set<Integer>>> filesToSend;

    private final Handler clientHandler = new Handler() {
        private final ConcurrentHashMap<TCPConnection, ConcurrentHashMap<String, ArrayList<Integer>>> loadAttempts = new ConcurrentHashMap<>();

        @Override
        public void handle(TCPConnection connection, String message) {
            String[] messageParts = message.split(" ");
            String command = messageParts[0];
            try {
                switch (command) {
                    case Message.LIST -> list(connection, messageParts);
                    case Message.RELOAD -> reload(connection, messageParts);
                    case Message.LOAD -> load(connection, messageParts);
                    case Message.STORE -> store(connection, messageParts);
                    case Message.REMOVE -> remove(connection, messageParts);
                    default -> logger.error("Invalid message : " + message);
                }
            } catch (NumberFormatException e) {
                // ignore malformed message
                logger.error("Invalid message : " + message);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void list(TCPConnection connection, String[] messageParts) throws InterruptedException {
            synchronized (rebalanceLock) {
                while (needRebalance) {
                    rebalanceLock.wait();
                }
                workers++;
            }
            if (connections.size() < R) {
                connection.send(Message.ERROR_NOT_ENOUGH_DSTORES);
            } else if (messageParts.length == 1) {
                ArrayList<String> list = fileIndex.fileList();
                StringBuilder messageBuilder = new StringBuilder(Message.LIST);
                list.stream().forEach(fileName -> messageBuilder.append(' ').append(fileName));
                connection.send(messageBuilder.toString());
            }
            synchronized (rebalanceLock) {
                workers--;
                if (workers == 0) {
                    rebalanceLock.notifyAll();
                }
            }
        }

        private void store(TCPConnection connection, String[] messageParts) throws InterruptedException {
            synchronized (rebalanceLock) {
                while (needRebalance) {
                    rebalanceLock.wait();
                }
                workers++;
            }
            if (connections.size() < R) {
                connection.send(Message.ERROR_NOT_ENOUGH_DSTORES);
            } else if (messageParts.length == 3) {
                // parse arguments
                String fileName = messageParts[1];
                int fileSize = Integer.parseInt(messageParts[2]);
                MetaFile file = new MetaFile(fileName, fileSize);
                try {
                    fileIndex.startStoringFile(file);
                    Integer[] nodes = fileIndex.getStoreNodes();
                    StringBuilder messageBuilder = new StringBuilder(Message.STORE_TO);
                    for (Integer node : nodes) {
                        messageBuilder.append(' ').append(node);
                    }
                    connection.send(messageBuilder.toString());
                    // waiting for ACK
                    long endTime = System.currentTimeMillis() + timeout;
                    while (System.currentTimeMillis() <= endTime
                            && fileIndex.getFile(fileName).getState() != MetaFile.State.AVAILABLE) {
                    }
                    if (fileIndex.getFile(fileName).getState() == MetaFile.State.AVAILABLE) {
                        // complete
                        connection.send(Message.STORE_COMPLETE);
                    } else {
                        // timeout
                        logger.error("Timeout to store file " + fileName);
                        fileIndex.revokeStoring(fileName);
                    }

                } catch (FileIndexException e) {
                    connection.send(e.getMessage());
                }
            }
            synchronized (rebalanceLock) {
                workers--;
                if (workers == 0) {
                    rebalanceLock.notifyAll();
                }
            }
        }

        private void load(TCPConnection connection, String[] messageParts) throws InterruptedException {
            synchronized (rebalanceLock) {
                while (needRebalance) {
                    rebalanceLock.wait();
                }
                workers++;
            }
            if (connections.size() < R) {
                connection.send(Message.ERROR_NOT_ENOUGH_DSTORES);
            } else if (messageParts.length == 2) {
                // parse arguments
                MetaFile file = fileIndex.getFile(messageParts[1]);
                if (file == null || file.getState() != MetaFile.State.AVAILABLE) {
                    logger.error("File " + messageParts[1] + " does not exist");
                    connection.send(Message.ERROR_FILE_DOES_NOT_EXIST);
                } else {
                    // attempts
                    loadAttempts.putIfAbsent(connection, new ConcurrentHashMap<>());
                    loadAttempts.get(connection).put(file.getName(), fileIndex.getLoadNodes(messageParts[1]));

                    // response
                    StringBuilder messageBuilder = new StringBuilder(Message.LOAD_FROM);
                    messageBuilder.append(' ')
                            .append(loadAttempts.get(connection).get(file.getName()).get(0))
                            .append(' ')
                            .append(file.getSize());
                    connection.send(messageBuilder.toString());
                    loadAttempts.get(connection).get(file.getName()).remove(0);
                }
            }
            synchronized (rebalanceLock) {
                workers--;
                if (workers == 0) {
                    rebalanceLock.notifyAll();
                }
            }
        }

        private void reload(TCPConnection connection, String[] messageParts) throws InterruptedException {
            synchronized (rebalanceLock) {
                while (needRebalance) {
                    rebalanceLock.wait();
                }
                workers++;
            }
            if (connections.size() < R) {
                connection.send(Message.ERROR_NOT_ENOUGH_DSTORES);
            } else if (messageParts.length == 2) {
                // parse arguments
                MetaFile file = fileIndex.getFile(messageParts[1]);
                if (file == null || file.getState() != MetaFile.State.AVAILABLE) {
                    logger.error("File " + messageParts[1] + " does not exist");
                    connection.send(Message.ERROR_FILE_DOES_NOT_EXIST);
                } else if (loadAttempts.get(connection).get(file.getName()).size() == 0) {
                    // ERROR_LOAD
                    loadAttempts.get(connection).remove(file.getName());
                    connection.send(Message.ERROR_LOAD);
                } else {
                    // response
                    StringBuilder messageBuilder = new StringBuilder(Message.LOAD_FROM);
                    messageBuilder.append(' ')
                            .append(loadAttempts.get(connection).get(file.getName()).get(0))
                            .append(' ')
                            .append(file.getSize());
                    connection.send(messageBuilder.toString());
                    loadAttempts.get(connection).get(file.getName()).remove(0);
                }
            }
            synchronized (rebalanceLock) {
                workers--;
                if (workers == 0) {
                    rebalanceLock.notifyAll();
                }
            }
        }

        private void remove(TCPConnection connection, String[] messageParts) throws InterruptedException {
            synchronized (rebalanceLock) {
                while (needRebalance) {
                    rebalanceLock.wait();
                }
                workers++;
            }
            if (connections.size() < R) {
                connection.send(Message.ERROR_NOT_ENOUGH_DSTORES);
            } else if (messageParts.length == 2) {
                String fileName = messageParts[1];
                try {
                    MetaFile metaFile = fileIndex.getFile(fileName);
                    // does not exist
                    if (metaFile == null || metaFile.getState() != MetaFile.State.AVAILABLE) {
                        connection.send(Message.ERROR_FILE_DOES_NOT_EXIST);
                    } else {
                        // send remove command to each Dstore
                        fileIndex.startRemovingFile(fileName);
                        final ArrayList<Integer> group = fileIndex.getLoadNodes(fileName);
                        for (Integer node : group) {
                            TCPConnection conn = connections.get(node);
                            conn.send(Message.REMOVE + ' ' + fileName);
                        }
                        // wait for ACK
                        long endTime = System.currentTimeMillis() + timeout;
                        while (System.currentTimeMillis() <= endTime
                                && fileIndex.getFile(fileName) != null) {
                        }

                        if (fileIndex.getFile(fileName) == null) {
                            // complete
                            connection.send(Message.REMOVE_COMPLETE);
                        } else {
                            // timeout, force remove the file from FileIndex. rebalance operation will
                            // finished the remove
                            fileIndex.forceRemove(fileName);
                        }
                    }
                } catch (FileIndexException e) {
                    connection.send(e.getMessage());
                }
            }
            synchronized (rebalanceLock) {
                workers--;
                if (workers == 0) {
                    rebalanceLock.notifyAll();
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
                switch (command) {
                    case Message.JOIN -> join(connection, messageParts);
                    case Message.STORE_ACK -> storeACK(connection, messageParts);
                    case Message.REMOVE_ACK -> removeACK(connection, messageParts);
                    // rebalance operation
                    case Message.LIST -> list(connection, messageParts);
                    case Message.REBALANCE_COMPLETE -> rebalanceComplete(connection, messageParts);
                    default -> logger.error("Invalid message : " + message);
                }
            } catch (NumberFormatException e) {
                // ignore malformed message
                logger.error("Invalid message : " + message);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void join(TCPConnection connection, String[] messageParts)
                throws NumberFormatException, InterruptedException {
            synchronized (rebalanceLock) {
                while (needRebalance) {
                    rebalanceLock.wait();
                }
                workers++;
            }
            if (messageParts.length == 2) {
                if (!firstRebalance) {
                    rebalanceTime.set(System.currentTimeMillis());
                }
                int port = Integer.parseInt(messageParts[1]);
                connections.put(port, connection);
                connectionsToPort.put(connection, port);
                fileIndex.addNode(port);
                logger.info("New dstore [" + port + "] joined ");
            }
            synchronized (rebalanceLock) {
                workers--;
                if (workers == 0) {
                    rebalanceLock.notifyAll();
                }
            }
        }

        public void storeACK(TCPConnection connection, String[] messageParts) {
            if (messageParts.length == 2) {
                String fileName = messageParts[1];
                fileIndex.storeAck(fileName, connectionsToPort.get(connection));
            }
        }

        public void removeACK(TCPConnection connection, String[] messageParts) {
            if (messageParts.length == 2) {
                String fileName = messageParts[1];
                fileIndex.removeAck(fileName, connectionsToPort.get(connection));
            }
        }

        public void list(TCPConnection connection, String[] messageParts) {
            Set<String> files = new HashSet<>();
            var index = fileIndex.getFiles();
            Integer port = connectionsToPort.get(connection);
            if (port == null) {
                // this node have been marked lost
                return;
            }
            filesToRemove.put(port, ConcurrentHashMap.newKeySet());
            filesToSend.put(port, new ConcurrentHashMap<>());

            // handle files in dstore
            for (int i = 1; i < messageParts.length; i++) {
                if (index.containsKey(messageParts[i])) {
                    files.add(messageParts[i]);
                } else {
                    filesToRemove.get(port).add(messageParts[i]);
                }
            }
            synchronized (fileIndex) {
                fileIndex.getNodeToFiles().put(port, files);
                for (var file : files) {
                    if (!fileIndex.getFileToNodes().containsKey(file)) {
                        fileIndex.getFileToNodes().put(file, new HashSet<>());
                    }
                    fileIndex.getFileToNodes().get(file).add(port);
                }
            }
            liveness.put(port, true);
        }

        public void rebalanceComplete(TCPConnection connection, String[] messageParts) {
            if (messageParts.length == 1) {
                liveness.put(connectionsToPort.get(connection), true);
            }
        }
    };

    private final Runnable rebalanceRoutine = new Runnable() {
        @Override
        public void run() {
            try {
                while (true) {
                    while (System.currentTimeMillis() < rebalanceTime.get()) {
                        // waiting
                    }

                    synchronized (rebalanceLock) {
                        needRebalance = true;
                        while (workers != 0) {
                            rebalanceLock.wait();
                        }
                    }
                    firstRebalance = false;
                    // start rebalance
                    logger.info("************** Start rebalance operation!!! **************");
                    liveness = new ConcurrentHashMap<>();
                    fileIndex.getFileToNodes().clear();
                    fileIndex.getNodeToFiles().clear();
                    filesToRemove = new ConcurrentHashMap<>();
                    filesToSend = new ConcurrentHashMap<>();
                    // 1. send list
                    for (TCPConnection connection : connections.values()) {
                        connection.send(Message.LIST);
                    }

                    // 2. waiting for ACK
                    long endTime = System.currentTimeMillis() + timeout;
                    while (System.currentTimeMillis() <= endTime) {
                        if (liveness.size() == connections.size()) {
                            break;
                        }
                    }

                    // 3. ensure nodes liveness
                    // some nodes failed
                    if (liveness.size() < connections.size()) {
                        logger.error("Timeout, cannot connect to some nodes");
                        // remove unreachable nodes
                        var needToRemove = new ArrayList<Integer>();
                        for (int port : connections.keySet()) {
                            if (!liveness.containsKey(port)) {
                                needToRemove.add(port);
                            }
                        }
                        for (int port : needToRemove) {
                            connectionsToPort.remove(connections.get(port));
                            connections.remove(port);
                        }
                    }

                    boolean success = false;
                    if (liveness.size() >= R) {
                        assert connections.size() == connectionsToPort.size() && connections.size() == liveness.size();

                        // 4. file allocation
                        logger.info("************** Start reallocation !!! **************");
                        // if all of the replicas lost, need clean invalid index information
                        ArrayList<String> needToRemove = new ArrayList<>();
                        for (String fileName : fileIndex.getFiles().keySet()) {
                            if (!fileIndex.getFileToNodes().containsKey(fileName)) {
                                needToRemove.add(fileName);
                            }
                        }
                        for (String fileName : needToRemove) {
                            logger.error("Clean invalid file index: " + fileName);
                            fileIndex.getFiles().remove(fileName);
                        }

                        assert fileIndex.getFileToNodes().size() == fileIndex.getFiles().size();

                        // make up replicas or remove duplicate replicas
                        for (Entry<String, Set<Integer>> entry : fileIndex.getFileToNodes().entrySet()) {
                            String fileName = entry.getKey();
                            Set<Integer> group = entry.getValue();
                            assert group.size() != 0;
                            int sourceNode = group.iterator().next();
                            if (group.size() < R) {
                                Integer[] nodes = fileIndex.findNodesToStore(fileName, R - group.size());
                                if (!filesToSend.get(sourceNode).containsKey(fileName)) {
                                    filesToSend.get(sourceNode).put(fileName, ConcurrentHashMap.newKeySet());
                                }
                                for (int node : nodes) {
                                    filesToSend.get(sourceNode).get(fileName).add(node);
                                    fileIndex.getNodeToFiles().get(node).add(fileName);
                                    fileIndex.getFileToNodes().get(fileName).add(node);
                                }
                            } else if (group.size() > R) {
                                Integer[] nodes = fileIndex.findNodesToRemove(fileName, group.size() - R);
                                for (int node : nodes) {
                                    filesToRemove.get(node).add(fileName);
                                    fileIndex.getNodeToFiles().get(node).remove(fileName);
                                    fileIndex.getFileToNodes().get(fileName).remove(node);
                                }
                            }
                        }

                        // reallocation
                        int maxNum = (int) Math.ceil(R * fileIndex.getFiles().size() * 1.0 / connections.size());
                        int minNum = (int) Math.floor(R * fileIndex.getFiles().size() * 1.0 / connections.size());
                        while (true) {
                            // determine nodes which have too many files
                            HashMap<Integer, Integer> nodesNeedToSend = new HashMap<>();
                            for (Entry<Integer, Set<String>> entry : fileIndex.getNodeToFiles().entrySet()) {
                                int node = entry.getKey();
                                Set<String> files = entry.getValue();
                                if (files.size() > maxNum) {
                                    nodesNeedToSend.put(node, files.size() - maxNum);
                                }
                            }

                            // move file
                            for (int node : nodesNeedToSend.keySet()) {
                                ArrayList<String> toBeRemoved = new ArrayList<>();
                                int count = nodesNeedToSend.get(node);
                                for (String fileName : fileIndex.getNodeToFiles().get(node)) {
                                    Integer recvNode = fileIndex.findNodeToRecv(fileName, maxNum);
                                    if (recvNode == null) {
                                        continue;
                                    }
                                    count--;
                                    filesToRemove.get(node).add(fileName);
                                    if (!filesToSend.get(node).containsKey(fileName)) {
                                        filesToSend.get(node).put(fileName, ConcurrentHashMap.newKeySet());
                                    }
                                    filesToSend.get(node).get(fileName).add(recvNode);
                                    // move this file to the receiving node
                                    // remove
                                    toBeRemoved.add(fileName);
                                    fileIndex.getFileToNodes().get(fileName).remove(node);
                                    // add
                                    fileIndex.getFileToNodes().get(fileName).add(recvNode);
                                    fileIndex.getNodeToFiles().get(recvNode).add(fileName);
                                    if (count == 0)
                                        break;
                                }
                                for (String fileName : toBeRemoved) {
                                    fileIndex.getNodeToFiles().get(node).remove(fileName);
                                }
                            }

                            // determine nodes which have too few files
                            HashMap<Integer, Integer> nodesNeedToRecv = new HashMap<>();
                            for (Entry<Integer, Set<String>> entry : fileIndex.getNodeToFiles().entrySet()) {
                                int node = entry.getKey();
                                Set<String> files = entry.getValue();
                                if (files.size() < minNum) {
                                    nodesNeedToRecv.put(node, minNum - files.size());
                                }
                            }

                            // move file
                            for (int node : nodesNeedToRecv.keySet()) {
                                ArrayList<String> toBeAdded = new ArrayList<>();
                                int count = nodesNeedToRecv.get(node);
                                for (String fileName : fileIndex.getFiles().keySet()) {
                                    if (!fileIndex.getNodeToFiles().get(node).contains(fileName)) {
                                        Integer sendNode = fileIndex.findNodeToMove(fileName, minNum);
                                        if (sendNode == null) {
                                            continue;
                                        }
                                        count--;

                                        filesToRemove.get(sendNode).add(fileName);
                                        if (!filesToSend.get(sendNode).containsKey(fileName)) {
                                            filesToSend.get(sendNode).put(fileName, ConcurrentHashMap.newKeySet());
                                        }
                                        filesToSend.get(sendNode).get(fileName).add(node);
                                        // move this file to the receiving node
                                        // remove
                                        fileIndex.getNodeToFiles().get(sendNode).remove(fileName);
                                        fileIndex.getFileToNodes().get(fileName).remove(sendNode);
                                        // add
                                        fileIndex.getFileToNodes().get(fileName).add(node);
                                        toBeAdded.add(fileName);

                                        if (count == 0) {
                                            break;
                                        }
                                    }
                                }
                                for (String fileName : toBeAdded) {
                                    fileIndex.getNodeToFiles().get(node).add(fileName);
                                }
                            }
                            // terminate condition
                            if (nodesNeedToRecv.isEmpty() && nodesNeedToSend.isEmpty()) {
                                break;
                            }
                        }
                        // 5. send rebalance request
                        liveness.clear();
                        for (int node : connections.keySet()) {
                            StringBuilder respBuilder = new StringBuilder(Message.REBALANCE);
                            // to send
                            respBuilder.append(' ').append(filesToSend.get(node).size());
                            for (Entry<String, Set<Integer>> entry : filesToSend.get(node).entrySet()) {
                                String fileName = entry.getKey();
                                Set<Integer> nodes = entry.getValue();
                                respBuilder.append(' ').append(fileName).append(' ').append(nodes.size());
                                for (int recvNode : nodes) {
                                    respBuilder.append(' ').append(recvNode);
                                }
                            }
                            // to remove
                            respBuilder.append(' ').append(filesToRemove.get(node).size());
                            for (String fileName : filesToRemove.get(node)) {
                                respBuilder.append(' ').append(fileName);
                            }
                            // send rebalance command to dstore
                            connections.get(node).send(respBuilder.toString());
                        }
                        // 6. waiting for ACK
                        endTime = System.currentTimeMillis() + timeout;
                        while (System.currentTimeMillis() <= endTime) {
                            if (liveness.size() == connections.size()) {
                                success = true;
                                break;
                            }
                        }
                    } else {
                        logger.error("Too few nodes in group");
                    }
                    if (success) {
                        logger.info("************** Rebalance operation Succeeded!!! **************");
                    } else {
                        logger.error("************** Rebalance operation Failed!!! **************");
                    }
                    // anyhow end the rebalance
                    synchronized (rebalanceLock) {
                        needRebalance = false;
                        rebalanceLock.notifyAll();
                    }
                    // next rebalance time
                    rebalanceTime.set(System.currentTimeMillis() + rebalance_period);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    public void startController() {
        logger.info(String.format(
                "Start controller with arguments: [port %d] [replica %d] [timeout %d] [rebalance period %d]", cport, R,
                timeout, rebalance_period));
        new Thread(rebalanceRoutine).start();
        server.run(); // blocking
    }

    public Controller(int cport, int R, int timeout, int rebalance_period) {
        this.cport = cport;
        this.R = R;
        this.timeout = timeout;
        this.rebalance_period = rebalance_period;
        this.rebalanceTime.set(System.currentTimeMillis() + rebalance_period);
        this.logger = new Logger("Controller");
        this.connections = new ConcurrentHashMap<>();
        this.connectionsToPort = new ConcurrentHashMap<>();
        this.fileIndex = new FileIndex(logger, R);
        this.server = new TCPServer(cport, timeout, new Handler() {
            @Override
            public void handle(TCPConnection connection, String message) {
                // dispatch the message to specific handler
                if (Message.isJoin(message) || connections.containsValue(connection)) {
                    dstoreHandler.handle(connection, message);
                } else {
                    clientHandler.handle(connection, message);
                }
            }
        }, logger);
    }

    public static void main(String[] args) {
        // parse arguments
        int cport = Integer.parseInt(args[0]);
        int R = Integer.parseInt(args[1]);
        int timeout = Integer.parseInt(args[2]);
        int rebalance_period = Integer.parseInt(args[3]);
        // start controller
        Controller controller = new Controller(cport, R, timeout, rebalance_period);
        controller.startController();
    }
}
