public class MetaFile {
    enum State {
        AVAILABLE,
        STORING,
        REMOVING,
    }

    private final String name;
    private final int size;
    private State state;

    public MetaFile(String name, int size) {
        this.name = name;
        this.size = size;
        this.state = State.STORING;
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized void setState(State state) {
        this.state = state;
    }

    @Override
    public boolean equals(Object obj) {
        MetaFile file = (MetaFile) obj;
        return this.name == file.name;
    }
}
