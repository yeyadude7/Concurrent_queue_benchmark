package simulation;

import core.IQueue;
import core.MetricsRecorder;
import java.util.concurrent.ThreadLocalRandom;

public class RequestGenerator implements Runnable {
    private final IQueue<Request> ingressQueue;
    private final int count;
    private final int maxDelayMicros;
    private final MetricsRecorder metrics;

    public RequestGenerator(IQueue<Request> ingressQueue, int count, int maxDelayMicros, MetricsRecorder metrics) {
        this.ingressQueue = ingressQueue;
        this.count = count;
        this.maxDelayMicros = maxDelayMicros;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        for (int i = 0; i < count; i++) {
            Request req = new Request(i, "req-" + i);
            req.markEnqueued();
            long start = System.nanoTime();
            ingressQueue.enqueue(req);
            long end = System.nanoTime();
            metrics.recordEnqueue(end - start);

            try {
                Thread.sleep(0, ThreadLocalRandom.current().nextInt(maxDelayMicros * 1000));
            } catch (InterruptedException ignored) {}
        }
    }
}
