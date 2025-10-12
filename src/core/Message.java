package core;

public class Message {
    private final int id;
    private final long timestamp;
    private final String payload;

    public Message(int id, String payload) {
        this.id = id;
        this.payload = payload;
        this.timestamp = System.nanoTime(); // capture creation time
    }

    public int getId() { return id; }
    public long getTimestamp() { return timestamp; }
    public String getPayload() { return payload; }

    @Override
    public String toString() {
        return "Message{id=" + id + ", payload='" + payload + "'}";
    }
}
