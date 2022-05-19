import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FileIndex {
    private final Map<String, Set<Integer>> fileToNodes; // file -> nodes holding this file

    private final Map<Integer, Set<String>> nodeToFiles; // node -> all of the files held by that node

    private final Map<String, Set<Integer>> storingFiles; // pending store operation

    private final Map<String, Set<Integer>> removingFiles; // pending remove operation

    private final Map<String, MetaFile> files;

    private final Logger logger;

    private final int R;

    public FileIndex(Logger logger, int R) {
        this.fileToNodes = new HashMap<>();
        this.nodeToFiles = new HashMap<>();
        this.storingFiles = new HashMap<>();
        this.removingFiles = new HashMap<>();
        this.files = new HashMap<>();
        this.logger = logger;
        this.R = R;
    }

    public void addNode(int nodePort) {
        synchronized (nodeToFiles) {
            nodeToFiles.put(nodePort, new HashSet<>());
        }
    }

    public ArrayList<String> fileList() {
        synchronized (files) {
            ArrayList<String> list = new ArrayList<>();
            for (var file : files.values()) {
                if (file.getState() == MetaFile.State.AVAILABLE) {
                    list.add(file.getName());
                }
            }
            return list;
        }
    }

    public MetaFile getFile(String file) {
        synchronized (files) {
            return files.get(file);
        }
    }

    // get loads to load a file
    public ArrayList<Integer> getLoadNodes(String file) {
        ArrayList<Integer> nodes = new ArrayList<>();
        synchronized (fileToNodes) {
            Set<Integer> group = fileToNodes.get(file);
            if (group == null) {
                return null;
            }
            group.forEach(e -> {
                nodes.add(e);
            });
        }
        return nodes;
    }

    public Integer[] getStoreNodes() {
        Integer[] nodes = new Integer[R];
        synchronized (nodeToFiles) {
            int count = 0;
            for (Integer node : nodeToFiles.keySet()) {
                int max = 0;
                if (count < R) {
                    nodes[count] = node;
                    count++;
                    continue;
                }

                for (int i = 0; i < R; i++) {
                    // adjust max
                    if (nodeToFiles.get(nodes[i]).size() > nodeToFiles.get(nodes[max]).size()) {
                        max = i;
                    }
                }

                // replace
                if (nodeToFiles.get(node).size() < nodeToFiles.get(nodes[max]).size()) {
                    nodes[max] = node;
                }
            }
        }
        return nodes;
    }

    // NOT THREAD SAFE, only used in rebalance operation
    public Map<String, Set<Integer>> getFileToNodes() {
        return fileToNodes;
    }

    // NOT THREAD SAFE, only used in rebalance operation
    public Map<Integer, Set<String>> getNodeToFiles() {
        return nodeToFiles;
    }

    // NOT THREAD SAFE, only used in rebalance operation
    public Map<String, MetaFile> getFiles() {
        return files;
    }

    // NOT THREAD SAFE, only used in rebalance operation
    public Integer[] findNodesToStore(String file, int num) {
        Integer[] nodes = new Integer[num];
        int count = 0;
        for (Integer node : nodeToFiles.keySet()) {
            int max = 0;
            if (fileToNodes.get(file).contains(node)) {
                continue;
            }

            if (count < num) {
                nodes[count] = node;
                count++;
                continue;
            }

            for (int i = 0; i < num; i++) {
                // adjust max
                if (nodeToFiles.get(nodes[i]).size() > nodeToFiles.get(nodes[max]).size()) {
                    max = i;
                }
            }

            // replace
            if (nodeToFiles.get(node).size() < nodeToFiles.get(nodes[max]).size()) {
                nodes[max] = node;
            }
        }
        return nodes;
    }

    // NOT THREAD SAFE, only used in rebalance operation
    public Integer findNodeToRecv(String file, int ceil) {
        int res = -1;
        for (int node : nodeToFiles.keySet()) {
            if (fileToNodes.get(file).contains(node)) {
                continue;
            }

            if (res == -1) {
                res = node;
            }

            // replace
            if (nodeToFiles.get(node).size() < nodeToFiles.get(res).size()) {
                res = node;
            }
        }
        if (nodeToFiles.get(res).size() >= ceil) {
            return null;
        }
        return res;
    }

    // NOT THREAD SAFE, only used in rebalance operation
    public Integer findNodeToMove(String file, int floor) {
        int res = -1;
        for (int node : nodeToFiles.keySet()) {
            if (!fileToNodes.get(file).contains(node)) {
                continue;
            }

            if (res == -1) {
                res = node;
            }

            // replace
            if (nodeToFiles.get(node).size() > nodeToFiles.get(res).size()) {
                res = node;
            }
        }
        if (nodeToFiles.get(res).size() <= floor) {
            return null;
        }
        return res;
    }

    // NOT THREAD SAFE, only used in rebalance operation
    public Integer[] findNodesToRemove(String file, int num) {
        Integer[] nodes = new Integer[num];
        int count = 0;
        for (Integer node : nodeToFiles.keySet()) {
            int min = 0;
            if (!fileToNodes.get(file).contains(node)) {
                continue;
            }

            if (count < num) {
                nodes[count] = node;
                count++;
                continue;
            }

            for (int i = 0; i < num; i++) {
                // adjust min
                if (nodeToFiles.get(nodes[i]).size() < nodeToFiles.get(nodes[min]).size()) {
                    min = i;
                }
            }

            // replace
            if (nodeToFiles.get(node).size() > nodeToFiles.get(nodes[min]).size()) {
                nodes[min] = node;
            }
        }
        return nodes;
    }

    public void startStoringFile(MetaFile file) throws FileIndexException {
        synchronized (files) {
            if (files.containsKey(file.getName())) {
                logger.error("File " + file.getName() + " is already exist in FileIndex!!!");
                throw new FileIndexException(Message.ERROR_FILE_ALREADY_EXISTS);
            }
            files.put(file.getName(), file);
        }

        synchronized (storingFiles) {
            if (storingFiles.putIfAbsent(file.getName(), new HashSet<>()) != null) {
                logger.error("File " + file.getName() + " is already in Storing stage!!!");
                throw new FileIndexException(Message.ERROR_FILE_ALREADY_EXISTS);
            }
        }
        logger.info("Start storing file " + file.getName());
    }

    public void revokeStoring(String file) {
        synchronized (storingFiles) {
            if (storingFiles.remove(file) != null) {
                logger.error("Revoke storing file operation of " + file);
            }
        }

        synchronized (files) {
            if (files.remove(file) != null) {
                logger.error("Revoke stored file " + file);
            }
        }
    }

    public void storeAck(String file, Integer node) {
        synchronized (storingFiles) {
            Set<Integer> group = storingFiles.get(file);
            if (group == null) {
                logger.error("File " + file + " is not in Storing stage!!!");
                return;
            }

            if (!group.add(node)) {
                logger.error("Have received store ack from node " + node);
                return;
            }
            logger.info("Receive store ack from node" + node);

            if (group.size() == R) {
                logger.info("All of the nodes finish storing file " + file);
                storeFile(file, group);
                storingFiles.remove(file);
            }
        }
    }

    private void storeFile(String file, Set<Integer> group) {
        String groupStr = "";
        synchronized (nodeToFiles) {
            for (Integer node : group) {
                if (!nodeToFiles.get(node).add(file)) {
                    logger.error("File " + file + " is already in" + node);
                }
                groupStr += (" " + node);
            }
        }

        synchronized (fileToNodes) {
            fileToNodes.put(file, group);
        }

        synchronized (files) {
            files.get(file).setState(MetaFile.State.AVAILABLE);
        }
        logger.info("Finish Storing file " + file + " to " + groupStr);
    }

    public void startRemovingFile(String file) throws FileIndexException {
        synchronized (files) {
            if (!files.containsKey(file)) {
                logger.error("File " + file + " does not exist in FileIndex!!!");
                throw new FileIndexException(Message.ERROR_FILE_DOES_NOT_EXIST);
            }
            files.get(file).setState(MetaFile.State.REMOVING);
        }

        synchronized (removingFiles) {
            if (removingFiles.putIfAbsent(file, new HashSet<>()) != null) {
                logger.error("File " + file + " is already in Removing stage!!!");
                throw new FileIndexException(Message.ERROR_FILE_DOES_NOT_EXIST);
            }
        }
        logger.info("Start removing file " + file);
    }

    public void removeAck(String file, Integer node) {
        synchronized (removingFiles) {
            Set<Integer> group = removingFiles.get(file);

            if (group == null) {
                logger.error("File " + file + " is not in Removing stage!!!");
                return;
            }

            if (!group.add(node)) {
                logger.error("Have received remove ack from node " + node);
                return;
            }
            logger.info("Receive remove ack from node " + node);

            if (removingFiles.get(file).size() == R) {
                logger.info("All of the nodes finish removing file " + file);
                removeFile(file);
                removingFiles.remove(file);
            }
        }
    }

    private void removeFile(String file) {
        synchronized (files) {
            if (files.remove(file) == null) {
                logger.error("File " + file + "does not exist!!!");
                return;
            }
        }

        synchronized (fileToNodes) {
            fileToNodes.remove(file);
        }

        synchronized (nodeToFiles) {
            nodeToFiles.values().forEach(e -> {
                e.remove(file);
            });
        }
    }

    public void forceRemove(String file) {
        synchronized (removingFiles) {
            removingFiles.remove(file);
        }

        synchronized (files) {
            files.remove(file);
        }

        synchronized (fileToNodes) {
            fileToNodes.remove(file);
        }

        synchronized (nodeToFiles) {
            nodeToFiles.values().forEach(e -> {
                e.remove(file);
            });
        }
    }
}
