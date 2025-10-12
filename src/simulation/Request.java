package simulation;

public class Request {
    private final int id;
    private final long createdAt;  // time of creation
    private long enqueuedAt;       // time producer enqueued
    private long dequeuedAt;       // time consumer dequeued

    private final String payload;

    public Request(int id, String payload) {
        this.id = id;
        this.payload = payload;
        this.createdAt = System.nanoTime();
    }

    public int getId() { return id; }
    public String getPayload() { return payload; }

    public void markEnqueued() { this.enqueuedAt = System.nanoTime(); }
    public void markDequeued() { this.dequeuedAt = System.nanoTime(); }

    public long getLatencyNanos() {
        if (enqueuedAt == 0 || dequeuedAt == 0) return 0;
        return dequeuedAt - enqueuedAt;
    }
}
