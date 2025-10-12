package core;

public class Consumer implements Runnable {
    private final IQueue<Message> queue;
    private final int totalMessages;
    private final MetricsRecorder metrics;

    public Consumer(IQueue<Message> queue, int totalMessages, MetricsRecorder metrics) {
        this.queue = queue;
        this.totalMessages = totalMessages;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        for (int i = 0; i < totalMessages; i++) {
            long start = System.nanoTime();
            Message msg = queue.dequeue();
            long end = System.nanoTime();
            metrics.recordDequeue(end - start);
        }
    }
}
