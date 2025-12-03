// src/core/Message.java
package core;

public class Message {
    private final int id;
    private final String payload;

    // Timing
    private final long createdAt;
    private long enqueuedAt;
    private long dequeuedAt;

    // Poison flag
    private final boolean poison;

    public static Message poisonPill() {
        return new Message(-1, "__POISON__", true);
    }

    public Message(int id, String payload) {
        this(id, payload, false);
    }

    private Message(int id, String payload, boolean poison) {
        this.id = id;
        this.payload = payload;
        this.poison = poison;
        this.createdAt = System.nanoTime();
    }

    public boolean isPoison() { return poison; }

    public void markEnqueued() { this.enqueuedAt = System.nanoTime(); }
    public void markDequeued() { this.dequeuedAt = System.nanoTime(); }

    public long getLatencyNanos() {
        if (enqueuedAt == 0 || dequeuedAt == 0) return 0L;
        return dequeuedAt - enqueuedAt;
    }

    public int getId() { return id; }
    public String getPayload() { return payload; }
    public long getCreatedAt() { return createdAt; }
    public long getEnqueuedAt() { return enqueuedAt; }
    public long getDequeuedAt() { return dequeuedAt; }

    @Override
    public String toString() {
        return "Message{id=" + id + ", poison=" + poison + "}";
    }
}
