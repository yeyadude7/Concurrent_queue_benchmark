package core;

public class Producer implements Runnable {
    private final IQueue<Message> queue;
    private final int startId;
    private final int messageCount;
    private final MetricsRecorder metrics;

    public Producer(IQueue<Message> queue, int startId, int messageCount, MetricsRecorder metrics) {
        this.queue = queue;
        this.startId = startId;
        this.messageCount = messageCount;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        for (int i = 0; i < messageCount; i++) {
            long start = System.nanoTime();
            queue.enqueue(new Message(startId + i, "msg-" + (startId + i)));
            long end = System.nanoTime();
            metrics.recordEnqueue(end - start);
        }
    }
}
